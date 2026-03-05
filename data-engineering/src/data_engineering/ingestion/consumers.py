"""Kafka consumer factory and dead-letter queue writer."""

from __future__ import annotations

import json
import logging
from datetime import datetime
from pathlib import Path
from typing import Any

from kafka import KafkaConsumer

from data_engineering.config import (
    DLQ_PATH,
    KAFKA_BOOTSTRAP_SERVERS,
    KAFKA_GROUP_ID,
    KAFKA_TOPIC_POLL_EVENTS,
    KAFKA_TOPIC_VOTE_EVENTS,
)

logger = logging.getLogger(__name__)


def create_consumer() -> KafkaConsumer:
    """
    Create and return a Kafka consumer subscribed to vote and poll event topics.

    auto_offset_reset="earliest" ensures no events are missed on restart.
    enable_auto_commit=False lets the pipeline commit only after a successful
    DB write, providing at-least-once delivery guarantees.
    """
    return KafkaConsumer(
        KAFKA_TOPIC_VOTE_EVENTS,
        KAFKA_TOPIC_POLL_EVENTS,
        bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
        group_id=KAFKA_GROUP_ID,
        auto_offset_reset="earliest",
        enable_auto_commit=False,
        value_deserializer=lambda m: json.loads(m.decode("utf-8")),
    )


def write_to_dlq(event: Any) -> None:
    """
    Persist a failed event to the dead-letter queue directory as a JSON file.
    The filename encodes the event_type and timestamp for easy inspection.
    """
    dlq_dir = Path(DLQ_PATH)
    dlq_dir.mkdir(parents=True, exist_ok=True)

    event_type = "unknown"
    if isinstance(event, dict):
        event_type = event.get("event_type", "unknown")

    timestamp = datetime.utcnow().strftime("%Y%m%dT%H%M%S%f")
    filepath = dlq_dir / f"{event_type}_{timestamp}.json"

    with filepath.open("w") as f:
        json.dump(event, f, indent=2, default=str)

    logger.warning("Event written to DLQ: %s", filepath)
