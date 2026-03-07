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
    # Batch (scoped recompute)
    extract_options_by_polls,
    extract_polls,
    extract_polls_by_creators,
    extract_polls_by_ids,
    # Incremental (delta detection)
    extract_polls_since,
    extract_users,
    extract_users_by_ids,
    extract_users_since,
    extract_votes,
    extract_votes_by_polls,
    extract_votes_by_users,
    extract_votes_since,
    get_total_users,
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

_ENTITIES = ("polls", "votes", "users")


def run_backfill() -> None:
    """
    Watermark-aware backfill orchestrator.

    - If FORCE_FULL_BACKFILL is True or any watermark is missing: full load
    - Otherwise: incremental extract → identify affected IDs →
      batch recompute → upsert → advance watermarks
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
    # Options have no timestamp column in the OLTP schema, so no watermark is tracked.
    _advance_watermark("polls", polls_df, "created_at")
    _advance_watermark("votes", votes_df, "created_at")
    _advance_watermark("users", users_df, "created_at")

    logger.info("Full backfill complete. Watermarks set.")


def _incremental_backfill(watermarks: dict[str, datetime]) -> None:
    """
    Incremental backfill: extract deltas, identify affected IDs, batch recompute.

    Note: poll_options has no timestamp column in the OLTP schema, so options
    are not tracked independently.  Option changes are captured indirectly:
    whenever a poll is modified (option added/removed), the poll's updated_at
    changes, which surfaces the poll_id for a full-option reload via
    extract_options_by_polls.
    """
    overlap = timedelta(minutes=WATERMARK_OVERLAP_MINUTES)

    # 1. Incremental extract (delta detection)
    delta_polls = extract_polls_since(watermarks["polls"] - overlap)
    delta_votes = extract_votes_since(watermarks["votes"] - overlap)
    delta_users = extract_users_since(watermarks["users"] - overlap)

    # 2. Early return if no changes
    if delta_polls.empty and delta_votes.empty and delta_users.empty:
        logger.info("Incremental backfill: no changes detected.")
        return

    logger.info(
        "Incremental deltas: %d polls, %d votes, %d users",
        len(delta_polls),
        len(delta_votes),
        len(delta_users),
    )

    # 3. Identify affected IDs
    affected_poll_ids: set[int] = set()
    affected_user_ids: set[int] = set()

    if not delta_votes.empty:
        affected_poll_ids.update(delta_votes["poll_id"].unique())
        affected_user_ids.update(delta_votes["user_id"].unique())
    if not delta_polls.empty:
        affected_poll_ids.update(delta_polls["id"].unique())
        if "creator_id" in delta_polls.columns:
            affected_user_ids.update(delta_polls["creator_id"].unique())
    if not delta_users.empty:
        affected_user_ids.update(delta_users["id"].unique())

    affected_poll_ids_list = sorted(affected_poll_ids)
    affected_user_ids_list = sorted(affected_user_ids)

    logger.info(
        "Recomputing %d polls, %d users",
        len(affected_poll_ids_list),
        len(affected_user_ids_list),
    )

    # 4. Batch scoped extract (full data for affected entities only)
    total_users = get_total_users()

    if affected_poll_ids_list:
        polls_df = extract_polls_by_ids(affected_poll_ids_list)
        votes_df = extract_votes_by_polls(affected_poll_ids_list)
        options_df = extract_options_by_polls(affected_poll_ids_list)

        # 5. Transform (reuse existing compute_* functions as-is)
        summary_df = compute_poll_summary(polls_df, votes_df, total_users)
        option_df = compute_option_breakdown(options_df, votes_df)
        timeseries_df = compute_votes_timeseries(votes_df)

        # 6. Upsert
        upsert_poll_summary(summary_df)
        upsert_option_breakdown(option_df)
        upsert_votes_timeseries(timeseries_df)

    if affected_user_ids_list:
        users_df = extract_users_by_ids(affected_user_ids_list)
        user_votes_df = extract_votes_by_users(affected_user_ids_list)
        user_polls_df = extract_polls_by_creators(affected_user_ids_list)

        participation_df = compute_user_participation(
            users_df, user_votes_df, user_polls_df
        )
        upsert_user_participation(participation_df)

    # 7. Advance watermarks (use updated_at for entities that track modifications)
    if not delta_polls.empty:
        _advance_watermark("polls", delta_polls, "updated_at")
    if not delta_votes.empty:
        _advance_watermark("votes", delta_votes, "created_at")
    if not delta_users.empty:
        _advance_watermark("users", delta_users, "updated_at")

    logger.info("Incremental backfill complete.")


def _advance_watermark(entity: str, df: pd.DataFrame, timestamp_col: str) -> None:
    """Set the watermark for an entity to the MAX of the given timestamp column.

    Only advances forward — never regresses the watermark.
    """
    if df.empty or timestamp_col not in df.columns:
        return
    max_ts = pd.to_datetime(df[timestamp_col]).max()
    if pd.notna(max_ts):
        new_value = max_ts.to_pydatetime()
        current = get_watermark(entity)
        if current is None or new_value > current:
            set_watermark(entity, new_value)
