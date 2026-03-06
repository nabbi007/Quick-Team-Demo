"""Kafka consumer factory and dead-letter queue writer."""

from __future__ import annotations

import json
import logging
from datetime import UTC, datetime
from typing import Any

import botocore.exceptions
from kafka import KafkaConsumer

from data_engineering.config import (
    KAFKA_BOOTSTRAP_SERVERS,
    KAFKA_GROUP_ID,
    KAFKA_TOPIC_POLL_EVENTS,
    KAFKA_TOPIC_VOTE_EVENTS,
    R2_DLQ_BUCKET,
    get_s3_client,
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
        max_poll_records=50,
        max_poll_interval_ms=600_000,  # 10 min — each event triggers DB I/O
        value_deserializer=lambda m: json.loads(m.decode("utf-8")),
    )


def check_dlq_bucket() -> None:
    """Verify the R2 DLQ bucket is reachable at startup. Non-fatal on failure."""
    try:
        get_s3_client().head_bucket(Bucket=R2_DLQ_BUCKET)
        logger.info("DLQ bucket '%s' is reachable.", R2_DLQ_BUCKET)
    except botocore.exceptions.ClientError as exc:
        logger.warning(
            "DLQ bucket '%s' is not reachable (%s). Failed events will be lost.",
            R2_DLQ_BUCKET,
            exc,
        )


def write_to_dlq(event: Any) -> None:
    """Upload a failed event as a JSON object to the Cloudflare R2 DLQ bucket."""
    event_type = "unknown"
    if isinstance(event, dict):
        event_type = event.get("event_type", "unknown")

    timestamp = datetime.now(UTC).strftime("%Y%m%dT%H%M%S%f")
    key = f"dlq/{event_type}/{timestamp}.json"

    try:
        get_s3_client().put_object(
            Bucket=R2_DLQ_BUCKET,
            Key=key,
            Body=json.dumps(event, default=str).encode(),
            ContentType="application/json",
        )
        logger.warning("Event uploaded to R2 DLQ: s3://%s/%s", R2_DLQ_BUCKET, key)
    except botocore.exceptions.ClientError as exc:
        logger.error("Failed to upload event to R2 DLQ: %s", exc)
