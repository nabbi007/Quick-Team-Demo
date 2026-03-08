# Backend Kafka Integration Guide — QuickPoll


## Overview

The analytics pipeline consumes Kafka events and enriches from PostgreSQL.
Backend must publish events **after successful DB commit**.

Current required topics:

- `vote_events`
- `poll_events`

> `invite_events` is **phase 2** and not required in this sprint.

---

## 1. Topic Contracts (Required Fields)

### Topic: `vote_events`

Publish on every successful vote insert (`votes` table).

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

- `event_type` (must be `VOTE_CAST`)
- `vote_id`
- `poll_id`
- `option_id`
- `user_id`
- `voted_at` (ISO-8601 UTC)

### Topic: `poll_events`

Publish on poll create/close transitions.

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

- `event_type` (must be `POLL_CREATED` or `POLL_CLOSED`)
- `poll_id`
- `creator_id`
- `occurred_at` (ISO-8601 UTC)

### Optional Poll Context Fields

You may include these for richer observability (consumer ignores missing optional fields):

- `title`
- `question`
- `multi_select`
- `expires_at`
- `active`
- `created_at`

Example with optional fields:

```json
{
  "event_type": "POLL_CREATED",
  "poll_id": 7,
  "title": "Programming Languages",
  "creator_id": 3,
  "occurred_at": "2026-03-08T14:30:00Z",
  "question": "Best programming language?",
  "multi_select": false,
  "expires_at": "2026-04-05T23:59:59Z",
  "active": true,
  "created_at": "2026-03-08T14:25:00Z"
}
```

---

## 2. Non-Negotiable Rules

1. Use `snake_case` JSON keys.
2. Publish only after DB transaction succeeds.
3. Use `poll_id` as Kafka message key for both topics.
4. Timestamps must be ISO-8601 UTC (`...Z`).
5. Unrecognized `event_type` values are skipped by analytics consumer.

---

## 3. Mapping from OLTP Schema

From `data-engineering/schema.sql`:

- `votes.id` -> `vote_id`
- `votes.poll_id` -> `poll_id`
- `votes.option_id` -> `option_id`
- `votes.user_id` -> `user_id`
- `votes.created_at` -> `voted_at`
- `polls.id` -> `poll_id`
- `polls.creator_id` -> `creator_id`
- `polls.title` -> `title` (optional)
- `polls.question` -> `question` (optional)
- `polls.multi_select` -> `multi_select` (optional)
- `polls.expires_at` -> `expires_at` (optional)
- `polls.active` -> `active` (optional)
- `polls.created_at` -> `created_at` (optional)

---

## 4. Spring Boot Setup

### 4.1 Dependency

```xml
<dependency>
  <groupId>org.springframework.kafka</groupId>
  <artifactId>spring-kafka</artifactId>
</dependency>
```

### 4.2 Properties

```properties
spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
spring.kafka.producer.acks=all
spring.kafka.producer.retries=3
spring.kafka.producer.properties.enable.idempotence=true
spring.jackson.property-naming-strategy=SNAKE_CASE
```

### 4.3 DTOs (Lean Contract)

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

### 4.4 Publisher Pattern

```java
kafkaTemplate.send("vote_events", String.valueOf(vote.getPoll().getId()), event);
kafkaTemplate.send("poll_events", String.valueOf(poll.getId()), event);
```

Call from service layer after save/commit for:

- vote cast -> `VOTE_CAST`
- poll created -> `POLL_CREATED`
- poll closed/expired -> `POLL_CLOSED`

---

## 5. Quick Verification

List topics:

```bash
docker exec qp-kafka kafka-topics --bootstrap-server localhost:9092 --list
```

Watch events:

```bash
docker exec qp-kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic vote_events --from-beginning

docker exec qp-kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic poll_events --from-beginning
```

Expected analytics logs (pipeline running):

```text
Processed VOTE_CAST: poll_id=..., user_id=...
Processed POLL_CREATED: poll_id=...
Processed POLL_CLOSED: poll_id=...
```

---

## 6. Backend Checklist

- [ ] Add `spring-kafka` dependency
- [ ] Configure `KAFKA_BOOTSTRAP_SERVERS`
- [ ] Emit `VOTE_CAST` with all required vote fields
- [ ] Emit `POLL_CREATED` and `POLL_CLOSED` with required poll fields
- [ ] Keep event keys/message field names in `snake_case`
- [ ] Use `poll_id` as Kafka key
- [ ] Publish after DB commit
