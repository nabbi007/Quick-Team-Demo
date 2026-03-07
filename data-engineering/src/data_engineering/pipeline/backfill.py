"""Startup backfill: reads all OLTP data and populates the four analytics tables."""

from __future__ import annotations

import logging

from data_engineering.ingestion.extractors import (
    extract_options,
    extract_polls,
    extract_users,
    extract_votes,
    get_total_users,
)
from data_engineering.loading.writers import (
    upsert_option_breakdown,
    upsert_poll_summary,
    upsert_user_participation,
    upsert_votes_timeseries,
)
from data_engineering.transformation.transformers import (
    compute_option_breakdown,
    compute_poll_summary,
    compute_user_participation,
    compute_votes_timeseries,
)

logger = logging.getLogger(__name__)


def run_backfill() -> None:
    """
    Full backfill from OLTP tables into all four analytics tables.
    Called once on container startup before the Kafka consumer loop begins.
    Idempotent — safe to re-run; uses upsert (ON CONFLICT DO UPDATE).
    """
    logger.info("Starting startup backfill from OLTP tables...")

    polls_df = extract_polls()
    votes_df = extract_votes()
    options_df = extract_options()
    users_df = extract_users()
    total_users = get_total_users()

    logger.info(
        "Extracted: %d polls, %d votes, %d options, %d users",
        len(polls_df),
        len(votes_df),
        len(options_df),
        len(users_df),
    )

    summary_df = compute_poll_summary(polls_df, votes_df, total_users)
    option_df = compute_option_breakdown(options_df, votes_df)
    timeseries_df = compute_votes_timeseries(votes_df)
    participation_df = compute_user_participation(users_df, votes_df, polls_df)

    upsert_poll_summary(summary_df)
    upsert_option_breakdown(option_df)
    upsert_votes_timeseries(timeseries_df)
    upsert_user_participation(participation_df)

    logger.info("Backfill complete.")
