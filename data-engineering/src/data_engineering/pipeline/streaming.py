"""Kafka consumer loop: handles vote and poll events with scoped analytics recompute."""

from __future__ import annotations

import logging
import time

import pandas as pd
from kafka.errors import CommitFailedError, NoBrokersAvailable

from data_engineering.config import BACKFILL_INTERVAL_MINUTES
from data_engineering.ingestion.consumers import create_consumer, write_to_dlq
from data_engineering.ingestion.extractors import (
    extract_options_by_poll,
    extract_poll_by_id,
    extract_polls_by_creator,
    extract_user_by_id,
    extract_votes_by_poll,
    extract_votes_by_user,
    get_total_users,
)
from data_engineering.loading.writers import (
    upsert_option_breakdown,
    upsert_poll_summary,
    upsert_user_participation,
    upsert_votes_timeseries,
)
from data_engineering.pipeline.backfill import run_backfill
from data_engineering.transformation.transformers import (
    compute_option_breakdown,
    compute_poll_summary,
    compute_user_participation,
)

logger = logging.getLogger(__name__)


def run_streaming() -> None:
    """
    Long-running Kafka consumer loop.
    On each event: recomputes analytics scoped to the affected poll/user,
    writes to DB, then commits the Kafka offset.
    On failure: writes the raw event to the DLQ and commits to avoid a stuck consumer.
    Retries with exponential backoff when Kafka is unavailable.
    """
    max_delay = 60
    delay = 5

    while True:
        try:
            logger.info("Starting Kafka consumer loop...")
            consumer = create_consumer()
            delay = 5  # reset on successful connection
        except NoBrokersAvailable:
            logger.warning("Kafka is not available. Retrying in %ds...", delay)
            time.sleep(delay)
            delay = min(delay * 2, max_delay)
            continue

        try:
            _consume_loop(consumer)
        except NoBrokersAvailable:
            logger.warning("Lost Kafka connection. Reconnecting in %ds...", delay)
            time.sleep(delay)
            delay = min(delay * 2, max_delay)
        finally:
            try:
                consumer.close()
            except Exception:
                logger.debug("Error closing Kafka consumer", exc_info=True)


def _consume_loop(consumer) -> None:
    """Process messages with periodic backfill check."""
    backfill_interval_sec = BACKFILL_INTERVAL_MINUTES * 60
    last_backfill = time.monotonic()

    while True:
        # Poll for messages (returns dict of TopicPartition → list[ConsumerRecord])
        records = consumer.poll(timeout_ms=5000)

        for _tp, messages in records.items():
            for message in messages:
                event = message.value
                event_type = (
                    event.get("event_type") if isinstance(event, dict) else None
                )
                try:
                    if event_type == "VOTE_CAST":
                        _handle_vote_event(event)
                    elif event_type in ("POLL_CREATED", "POLL_CLOSED"):
                        _handle_poll_event(event)
                    else:
                        logger.warning(
                            "Unrecognised event_type=%s — skipping",
                            event_type,
                        )
                except Exception:
                    logger.exception(
                        "Failed to process event_type=%s — writing to DLQ", event_type
                    )
                    write_to_dlq(event)

        if records:
            try:
                consumer.commit()
            except CommitFailedError:
                logger.warning(
                    "Offset commit failed (group rebalanced). "
                    "Events will be re-delivered on next poll."
                )

        # Periodic backfill check
        elapsed = time.monotonic() - last_backfill
        if elapsed >= backfill_interval_sec:
            logger.info("Periodic backfill triggered (%.0fs elapsed).", elapsed)
            try:
                run_backfill()
            except Exception:
                logger.exception("Periodic backfill failed — will retry next interval.")
            last_backfill = time.monotonic()


def _handle_vote_event(event: dict) -> None:
    """Recompute analytics for the affected poll and voter on a VOTE_CAST event."""
    poll_id: int = event["poll_id"]
    user_id: int = event["user_id"]

    # Scoped poll recompute
    poll_df = extract_poll_by_id(poll_id)
    votes_df = extract_votes_by_poll(poll_id)
    options_df = extract_options_by_poll(poll_id)
    total_users = get_total_users()

    summary_df = compute_poll_summary(poll_df, votes_df, total_users)
    option_df = compute_option_breakdown(options_df, votes_df)
    upsert_poll_summary(summary_df)
    upsert_option_breakdown(option_df)

    # Upsert the current hour bucket for this poll
    current_bucket = pd.Timestamp.now().floor("h")
    bucket_votes = votes_df.copy()
    bucket_votes["bucket"] = pd.to_datetime(bucket_votes["created_at"]).dt.floor("h")
    votes_this_hour = int(
        bucket_votes[bucket_votes["bucket"] == current_bucket].shape[0]
    )
    upsert_votes_timeseries(
        pd.DataFrame(
            [
                {
                    "poll_id": poll_id,
                    "bucket_time": current_bucket,
                    "votes_in_bucket": votes_this_hour,
                }
            ]
        )
    )

    # Scoped user recompute
    user_df = extract_user_by_id(user_id)
    user_votes_df = extract_votes_by_user(user_id)
    user_polls_df = extract_polls_by_creator(user_id)
    participation_df = compute_user_participation(user_df, user_votes_df, user_polls_df)
    upsert_user_participation(participation_df)

    logger.info("Processed VOTE_CAST: poll_id=%d, user_id=%d", poll_id, user_id)


def _handle_poll_event(event: dict) -> None:
    """Update analytics_poll_summary when a poll is created or closed."""
    poll_id: int = event["poll_id"]
    event_type: str = event["event_type"]

    poll_df = extract_poll_by_id(poll_id)
    votes_df = extract_votes_by_poll(poll_id)
    total_users = get_total_users()

    summary_df = compute_poll_summary(poll_df, votes_df, total_users)
    upsert_poll_summary(summary_df)

    logger.info("Processed %s: poll_id=%d", event_type, poll_id)
