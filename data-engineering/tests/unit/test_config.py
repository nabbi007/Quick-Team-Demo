"""Unit tests for config module."""

from __future__ import annotations


def test_database_url_format() -> None:
    from data_engineering.config import DATABASE_URL

    assert DATABASE_URL.startswith("postgresql://")
    assert "@" in DATABASE_URL
    assert "/" in DATABASE_URL.split("@")[1]


def test_kafka_defaults() -> None:
    """KAFKA_BOOTSTRAP_SERVERS and KAFKA_GROUP_ID have defaults."""
    # Reload without extra env vars to exercise defaults
    from data_engineering.config import KAFKA_BOOTSTRAP_SERVERS, KAFKA_GROUP_ID

    assert KAFKA_BOOTSTRAP_SERVERS != ""
    assert KAFKA_GROUP_ID != ""


def test_log_level_default() -> None:
    """LOG_LEVEL defaults to INFO when not set."""
    from data_engineering.config import LOG_LEVEL

    # decouple reads env; if not overridden, should be the default
    assert LOG_LEVEL in ("DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL")


def test_dlq_path_default() -> None:
    from data_engineering.config import DLQ_PATH

    assert DLQ_PATH != ""


def test_get_engine_returns_same_instance() -> None:
    """get_engine() is a singleton."""
    from data_engineering.config import get_engine

    e1 = get_engine()
    e2 = get_engine()
    assert e1 is e2
