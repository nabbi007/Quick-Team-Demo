# DevOps Infrastructure Guide — QuickPoll

**From:** Henry Nana Antwi — Data Engineer, Team 6
**To:** DevOps Engineer — Illiasu
**Project:** QuickPoll — AmaliTech Phase 1 Capstone

---

## TL;DR

The EC2 instance runs **three** interdependent services. Two of them — the **Java Spring Boot
backend** and the **Python data-engineering pipeline** — both depend on a **shared PostgreSQL**
database and a **shared Kafka** broker. Neither service can function without both dependencies
running and healthy.

```
┌──────────────────────────────────────────────────────────────────────┐
│                           EC2 Instance                               │
│                                                                      │
│   ┌────────────┐    writes    ┌──────────────────┐    reads          │
│   │  Backend   │ ──────────► │    PostgreSQL     │ ◄──────────┐     │
│   │ (Spring    │              │   (AWS RDS or     │             │     │
│   │  Boot)     │              │    Docker)        │             │     │
│   └────┬───────┘              └──────────────────┘     ┌───────┴──┐ │
│        │ publishes                                      │  Data    │ │
│        ▼                                                │  Eng     │ │
│   ┌────────────┐    consumes                            │ Pipeline │ │
│   │   Kafka    │ ──────────────────────────────────────►│ (Python) │ │
│   │ (Docker)   │                                        └──────────┘ │
│   └────────────┘                                                     │
│                                                                      │
│   ┌────────────┐                                                     │
│   │  Frontend  │   (served by Nginx, talks to Backend API)           │
│   │ (Angular)  │                                                     │
│   └────────────┘                                                     │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 1. Service Inventory

| Service | Image / Build | Container Name | Ports |
|---------|---------------|----------------|-------|
| **PostgreSQL** | `postgres:16-alpine` (or AWS RDS) | `quickpoll-db` | `5432` |
| **Kafka** | `confluentinc/cp-kafka:7.5.0` | `qp-kafka` | `9092` |
| **Zookeeper** | `confluentinc/cp-zookeeper:7.5.0` | `qp-zookeeper` | `2181` |
| **Backend** | `./backend` (Dockerfile) | `quickpoll-backend` | `8080` |
| **Data Engineering** | `./data-engineering` (Dockerfile) | `quickpoll-analytics` | — (no exposed port) |
| **Frontend** | `./frontend` (Dockerfile) | `quickpoll-frontend` | `4200 → 80` |

---

## 2. Why Both Services Need Kafka AND Postgres

### PostgreSQL is shared storage

Both services read from and write to the **same** `quickpoll` database:

- **Backend** owns the OLTP tables (`users`, `polls`, `poll_options`, `votes`)
- **Data Engineering** reads those OLTP tables and writes to `analytics_*` tables + `pipeline_watermarks`

They share one database, one user (`quickpoll`), one schema. No separate databases needed.

### Kafka is the event bus

The backend **publishes** events when votes are cast or polls are created/closed. The data
engineering pipeline **consumes** those events to compute real-time analytics. Without Kafka:

- The backend can still write to PostgreSQL ✅
- But the analytics pipeline has **no real-time signal** — it falls back to 30-minute batch
  backfills ⚠️
- Dashboard data becomes stale instead of near-real-time ❌

**Both services must have `KAFKA_BOOTSTRAP_SERVERS` in their environment.**

---

## 3. Recommended `docker-compose.yml`

This is a unified compose file that runs everything. Merge it with the existing root
`docker-compose.yml` or replace it entirely.

```yaml
version: "3.8"

services:
  # ── Shared Infrastructure ────────────────────────────────────

  postgres:
    image: postgres:16-alpine
    container_name: quickpoll-db
    environment:
      POSTGRES_DB: quickpoll
      POSTGRES_USER: quickpoll
      POSTGRES_PASSWORD: quickpoll123
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U quickpoll"]
      interval: 10s
      timeout: 5s
      retries: 5

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    container_name: qp-zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: qp-kafka
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: >-
        PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_LISTENERS: >-
        PLAINTEXT://0.0.0.0:29092,PLAINTEXT_HOST://0.0.0.0:9092
      KAFKA_ADVERTISED_LISTENERS: >-
        PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    healthcheck:
      test: ["CMD-SHELL", "kafka-topics --bootstrap-server localhost:9092 --list"]
      interval: 15s
      timeout: 10s
      retries: 10
      start_period: 30s

  # ── Application Services ─────────────────────────────────────

  backend:
    build: ./backend
    container_name: quickpoll-backend
    ports:
      - "8080:8080"
    environment:
      # PostgreSQL
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/quickpoll
      SPRING_DATASOURCE_USERNAME: quickpoll
      SPRING_DATASOURCE_PASSWORD: quickpoll123
      JWT_SECRET: ${JWT_SECRET:-Z29vZC1zZWNyZXQta2V5LWZvcg}
      # Kafka — REQUIRED for event publishing
      KAFKA_BOOTSTRAP_SERVERS: kafka:29092
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_healthy

  data-engineering:
    build: ./data-engineering
    container_name: quickpoll-analytics
    environment:
      # PostgreSQL
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: quickpoll
      DB_USER: quickpoll
      DB_PASSWORD: quickpoll123
      # Kafka — REQUIRED for event consuming
      KAFKA_BOOTSTRAP_SERVERS: kafka:29092
      KAFKA_TOPIC_VOTE_EVENTS: vote_events
      KAFKA_TOPIC_POLL_EVENTS: poll_events
      KAFKA_GROUP_ID: quickpoll-analytics
      # Pipeline
      LOG_LEVEL: INFO
      BACKFILL_INTERVAL_MINUTES: 30
      FORCE_FULL_BACKFILL: "false"
      # Cloudflare R2 (DLQ) — fill these in
      R2_ENDPOINT_URL: ${R2_ENDPOINT_URL:-}
      R2_ACCESS_KEY_ID: ${R2_ACCESS_KEY_ID:-}
      R2_SECRET_ACCESS_KEY: ${R2_SECRET_ACCESS_KEY:-}
      R2_DLQ_BUCKET: ${R2_DLQ_BUCKET:-quickpoll-dlq}
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_healthy
    restart: on-failure

  frontend:
    build: ./frontend
    container_name: quickpoll-frontend
    ports:
      - "4200:80"
    depends_on:
      - backend

volumes:
  pgdata:
```

### Key decisions in this compose file

| Decision | Why |
|----------|-----|
| `kafka:29092` for inter-container traffic | The `PLAINTEXT` listener on port 29092 is for Docker-internal communication. `localhost:9092` only works from the host machine. |
| `condition: service_healthy` on kafka | Both backend and data-engineering MUST wait for Kafka. Without the health gate, they start before Kafka is ready and crash. |
| `start_period: 30s` on Kafka healthcheck | Kafka is slow to initialize. Without a start period, Docker counts early failures toward retries and gives up too soon. |
| `restart: on-failure` on data-engineering | The Python pipeline is a long-running consumer. If it crashes for a transient reason, Docker restarts it automatically. |
| `KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"` | Topics (`vote_events`, `poll_events`) are auto-created on first publish. No manual topic creation needed. |

---

## 4. Kafka Listener Explanation

Kafka requires different advertised listeners depending on where the client connects from:

| Listener | Port | Who uses it | `KAFKA_BOOTSTRAP_SERVERS` value |
|----------|------|-------------|-------------------------------|
| `PLAINTEXT` | 29092 | Docker containers (backend, data-eng) | `kafka:29092` |
| `PLAINTEXT_HOST` | 9092 | Host machine (local dev, debugging) | `localhost:9092` |

**Rule:** Any service running inside Docker must use `kafka:29092`. Any process running
directly on the host uses `localhost:9092`.

If you see `NoBrokersAvailable` or `Connection refused` in either service's logs, this is
almost always a listener mismatch. Check which bootstrap server value that service is using.

---

## 5. PostgreSQL — Shared Schema

Both services operate in the same `quickpoll` database. There are NO separate schemas or
databases.

### Tables owned by Backend (OLTP)
| Table | Purpose |
|-------|---------|
| `users` | Registered users |
| `polls` | Poll questions (has both `title` and `question` as NOT NULL columns) |
| `poll_options` | Answer choices per poll |
| `votes` | Individual vote records |

### Tables owned by Data Engineering (Analytics)
| Table | Purpose |
|-------|---------|
| `analytics_poll_summary` | Aggregated poll stats |
| `analytics_option_breakdown` | Vote distribution per option |
| `analytics_votes_timeseries` | Hourly vote buckets |
| `analytics_user_participation` | Per-user engagement metrics |
| `pipeline_watermarks` | Incremental backfill tracking |

**Data Engineering creates its own tables on startup** via SQLAlchemy `CREATE IF NOT EXISTS`.
No manual DDL is needed.

**Conflict risk: ZERO.** The backend writes to OLTP tables. Data Engineering reads OLTP tables
and writes only to `analytics_*` + `pipeline_watermarks`. They never write to the same tables.

---

## 6. Kafka Topics

| Topic | Producer | Consumer | Purpose |
|-------|----------|----------|---------|
| `vote_events` | Backend | Data Engineering | Vote cast notifications |
| `poll_events` | Backend | Data Engineering | Poll created/closed notifications |

Both topics are auto-created when the backend first publishes to them
(`KAFKA_AUTO_CREATE_TOPICS_ENABLE=true`). Alternatively, pre-create them:

```bash
docker exec qp-kafka kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic vote_events --partitions 1 --replication-factor 1

docker exec qp-kafka kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic poll_events --partitions 1 --replication-factor 1
```

---

## 7. Environment Variables Reference

### Backend (`quickpoll-backend`)

| Variable | Value (Docker) | Required | Notes |
|----------|----------------|----------|-------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/quickpoll` | ✅ | |
| `SPRING_DATASOURCE_USERNAME` | `quickpoll` | ✅ | |
| `SPRING_DATASOURCE_PASSWORD` | `quickpoll123` | ✅ | |
| `JWT_SECRET` | (secret) | ✅ | |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka:29092` | ✅ | **NEW** — needed for event publishing |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `kafka:29092` | ✅ | Spring Boot auto-config reads this |

### Data Engineering (`quickpoll-analytics`)

| Variable | Value (Docker) | Required | Notes |
|----------|----------------|----------|-------|
| `DB_HOST` | `postgres` | ✅ | Docker DNS name |
| `DB_PORT` | `5432` | ✅ | |
| `DB_NAME` | `quickpoll` | ✅ | |
| `DB_USER` | `quickpoll` | ✅ | |
| `DB_PASSWORD` | `quickpoll123` | ✅ | |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka:29092` | ✅ | |
| `KAFKA_TOPIC_VOTE_EVENTS` | `vote_events` | ✅ | |
| `KAFKA_TOPIC_POLL_EVENTS` | `poll_events` | ✅ | |
| `KAFKA_GROUP_ID` | `quickpoll-analytics` | ✅ | |
| `LOG_LEVEL` | `INFO` | | |
| `BACKFILL_INTERVAL_MINUTES` | `30` | | |
| `FORCE_FULL_BACKFILL` | `false` | | Set `true` for first run |
| `R2_ENDPOINT_URL` | (Cloudflare R2 URL) | ⚠️ | DLQ — optional for MVP |
| `R2_ACCESS_KEY_ID` | (R2 key) | ⚠️ | |
| `R2_SECRET_ACCESS_KEY` | (R2 secret) | ⚠️ | |
| `R2_DLQ_BUCKET` | `quickpoll-dlq` | | |

> **⚠️ R2 variables:** If left empty, the DLQ write will fail but the pipeline continues
> processing events normally. Not blocking for MVP.

---

## 8. Startup Order & Health Checks

The correct startup order is:

```
1. postgres      (healthcheck: pg_isready)
2. zookeeper     (no healthcheck needed — Kafka handles retries)
3. kafka         (healthcheck: kafka-topics --list)
4. backend       (depends_on: postgres ✅, kafka ✅)
5. data-engineering (depends_on: postgres ✅, kafka ✅)
6. frontend      (depends_on: backend)
```

The `data-engineering` container has an additional in-container readiness check
(`entrypoint.sh`) that waits for both Postgres and Kafka with `pg_isready` and `nc -z`
before starting the Python process. This is a safety net — the Docker healthchecks should
already ensure readiness.

---

## 9. If Using AWS RDS Instead of Docker Postgres

If Postgres is on RDS rather than in Docker:

1. **Remove** the `postgres` service from docker-compose
2. **Remove** the `pgdata` volume
3. **Update** connection details for both services:

   **Backend:**
   ```
   SPRING_DATASOURCE_URL=jdbc:postgresql://<RDS_ENDPOINT>:5432/quickpoll
   ```

   **Data Engineering:**
   ```
   DB_HOST=<RDS_ENDPOINT>
   DB_PORT=5432
   ```

4. **Remove** the `depends_on: postgres` from both services
5. **Ensure** the EC2 security group allows outbound traffic to the RDS security group on
   port 5432

Everything else stays the same. Kafka still runs in Docker on the EC2.

---

## 10. Verification Checklist

After `docker compose up -d`, run through this checklist:

### Infrastructure
```bash
# All containers running?
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# Postgres accepting connections?
docker exec quickpoll-db pg_isready -U quickpoll

# Kafka healthy?
docker exec qp-kafka kafka-topics --bootstrap-server localhost:9092 --list
```

### Backend → Kafka connectivity
```bash
# After casting a vote via the API, check the topic:
docker exec qp-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic vote_events \
  --from-beginning --max-messages 1
```

Expected: a JSON message with `"event_type": "VOTE_CAST"`.

### Data Engineering → Kafka + Postgres
```bash
# Pipeline started and consuming?
docker logs quickpoll-analytics | head -20
```

Expected:
```
Waiting for PostgreSQL at postgres:5432...
PostgreSQL is ready.
Waiting for Kafka at kafka:29092...
Kafka is ready.
Starting QuickPoll analytics pipeline...
...
INFO | data_engineering.pipeline.backfill | Full backfill complete. Watermarks set.
INFO | data_engineering.pipeline.streaming | Starting Kafka consumer loop...
```

### End-to-end
```bash
# After a vote, check analytics tables:
docker exec quickpoll-db psql -U quickpoll -d quickpoll \
  -c "SELECT poll_id, total_votes FROM analytics_poll_summary LIMIT 5;"
```

---

## 11. Troubleshooting

| Symptom | Likely Cause | Fix |
|---------|--------------|-----|
| Backend can't connect to Kafka | Wrong bootstrap server — using `localhost:9092` inside Docker | Change to `kafka:29092` |
| Data Engineering keeps restarting | Kafka not ready yet | Ensure Kafka healthcheck has `start_period: 30s` |
| `NoBrokersAvailable` in Python logs | Kafka listener mismatch | Use `kafka:29092` inside Docker, `localhost:9092` on host |
| Analytics tables empty after votes | Backend not publishing events | Check `docker logs quickpoll-backend` for Kafka errors |
| Analytics tables stale (>30 min old) | Kafka events lost + backfill hasn't run | Force full backfill: set `FORCE_FULL_BACKFILL=true`, restart data-engineering |
| `Connection refused` on port 5432 | Postgres not running or RDS security group | Check container/RDS status, security groups |
| Kafka OOM on small EC2 | Default JVM heap too large | Add `KAFKA_HEAP_OPTS: "-Xmx256m -Xms256m"` to Kafka environment |

---

## 12. Resource Considerations for EC2

Kafka + Zookeeper + Postgres + Backend + Data Engineering + Frontend = **minimum 4 GB RAM**.

| Service | Approx Memory |
|---------|---------------|
| PostgreSQL | ~200 MB |
| Zookeeper | ~100 MB |
| Kafka | ~500 MB (default), tunable via `KAFKA_HEAP_OPTS` |
| Backend (JVM) | ~512 MB |
| Data Engineering (Python) | ~150 MB |
| Frontend (Nginx) | ~50 MB |
| **Total** | **~1.5 GB** (with headroom: **use t3.medium / 4 GB**) |

Recommended instance: **t3.medium** (2 vCPU, 4 GB RAM) or larger.

---

## Questions?

If anything in this guide is unclear or you need me to adjust the data-engineering
Dockerfile/entrypoint, reach out directly.
