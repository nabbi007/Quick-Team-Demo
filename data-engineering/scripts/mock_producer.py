"""Publish fake Kafka events to test the consumer without the backend running.

Publishes a configurable number of VOTE_CAST events and one POLL_CREATED event
to the topics the pipeline listens on.

Usage (from data-engineering/ with .env or env vars set):
    uv run python scripts/mock_producer.py            # 5 vote events (default)
    uv run python scripts/mock_producer.py --votes 20
"""

from __future__ import annotations

import argparse
import json
import logging
import random
import sys
from datetime import UTC, datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from kafka import KafkaProducer  # noqa: E402

from data_engineering.config import (  # noqa: E402
    KAFKA_BOOTSTRAP_SERVERS,
    KAFKA_TOPIC_POLL_EVENTS,
    KAFKA_TOPIC_VOTE_EVENTS,
)
from data_engineering.utils.logging import configure_logging  # noqa: E402

logger = logging.getLogger(__name__)

_POLL_IDS = [1, 2, 3, 4]
_OPTION_MAP = {1: [1, 2, 3, 4], 2: [5, 6, 7], 3: [8, 9, 10, 11], 4: [12, 13, 14, 15]}
_USER_IDS = [1, 2, 3, 4, 5, 6, 7]


def _make_producer() -> KafkaProducer:
    return KafkaProducer(
        bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
        value_serializer=lambda v: json.dumps(v).encode("utf-8"),
    )


def publish_vote_events(producer: KafkaProducer, count: int) -> None:
    for i in range(1, count + 1):
        poll_id = random.choice(_POLL_IDS)
        event = {
            "event_type": "VOTE_CAST",
            "vote_id": 1000 + i,
            "poll_id": poll_id,
            "option_id": random.choice(_OPTION_MAP[poll_id]),
            "user_id": random.choice(_USER_IDS),
            "voted_at": datetime.now(UTC).isoformat().replace("+00:00", "Z"),
        }
        producer.send(KAFKA_TOPIC_VOTE_EVENTS, value=event)
        logger.info("Published VOTE_CAST #%d: poll_id=%d", i, poll_id)
    producer.flush()


def publish_poll_event(producer: KafkaProducer) -> None:
    event = {
        "event_type": "POLL_CREATED",
        "poll_id": 99,
        "title": "Mock Poll Title",
        "creator_id": 1,
        "occurred_at": datetime.now(UTC).isoformat().replace("+00:00", "Z"),
        "question": "Mock poll from producer script",
        "multi_select": False,
        "expires_at": "2026-12-31T23:59:59Z",
        "active": True,
        "created_at": datetime.now(UTC).isoformat().replace("+00:00", "Z"),
    }
    producer.send(KAFKA_TOPIC_POLL_EVENTS, value=event)
    producer.flush()
    logger.info("Published POLL_CREATED: poll_id=99")


if __name__ == "__main__":
    configure_logging()
    parser = argparse.ArgumentParser(description="Publish mock Kafka events")
    parser.add_argument(
        "--votes", type=int, default=5, help="Number of VOTE_CAST events"
    )
    args = parser.parse_args()

    logger.info("Connecting to Kafka at %s...", KAFKA_BOOTSTRAP_SERVERS)
    prod = _make_producer()
    publish_poll_event(prod)
    publish_vote_events(prod, args.votes)
    prod.close()
    logger.info("Done. Published 1 POLL_CREATED + %d VOTE_CAST events.", args.votes)
