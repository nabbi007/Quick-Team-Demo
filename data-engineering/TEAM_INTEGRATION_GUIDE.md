# QuickPoll Data Engineering — Team Integration Guide


> This document explains how my data engineering pipeline fits into the project and tells each teammate exactly what they need to do to make it work. You do not need to read the full `PIPELINE_ARCHITECTURE.md` — just find your role below and follow the steps.

---

## What My Pipeline Does (Short Version)

Every time someone votes on a poll or creates a poll, my pipeline wakes up and computes analytics — things like vote counts, participation rates, and voting trends. The results are stored in the same PostgreSQL database that the backend already uses, in dedicated `analytics_*` tables. The frontend dashboard reads from those tables to display charts and KPIs.

**I do not touch any backend tables. I only write to my own analytics tables.**

```
Backend writes a vote
       ↓
Kafka receives the event
       ↓
My Python pipeline picks it up
       ↓
Computes KPIs (vote counts, participation rates, trends)
       ↓
Writes to analytics tables in PostgreSQL
       ↓
Frontend dashboard reads fresh numbers
```

---

## Analytics Tables I Will Create

These tables live in the shared `quickpoll` database. The frontend can query them directly.

| Table | What It Contains |
|---|---|
| `analytics_poll_summary` | Total votes, unique voters, participation rate — one row per poll |
| `analytics_option_breakdown` | Vote count and % share per option — one row per poll option |
| `analytics_votes_timeseries` | Votes bucketed by hour — powers trend charts |
| `analytics_user_participation` | Total votes cast, polls participated in — one row per user |

---

---

# FOR DEVOPS — Illiasu

> **This section explains my data engineering container and what it needs from the compose setup. How you structure the rest of docker-compose is your call — I just need these specific things wired correctly.**

---

## My Container

I have a `data-engineering/` folder with its own `Dockerfile`. My container runs a long-lived Python process (`etl_pipeline.py`) that continuously listens for Kafka events and writes analytics results to PostgreSQL.

My `Dockerfile` is already written and handles its own startup resilience — it installs `postgresql-client` and `netcat` so it can wait for Postgres and Kafka to be ready before the Python process starts. You don't need to change my Dockerfile.

---

## What My Service Needs in docker-compose

When you add my service to `docker-compose.yml`, it needs:

**Build path:** `./data-engineering`

**Service name:** `data-engineering`  
**Container name:** `quickpoll-analytics`

**Environment variables it reads at startup:**
```
DB_HOST=postgres
DB_PORT=5432
DB_NAME=quickpoll
DB_USER=quickpoll
DB_PASSWORD=quickpoll123
KAFKA_BOOTSTRAP_SERVERS=kafka:9092
```

**Critical: dependencies must use `condition: service_healthy`** — not just `depends_on: [postgres, kafka]`. My entrypoint script does a final connectivity check, but Docker needs to hold my container until both services are actually healthy, not just started. This means:
- `postgres` needs its existing healthcheck (it already has one)
- `kafka` needs a healthcheck block too (see below)

**Also needed:** `restart: on-failure` — so if something goes wrong at startup, Docker retries automatically.

---

## Kafka Health Check Requirement

By default Kafka has no healthcheck, which means `condition: service_healthy` won't work for it. The healthcheck needs to verify Kafka is actually accepting broker connections, not just that the process started. The command to use for the test is:

```
kafka-broker-api-versions --bootstrap-server localhost:9092
```

Reasonable values: `interval: 15s`, `timeout: 10s`, `retries: 10`, `start_period: 30s` — Kafka is slow to initialise so the start period needs to be generous.

---

## Backend Needs Kafka Too

The backend publishes events to Kafka, so it also needs `KAFKA_BOOTSTRAP_SERVERS=kafka:9092` in its environment block. Without this, no events reach Kafka and my pipeline has nothing to process.

---

## Confirming My Container Is Working

Once everything is wired up, run:

```bash
docker logs -f quickpoll-analytics
```

You should see:
```
Waiting for PostgreSQL at postgres:5432...
PostgreSQL is ready.
Waiting for Kafka at kafka:9092...
Kafka is ready.
Starting QuickPoll analytics pipeline...
```

If the container keeps restarting, share the logs with me and I will investigate.

---

---

# FOR BACKEND — Abdul Basit

> **My pipeline is a Kafka consumer. It sits downstream of your backend and reacts to events you publish. I don't touch your code or your tables — I just need you to fire two types of events so I have data to work with.**

---

## How My Pipeline Connects to Yours

When a user votes or a poll changes state, your backend writes to PostgreSQL. That's your job and I don't interfere with it. What I need additionally is for those same actions to publish a lightweight JSON message to a Kafka topic. My Python consumer subscribes to those topics, processes the events, and writes to my own separate analytics tables.

```
Your backend saves vote to DB  ──►  also publishes to Kafka  ──►  my pipeline picks it up
```

The two topics I listen to are: `vote_events` and `poll_events`.

---

## Exact Message Schemas I Expect

These are the schemas my consumer is built around. The field names must match exactly — if a required field is missing, my pipeline will log the bad message and skip it, meaning that event won't appear in the analytics.

### `vote_events` — publish when a vote is successfully saved

```json
{
  "event_type": "VOTE_CAST",
  "vote_id": 12,
  "poll_id": 3,
  "option_id": 7,
  "user_id": 45,
  "voted_at": "2026-03-05T10:30:00Z"
}
```

### `poll_events` — publish when a poll is created or closed

```json
{
  "event_type": "POLL_CREATED",
  "poll_id": 3,
  "creator_id": 10,
  "title": "What should we name the feature?",
  "poll_type": "SINGLE",
  "status": "ACTIVE",
  "expires_at": "2026-03-06T10:00:00Z",
  "created_at": "2026-03-05T09:00:00Z"
}
```

`event_type` values I handle: `POLL_CREATED`, `POLL_CLOSED`.

---

## Confirming the Integration Is Working

Once your Kafka producer is in place, cast a test vote and check my pipeline logs:

```bash
docker logs -f quickpoll-analytics
```

You should see:
```
Received VOTE_CAST event for poll_id=3, processing...
Upserted analytics_poll_summary for poll_id=3
```

If those lines appear, your events are reaching my consumer correctly.

---

---

# FOR FRONTEND — Jude

> **My pipeline is what feeds your dashboard with live analytics. You don't need to change anything about how your pipeline runs — I just want you to know what data I'm making available and how it maps to the dashboard features you need to build.**

---

## How My Pipeline Feeds Your Dashboard

Your dashboard needs to show real-time KPIs — vote counts, participation rates, charts, and trends. Rather than your frontend calling raw vote tables and doing aggregation, my pipeline has already done that work and written clean, pre-computed results into analytics tables in the same PostgreSQL database. The backend can expose these through API endpoints for you to consume.

---

## What Analytics Data Is Available

| Table | Columns Available | Maps to Dashboard Feature |
|---|---|---|
| `analytics_poll_summary` | `poll_id`, `title`, `status`, `total_votes`, `unique_voters`, `participation_rate`, `last_updated` | Active polls list, participation rate cards, poll status |
| `analytics_option_breakdown` | `poll_id`, `option_id`, `option_text`, `vote_count`, `vote_percentage` | Bar chart and pie chart per poll |
| `analytics_votes_timeseries` | `poll_id`, `bucket_time`, `votes_in_bucket` | Line/area chart showing voting activity over time |
| `analytics_user_participation` | `user_id`, `user_name`, `total_votes_cast`, `polls_participated`, `polls_created`, `last_active` | Engagement leaderboard, active user count |

---

## Data Freshness

Every table has a `last_updated` column showing when I last recomputed it. This updates in near real-time — within seconds of a vote being cast. You can surface this as a "last refreshed" timestamp on the dashboard if needed.

---

## Asking the Backend to Expose These

You'll be talking to the backend API, not querying the database directly. Ask Abdul Basit to add API endpoints that read from my analytics tables and return the data your charts need. Point him at this table so he knows the exact column names to use.

---

---

# FOR QA — Samuel

> **This section focuses only on my data engineering pipeline and how it integrates with your testing work. Your test plan for the rest of the project (API tests, UI tests, edge cases) is your domain — I'm not prescribing that. What I am giving you here is what you need to validate that my analytics pipeline is correct, since the dashboard data your tests will rely on flows through me.**

---

## How My Pipeline Connects to What You Test

When you run your voting tests (casting votes, testing duplicates, testing expiry), those actions flow through the backend and then through my pipeline before reaching the dashboard. If my pipeline is broken, the dashboard will show stale or missing data — which could look like a frontend bug when it's actually mine.

This means **my pipeline is a dependency for your dashboard tests**. The checks below let you quickly confirm I'm working before you start testing the dashboard display.

---

## What to Validate About My Pipeline

### 1 — Container Startup

```bash
docker-compose up
docker logs quickpoll-analytics
```

Expected output:
```
Waiting for PostgreSQL at postgres:5432...
PostgreSQL is ready.
Waiting for Kafka at kafka:9092...
Kafka is ready.
Starting QuickPoll analytics pipeline...
```

**Fail condition:** Container exits with an error or keeps restarting (`docker ps -a` shows `Restarting`).

---

### 2 — Vote Triggers Analytics Update

1. Log in and cast a vote on any poll
2. Wait ~5 seconds
3. Run this query against the database:

```sql
SELECT poll_id, total_votes, participation_rate, last_updated
FROM analytics_poll_summary
WHERE poll_id = <the poll you voted on>;
```

**Pass condition:** `total_votes` incremented by 1 and `last_updated` reflects a recent timestamp.

---

### 3 — Option Breakdown Percentages Are Accurate

After several votes across different options on the same poll, run:

```sql
SELECT option_text, vote_count, vote_percentage
FROM analytics_option_breakdown
WHERE poll_id = <any active poll>;
```

**Pass condition:** `vote_percentage` values for all options on a single poll sum to 100.

---

### 4 — Pipeline Resilience (Does Not Crash on Bad Data)

My pipeline is built to handle malformed events gracefully — it logs the problem and continues rather than crashing.

**To test:** Check `docker logs quickpoll-analytics` after the full system has been running for a while. The container should still be in a running state even if any errors were encountered.

```bash
docker ps | grep quickpoll-analytics   # should show "Up"
```

---

### 5 — Pipeline Recovers After Restart (No Data Loss)

1. Run `docker stop quickpoll-analytics`
2. While it's stopped, cast a vote via the UI (Kafka will hold the event)
3. Run `docker start quickpoll-analytics`
4. Wait ~10 seconds
5. Query `analytics_poll_summary` and confirm the vote from step 2 is now reflected

**Pass condition:** No vote events are lost during the container restart window.

---

## Distinguishing My Bugs from Application Bugs

If the dashboard shows wrong numbers, before raising it as a frontend or backend bug, check these first:

1. Is my container running? `docker ps | grep quickpoll-analytics` — should show `Up`
2. Are analytics tables being written to? Query `analytics_poll_summary` and check `last_updated`
3. Are there errors in my logs? `docker logs --tail 50 quickpoll-analytics`

If the container is up, tables are updating, and the numbers are still wrong — that's my bug. Bring the logs to me.  
If the container isn't running or tables are empty — that's my bug. Bring the compose output to me.  
If the container is healthy and tables are correct but the UI shows wrong data — that's a frontend or backend API bug.

---

## Commands for Checking My Pipeline

```bash
# Is my container running?
docker ps | grep quickpoll-analytics

# Recent pipeline logs
docker logs --tail 50 quickpoll-analytics

# Connect to the database
docker exec -it quickpoll-db psql -U quickpoll -d quickpoll

# Check analytics tables inside psql
\dt analytics_*
SELECT * FROM analytics_poll_summary;
SELECT * FROM analytics_option_breakdown WHERE poll_id = 1;
SELECT * FROM analytics_votes_timeseries ORDER BY recorded_at DESC LIMIT 20;
SELECT * FROM analytics_user_participation;
```

---

---

## Questions?

If you have any questions about my pipeline or need something from me, reach out directly.  
