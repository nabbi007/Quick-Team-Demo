# DevOps PubSub Infrastructure Guide (AI-Agent Ready)

This is the infrastructure runbook for setting up Kafka + ZooKeeper based PubSub so:

- Backend publishes events reliably
- Data-engineering consumes and processes events reliably
- Both services share the same Postgres source of truth

Use this as both a human guide and a direct handoff to an AI agent.

## 1. Target Outcome

At the end of setup, all of these must be true:

1. Kafka and ZooKeeper are running and healthy.
2. Backend can publish `vote_events` and `poll_events`.
3. Data-engineering can consume those topics.
4. Both backend and data-engineering point to the same Postgres instance.
5. Pipeline logs show processed `VOTE_CAST`, `POLL_CREATED`, `POLL_CLOSED`.

## 2. Architecture (PubSub + Shared DB)

```text
                        +-------------------+
                        |   Frontend (UI)   |
                        +---------+---------+
                                  |
                                  v
                        +-------------------+
                        | Backend (Spring)  |
                        | OLTP owner        |
                        +----+---------+----+
                             |         |
        writes OLTP rows ----+         +---- publishes events ----+
                             |                                  |
                             v                                  v
                    +----------------+                 +------------------+
                    | Postgres       |                 | Kafka Broker     |
                    | (shared)       |                 | + ZooKeeper      |
                    +-------+--------+                 +--------+---------+
                            ^                                   |
                            |                                   |
            reads OLTP + writes analytics                       |
                            |                          consumes events
                            |                                   v
                    +-------+-------------------------------+----------+
                    | Data-engineering pipeline                        |
                    | backfill + streaming + analytics tables          |
                    +--------------------------------------------------+
```

## 3. Event Topics and Contracts

The required topics are:

- `vote_events`
- `poll_events`

Exact payload contract is defined in:

- `data-engineering/docs/BACKEND_KAFKA_GUIDE.md`

Current accepted event types by pipeline:

- `VOTE_CAST`
- `POLL_CREATED`
- `POLL_CLOSED`

Any other `event_type` is skipped.

## 4. Current Repo Reality (Important)

Current files in repo:

- Root `docker-compose.yml` does not include Kafka/ZooKeeper or data-engineering service.
- `data-engineering/docker-compose.kafka-dev.yml` already provides:
  - Postgres
  - ZooKeeper
  - Kafka with multi-listener setup

So DevOps has two valid patterns:

1. Keep infra compose separate (`data-engineering/docker-compose.kafka-dev.yml`) and run backend independently.
2. Create a unified compose that includes backend + data-engineering + Kafka + ZooKeeper + Postgres.

For team consistency, prefer option 2 for shared environments.

## 5. Kafka Listener Matrix

Kafka is configured with multiple listeners in `data-engineering/docker-compose.kafka-dev.yml`.

Use the right bootstrap server per client location:

- Process running on host machine:
  - `localhost:9092`
- Containerized backend outside Kafka compose network:
  - `host.docker.internal:9094`
- Container in same compose network as Kafka service:
  - `kafka:29092`
- Tunnel clients:
  - `<tunnel-host>:9093`

If clients fail with broker connection errors, this matrix is the first thing to check.

## 6. Compose Blueprint (Unified Stack)

Create a compose file (recommended name: `docker-compose.pubsub.yml`) at repo root.

This blueprint is intentionally explicit and safe for AI-assisted setup:

```yaml
version: "3.8"

services:
  postgres:
    image: postgres:17
    container_name: qp-postgres
    environment:
      POSTGRES_DB: quickpoll
      POSTGRES_USER: quickpoll
      POSTGRES_PASSWORD: quickpoll123
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./data-engineering/schema.sql:/docker-entrypoint-initdb.d/001-schema.sql:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U quickpoll -d quickpoll"]
      interval: 10s
      timeout: 5s
      retries: 10

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    container_name: qp-zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    ports:
      - "2181:2181"

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: qp-kafka
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
      - "9093:9093"
      - "9094:9094"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT,PLAINTEXT_TUNNEL:PLAINTEXT,PLAINTEXT_DOCKER:PLAINTEXT
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:29092,PLAINTEXT_HOST://0.0.0.0:9092,PLAINTEXT_TUNNEL://0.0.0.0:9093,PLAINTEXT_DOCKER://0.0.0.0:9094
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092,PLAINTEXT_TUNNEL://port-9093.henryantwi.me:9093,PLAINTEXT_DOCKER://host.docker.internal:9094
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
      KAFKA_HEAP_OPTS: "-Xmx256m -Xms256m"
    healthcheck:
      test: ["CMD-SHELL", "kafka-topics --bootstrap-server localhost:9092 --list"]
      interval: 15s
      timeout: 10s
      retries: 10

  backend:
    build: ./backend
    container_name: quickpoll-backend
    ports:
      - "8080:8080"
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/quickpoll
      DB_USERNAME: quickpoll
      DB_PASSWORD: quickpoll123
      JWT_SECRET: ${JWT_SECRET:-replace-me}
      KAFKA_BOOTSTRAP_SERVERS: kafka:29092
      KAFKA_TOPIC_VOTE_EVENTS: vote_events
      KAFKA_TOPIC_POLL_EVENTS: poll_events
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
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: quickpoll
      DB_USER: quickpoll
      DB_PASSWORD: quickpoll123
      KAFKA_BOOTSTRAP_SERVERS: kafka:29092
      KAFKA_TOPIC_VOTE_EVENTS: vote_events
      KAFKA_TOPIC_POLL_EVENTS: poll_events
      KAFKA_GROUP_ID: quickpoll-analytics
      LOG_LEVEL: INFO
      BACKFILL_INTERVAL_MINUTES: 30
      WATERMARK_OVERLAP_MINUTES: 5
      FORCE_FULL_BACKFILL: "false"
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

## 7. Startup and Teardown Commands

From repo root:

```bash
docker compose -f docker-compose.pubsub.yml up -d --build
docker compose -f docker-compose.pubsub.yml ps
```

Teardown:

```bash
docker compose -f docker-compose.pubsub.yml down
```

Teardown with volume reset:

```bash
docker compose -f docker-compose.pubsub.yml down -v
```

## 8. Health and Smoke Checks

### 8.1 Infrastructure checks

```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
docker exec qp-postgres pg_isready -U quickpoll -d quickpoll
docker exec qp-kafka kafka-topics --bootstrap-server localhost:9092 --list
```

Expected topics at minimum (after first publish):

- `vote_events`
- `poll_events`

### 8.2 Producer-side check (Backend -> Kafka)

```bash
docker exec qp-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic poll_events \
  --from-beginning
```

Trigger poll create/close from backend API and confirm events appear.

### 8.3 Consumer-side check (Kafka -> Data Engineering)

```bash
docker logs quickpoll-analytics --tail 200
```

Expected lines include:

- `Starting Kafka consumer loop...`
- `Processed VOTE_CAST: ...`
- `Processed POLL_CREATED: ...`
- `Processed POLL_CLOSED: ...`

## 9. Required Env Map by Service

### 9.1 Backend env (containerized)

- `DB_URL=jdbc:postgresql://postgres:5432/quickpoll`
- `DB_USERNAME=quickpoll`
- `DB_PASSWORD=quickpoll123`
- `JWT_SECRET=<secret>`
- `KAFKA_BOOTSTRAP_SERVERS=kafka:29092`
- `KAFKA_TOPIC_VOTE_EVENTS=vote_events`
- `KAFKA_TOPIC_POLL_EVENTS=poll_events`

### 9.2 Data-engineering env (containerized)

- `DB_HOST=postgres`
- `DB_PORT=5432`
- `DB_NAME=quickpoll`
- `DB_USER=quickpoll`
- `DB_PASSWORD=quickpoll123`
- `KAFKA_BOOTSTRAP_SERVERS=kafka:29092`
- `KAFKA_TOPIC_VOTE_EVENTS=vote_events`
- `KAFKA_TOPIC_POLL_EVENTS=poll_events`
- `KAFKA_GROUP_ID=quickpoll-analytics`

## 10. Common Failure Patterns

1. Backend cannot connect to Kafka:
   - Usually wrong listener value (`localhost` inside container).
   - Fix to `kafka:29092`.

2. Data-engineering restarts repeatedly:
   - Kafka/Postgres not healthy when pipeline starts.
   - Ensure `depends_on: condition: service_healthy` and keep health checks.

3. No real-time analytics updates:
   - Backend not publishing events or wrong topic name.
   - Verify message flow on Kafka console consumer.

4. Schema-related runtime errors:
   - Backend DB model not aligned with `data-engineering/schema.sql`.
   - Align schema and event payload contracts (see backend guide).

## 11. AI-Agent Task Prompt for DevOps

Copy/paste this directly into an AI coding agent:

```text
Set up a unified Docker Compose PubSub infrastructure for QuickPoll so backend and data-engineering both work with shared Postgres and Kafka/ZooKeeper.

Repository context:
- Existing infra baseline: data-engineering/docker-compose.kafka-dev.yml
- Backend compose exists but lacks Kafka/ZooKeeper integration.
- Root compose exists but currently has no Kafka/ZooKeeper and no data-engineering service.

Tasks:
1) Create docker-compose.pubsub.yml at repo root using the blueprint in data-engineering/docs/DEVOPS_INFRA_GUIDE.md.
2) Include services: postgres, zookeeper, kafka, backend, data-engineering, frontend.
3) Preserve Kafka multi-listener strategy:
   - kafka:29092 for intra-compose containers
   - localhost:9092 for host clients
   - host.docker.internal:9094 for external containers
4) Add health checks for postgres and kafka and wire backend/data-engineering depends_on with service_healthy conditions.
5) Wire backend env for DB and Kafka topics/bootstrap.
6) Wire data-engineering env for DB, Kafka topics/group, and pipeline settings.
7) Do not remove existing compose files; add this as a new unified compose.
8) Provide verification commands:
   - list running containers
   - check kafka topics
   - consume poll_events and vote_events
   - inspect data-engineering logs for processed events
9) Document any assumptions in a short README section.

Definition of done:
- docker compose -f docker-compose.pubsub.yml up -d --build succeeds
- backend can publish poll_events and vote_events
- data-engineering consumes events and logs processing
- shared postgres is healthy and reachable by both services
```

## 12. Definition of Done (DevOps)

Infrastructure is complete only when:

1. `docker compose -f docker-compose.pubsub.yml up -d --build` succeeds.
2. Postgres, ZooKeeper, Kafka, backend, data-engineering, frontend are healthy/running.
3. Backend publishes events to `vote_events` and `poll_events`.
4. Data-engineering consumes and processes those events.
5. Listener mismatch errors are absent from logs.
