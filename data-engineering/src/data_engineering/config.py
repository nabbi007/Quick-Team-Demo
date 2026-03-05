"""Pipeline configuration — reads from .env or environment variables."""

from __future__ import annotations

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
DLQ_PATH: str = config("DLQ_PATH", default="data/dlq")

# ── Engine singleton ──────────────────────────────────────────────────────────
_engine: Engine | None = None


def get_engine() -> Engine:
    """Return a shared SQLAlchemy engine (created once per process)."""
    global _engine
    if _engine is None:
        _engine = _sa_create_engine(DATABASE_URL, pool_pre_ping=True)
    return _engine
