"""SQLAlchemy table definitions for all four analytics tables."""

from __future__ import annotations

from sqlalchemy import (
    BigInteger,
    Column,
    DateTime,
    Float,
    Integer,
    MetaData,
    String,
    Table,
    UniqueConstraint,
    text,
)
from sqlalchemy.engine import Engine

metadata = MetaData()

analytics_poll_summary = Table(
    "analytics_poll_summary",
    metadata,
    Column("poll_id", BigInteger, primary_key=True),
    Column("title", String(255), nullable=False),
    Column("creator_name", String(255)),
    Column("status", String(50)),
    Column("total_votes", Integer, server_default="0"),
    Column("unique_voters", Integer, server_default="0"),
    Column("participation_rate", Float, server_default="0"),
    Column("created_at", DateTime),
    Column("last_updated", DateTime, server_default=text("NOW()")),
)

analytics_option_breakdown = Table(
    "analytics_option_breakdown",
    metadata,
    Column("option_id", BigInteger, primary_key=True),
    Column("poll_id", BigInteger, nullable=False),
    Column("option_text", String(500)),
    Column("vote_count", Integer, server_default="0"),
    Column("vote_percentage", Float, server_default="0"),
    Column("last_updated", DateTime, server_default=text("NOW()")),
)

analytics_votes_timeseries = Table(
    "analytics_votes_timeseries",
    metadata,
    Column("id", Integer, primary_key=True, autoincrement=True),
    Column("poll_id", BigInteger, nullable=False),
    Column("bucket_time", DateTime, nullable=False),
    Column("votes_in_bucket", Integer, server_default="0"),
    Column("recorded_at", DateTime, server_default=text("NOW()")),
    UniqueConstraint("poll_id", "bucket_time", name="uq_timeseries_poll_bucket"),
)

analytics_user_participation = Table(
    "analytics_user_participation",
    metadata,
    Column("user_id", BigInteger, primary_key=True),
    Column("user_name", String(255)),
    Column("total_votes_cast", Integer, server_default="0"),
    Column("polls_participated", Integer, server_default="0"),
    Column("polls_created", Integer, server_default="0"),
    Column("last_active", DateTime),
    Column("last_updated", DateTime, server_default=text("NOW()")),
)

pipeline_watermarks = Table(
    "pipeline_watermarks",
    metadata,
    Column("entity_name", String(50), primary_key=True),
    Column("high_watermark", DateTime, nullable=False),
    Column("updated_at", DateTime, server_default=text("NOW()")),
)


def create_analytics_tables(engine: Engine) -> None:
    """Create all analytics tables in PostgreSQL if they don't exist yet."""
    metadata.create_all(engine, checkfirst=True)
