"""Structured logging configuration."""

from __future__ import annotations

import logging

from data_engineering.config import LOG_LEVEL

LOG_FORMAT = "%(asctime)s | %(levelname)-8s | %(name)s | %(message)s"


def configure_logging() -> None:
    """Configure root logger using LOG_LEVEL from environment."""
    level = logging.getLevelName(LOG_LEVEL.upper())
    logging.basicConfig(level=level, format=LOG_FORMAT)
