# Backend Kafka Integration Guide — QuickPoll

**From:** Henry Nana Antwi — Data Engineer, Team 6
**To:** Backend Developer
**Project:** QuickPoll — AmaliTech Phase 1 Capstone

---

## Overview

The analytics pipeline consumes events from **two Kafka topics** to compute real-time KPIs
(poll summaries, option breakdowns, vote time-series, user participation). The backend must
publish events to Kafka **after** successful database writes — a dual-write pattern.

This guide provides everything needed to integrate Kafka publishing into the Spring Boot
backend.

---

## 1. Infrastructure (EC2 Deployment)

On the shared EC2 instance, DevOps will run:

| Service | How | Access |
|---------|-----|--------|
| **PostgreSQL** | AWS RDS (managed) | Shared by backend + data-eng via `SPRING_DATASOURCE_URL` / `DATABASE_URL` |
| **Kafka + Zookeeper** | Docker Compose on EC2 | `localhost:9092` from both services on the same host |

The Kafka docker-compose is at `data-engineering/docker-compose.kafka-dev.yml`. For production
on EC2, the relevant config is:

```yaml
# Kafka broker
image: confluentinc/cp-kafka:7.5.0
ports:
  - "9092:9092"  # local access (backend + data-eng on same EC2)
environment:
  KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"  # topics auto-create on first publish
  KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
```

**Key detail:** `KAFKA_AUTO_CREATE_TOPICS_ENABLE=true` means you don't need to manually create
topics — they're created on first publish. But if you prefer explicit creation:

```bash
docker exec qp-kafka kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic vote_events --partitions 1 --replication-factor 1

docker exec qp-kafka kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic poll_events --partitions 1 --replication-factor 1
```

---

## 2. Kafka Topics & Event Schemas

### Topic: `vote_events`

Publish a **VOTE_CAST** event every time a user casts a vote (after the `votes` row is
committed to PostgreSQL).

```json
{
  "event_type": "VOTE_CAST",
  "vote_id": 42,
  "poll_id": 7,
  "option_id": 15,
  "user_id": 3,
  "voted_at": "2026-03-06T14:30:00Z"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `event_type` | String | ✅ | Must be exactly `"VOTE_CAST"` |
| `vote_id` | Integer | ✅ | The `votes.id` from the DB |
| `poll_id` | Integer | ✅ | The poll being voted on |
| `option_id` | Integer | ✅ | The selected poll option |
| `user_id` | Integer | ✅ | The voter's user ID |
| `voted_at` | String (ISO 8601) | ✅ | Timestamp of the vote |

### Topic: `poll_events`

Publish a **POLL_CREATED** event when a new poll is created, and a **POLL_CLOSED** event when
a poll is deactivated/expired.

```json
{
  "event_type": "POLL_CREATED",
  "poll_id": 7,
  "creator_id": 3,
  "title": "Best programming language?",
  "poll_type": "SINGLE",
  "status": "ACTIVE",
  "expires_at": "2026-04-05T23:59:59Z",
  "created_at": "2026-03-06T14:30:00Z"
}
```

```json
{
  "event_type": "POLL_CLOSED",
  "poll_id": 7,
  "creator_id": 3,
  "title": "Best programming language?",
  "poll_type": "SINGLE",
  "status": "CLOSED",
  "expires_at": "2026-04-05T23:59:59Z",
  "created_at": "2026-03-06T14:30:00Z"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `event_type` | String | ✅ | `"POLL_CREATED"` or `"POLL_CLOSED"` |
| `poll_id` | Integer | ✅ | The `polls.id` from the DB |
| `creator_id` | Integer | ✅ | The user who created the poll |
| `title` | String | ✅ | The poll title/question |
| `poll_type` | String | ✅ | `"SINGLE"` or `"MULTI"` (maps to `multi_select`) |
| `status` | String | ✅ | `"ACTIVE"` or `"CLOSED"` |
| `expires_at` | String (ISO 8601) | ✅ | Poll expiry timestamp |
| `created_at` | String (ISO 8601) | ✅ | Poll creation timestamp |

### ⚠️ Important Rules

1. **All fields are required.** Missing fields will cause the event to be sent to the
   dead-letter queue (DLQ) and not processed.
2. **`event_type` must match exactly** — it's the discriminator. Unrecognized values are
   silently skipped.
3. **Timestamps must be ISO 8601** (e.g., `2026-03-06T14:30:00Z`). Java's
   `Instant.now().toString()` or `OffsetDateTime.now().toString()` works perfectly.
4. **Publish AFTER the database commit succeeds** — not before. The analytics pipeline reads
   back from the OLTP tables using the IDs in the event, so the row must exist.

---

## 3. Spring Boot Integration

### 3a. Add Kafka Dependency

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

### 3b. Application Properties

```properties
# application.properties (or application.yml equivalent)

# On EC2 (same host as Kafka):
spring.kafka.bootstrap-servers=localhost:9092

# JSON serialization
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer

# Reliability settings
spring.kafka.producer.acks=all
spring.kafka.producer.retries=3
spring.kafka.producer.properties.enable.idempotence=true
```

For environment-based override (recommended for deployment):

```properties
spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
```

### 3c. Event DTOs

```java
// src/main/java/com/quickpoll/events/VoteCastEvent.java
public record VoteCastEvent(
    String event_type,  // "VOTE_CAST"
    Long vote_id,
    Long poll_id,
    Long option_id,
    Long user_id,
    String voted_at     // ISO 8601
) {
    public static VoteCastEvent from(Vote vote) {
        return new VoteCastEvent(
            "VOTE_CAST",
            vote.getId(),
            vote.getPoll().getId(),
            vote.getOption().getId(),
            vote.getUser().getId(),
            vote.getCreatedAt().toInstant().toString()
        );
    }
}
```

```java
// src/main/java/com/quickpoll/events/PollEvent.java
public record PollEvent(
    String event_type,  // "POLL_CREATED" or "POLL_CLOSED"
    Long poll_id,
    Long creator_id,
    String title,
    String poll_type,   // "SINGLE" or "MULTI"
    String status,      // "ACTIVE" or "CLOSED"
    String expires_at,  // ISO 8601
    String created_at   // ISO 8601
) {
    public static PollEvent created(Poll poll) {
        return new PollEvent(
            "POLL_CREATED",
            poll.getId(),
            poll.getCreator().getId(),
            poll.getTitle(),
            poll.isMultiSelect() ? "MULTI" : "SINGLE",
            "ACTIVE",
            poll.getExpiresAt().toInstant().toString(),
            poll.getCreatedAt().toInstant().toString()
        );
    }

    public static PollEvent closed(Poll poll) {
        return new PollEvent(
            "POLL_CLOSED",
            poll.getId(),
            poll.getCreator().getId(),
            poll.getTitle(),
            poll.isMultiSelect() ? "MULTI" : "SINGLE",
            "CLOSED",
            poll.getExpiresAt().toInstant().toString(),
            poll.getCreatedAt().toInstant().toString()
        );
    }
}
```

> **Note on field naming:** Use `snake_case` (`event_type`, `poll_id`, etc.) — NOT camelCase.
> The analytics consumer expects snake_case JSON keys. Configure Jackson:
>
> ```java
> // In application.properties:
> spring.jackson.property-naming-strategy=SNAKE_CASE
>
> // OR annotate records with:
> @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
> ```

### 3d. Kafka Publisher Service

```java
// src/main/java/com/quickpoll/events/EventPublisher.java
@Service
@Slf4j
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String VOTE_TOPIC = "vote_events";
    private static final String POLL_TOPIC = "poll_events";

    public EventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishVoteCast(Vote vote) {
        VoteCastEvent event = VoteCastEvent.from(vote);
        kafkaTemplate.send(VOTE_TOPIC, String.valueOf(vote.getPoll().getId()), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish VOTE_CAST for vote_id={}: {}",
                              vote.getId(), ex.getMessage());
                } else {
                    log.info("Published VOTE_CAST: vote_id={}, poll_id={}",
                             vote.getId(), vote.getPoll().getId());
                }
            });
    }

    public void publishPollCreated(Poll poll) {
        PollEvent event = PollEvent.created(poll);
        kafkaTemplate.send(POLL_TOPIC, String.valueOf(poll.getId()), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish POLL_CREATED for poll_id={}: {}",
                              poll.getId(), ex.getMessage());
                } else {
                    log.info("Published POLL_CREATED: poll_id={}", poll.getId());
                }
            });
    }

    public void publishPollClosed(Poll poll) {
        PollEvent event = PollEvent.closed(poll);
        kafkaTemplate.send(POLL_TOPIC, String.valueOf(poll.getId()), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish POLL_CLOSED for poll_id={}: {}",
                              poll.getId(), ex.getMessage());
                } else {
                    log.info("Published POLL_CLOSED: poll_id={}", poll.getId());
                }
            });
    }
}
```

### 3e. Where to Call the Publisher

Inject `EventPublisher` into your service layer and publish **after** the DB transaction
commits:

```java
// In VoteService.java (or equivalent)
@Transactional
public Vote castVote(Long pollId, Long optionId, Long userId) {
    // ... existing logic: validate, create Vote entity, save to DB ...
    Vote saved = voteRepository.save(vote);

    // Increment option vote_count
    optionRepository.incrementVoteCount(optionId);

    // Publish to Kafka AFTER successful save
    eventPublisher.publishVoteCast(saved);

    return saved;
}

// In PollService.java (or equivalent)
@Transactional
public Poll createPoll(CreatePollRequest request, Long creatorId) {
    // ... existing logic: create Poll entity, save to DB ...
    Poll saved = pollRepository.save(poll);

    // Publish to Kafka AFTER successful save
    eventPublisher.publishPollCreated(saved);

    return saved;
}

public Poll closePoll(Long pollId) {
    Poll poll = pollRepository.findById(pollId).orElseThrow();
    poll.setActive(false);
    Poll saved = pollRepository.save(poll);

    eventPublisher.publishPollClosed(saved);

    return saved;
}
```

> **⚠️ Important:** The `@Transactional` + Kafka publish pattern means if Kafka is
> temporarily down, the DB write succeeds but the event is lost. This is acceptable for our
> use case — the analytics pipeline runs periodic backfills every 30 minutes that catch any
> missed events by querying the OLTP tables directly.

---

## 4. Message Key Strategy

Use **`poll_id`** as the Kafka message key (as shown in the publisher above). This ensures:

- All events for the same poll land on the same partition
- Ordering is preserved per-poll (vote A before vote B)
- The analytics consumer can process events for the same poll sequentially

```java
// Key = poll_id as string
kafkaTemplate.send("vote_events", String.valueOf(vote.getPoll().getId()), event);
kafkaTemplate.send("poll_events", String.valueOf(poll.getId()), event);
```

---

## 5. Environment Variables Summary

| Variable | Default | Description |
|----------|---------|-------------|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `SPRING_DATASOURCE_URL` | — | PostgreSQL JDBC URL (shared with data-eng) |
| `SPRING_DATASOURCE_USERNAME` | `quickpoll` | DB user |
| `SPRING_DATASOURCE_PASSWORD` | `quickpoll123` | DB password |

On EC2, both services connect to Kafka at `localhost:9092` since they're on the same host.

---

## 6. Testing the Integration

### Verify Kafka is running

```bash
docker exec qp-kafka kafka-topics --bootstrap-server localhost:9092 --list
```

Expected output should include `vote_events` and `poll_events` (created on first publish).

### Verify events are being published

```bash
# Watch vote events in real-time
docker exec qp-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic vote_events \
  --from-beginning

# Watch poll events
docker exec qp-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic poll_events \
  --from-beginning
```

### Verify the analytics pipeline picks them up

When the data-engineering pipeline is running (`uv run python main.py`), you'll see log lines
like:

```
INFO  | data_engineering.pipeline.streaming | Processed VOTE_CAST: poll_id=7, user_id=3
INFO  | data_engineering.pipeline.streaming | Processed POLL_CREATED: poll_id=99
```

---

## 7. What Happens on the Analytics Side

When you publish an event, here's what the pipeline does:

### VOTE_CAST
1. Reads the vote's `poll_id` and `user_id` from the event
2. Queries the OLTP tables for full poll data, votes, and options
3. Recomputes: poll summary, option breakdown, vote time-series, user participation
4. Upserts results into 4 analytics tables

### POLL_CREATED / POLL_CLOSED
1. Reads the `poll_id` from the event
2. Queries the OLTP tables for the poll + its votes
3. Recomputes the poll summary
4. Upserts into `analytics_poll_summary`

### Failed events
- Go to a dead-letter queue (DLQ) on Cloudflare R2
- Logged as warnings — don't crash the consumer
- The periodic backfill (every 30 min) catches missed data anyway

---

## 8. Quick Checklist

- [ ] Add `spring-kafka` dependency to `pom.xml`
- [ ] Add Kafka properties to `application.properties`
- [ ] Configure Jackson for `snake_case` JSON keys
- [ ] Create `VoteCastEvent` and `PollEvent` DTOs
- [ ] Create `EventPublisher` service
- [ ] Call `publishVoteCast()` in vote creation flow
- [ ] Call `publishPollCreated()` in poll creation flow
- [ ] Call `publishPollClosed()` in poll close/expire flow
- [ ] Test with `kafka-console-consumer` to verify events appear
- [ ] Coordinate with DevOps for `KAFKA_BOOTSTRAP_SERVERS` env var on EC2
