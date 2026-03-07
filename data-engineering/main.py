"""Entry point for the QuickPoll data engineering pipeline."""

from __future__ import annotations

import logging

from data_engineering.config import get_engine
from data_engineering.ingestion.consumers import check_dlq_bucket
from data_engineering.loading.models import create_analytics_tables
from data_engineering.pipeline.backfill import run_backfill
from data_engineering.pipeline.streaming import run_streaming
from data_engineering.utils.logging import configure_logging

logger = logging.getLogger(__name__)


def main() -> None:
    """Bootstrap and run the pipeline: create tables → backfill → stream."""
    configure_logging()

    engine = get_engine()
    create_analytics_tables(engine)
    logger.info("Analytics tables ensured.")

    check_dlq_bucket()

    run_backfill()
    run_streaming()


if __name__ == "__main__":
    main()
