# Incremental Backfill with Watermark Tracking — Implementation Spec

> **Status**: Ready for implementation  
> **Author**: Data Engineering team  
> **Date**: 2026-03-06  
> **Scope**: `data-engineering/` directory only — no changes to backend, frontend, or OLTP schema

---

## Problem

The current `run_backfill()` in `src/data_engineering/pipeline/backfill.py` does a **full-table extract** of all polls, votes, options, and users on every startup. As data grows, this becomes wasteful — re-processing thousands of unchanged rows every time the container restarts.

## Solution

Replace the full-table backfill with a **watermark-driven incremental approach**:

1. A `pipeline_watermarks` PostgreSQL table tracks the last-processed timestamp per OLTP entity
2. On first run (no watermarks): full load (existing behaviour)
3. On subsequent runs: extract only rows created/updated since the watermark
4. Identify affected `poll_ids` and `user_ids` from the deltas
5. Batch-extract full data for just those entities via `WHERE id IN (...)` queries
6. Recompute KPIs using the existing `compute_*` transformer functions (unchanged)
7. Upsert results and advance watermarks
8. Periodic re-sync every 30 minutes alongside the Kafka consumer for resilience

---

## Architecture Overview

```
Startup
  │
  ├─ create_analytics_tables()      # Now also creates pipeline_watermarks
  ├─ run_backfill()                 # ← CHANGED: watermark-aware
  │   ├─ FORCE_FULL or no watermarks? → _full_backfill() → set all watermarks
  │   └─ Watermarks exist? → incremental extract → identify affected IDs
  │       → batch scoped extract → transform → upsert → advance watermarks
  │
  └─ run_streaming()                # ← CHANGED: poll()-based loop with periodic backfill
      └─ while True:
          ├─ consumer.poll(timeout_ms=5000)
          ├─ process any messages (existing handlers)
          └─ if 30 min elapsed: run_backfill()
```

---

## OLTP Schema Reference (Source Tables)

These are the source tables in the shared PostgreSQL database. **Do not modify these.**

```
USERS
  id            bigint PK
  email         varchar UK
  password_hash varchar
  display_name  varchar
  role          varchar
  is_active     boolean
  created_at    timestamptz    ← watermark column
  updated_at    timestamptz    ← watermark column (catches modifications)

POLLS
  id            bigint PK
  creator_id    bigint FK → users.id
  title         varchar
  description   text
  poll_type     varchar
  status        varchar
  is_anonymous  boolean
  max_choices   int
  expires_at    timestamptz
  created_at    timestamptz    ← watermark column
  updated_at    timestamptz    ← watermark column (catches status changes like ACTIVE→CLOSED)

POLL_OPTIONS
  id            bigint PK
  poll_id       bigint FK → polls.id
  option_text   varchar
  display_order int
  created_at    timestamptz    ← watermark column (append-only, no updated_at)

VOTES
  id            bigint PK
  poll_id       bigint FK → polls.id
  option_id     bigint FK → poll_options.id
  user_id       bigint FK → users.id
  voted_at      timestamptz    ← watermark column (append-only, no updated_at)
```

**Important**: The current extractors reference `created_at` for votes, but the OLTP schema column name is `voted_at`. Check the actual database — the seed scripts may create `created_at` as an alias. Verify which column name is actually used and be consistent.

---

## Analytics Tables (Target — already exist)

```
analytics_poll_summary       (PK: poll_id)         — last_updated
analytics_option_breakdown   (PK: option_id)       — last_updated
analytics_votes_timeseries   (PK: id, UQ: poll_id+bucket_time) — recorded_at
analytics_user_participation (PK: user_id)          — last_updated
```

---

## Detailed Implementation Steps

### Step 1: Add Configuration Variables

**File**: `src/data_engineering/config.py`

Add these three new config vars in the `# ── Pipeline` section, after `LOG_LEVEL`:

```python
# ── Pipeline ──────────────────────────────────────────────────────────────────
LOG_LEVEL: str = config("LOG_LEVEL", default="INFO")
BACKFILL_INTERVAL_MINUTES: int = config("BACKFILL_INTERVAL_MINUTES", default=30, cast=int)
WATERMARK_OVERLAP_MINUTES: int = config("WATERMARK_OVERLAP_MINUTES", default=5, cast=int)
FORCE_FULL_BACKFILL: bool = config("FORCE_FULL_BACKFILL", default="false", cast=str).lower() == "true"
```

**Rationale**:
- `BACKFILL_INTERVAL_MINUTES` (default 30): How often the periodic backfill runs during streaming
- `WATERMARK_OVERLAP_MINUTES` (default 5): Subtracted from watermark when querying, to guard against clock skew — guarantees no missed data at cost of minor re-processing
- `FORCE_FULL_BACKFILL` (default false): Set to `true` for disaster recovery or after schema changes

---

### Step 2: Add `pipeline_watermarks` Table

**File**: `src/data_engineering/loading/models.py`

Add a new table definition after the existing analytics tables, using the same `metadata` object so it's created by `metadata.create_all()`:

```python
pipeline_watermarks = Table(
    "pipeline_watermarks",
    metadata,
    Column("entity_name", String(50), primary_key=True),
    Column("high_watermark", DateTime, nullable=False),
    Column("updated_at", DateTime, server_default=text("NOW()")),
)
```

**No changes needed to `create_analytics_tables()`** — it already calls `metadata.create_all(engine, checkfirst=True)`, which will pick up the new table automatically.

**Entities tracked**: `"polls"`, `"votes"`, `"options"`, `"users"`

---

### Step 3: Add Watermark Read/Write Helpers

**File**: `src/data_engineering/loading/writers.py`

Add two new functions. Import the new table at the top alongside existing imports:

```python
from data_engineering.loading.models import (
    analytics_option_breakdown,
    analytics_poll_summary,
    analytics_user_participation,
    analytics_votes_timeseries,
    pipeline_watermarks,           # ← ADD THIS
)
```

#### `get_watermark()`

```python
def get_watermark(entity_name: str) -> datetime | None:
    """Return the high-watermark timestamp for the given entity, or None if no row exists."""
    with get_engine().connect() as conn:
        row = conn.execute(
            pipeline_watermarks.select().where(
                pipeline_watermarks.c.entity_name == entity_name
            )
        ).fetchone()
    return row.high_watermark if row else None
```

#### `set_watermark()`

```python
def set_watermark(entity_name: str, value: datetime) -> None:
    """Upsert the high-watermark for the given entity."""
    stmt = insert(pipeline_watermarks).values(
        entity_name=entity_name,
        high_watermark=value,
        updated_at=_now(),
    )
    stmt = stmt.on_conflict_do_update(
        index_elements=["entity_name"],
        set_={
            "high_watermark": stmt.excluded.high_watermark,
            "updated_at": stmt.excluded.updated_at,
        },
    )
    with get_engine().begin() as conn:
        conn.execute(stmt)
    logger.debug("Watermark for '%s' set to %s", entity_name, value)
```

---

### Step 4: Add Incremental Extractors

**File**: `src/data_engineering/ingestion/extractors.py`

Add a new section after the existing scoped extracts. All functions follow the same pattern as existing extractors: accept an optional `engine` param, use parameterised queries, return a DataFrame.

#### Incremental (delta detection) extractors

```python
# ── Incremental extracts (used by backfill for delta detection) ───────────────

from datetime import datetime  # add to imports at top


def extract_polls_since(since: datetime, engine: Engine | None = None) -> pd.DataFrame:
    """Extract polls created or updated after the given timestamp."""
    engine = engine or get_engine()
    query = text("""
        SELECT p.id, p.title, p.active, p.multi_select, p.expires_at,
               p.created_at, p.creator_id, u.full_name AS creator_name
        FROM polls p
        JOIN users u ON p.creator_id = u.id
        WHERE p.updated_at > :since
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"since": since})


def extract_votes_since(since: datetime, engine: Engine | None = None) -> pd.DataFrame:
    """Extract votes created after the given timestamp."""
    engine = engine or get_engine()
    query = text("""
        SELECT id, poll_id, option_id, user_id, created_at
        FROM votes
        WHERE created_at > :since
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"since": since})


def extract_options_since(since: datetime, engine: Engine | None = None) -> pd.DataFrame:
    """Extract poll options created after the given timestamp."""
    engine = engine or get_engine()
    query = text("""
        SELECT id, poll_id, option_text
        FROM poll_options
        WHERE created_at > :since
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"since": since})


def extract_users_since(since: datetime, engine: Engine | None = None) -> pd.DataFrame:
    """Extract users created or updated after the given timestamp."""
    engine = engine or get_engine()
    query = text("""
        SELECT id, full_name, email, created_at
        FROM users
        WHERE updated_at > :since
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"since": since})
```

**Important note about `votes.created_at` vs `votes.voted_at`**: The existing extractors use `created_at` for the votes table. The ERD says `voted_at`. Check which column actually exists in the database and use the correct one consistently. If a column alias is set up by the backend's `data.sql`, match that.

#### Batch (scoped recompute) extractors

```python
# ── Batch extracts (used by incremental backfill for scoped recompute) ────────

def extract_polls_by_ids(poll_ids: list[int], engine: Engine | None = None) -> pd.DataFrame:
    """Extract multiple polls by ID with creator names."""
    if not poll_ids:
        return pd.DataFrame()
    engine = engine or get_engine()
    query = text("""
        SELECT p.id, p.title, p.active, p.multi_select, p.expires_at,
               p.created_at, p.creator_id, u.full_name AS creator_name
        FROM polls p
        JOIN users u ON p.creator_id = u.id
        WHERE p.id = ANY(:ids)
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"ids": poll_ids})


def extract_votes_by_polls(poll_ids: list[int], engine: Engine | None = None) -> pd.DataFrame:
    """Extract all votes for multiple polls."""
    if not poll_ids:
        return pd.DataFrame()
    engine = engine or get_engine()
    query = text("""
        SELECT id, poll_id, option_id, user_id, created_at
        FROM votes
        WHERE poll_id = ANY(:ids)
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"ids": poll_ids})


def extract_options_by_polls(poll_ids: list[int], engine: Engine | None = None) -> pd.DataFrame:
    """Extract all options for multiple polls."""
    if not poll_ids:
        return pd.DataFrame()
    engine = engine or get_engine()
    query = text("""
        SELECT id, poll_id, option_text
        FROM poll_options
        WHERE poll_id = ANY(:ids)
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"ids": poll_ids})


def extract_users_by_ids(user_ids: list[int], engine: Engine | None = None) -> pd.DataFrame:
    """Extract multiple users by ID."""
    if not user_ids:
        return pd.DataFrame()
    engine = engine or get_engine()
    query = text("""
        SELECT id, full_name, email, created_at
        FROM users
        WHERE id = ANY(:ids)
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"ids": user_ids})


def extract_votes_by_users(user_ids: list[int], engine: Engine | None = None) -> pd.DataFrame:
    """Extract all votes cast by multiple users."""
    if not user_ids:
        return pd.DataFrame()
    engine = engine or get_engine()
    query = text("""
        SELECT id, poll_id, option_id, user_id, created_at
        FROM votes
        WHERE user_id = ANY(:ids)
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"ids": user_ids})


def extract_polls_by_creators(creator_ids: list[int], engine: Engine | None = None) -> pd.DataFrame:
    """Extract all polls created by multiple users."""
    if not creator_ids:
        return pd.DataFrame()
    engine = engine or get_engine()
    query = text("""
        SELECT id, title, active, created_at, creator_id
        FROM polls
        WHERE creator_id = ANY(:ids)
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"ids": creator_ids})
```

**Note on `ANY(:ids)` vs `IN`**: PostgreSQL's `ANY(:ids)` with a list parameter works cleanly with SQLAlchemy's `text()` + `params`. If you run into issues with array binding, an alternative is to use `sqlalchemy.bindparam("ids", expanding=True)` with `WHERE id IN :ids`. Test whichever approach works with the current psycopg2 version.

---

### Step 5: Refactor `run_backfill()`

**File**: `src/data_engineering/pipeline/backfill.py`

Replace the entire file contents. The existing `run_backfill()` logic moves into `_full_backfill()`, and the new `run_backfill()` becomes the watermark-aware orchestrator.

```python
"""Startup backfill: reads OLTP data and populates the four analytics tables.

Supports two modes:
- Full backfill: extracts everything (first run or forced)
- Incremental backfill: extracts only changes since last watermark
"""

from __future__ import annotations

import logging
from datetime import datetime, timedelta

import pandas as pd

from data_engineering.config import FORCE_FULL_BACKFILL, WATERMARK_OVERLAP_MINUTES
from data_engineering.ingestion.extractors import (
    # Full-table
    extract_options,
    extract_polls,
    extract_users,
    extract_votes,
    get_total_users,
    # Incremental (delta detection)
    extract_options_since,
    extract_polls_since,
    extract_users_since,
    extract_votes_since,
    # Batch (scoped recompute)
    extract_options_by_polls,
    extract_polls_by_creators,
    extract_polls_by_ids,
    extract_users_by_ids,
    extract_votes_by_polls,
    extract_votes_by_users,
)
from data_engineering.loading.writers import (
    get_watermark,
    set_watermark,
    upsert_option_breakdown,
    upsert_poll_summary,
    upsert_user_participation,
    upsert_votes_timeseries,
)
from data_engineering.transformation.transformers import (
    compute_option_breakdown,
    compute_poll_summary,
    compute_user_participation,
    compute_votes_timeseries,
)

logger = logging.getLogger(__name__)

_ENTITIES = ("polls", "votes", "options", "users")


def run_backfill() -> None:
    """
    Watermark-aware backfill orchestrator.

    - If FORCE_FULL_BACKFILL is True or any watermark is missing: full load
    - Otherwise: incremental extract → identify affected IDs → batch recompute → upsert → advance watermarks
    """
    watermarks = {e: get_watermark(e) for e in _ENTITIES}

    if FORCE_FULL_BACKFILL or any(wm is None for wm in watermarks.values()):
        reason = "FORCE_FULL_BACKFILL" if FORCE_FULL_BACKFILL else "missing watermarks"
        logger.info("Running full backfill (%s)...", reason)
        _full_backfill()
        return

    logger.info("Running incremental backfill...")
    _incremental_backfill(watermarks)


def _full_backfill() -> None:
    """
    Full-table backfill (existing logic).
    Extracts everything, computes all KPIs, upserts all rows, sets watermarks.
    """
    polls_df = extract_polls()
    votes_df = extract_votes()
    options_df = extract_options()
    users_df = extract_users()
    total_users = get_total_users()

    logger.info(
        "Full backfill extracted: %d polls, %d votes, %d options, %d users",
        len(polls_df),
        len(votes_df),
        len(options_df),
        len(users_df),
    )

    summary_df = compute_poll_summary(polls_df, votes_df, total_users)
    option_df = compute_option_breakdown(options_df, votes_df)
    timeseries_df = compute_votes_timeseries(votes_df)
    participation_df = compute_user_participation(users_df, votes_df, polls_df)

    upsert_poll_summary(summary_df)
    upsert_option_breakdown(option_df)
    upsert_votes_timeseries(timeseries_df)
    upsert_user_participation(participation_df)

    # Set watermarks to MAX timestamp from each entity
    _advance_watermark("polls", polls_df, "created_at")    # use updated_at if available in df
    _advance_watermark("votes", votes_df, "created_at")
    _advance_watermark("options", options_df, "created_at") # options_df may not have created_at — see note below
    _advance_watermark("users", users_df, "created_at")

    logger.info("Full backfill complete. Watermarks set.")


def _incremental_backfill(watermarks: dict[str, datetime]) -> None:
    """
    Incremental backfill: extract deltas, identify affected IDs, batch recompute.
    """
    overlap = timedelta(minutes=WATERMARK_OVERLAP_MINUTES)

    # 1. Incremental extract (delta detection)
    delta_polls = extract_polls_since(watermarks["polls"] - overlap)
    delta_votes = extract_votes_since(watermarks["votes"] - overlap)
    delta_options = extract_options_since(watermarks["options"] - overlap)
    delta_users = extract_users_since(watermarks["users"] - overlap)

    # 2. Early return if no changes
    if delta_polls.empty and delta_votes.empty and delta_options.empty and delta_users.empty:
        logger.info("Incremental backfill: no changes detected.")
        return

    logger.info(
        "Incremental deltas: %d polls, %d votes, %d options, %d users",
        len(delta_polls),
        len(delta_votes),
        len(delta_options),
        len(delta_users),
    )

    # 3. Identify affected IDs
    affected_poll_ids = set()
    affected_user_ids = set()

    if not delta_votes.empty:
        affected_poll_ids.update(delta_votes["poll_id"].unique())
        affected_user_ids.update(delta_votes["user_id"].unique())
    if not delta_polls.empty:
        affected_poll_ids.update(delta_polls["id"].unique())
    if not delta_options.empty:
        affected_poll_ids.update(delta_options["poll_id"].unique())
    if not delta_users.empty:
        affected_user_ids.update(delta_users["id"].unique())

    affected_poll_ids = sorted(affected_poll_ids)
    affected_user_ids = sorted(affected_user_ids)

    logger.info(
        "Recomputing %d polls, %d users",
        len(affected_poll_ids),
        len(affected_user_ids),
    )

    # 4. Batch scoped extract (full data for affected entities only)
    total_users = get_total_users()

    if affected_poll_ids:
        polls_df = extract_polls_by_ids(affected_poll_ids)
        votes_df = extract_votes_by_polls(affected_poll_ids)
        options_df = extract_options_by_polls(affected_poll_ids)

        # 5. Transform (reuse existing compute_* functions as-is)
        summary_df = compute_poll_summary(polls_df, votes_df, total_users)
        option_df = compute_option_breakdown(options_df, votes_df)
        timeseries_df = compute_votes_timeseries(votes_df)

        # 6. Upsert
        upsert_poll_summary(summary_df)
        upsert_option_breakdown(option_df)
        upsert_votes_timeseries(timeseries_df)

    if affected_user_ids:
        users_df = extract_users_by_ids(affected_user_ids)
        user_votes_df = extract_votes_by_users(affected_user_ids)
        user_polls_df = extract_polls_by_creators(affected_user_ids)

        participation_df = compute_user_participation(users_df, user_votes_df, user_polls_df)
        upsert_user_participation(participation_df)

    # 7. Advance watermarks
    if not delta_polls.empty:
        _advance_watermark("polls", delta_polls, "created_at")   # prefer updated_at if in df
    if not delta_votes.empty:
        _advance_watermark("votes", delta_votes, "created_at")
    if not delta_options.empty:
        _advance_watermark("options", delta_options, "created_at")
    if not delta_users.empty:
        _advance_watermark("users", delta_users, "created_at")

    logger.info("Incremental backfill complete.")


def _advance_watermark(entity: str, df: pd.DataFrame, timestamp_col: str) -> None:
    """Set the watermark for an entity to the MAX of the given timestamp column."""
    if df.empty or timestamp_col not in df.columns:
        return
    max_ts = pd.to_datetime(df[timestamp_col]).max()
    if pd.notna(max_ts):
        set_watermark(entity, max_ts.to_pydatetime())
```

### Key Notes for `_advance_watermark`:
- For **polls**: The extractor selects `created_at` but ideally you should also select `updated_at` and use that for the watermark (since `extract_polls_since` filters on `updated_at`). Either add `updated_at` to the poll extractor SELECT, or use `created_at` as a fallback — but be aware this won't catch poll status changes (ACTIVE→CLOSED) if those only update `updated_at`.
- For **options**: The current `extract_options()` doesn't select `created_at`. You'll need to add it to the SELECT query, or use a separate query to get `MAX(created_at) FROM poll_options`.
- For **users**: Similarly, `extract_users()` selects `created_at` but `extract_users_since` filters on `updated_at`. Add `updated_at` to the user extractor SELECT for accurate watermarking.

**Recommended**: Update the full-table extractors to also SELECT the timestamp column used for watermarking:
- `extract_polls()`: add `p.updated_at` to SELECT
- `extract_options()`: add `created_at` to SELECT (it might already be there in the DB)
- `extract_users()`: add `updated_at` to SELECT

---

### Step 6: Refactor `run_streaming()` with Periodic Backfill

**File**: `src/data_engineering/pipeline/streaming.py`

The `_consume_loop(consumer)` function currently uses `for message in consumer:` which blocks indefinitely. Replace it with a `consumer.poll()` loop that checks elapsed time and triggers periodic backfill.

```python
import time

from data_engineering.config import BACKFILL_INTERVAL_MINUTES
from data_engineering.pipeline.backfill import run_backfill  # ← ADD to imports

# ... existing run_streaming() stays the same (retry logic around _consume_loop) ...

def _consume_loop(consumer) -> None:
    """Process messages with periodic backfill check."""
    backfill_interval_sec = BACKFILL_INTERVAL_MINUTES * 60
    last_backfill = time.monotonic()

    while True:
        # Poll for messages (returns dict of TopicPartition → list[ConsumerRecord])
        records = consumer.poll(timeout_ms=5000)

        for tp, messages in records.items():
            for message in messages:
                event = message.value
                event_type = event.get("event_type") if isinstance(event, dict) else None
                try:
                    if event_type == "VOTE_CAST":
                        _handle_vote_event(event)
                    elif event_type in ("POLL_CREATED", "POLL_CLOSED"):
                        _handle_poll_event(event)
                    else:
                        logger.warning("Unrecognised event_type=%s — skipping", event_type)
                except Exception:
                    logger.exception(
                        "Failed to process event_type=%s — writing to DLQ", event_type
                    )
                    write_to_dlq(event)

            consumer.commit()

        # Periodic backfill check
        elapsed = time.monotonic() - last_backfill
        if elapsed >= backfill_interval_sec:
            logger.info("Periodic backfill triggered (%.0fs elapsed).", elapsed)
            try:
                run_backfill()
            except Exception:
                logger.exception("Periodic backfill failed — will retry next interval.")
            last_backfill = time.monotonic()
```

**Important**: The `run_streaming()` function with the `NoBrokersAvailable` retry logic stays exactly as-is. Only `_consume_loop()` changes. Add the new `run_backfill` import at the top of the file.

---

### Step 7: Update `.env.example`

**File**: `.env.example`

Add at the end of the `# ── Pipeline` section:

```
# ─── Incremental Backfill ─────────────────────────────────────
# How often (in minutes) the periodic backfill runs during streaming
BACKFILL_INTERVAL_MINUTES=30
# Minutes subtracted from watermark to guard against clock skew
WATERMARK_OVERLAP_MINUTES=5
# Set to "true" to force a full backfill (ignores watermarks)
FORCE_FULL_BACKFILL=false
```

---

### Step 8: Unit Tests

#### New file: `tests/unit/test_backfill.py`

```python
"""Tests for watermark-aware backfill logic."""

from __future__ import annotations

from datetime import datetime
from unittest.mock import MagicMock, patch

import pandas as pd
import pytest


class TestFullBackfill:
    """Tests for full backfill behaviour (no watermarks or forced)."""

    @patch("data_engineering.pipeline.backfill.set_watermark")
    @patch("data_engineering.pipeline.backfill.upsert_user_participation")
    @patch("data_engineering.pipeline.backfill.upsert_votes_timeseries")
    @patch("data_engineering.pipeline.backfill.upsert_option_breakdown")
    @patch("data_engineering.pipeline.backfill.upsert_poll_summary")
    @patch("data_engineering.pipeline.backfill.get_watermark", return_value=None)
    @patch("data_engineering.pipeline.backfill.get_total_users", return_value=3)
    @patch("data_engineering.pipeline.backfill.extract_users")
    @patch("data_engineering.pipeline.backfill.extract_options")
    @patch("data_engineering.pipeline.backfill.extract_votes")
    @patch("data_engineering.pipeline.backfill.extract_polls")
    def test_full_backfill_when_no_watermarks(
        self, mock_polls, mock_votes, mock_options, mock_users,
        mock_total, mock_get_wm, mock_upsert_ps, mock_upsert_ob,
        mock_upsert_ts, mock_upsert_up, mock_set_wm
    ):
        """When any watermark is None, full extractors should be called."""
        mock_polls.return_value = pd.DataFrame({"id": [1], ...})  # provide minimal DataFrames
        mock_votes.return_value = pd.DataFrame()
        mock_options.return_value = pd.DataFrame()
        mock_users.return_value = pd.DataFrame()

        from data_engineering.pipeline.backfill import run_backfill
        run_backfill()

        mock_polls.assert_called_once()
        mock_votes.assert_called_once()
        mock_options.assert_called_once()
        mock_users.assert_called_once()


class TestIncrementalBackfill:
    """Tests for incremental backfill behaviour (watermarks exist)."""

    def test_no_changes_returns_early(self):
        """When all deltas are empty, no upserts should be called."""
        # Mock get_watermark to return a datetime for all 4 entities
        # Mock extract_*_since to return empty DataFrames
        # Assert upsert_* functions are NOT called
        pass

    def test_affected_poll_ids_computed_from_vote_deltas(self):
        """New votes should trigger recompute for their poll_ids."""
        # Mock delta_votes with poll_ids [1, 3]
        # Assert extract_polls_by_ids called with [1, 3]
        pass

    def test_watermarks_advanced_after_successful_backfill(self):
        """set_watermark should be called with MAX timestamp from deltas."""
        # Mock deltas with known timestamps
        # Assert set_watermark called with the max
        pass

    @patch("data_engineering.pipeline.backfill.FORCE_FULL_BACKFILL", True)
    def test_force_full_backfill_ignores_watermarks(self):
        """FORCE_FULL_BACKFILL=True should trigger full backfill even with watermarks."""
        # Mock get_watermark to return a datetime
        # Assert full extractors (extract_polls, etc.) are called, not incremental
        pass
```

#### New file: `tests/unit/test_watermarks.py`

```python
"""Tests for watermark read/write helpers."""

from __future__ import annotations

from datetime import datetime
from unittest.mock import MagicMock, patch


def test_get_watermark_returns_none_for_missing_entity():
    """get_watermark should return None when no row exists."""
    # Mock the DB query to return no rows
    pass


def test_set_watermark_creates_new_row():
    """set_watermark should INSERT when entity doesn't exist."""
    # Mock the DB and verify INSERT is executed
    pass


def test_set_watermark_updates_existing_row():
    """set_watermark should UPDATE when entity already exists."""
    # Mock the DB with existing row, verify ON CONFLICT UPDATE
    pass
```

#### Extend: `tests/unit/test_streaming.py`

Add two tests to the existing file:

```python
def test_periodic_backfill_triggers_after_interval():
    """Periodic backfill should run when BACKFILL_INTERVAL_MINUTES elapsed."""
    # Mock consumer.poll() to return empty dicts
    # Mock time.monotonic() to simulate elapsed time > interval
    # Assert run_backfill() is called
    pass


def test_periodic_backfill_does_not_trigger_before_interval():
    """Periodic backfill should NOT run before interval elapsed."""
    # Mock consumer.poll() to return empty dicts
    # Mock time.monotonic() to simulate short elapsed time
    # Assert run_backfill() is NOT called
    pass
```

---

## File Checklist

| File | Action | What Changes |
|------|--------|-------------|
| `src/data_engineering/config.py` | Edit | Add 3 config vars |
| `src/data_engineering/loading/models.py` | Edit | Add `pipeline_watermarks` table |
| `src/data_engineering/loading/writers.py` | Edit | Add `get_watermark()`, `set_watermark()`, import new table |
| `src/data_engineering/ingestion/extractors.py` | Edit | Add 4 incremental + 6 batch extractors, add `datetime` import |
| `src/data_engineering/pipeline/backfill.py` | Rewrite | Watermark-aware orchestrator with `_full_backfill` + `_incremental_backfill` |
| `src/data_engineering/pipeline/streaming.py` | Edit | Replace `_consume_loop` with `consumer.poll()` loop + periodic backfill |
| `.env.example` | Edit | Add 3 new env var examples |
| `main.py` | No change | — |
| `tests/unit/test_backfill.py` | Create | ~6 tests |
| `tests/unit/test_watermarks.py` | Create | ~3 tests |
| `tests/unit/test_streaming.py` | Edit | Add ~2 tests |

---

## Current File Contents for Reference

Below are the current file contents as of 2026-03-06. Use these to understand exactly what exists and where to make edits.

### `src/data_engineering/config.py` (current)

```python
"""Pipeline configuration — reads from .env or environment variables."""

from __future__ import annotations

from typing import Any

import boto3
from decouple import config
from sqlalchemy import create_engine as _sa_create_engine
from sqlalchemy.engine import Engine

# ── Database ──────────────────────────────────────────────────────────────────
DB_HOST: str = config("DB_HOST", default="localhost")
DB_PORT: int = config("DB_PORT", default=5432, cast=int)
DB_NAME: str = config("DB_NAME", default="quickpoll")
DB_USER: str = config("DB_USER", default="quickpoll")
DB_PASSWORD: str = config("DB_PASSWORD", default="quickpoll123")

DATABASE_URL: str = (
    f"postgresql://{DB_USER}:{DB_PASSWORD}@{DB_HOST}:{DB_PORT}/{DB_NAME}"
)

# ── Kafka ─────────────────────────────────────────────────────────────────────
KAFKA_BOOTSTRAP_SERVERS: str = config(
    "KAFKA_BOOTSTRAP_SERVERS", default="localhost:9092"
)
KAFKA_TOPIC_VOTE_EVENTS: str = config("KAFKA_TOPIC_VOTE_EVENTS", default="vote_events")
KAFKA_TOPIC_POLL_EVENTS: str = config("KAFKA_TOPIC_POLL_EVENTS", default="poll_events")
KAFKA_GROUP_ID: str = config("KAFKA_GROUP_ID", default="quickpoll-analytics")

# ── Pipeline ──────────────────────────────────────────────────────────────────
LOG_LEVEL: str = config("LOG_LEVEL", default="INFO")

# ── Cloudflare R2 (Dead-Letter Queue) ─────────────────────────────────────────
R2_ENDPOINT_URL: str = config("R2_ENDPOINT_URL", default="")
R2_ACCESS_KEY_ID: str = config("R2_ACCESS_KEY_ID", default="")
R2_SECRET_ACCESS_KEY: str = config("R2_SECRET_ACCESS_KEY", default="")
R2_DLQ_BUCKET: str = config("R2_DLQ_BUCKET", default="quickpoll-dlq")

# ── Engine singleton ──────────────────────────────────────────────────────────
_engine: Engine | None = None


def get_engine() -> Engine:
    """Return a shared SQLAlchemy engine (created once per process)."""
    global _engine
    if _engine is None:
        _engine = _sa_create_engine(DATABASE_URL, pool_pre_ping=True)
    return _engine


# ── S3 client singleton ───────────────────────────────────────────────────────
_s3_client: Any = None


def get_s3_client() -> Any:
    """Return a shared boto3 S3 client configured for Cloudflare R2."""
    global _s3_client
    if _s3_client is None:
        _s3_client = boto3.client(
            "s3",
            endpoint_url=R2_ENDPOINT_URL,
            aws_access_key_id=R2_ACCESS_KEY_ID,
            aws_secret_access_key=R2_SECRET_ACCESS_KEY,
        )
    return _s3_client
```

### `src/data_engineering/loading/models.py` (current)

```python
"""SQLAlchemy table definitions for all four analytics tables."""

from __future__ import annotations

from sqlalchemy import (
    BigInteger,
    Column,
    DateTime,
    Float,
    Integer,
    MetaData,
    String,
    Table,
    UniqueConstraint,
    text,
)
from sqlalchemy.engine import Engine

metadata = MetaData()

analytics_poll_summary = Table(
    "analytics_poll_summary",
    metadata,
    Column("poll_id", BigInteger, primary_key=True),
    Column("title", String(255), nullable=False),
    Column("creator_name", String(255)),
    Column("status", String(50)),
    Column("total_votes", Integer, server_default="0"),
    Column("unique_voters", Integer, server_default="0"),
    Column("participation_rate", Float, server_default="0"),
    Column("created_at", DateTime),
    Column("last_updated", DateTime, server_default=text("NOW()")),
)

analytics_option_breakdown = Table(
    "analytics_option_breakdown",
    metadata,
    Column("option_id", BigInteger, primary_key=True),
    Column("poll_id", BigInteger, nullable=False),
    Column("option_text", String(500)),
    Column("vote_count", Integer, server_default="0"),
    Column("vote_percentage", Float, server_default="0"),
    Column("last_updated", DateTime, server_default=text("NOW()")),
)

analytics_votes_timeseries = Table(
    "analytics_votes_timeseries",
    metadata,
    Column("id", Integer, primary_key=True, autoincrement=True),
    Column("poll_id", BigInteger, nullable=False),
    Column("bucket_time", DateTime, nullable=False),
    Column("votes_in_bucket", Integer, server_default="0"),
    Column("recorded_at", DateTime, server_default=text("NOW()")),
    UniqueConstraint("poll_id", "bucket_time", name="uq_timeseries_poll_bucket"),
)

analytics_user_participation = Table(
    "analytics_user_participation",
    metadata,
    Column("user_id", BigInteger, primary_key=True),
    Column("user_name", String(255)),
    Column("total_votes_cast", Integer, server_default="0"),
    Column("polls_participated", Integer, server_default="0"),
    Column("polls_created", Integer, server_default="0"),
    Column("last_active", DateTime),
    Column("last_updated", DateTime, server_default=text("NOW()")),
)


def create_analytics_tables(engine: Engine) -> None:
    """Create all analytics tables in PostgreSQL if they don't exist yet."""
    metadata.create_all(engine, checkfirst=True)
```

### `tests/conftest.py` (current)

```python
"""Shared pytest fixtures for unit tests."""

from __future__ import annotations

import pandas as pd
import pytest


@pytest.fixture
def polls_df() -> pd.DataFrame:
    return pd.DataFrame(
        {
            "id": [1, 2],
            "title": ["Best cloud?", "Favourite DB?"],
            "creator_name": ["Alice", "Bob"],
            "active": [True, False],
            "creator_id": [1, 2],
            "created_at": pd.to_datetime(["2026-01-01", "2026-01-02"]),
        }
    )


@pytest.fixture
def votes_df() -> pd.DataFrame:
    return pd.DataFrame(
        {
            "id": [1, 2, 3, 4],
            "poll_id": [1, 1, 2, 1],
            "option_id": [1, 2, 3, 1],
            "user_id": [10, 11, 10, 12],
            "created_at": pd.to_datetime(
                [
                    "2026-01-01 08:00",
                    "2026-01-01 08:30",
                    "2026-01-01 09:00",
                    "2026-01-01 09:15",
                ]
            ),
        }
    )


@pytest.fixture
def options_df() -> pd.DataFrame:
    return pd.DataFrame(
        {
            "id": [1, 2, 3],
            "poll_id": [1, 1, 2],
            "option_text": ["AWS", "GCP", "PostgreSQL"],
        }
    )


@pytest.fixture
def users_df() -> pd.DataFrame:
    return pd.DataFrame(
        {
            "id": [10, 11, 12],
            "full_name": ["Alice", "Bob", "Carol"],
            "email": ["a@x.com", "b@x.com", "c@x.com"],
            "created_at": pd.to_datetime(["2025-12-01", "2025-12-02", "2025-12-03"]),
        }
    )
```

---

## Verification Criteria

After implementation, all of the following must pass:

1. `ruff check src/ tests/` — no lint errors
2. `ruff format src/ tests/` — all formatted
3. `uv run pytest tests/ -v` — all tests pass (existing 44 + ~11 new ≈ 55 total)
4. Manual test: `uv run python main.py` on fresh analytics DB → logs "Full backfill", `SELECT * FROM pipeline_watermarks` shows 4 rows
5. Manual test: restart → logs "Incremental backfill: no changes detected"
6. Manual test: insert a new vote via `uv run python scripts/seed_data.py`, restart → logs "Incremental deltas: ... 1 votes ..." and only the affected poll is recomputed
7. `FORCE_FULL_BACKFILL=true uv run python main.py` → logs "Full backfill (FORCE_FULL_BACKFILL)"

---

## Constraints

- **Only modify files in `data-engineering/`** — no changes to backend, frontend, devops, qa
- **Do not modify OLTP schema** — the `polls`, `votes`, `poll_options`, `users` tables are owned by the backend team
- **Do not modify existing transformer functions** (`compute_*`) — they accept DataFrames and return DataFrames; the incremental logic works by feeding them scoped DataFrames
- **Do not modify existing upsert writers** (`upsert_*`) — they're already idempotent
- **Use `uv` package manager** — activate `.venv` before running commands
- **Run `ruff check` and `ruff format`** on all modified files
- **Write unit tests** for every new function
- **Both `requirements.txt` files must match** if any dependencies are added (none expected for this change)
