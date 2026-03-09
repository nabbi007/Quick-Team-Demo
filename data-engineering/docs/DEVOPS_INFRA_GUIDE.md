# DevOps PubSub Infrastructure Guide (Human + AI Agent Ready)

This guide defines the shared infrastructure needed so backend publishing and data-pipeline consuming work reliably.

## 1. Final Architecture

For shared environments, use:

- `frontend` (Docker)
- `backend` (Docker)
- `data-pipeline` (Docker; data-engineering service name)
- `kafka` (Docker)
- `zookeeper` (Docker)
- **AWS Managed Postgres (RDS)** (external, not in Docker)

Important:

- The Postgres service inside `data-engineering/docker-compose.kafka-dev.yml` is **dev only**.
- Do not use local Postgres for shared dev/staging/prod environments.

## 2. Data Flow (PubSub + Shared OLTP)

```text
Frontend -> Backend API

Backend -> Postgres (OLTP writes)
Backend -> Kafka topics (publish events)

Data-pipeline -> Kafka topics (consume events)
Data-pipeline -> Postgres (read OLTP, write analytics tables)
```

Required Kafka topics:

- `vote_events`
- `poll_events`

Required event types consumed:

- `VOTE_CAST`
- `POLL_CREATED`
- `POLL_CLOSED`

Event payload contract source:

- `data-engineering/docs/BACKEND_KAFKA_GUIDE.md`

## 3. Repo Reality and Boundaries

Current repo state:

- Root `docker-compose.yml` currently does not include Kafka/ZooKeeper/data-pipeline.
- `data-engineering/docker-compose.kafka-dev.yml` contains Kafka/ZooKeeper and a local Postgres for local-only testing.

Boundary to enforce:

- Local Postgres in data-engineering compose is for personal local dev only.
- Shared infrastructure must point both backend and data-pipeline to AWS RDS.

## 4. Kafka and ZooKeeper Deployment Mode

Default mode to implement now:

- Keep **Kafka + ZooKeeper in Docker**.

Future option to document for later:

- AWS MSK (managed Kafka), replacing local Kafka/ZooKeeper containers.

## 5. Kafka Listener Matrix

Use the right bootstrap server based on where the client runs:

- Host process -> `localhost:9092`
- Container in same compose network -> `kafka:29092`
- Container outside compose network -> `host.docker.internal:9094`
- Tunnel client -> `<tunnel-host>:9093`

If broker connection fails, check this matrix first.

## 6. Unified Compose Blueprint (RDS-Based)

Create a unified compose file at repo root (name is DevOps choice).  
In commands below, replace `<compose-file>.yml` with your chosen filename.

```yaml
version: "3.8"

services:
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
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092,PLAINTEXT_TUNNEL://<tunnel-host>:9093,PLAINTEXT_DOCKER://host.docker.internal:9094
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
      DB_URL: jdbc:postgresql://<rds-endpoint>:5432/quickpoll
      DB_USERNAME: <rds-username>
      DB_PASSWORD: <rds-password>
      JWT_SECRET: ${JWT_SECRET:-replace-me}
      KAFKA_BOOTSTRAP_SERVERS: kafka:29092
      KAFKA_TOPIC_VOTE_EVENTS: vote_events
      KAFKA_TOPIC_POLL_EVENTS: poll_events
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
    depends_on:
      kafka:
        condition: service_healthy

  data-pipeline:
    build: ./data-engineering
    container_name: quickpoll-data-pipeline
    environment:
      DB_HOST: <rds-endpoint>
      DB_PORT: 5432
      DB_NAME: quickpoll
      DB_USER: <rds-username>
      DB_PASSWORD: <rds-password>
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
```

## 7. Startup and Teardown Commands

From repo root:

```bash
docker compose -f <compose-file>.yml up -d --build
docker compose -f <compose-file>.yml ps
```

Teardown:

```bash
docker compose -f <compose-file>.yml down
```

## 8. AWS RDS Requirements

RDS prerequisites:

1. RDS Postgres instance reachable from Docker host/EC2.
2. Security Group allows inbound `5432` from application host SG.
3. Database `quickpoll` exists (or backend creates schema via JPA update).
4. Credentials are available to both backend and data-pipeline.

Recommended checks:

```bash
psql "host=<rds-endpoint> port=5432 dbname=quickpoll user=<rds-username> sslmode=prefer"
```

If org policy requires TLS strictly, switch to `sslmode=require`.

## 9. Health and Smoke Verification

Infrastructure checks:

```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
docker exec qp-kafka kafka-topics --bootstrap-server localhost:9092 --list
```

Topic checks:

```bash
docker exec qp-kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic poll_events --from-beginning
docker exec qp-kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic vote_events --from-beginning
```

Pipeline checks:

```bash
docker logs quickpoll-data-pipeline --tail 200
```

Expected pipeline logs include:

- `Starting Kafka consumer loop...`
- `Processed VOTE_CAST: ...`
- `Processed POLL_CREATED: ...`
- `Processed POLL_CLOSED: ...`

## 10. Required Environment Mapping

Backend (containerized):

- `DB_URL=jdbc:postgresql://<rds-endpoint>:5432/quickpoll`
- `DB_USERNAME=<rds-username>`
- `DB_PASSWORD=<rds-password>`
- `JWT_SECRET=<secret>`
- `KAFKA_BOOTSTRAP_SERVERS=kafka:29092`
- `KAFKA_TOPIC_VOTE_EVENTS=vote_events`
- `KAFKA_TOPIC_POLL_EVENTS=poll_events`

Data-pipeline (containerized):

- `DB_HOST=<rds-endpoint>`
- `DB_PORT=5432`
- `DB_NAME=quickpoll`
- `DB_USER=<rds-username>`
- `DB_PASSWORD=<rds-password>`
- `KAFKA_BOOTSTRAP_SERVERS=kafka:29092`
- `KAFKA_TOPIC_VOTE_EVENTS=vote_events`
- `KAFKA_TOPIC_POLL_EVENTS=poll_events`
- `KAFKA_GROUP_ID=quickpoll-analytics`

## 11. Common Failure Patterns

1. Kafka connection failure in containers:
   - Cause: using `localhost:9092` inside container.
   - Fix: use `kafka:29092`.

2. Backend publishes but pipeline does not update:
   - Cause: wrong topic names or event_type mismatch.
   - Fix: verify `vote_events`/`poll_events` payload contract.

3. Both apps fail DB connection:
   - Cause: RDS SG/network/credentials.
   - Fix: validate SG, endpoint, user/password, TLS mode.

4. Pipeline starts before Kafka ready:
   - Cause: weak healthcheck or missing depends_on condition.
   - Fix: keep Kafka healthcheck + `condition: service_healthy`.

## 12. Optional Future Path: AWS MSK Proposal

If moving to AWS MSK later:

1. Remove `kafka` and `zookeeper` services from compose.
2. Set backend/data-pipeline bootstrap servers to MSK brokers.
3. Update SG/network rules for MSK broker ports.
4. Keep same topic names and payload contracts.
5. Re-run smoke checks using MSK bootstrap endpoints.

This is optional. Current baseline remains Docker Kafka + ZooKeeper.

## 13. AI Agent Prompt for DevOps

Copy/paste this into an AI coding agent:

```text
Implement a unified Docker Compose PubSub setup for QuickPoll using:
- Docker services: zookeeper, kafka, backend, data-pipeline, frontend
- External AWS RDS Postgres (no postgres service in compose)

Constraints:
1) Use service name "data-pipeline" for data-engineering.
2) Do not assume a compose filename. Use a placeholder in docs/commands.
3) Keep Kafka+ZooKeeper in Docker as default runtime.
4) Preserve Kafka multi-listener strategy (29092 internal, 9092 host, 9094 external containers, optional 9093 tunnel).
5) Wire backend and data-pipeline to the same RDS endpoint.
6) Add/keep Kafka health checks and service_healthy dependencies.
7) Ensure topic names are vote_events and poll_events.
8) Include verification commands for:
   - container health
   - kafka topics
   - event consumption from both topics
   - data-pipeline processed-event logs
9) Add a short optional section describing how to migrate to AWS MSK later.

Definition of done:
- Stack starts cleanly
- Backend publishes poll_events and vote_events
- Data-pipeline consumes and processes events
- Both backend and data-pipeline connect to the same AWS RDS database
```

## 14. Definition of Done (DevOps)

Infra setup is complete only when:

1. Kafka and ZooKeeper are healthy.
2. Backend and data-pipeline are running.
3. Backend publishes to `vote_events` and `poll_events`.
4. Data-pipeline processes those events in logs.
5. Both services connect successfully to AWS RDS.
