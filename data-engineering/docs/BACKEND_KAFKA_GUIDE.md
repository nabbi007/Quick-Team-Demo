# Backend Kafka Integration Runbook (AI-Agent Ready)

This document tells backend exactly what to implement so Spring Boot publishes the right Kafka events for the data pipeline.

It is written so you can hand it to an AI coding agent and have it execute end-to-end.

## 1. Source of Truth

Use these files as canonical contracts:

- `data-engineering/schema.sql`
- `data-engineering/src/data_engineering/pipeline/streaming.py`
- `data-engineering/scripts/seed_and_publish.py`
- `data-engineering/src/data_engineering/config.py`

What the pipeline currently consumes:

- Topic `vote_events` with `event_type=VOTE_CAST`
- Topic `poll_events` with `event_type=POLL_CREATED` or `event_type=POLL_CLOSED`

## 2. Current Backend Gaps (Observed in `backend/`)

From the current backend codebase:

- `backend/pom.xml` does not include `spring-kafka`.
- `backend/src/main/resources/application.yml` does not define Kafka producer config.
- `backend/src/main/java/com/amalitech/quickpoll/service/PollService.java` does not publish Kafka events.
- Vote flow is incomplete (`vote` method is TODO in `PollService`, vote endpoint TODO in `PollController`).
- `PollSchedulerService` uses bulk update (`closeExpiredPolls`) returning only a count, so per-poll `POLL_CLOSED` events cannot be emitted.
- `Poll` entity currently has `anonymous` and no `title`, but the updated DE schema has `title` and no `anonymous`.

You must fix these for stable pipeline integration.

## 3. Required Kafka Event Contracts

### 3.1 `vote_events`

Publish after every successful vote insert.

```json
{
  "event_type": "VOTE_CAST",
  "vote_id": 42,
  "poll_id": 7,
  "option_id": 15,
  "user_id": 3,
  "voted_at": "2026-03-08T14:30:00Z"
}
```

Required fields:

- `event_type` must be `VOTE_CAST`
- `vote_id`
- `poll_id`
- `option_id`
- `user_id`
- `voted_at` (ISO-8601 UTC string with `Z`)

### 3.2 `poll_events`

Publish after poll create and poll close (manual or scheduler).

```json
{
  "event_type": "POLL_CREATED",
  "poll_id": 7,
  "creator_id": 3,
  "occurred_at": "2026-03-08T14:30:00Z"
}
```

```json
{
  "event_type": "POLL_CLOSED",
  "poll_id": 7,
  "creator_id": 3,
  "occurred_at": "2026-03-09T00:00:00Z"
}
```

Required fields:

- `event_type` must be `POLL_CREATED` or `POLL_CLOSED`
- `poll_id`
- `creator_id`
- `occurred_at` (ISO-8601 UTC with `Z`)

Optional enrichment fields (safe to include):

- `title`
- `question`
- `multi_select`
- `expires_at`
- `active`
- `created_at`

### 3.3 Deprecated/Do Not Send as Contract Fields

Do not rely on these old poll-event fields:

- `poll_type`
- `status`
- `anonymous`

## 4. Runtime Networking Setup

### 4.1 Start Kafka + Postgres for local integration tests

From project root:

```bash
docker compose -f data-engineering/docker-compose.kafka-dev.yml up -d
```

### 4.2 Kafka bootstrap address by runtime mode

- Backend running on host machine:
  - `KAFKA_BOOTSTRAP_SERVERS=localhost:9092`
- Backend running in Docker container (outside DE compose network):
  - `KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9094`

Why: DE Kafka advertises `host.docker.internal:9094` for external containers.

## 5. File-by-File Implementation Plan (Backend)

### 5.1 `backend/pom.xml`

Add:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-kafka</artifactId>
</dependency>
```

### 5.2 `backend/src/main/resources/application.yml`

Add Kafka producer config and topic names:

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
      retries: 3
      properties:
        enable.idempotence: true

app:
  kafka:
    topics:
      vote-events: ${KAFKA_TOPIC_VOTE_EVENTS:vote_events}
      poll-events: ${KAFKA_TOPIC_POLL_EVENTS:poll_events}
```

### 5.3 Add event DTOs (new package recommended)

Suggested package:

- `backend/src/main/java/com/amalitech/quickpoll/events/`

Create:

- `VoteCastEvent.java`
- `PollEvent.java`

Use snake_case JSON names via `@JsonProperty` (or global Jackson naming strategy).

### 5.4 Add Kafka publisher service

Suggested file:

- `backend/src/main/java/com/amalitech/quickpoll/service/KafkaEventPublisher.java`

Responsibilities:

- publishVoteCast(Vote vote)
- publishPollCreated(Poll poll)
- publishPollClosed(Poll poll)
- Use `KafkaTemplate<String, Object>`
- Use `poll_id` as key for both topics

### 5.5 Publish only after DB commit

Preferred pattern:

- Emit internal domain events inside transactional service
- Handle them with `@TransactionalEventListener(phase = AFTER_COMMIT)`
- Listener then calls `KafkaEventPublisher`

If you publish inside a transaction before commit, pipeline can ingest events for rows that later roll back.

### 5.6 Update poll flows

### In `PollService.createPoll(...)`

After save succeeds, publish `POLL_CREATED` (AFTER_COMMIT listener path).

### In `PollService.closePoll(...)`

After close succeeds, publish `POLL_CLOSED`.

### 5.7 Implement vote flow fully

### `PollService`

Implement `vote(...)` method and persist vote row(s).

After successful insert, publish `VOTE_CAST` using the inserted vote row values:

- `vote_id` from `Vote.id`
- `poll_id` from `Vote.poll.id`
- `option_id` from `Vote.option.id`
- `user_id` from `Vote.user.id`
- `voted_at` from `Vote.createdAt`

### `PollController`

Add endpoint:

- `POST /api/polls/{id}/vote`

### 5.8 Fix scheduler close flow for events

Current `PollSchedulerService` bulk-updates rows using `pollRepository.closeExpiredPolls(now)` and gets only count.

That is not enough to emit one `POLL_CLOSED` event per poll.

Replace with flow like:

1. Query expired active polls (entity list or ids + creator_id)
2. Mark each poll inactive
3. Save changes
4. Emit one `POLL_CLOSED` event per poll AFTER_COMMIT

You can keep batch efficiency, but you must retain poll IDs to publish events.

### 5.9 Align backend entity with updated schema

DE schema now expects:

- `polls.title` exists and is required
- `polls.anonymous` does not exist

Backend currently has the opposite shape.

Update backend model/mappers/DTOs and DB migration strategy to match schema contract used by data-engineering.

Minimum alignment targets:

- Add `title` to `Poll` entity and request/response DTOs
- Remove `anonymous` column usage from entity + mapper + DTO if DB schema no longer has it

If backend and DE schema disagree, either writes fail or pipeline analytics become inconsistent.

## 6. Recommended Class Skeletons

### 6.1 Event records

```java
public record VoteCastEvent(
    String event_type,
    Long vote_id,
    Long poll_id,
    Long option_id,
    Long user_id,
    String voted_at
) {}

public record PollEvent(
    String event_type,
    Long poll_id,
    Long creator_id,
    String occurred_at,
    String title,
    String question,
    Boolean multi_select,
    String expires_at,
    Boolean active,
    String created_at
) {}
```

### 6.2 Producer send pattern

```java
kafkaTemplate.send(voteTopic, String.valueOf(pollId), voteCastEvent);
kafkaTemplate.send(pollTopic, String.valueOf(pollId), pollEvent);
```

## 7. Verification Checklist

### 7.1 Topic visibility

```bash
docker exec qp-kafka kafka-topics --bootstrap-server localhost:9092 --list
```

Must include:

- `vote_events`
- `poll_events`

### 7.2 Observe produced messages

```bash
docker exec qp-kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic vote_events --from-beginning
docker exec qp-kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic poll_events --from-beginning
```

### 7.3 End-to-end with pipeline

Run data pipeline and then backend actions:

1. Create poll
2. Cast vote
3. Close poll (manual)
4. Wait scheduler interval and ensure expired polls also produce close events

Expected pipeline logs:

```text
Processed VOTE_CAST: poll_id=..., user_id=...
Processed POLL_CREATED: poll_id=...
Processed POLL_CLOSED: poll_id=...
```

## 8. AI-Agent Task Prompt (Copy/Paste)

Use this prompt directly with an AI coding agent in `backend/`:

```text
Implement Kafka producer integration for QuickPoll backend so it emits events consumed by data-engineering pipeline.

Requirements:
1) Add spring-kafka dependency.
2) Add Kafka producer config in application.yml with env-driven bootstrap/topic values.
3) Create event DTOs for VoteCastEvent and PollEvent with snake_case JSON keys.
4) Add KafkaEventPublisher service using KafkaTemplate<String, Object>.
5) Ensure publish occurs after DB commit (use TransactionalEventListener AFTER_COMMIT).
6) Wire events into:
   - PollService.createPoll -> POLL_CREATED
   - PollService.closePoll -> POLL_CLOSED
   - Poll vote flow -> VOTE_CAST (implement vote method and controller endpoint if missing)
7) Refactor PollSchedulerService expired-close flow to emit one POLL_CLOSED event per closed poll.
8) Align Poll entity/DTO/mapper with DE schema: title required, anonymous removed.
9) Add tests for publisher payload shape and service-level publish triggers.
10) Provide run instructions and sample curl commands to verify events on Kafka topics vote_events and poll_events.

Use data-engineering/docs/BACKEND_KAFKA_GUIDE.md contracts exactly.
```

## 9. Definition of Done

- Backend publishes valid `VOTE_CAST`, `POLL_CREATED`, `POLL_CLOSED` payloads.
- Events are emitted only after successful DB commit.
- Manual poll close and scheduler poll close both emit `POLL_CLOSED`.
- Vote endpoint emits `VOTE_CAST` for every stored vote row.
- Backend model aligns with DE schema (`title` present, `anonymous` removed from DB usage).
- Kafka consumers show events in both required topics.
- Data pipeline logs confirm event processing.
