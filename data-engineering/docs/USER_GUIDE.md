# QuickPoll Analytics Pipeline — User Guide

**Author:** Henry Nana Antwi — Data Engineer, Team 6
**Project:** QuickPoll — AmaliTech Phase 1 Capstone

---

## Table of Contents

1. [What This Pipeline Does](#1-what-this-pipeline-does)
2. [Prerequisites](#2-prerequisites)
3. [Local Development Setup](#3-local-development-setup)
4. [Configuration Reference](#4-configuration-reference)
5. [Running the Pipeline](#5-running-the-pipeline)
6. [Docker Deployment](#6-docker-deployment)
7. [Seeding Test Data](#7-seeding-test-data)
8. [How Incremental Backfill Works](#8-how-incremental-backfill-works)
9. [Analytics Tables Reference](#9-analytics-tables-reference)
10. [Available Commands](#10-available-commands)
11. [Troubleshooting](#11-troubleshooting)

---

## 1. What This Pipeline Does

The QuickPoll analytics pipeline is a long-running Python service that:

1. **Backfills** — On startup, extracts data from the backend's PostgreSQL OLTP tables (polls, votes, options, users), computes KPIs (vote counts, participation rates, time-series buckets), and writes the results into four analytics tables.
2. **Streams** — Subscribes to two Kafka topics (`vote_events`, `poll_events`), processes each event in near-real-time, and upserts updated KPIs into the same analytics tables.
3. **Periodically re-syncs** — Every 30 minutes (configurable), the streaming loop triggers an incremental backfill to catch anything Kafka might have missed.

The frontend dashboard reads from the analytics tables to display live metrics.

```
                    ┌──────────────┐
  OLTP Tables ────► │   Backfill   │ ────► Analytics Tables
                    └──────────────┘
                    ┌──────────────┐
  Kafka Topics ───► │  Streaming   │ ────► Analytics Tables
                    └──────────────┘
                           │
                    (every 30 min)
                           │
                    ┌──────────────┐
                    │  Incremental │ ────► Analytics Tables
                    │   Backfill   │
                    └──────────────┘
```

---

## 2. Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Python | 3.11+ | [python.org](https://www.python.org/downloads/) |
| uv | latest | [astral.sh/uv](https://docs.astral.sh/uv/getting-started/installation/) |
| Docker | 20+ | [docker.com](https://docs.docker.com/get-docker/) |
| PostgreSQL | 16+ | Shared with backend (no separate install needed) |

---

## 3. Local Development Setup

```powershell
# 1. Navigate to the data-engineering directory
cd data-engineering

# 2. Install dependencies
uv sync

# 3. Create your environment file
cp .env.example .env
# Edit .env with your database and Kafka credentials (see Section 4)

# 4. Start local Kafka (if not using a remote broker)
docker compose -f docker-compose.kafka-dev.yml up -d

# 5. Seed the database with test data (optional)
rav x seed

# 6. Run the pipeline
rav x dev
```

---

## 4. Configuration Reference

All configuration is via environment variables. Copy `.env.example` to `.env` and fill in your values.

### Database

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `quickpoll` | Database name |
| `DB_USER` | `quickpoll` | Database user |
| `DB_PASSWORD` | `quickpoll123` | Database password |

### Kafka

| Variable | Default | Description |
|----------|---------|-------------|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address. Use `localhost:9092` for local dev, `kafka:9092` inside Docker Compose |
| `KAFKA_TOPIC_VOTE_EVENTS` | `vote_events` | Topic the backend publishes vote events to |
| `KAFKA_TOPIC_POLL_EVENTS` | `poll_events` | Topic the backend publishes poll events to |
| `KAFKA_GROUP_ID` | `quickpoll-analytics` | Consumer group ID |

### Pipeline Behavior

| Variable | Default | Description |
|----------|---------|-------------|
| `LOG_LEVEL` | `INFO` | Logging verbosity (`DEBUG`, `INFO`, `WARNING`, `ERROR`) |
| `BACKFILL_INTERVAL_MINUTES` | `30` | How often the streaming loop triggers an incremental backfill |
| `WATERMARK_OVERLAP_MINUTES` | `5` | Minutes subtracted from the watermark to guard against clock skew. Higher = more re-processing, zero data loss |
| `FORCE_FULL_BACKFILL` | `false` | Set to `true` to ignore watermarks and re-process all records from scratch |

### Dead-Letter Queue (Cloudflare R2)

Invalid Kafka messages are stored in an R2 bucket for later inspection.

| Variable | Default | Description |
|----------|---------|-------------|
| `R2_ENDPOINT_URL` | — | `https://<ACCOUNT_ID>.r2.cloudflarestorage.com` |
| `R2_ACCESS_KEY_ID` | — | R2 API access key |
| `R2_SECRET_ACCESS_KEY` | — | R2 API secret key |
| `R2_DLQ_BUCKET` | `quickpoll-dlq` | Bucket name for failed events |

### AI Seeding (optional)

Only required if using `scripts/seed_ai_oltp.py`.

| Variable | Default | Description |
|----------|---------|-------------|
| `GROQ_API_KEY` | — | Groq API key for LLM-generated test data |
| `GROQ_MODEL` | `llama-3.1-8b-instant` | Primary model for structured output |
| `GROQ_FALLBACK_MODEL` | `llama-3.3-70b-versatile` | Fallback if primary model fails |

---

## 5. Running the Pipeline

### What Happens on Startup

```
main.py
  ├─ configure_logging()        → Set up Python logging
  ├─ get_engine()               → Create PostgreSQL connection pool
  ├─ create_analytics_tables()  → Ensure all analytics tables exist (CREATE IF NOT EXISTS)
  ├─ check_dlq_bucket()         → Verify R2 bucket is accessible
  ├─ run_backfill()             → Full or incremental extract + transform + load
  └─ run_streaming()            → Infinite Kafka consumer loop (never returns)
```

### Backfill Modes

**Automatic (default):** The pipeline checks for existing watermarks in `pipeline_watermarks`. If none exist (first run), it does a full backfill. On subsequent runs, it only processes records changed since the last watermark.

**Force full backfill:** Useful after schema changes, data corruption, or disaster recovery.

```powershell
# Via rav (sets the env var for you)
rav x backfill-full

# Or manually
$env:FORCE_FULL_BACKFILL = "true"
rav x dev
```

### Streaming Loop

After backfill completes, the pipeline enters an infinite Kafka consumer loop:

1. Poll Kafka every 5 seconds for new messages
2. For each valid message: compute KPIs, upsert analytics, commit offset
3. For each invalid message: send to R2 Dead-Letter Queue, skip
4. Every `BACKFILL_INTERVAL_MINUTES` (default 30): re-run incremental backfill

The pipeline runs until terminated (`Ctrl+C` or container stop).

---

## 6. Docker Deployment

The pipeline ships as a single Docker image. In production, it runs alongside Kafka, PostgreSQL, and the rest of the QuickPoll stack via Docker Compose.

### Build the Image

```powershell
cd data-engineering
docker build -t data-engineering .
```

The Dockerfile uses a multi-stage build:
- **Builder stage** — installs Python dependencies via `uv sync`
- **Production stage** — slim Python 3.11 image with `postgresql-client` (for `pg_isready`) and `netcat-openbsd` (for Kafka port checks), running as non-root user `appuser`

### Entrypoint Behavior

The container's `entrypoint.sh` waits for dependencies before starting:

```
1. Wait for PostgreSQL → pg_isready loop (2s interval)
2. Wait for Kafka      → nc -z loop (2s interval)
3. Start pipeline      → exec python main.py
```

This is a safety net — Docker Compose healthchecks should hold the container until dependencies are healthy, but the entrypoint does a final in-container readiness check.

### Run Standalone (for testing)

```powershell
docker run --rm \
  -e DB_HOST=host.docker.internal \
  -e DB_PORT=5432 \
  -e DB_NAME=quickpoll \
  -e DB_USER=quickpoll \
  -e DB_PASSWORD=quickpoll123 \
  -e KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9092 \
  data-engineering
```

### Run with Docker Compose

When used in the full stack (root `docker-compose.yml`), the data-engineering service is configured to depend on the `postgres` and `kafka` services. Environment variables are passed from the `.env` file.

---

## 7. Seeding Test Data

Four scripts are available for populating the database with test data.

### Static Seed Data

Creates 5 users, 4 polls, 13 options, and 16 votes. Idempotent (uses `ON CONFLICT DO NOTHING`).

```powershell
rav x seed
```

### AI-Generated Data

Uses Groq LLMs to generate realistic, diverse test data. Requires `GROQ_API_KEY` in `.env`.

```powershell
# Recommended baseline
rav x seed-ai --profile small

# Same total volume, smaller per-call payloads
rav x seed-ai --profile small --chunks 10

# More robust model configuration when function calls fail
rav x seed-ai --model llama-3.3-70b-versatile --fallback-model llama-3.3-70b-versatile

# Equivalent rav shortcuts
rav x seed-ai
rav x seed-ai-small-10
rav x seed-ai-70b
```

| Flag | Description |
|------|-------------|
| `--profile {small,medium,large}` | Data volume profile |
| `--chunks N` | Override chunk count directly |
| `--seed 42` | Random seed for prompt variety |
| `--dry-run` | Generate only, no database writes |
| `--max-retries 2` | Validation retry count per chunk |

### Mock Kafka Producer

Publishes fake events to Kafka topics for testing the streaming pipeline without the backend running.

```powershell
# 5 vote events + 1 poll event (default)
rav x mock-produce

# Custom number of votes
uv run python scripts/mock_producer.py --votes 20
```

### End-to-End Simulation (OLTP + Kafka)

The `seed_and_publish.py` script is the **recommended way to test the full pipeline**. It
simulates what the backend would do: insert data into OLTP tables AND publish matching Kafka
events so both the backfill and streaming consumer can be exercised.

```powershell
# Default: 8 users, 5 polls, ~50 votes → OLTP + Kafka
rav x seed-publish

# Larger dataset
uv run python scripts/seed_and_publish.py --polls 15 --votes-per-poll 20

# Trickle mode: votes arrive one-by-one with a 2-second delay (simulates real-time)
uv run python scripts/seed_and_publish.py --stream-delay 2

# OLTP only (no Kafka — useful when Kafka isn't running)
uv run python scripts/seed_and_publish.py --no-kafka

# Reproducible runs
uv run python scripts/seed_and_publish.py --seed 42
```

| Flag | Default | Description |
|------|---------|-------------|
| `--polls N` | 5 | Number of polls to create |
| `--votes-per-poll N` | 10 | Max votes per poll (limited by user count) |
| `--users N` | 8 | Number of new users in the pool |
| `--stream-delay SEC` | 0 | Seconds between vote events (0 = batch) |
| `--kafka / --no-kafka` | `--kafka` | Toggle Kafka event publishing |
| `--seed N` | random | Random seed for reproducibility |

**Typical test workflow:**

```powershell
# 1. Start Kafka
docker compose -f docker-compose.kafka-dev.yml up -d

# 2. Start the pipeline (backfill + consumer)
rav x backfill-full

# 3. In another terminal, seed data + publish events
rav x seed-publish

# 4. Watch the pipeline consume events and update analytics in real-time
```

---

## 8. How Incremental Backfill Works

The pipeline uses a **watermark-driven** incremental strategy to avoid re-processing the entire database on every run.

### Core Concept

A **watermark** is a timestamp stored per entity (polls, votes, options, users) in the `pipeline_watermarks` table. It records: "I have processed all records up to this point in time."

### Flow

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. Read watermarks for polls, votes, options, users             │
│                                                                 │
│ 2. If any watermark is missing → FULL backfill (first run)      │
│    If FORCE_FULL_BACKFILL=true → FULL backfill (explicit)       │
│    Otherwise                   → INCREMENTAL backfill           │
│                                                                 │
│ 3. Incremental:                                                 │
│    a. Query each OLTP table for records WHERE updated_at >      │
│       (watermark - overlap_minutes)                             │
│    b. Collect affected poll_ids and user_ids from the deltas    │
│    c. Fetch full records for affected entities (scoped query)   │
│    d. Transform: compute KPIs via Pandas                        │
│    e. Upsert results into analytics tables                      │
│    f. Advance watermarks (monotonicity guard: never go backward)│
└─────────────────────────────────────────────────────────────────┘
```

### Overlap Guard

The `WATERMARK_OVERLAP_MINUTES` (default: 5) subtracts time from the watermark when querying. This handles clock skew between application servers and the database. The trade-off: a few minutes of records get re-processed on each run, but you never miss data.

### Monotonicity Guard

The `_advance_watermark` function checks the current watermark before writing. It only moves forward, never backward. This prevents accidental regression if a batch of records has older timestamps than previously processed ones.

### Forcing a Full Backfill

```powershell
rav x backfill-full
```

This ignores all watermarks and reprocesses every record. Use when:
- Analytics tables are corrupted or out of sync
- Schema changes affect KPI calculations
- You need to rebuild analytics from scratch

---

## 9. Analytics Tables Reference

The pipeline writes to four analytics tables, all created automatically on startup.

### `analytics_poll_summary`

One row per poll with aggregated metrics.

| Column | Type | Description |
|--------|------|-------------|
| `poll_id` | BIGINT (PK) | Poll identifier |
| `title` | VARCHAR(255) | Poll title |
| `creator_name` | VARCHAR(255) | Name of the poll creator |
| `status` | VARCHAR(50) | Poll status (ACTIVE, CLOSED, etc.) |
| `total_votes` | INTEGER | Total vote count |
| `unique_voters` | INTEGER | Distinct voter count |
| `participation_rate` | FLOAT | unique_voters / total_users |
| `created_at` | TIMESTAMP | When the poll was created |
| `last_updated` | TIMESTAMP | Last KPI recomputation |

### `analytics_option_breakdown`

One row per poll option with vote distribution.

| Column | Type | Description |
|--------|------|-------------|
| `option_id` | BIGINT (PK) | Option identifier |
| `poll_id` | BIGINT | Parent poll |
| `option_text` | VARCHAR(500) | Option label |
| `vote_count` | INTEGER | Votes for this option |
| `vote_percentage` | FLOAT | Percentage of poll's total votes |
| `last_updated` | TIMESTAMP | Last recomputation |

### `analytics_votes_timeseries`

Aggregated vote counts in time buckets for trend charts.

| Column | Type | Description |
|--------|------|-------------|
| `id` | INTEGER (PK) | Auto-increment |
| `poll_id` | BIGINT | Poll identifier |
| `bucket_time` | TIMESTAMP | Start of the time bucket |
| `votes_in_bucket` | INTEGER | Votes in this bucket |
| `recorded_at` | TIMESTAMP | When this row was written |

Unique constraint on `(poll_id, bucket_time)` — upserts merge into existing buckets.

### `analytics_user_participation`

One row per user with activity metrics.

| Column | Type | Description |
|--------|------|-------------|
| `user_id` | BIGINT (PK) | User identifier |
| `user_name` | VARCHAR(255) | Display name |
| `total_votes_cast` | INTEGER | Lifetime vote count |
| `polls_participated` | INTEGER | Distinct polls voted in |
| `polls_created` | INTEGER | Polls this user created |
| `last_active` | TIMESTAMP | Most recent activity |
| `last_updated` | TIMESTAMP | Last recomputation |

### `pipeline_watermarks`

Internal bookkeeping for incremental backfill.

| Column | Type | Description |
|--------|------|-------------|
| `entity_name` | VARCHAR(50) (PK) | Entity type (`polls`, `votes`, `options`, `users`) |
| `high_watermark` | TIMESTAMP | Last processed timestamp |
| `updated_at` | TIMESTAMP | When this watermark was set |

---

## 10. Available Commands

All commands use the [rav](https://github.com/jmitchel3/rav) task runner. Run from the `data-engineering/` directory.

### Pipeline

| Command | Description |
|---------|-------------|
| `rav x run` | Run the full pipeline (backfill → streaming) |
| `rav x dev` | Same as `run` (alias for development) |
| `rav x backfill` | Same as `run` |
| `rav x backfill-full` | Force a full backfill ignoring watermarks |

### Data Seeding

| Command | Description |
|---------|-------------|
| `rav x seed` | Insert static test data (5 users, 4 polls, 16 votes) |
| `rav x seed-ai --profile small` | Recommended AI seeding command |
| `rav x seed-ai --profile small --chunks 10` | Same volume with smaller per-call payloads |
| `rav x seed-ai --model llama-3.3-70b-versatile --fallback-model llama-3.3-70b-versatile` | Force robust primary/fallback model pair |
| `rav x seed-ai` | Shortcut for `rav x seed-ai --profile small` |
| `rav x seed-ai-small-10` | Shortcut for `--profile small --chunks 10` |
| `rav x seed-ai-70b` | Shortcut for 70b primary + fallback |
| `rav x seed-publish` | Seed OLTP + publish Kafka events (full E2E testing) |
| `rav x mock-produce` | Publish mock Kafka events for testing |

### Code Quality

| Command | Description |
|---------|-------------|
| `rav x lint` | Run ruff linter |
| `rav x lint-fix` | Run ruff linter with auto-fix |
| `rav x format` | Check formatting (no changes) |
| `rav x format-fix` | Auto-format code |
| `rav x check` | Lint + format check combined |

### Testing

| Command | Description |
|---------|-------------|
| `rav x test` | Run all tests |
| `rav x test-cov` | Run tests with coverage report |
| `rav x test-unit` | Run unit tests only |
| `rav x test-backfill` | Run backfill and watermark tests only |

### Dependency Management

| Command | Description |
|---------|-------------|
| `rav x sync` | Install/sync all dependencies |
| `rav x lock` | Lock dependency versions |
| `rav x add` | Add a new dependency |

---

## 11. Troubleshooting

### Pipeline exits immediately

**Symptom:** Container starts then exits with code 1.

**Check:**
1. Is PostgreSQL running? → `pg_isready -h <host> -p 5432`
2. Is Kafka running? → `nc -z localhost 9092`
3. Are credentials correct in `.env`?
4. Check logs: `docker logs <container_name>` or set `LOG_LEVEL=DEBUG`

### Backfill keeps doing a full load

**Symptom:** Logs show "No watermarks found — running full backfill" every time.

**Causes:**
- `FORCE_FULL_BACKFILL=true` is still set in `.env`
- The `pipeline_watermarks` table doesn't exist (check `create_analytics_tables()` ran)
- A previous run failed before advancing watermarks

**Fix:** Ensure `FORCE_FULL_BACKFILL=false`, restart the pipeline, and check that the first run completes successfully (watch for "Watermark advanced" log messages).

### Kafka consumer not receiving messages

**Symptom:** Pipeline is running but no events are processed.

**Check:**
1. Are topics created? → `kafka-topics --bootstrap-server localhost:9092 --list`
2. Is the backend publishing events?
3. Is `KAFKA_GROUP_ID` correct? A different group ID means a different consumer offset.
4. Try the mock producer: `rav x mock-produce`

### Invalid messages going to DLQ

**Symptom:** Messages appear in the R2 dead-letter queue bucket.

This is expected behavior for malformed messages. Check the DLQ objects for the raw message content and fix the producer.

### Analytics tables are stale

**Symptom:** Dashboard shows old data even though new votes are coming in.

**Check:**
1. Is the pipeline actually running? Check the container/process.
2. Is the streaming loop consuming? Look for "Processing message" in logs.
3. Force a fresh sync: `rav x backfill-full`

### Docker build fails

**Symptom:** `docker build` errors on dependency installation.

**Fix:** Ensure `uv.lock` is up to date:
```powershell
uv lock
docker build -t data-engineering .
```

---

## Further Reading

| Document | Description |
|----------|-------------|
| [PIPELINE_ARCHITECTURE.md](PIPELINE_ARCHITECTURE.md) | End-to-end system design, Kafka schemas, ETL flow |
| [TEAM_INTEGRATION_GUIDE.md](TEAM_INTEGRATION_GUIDE.md) | Integration notes for DevOps, Backend, Frontend, QA |
| [INCREMENTAL_BACKFILL_SPEC.md](INCREMENTAL_BACKFILL_SPEC.md) | Complete watermark implementation specification |
| [erd.md](erd.md) | Entity-relationship diagram for the database |
