"""Pipeline configuration — reads from .env or environment variables."""

from __future__ import annotations

from typing import Any

import boto3
from decouple import config
from sqlalchemy import create_engine as _sa_create_engine
from sqlalchemy.engine import Engine

# ── Database ──────────────────────────────────────────────────────────────────
DB_HOST: str = config("DB_HOST", default="localhost")
DB_PORT: int = config("DB_PORT", default=5432, cast=int)
DB_NAME: str = config("DB_NAME", default="quickpoll")
DB_USER: str = config("DB_USER", default="quickpoll")
DB_PASSWORD: str = config("DB_PASSWORD", default="quickpoll123")

DATABASE_URL: str = (
    f"postgresql://{DB_USER}:{DB_PASSWORD}@{DB_HOST}:{DB_PORT}/{DB_NAME}"
)

# ── Kafka ─────────────────────────────────────────────────────────────────────
KAFKA_BOOTSTRAP_SERVERS: str = config(
    "KAFKA_BOOTSTRAP_SERVERS", default="localhost:9092"
)
KAFKA_TOPIC_VOTE_EVENTS: str = config("KAFKA_TOPIC_VOTE_EVENTS", default="vote_events")
KAFKA_TOPIC_POLL_EVENTS: str = config("KAFKA_TOPIC_POLL_EVENTS", default="poll_events")
KAFKA_GROUP_ID: str = config("KAFKA_GROUP_ID", default="quickpoll-analytics")

# ── Pipeline ──────────────────────────────────────────────────────────────────
LOG_LEVEL: str = config("LOG_LEVEL", default="INFO")
BACKFILL_INTERVAL_MINUTES: int = config(
    "BACKFILL_INTERVAL_MINUTES", default=30, cast=int
)
WATERMARK_OVERLAP_MINUTES: int = config(
    "WATERMARK_OVERLAP_MINUTES", default=5, cast=int
)
FORCE_FULL_BACKFILL: bool = (
    config("FORCE_FULL_BACKFILL", default="false", cast=str).lower() == "true"
)

# ── Cloudflare R2(Dead-Letter Queue) ─────────────────────────────────────────
R2_ENDPOINT_URL: str = config("R2_ENDPOINT_URL", default="")
R2_ACCESS_KEY_ID: str = config("R2_ACCESS_KEY_ID", default="")
R2_SECRET_ACCESS_KEY: str = config("R2_SECRET_ACCESS_KEY", default="")
R2_DLQ_BUCKET: str = config("R2_DLQ_BUCKET", default="quickpoll-dlq")

# ── Engine singleton ──────────────────────────────────────────────────────────
_engine: Engine | None = None


def get_engine() -> Engine:
    """Return a shared SQLAlchemy engine (created once per process)."""
    global _engine
    if _engine is None:
        _engine = _sa_create_engine(DATABASE_URL, pool_pre_ping=True)
    return _engine


# ── S3 client singleton ───────────────────────────────────────────────────────
_s3_client: Any = None


def get_s3_client() -> Any:
    """Return a shared boto3 S3 client configured for Cloudflare R2."""
    global _s3_client
    if _s3_client is None:
        _s3_client = boto3.client(
            "s3",
            endpoint_url=R2_ENDPOINT_URL,
            aws_access_key_id=R2_ACCESS_KEY_ID,
            aws_secret_access_key=R2_SECRET_ACCESS_KEY,
        )
    return _s3_client
