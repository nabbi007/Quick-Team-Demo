"""PostgreSQL upsert writers for all four analytics tables."""

from __future__ import annotations

import logging
from datetime import datetime

import pandas as pd
from sqlalchemy.dialects.postgresql import insert

from data_engineering.config import get_engine
from data_engineering.loading.models import (
    analytics_option_breakdown,
    analytics_poll_summary,
    analytics_user_participation,
    analytics_votes_timeseries,
    pipeline_watermarks,
)

logger = logging.getLogger(__name__)


def _now() -> datetime:
    return datetime.utcnow()


def upsert_poll_summary(df: pd.DataFrame) -> None:
    """Upsert rows into analytics_poll_summary (keyed on poll_id)."""
    if df.empty:
        return
    records = df.to_dict("records")
    for r in records:
        r["last_updated"] = _now()
    stmt = insert(analytics_poll_summary).values(records)
    stmt = stmt.on_conflict_do_update(
        index_elements=["poll_id"],
        set_={
            "title": stmt.excluded.title,
            "creator_name": stmt.excluded.creator_name,
            "status": stmt.excluded.status,
            "total_votes": stmt.excluded.total_votes,
            "unique_voters": stmt.excluded.unique_voters,
            "participation_rate": stmt.excluded.participation_rate,
            "last_updated": stmt.excluded.last_updated,
        },
    )
    with get_engine().begin() as conn:
        conn.execute(stmt)
    logger.debug("Upserted %d rows into analytics_poll_summary", len(records))


def upsert_option_breakdown(df: pd.DataFrame) -> None:
    """Upsert rows into analytics_option_breakdown (keyed on option_id)."""
    if df.empty:
        return
    records = df.to_dict("records")
    for r in records:
        r["last_updated"] = _now()
    stmt = insert(analytics_option_breakdown).values(records)
    stmt = stmt.on_conflict_do_update(
        index_elements=["option_id"],
        set_={
            "poll_id": stmt.excluded.poll_id,
            "option_text": stmt.excluded.option_text,
            "vote_count": stmt.excluded.vote_count,
            "vote_percentage": stmt.excluded.vote_percentage,
            "last_updated": stmt.excluded.last_updated,
        },
    )
    with get_engine().begin() as conn:
        conn.execute(stmt)
    logger.debug("Upserted %d rows into analytics_option_breakdown", len(records))


def upsert_votes_timeseries(df: pd.DataFrame) -> None:
    """Upsert rows into analytics_votes_timeseries (keyed on poll_id + bucket_time)."""
    if df.empty:
        return
    records = df.to_dict("records")
    for r in records:
        r["recorded_at"] = _now()
    stmt = insert(analytics_votes_timeseries).values(records)
    stmt = stmt.on_conflict_do_update(
        constraint="uq_timeseries_poll_bucket",
        set_={
            "votes_in_bucket": stmt.excluded.votes_in_bucket,
            "recorded_at": stmt.excluded.recorded_at,
        },
    )
    with get_engine().begin() as conn:
        conn.execute(stmt)
    logger.debug("Upserted %d rows into analytics_votes_timeseries", len(records))


def upsert_user_participation(df: pd.DataFrame) -> None:
    """Upsert rows into analytics_user_participation (keyed on user_id)."""
    if df.empty:
        return
    records = df.to_dict("records")
    for r in records:
        r["last_updated"] = _now()
        # NaT survives .to_dict() — replace with None for PostgreSQL
        val = r.get("last_active")
        if val is pd.NaT or (val is not None and pd.isna(val)):
            r["last_active"] = None
    stmt = insert(analytics_user_participation).values(records)
    stmt = stmt.on_conflict_do_update(
        index_elements=["user_id"],
        set_={
            "user_name": stmt.excluded.user_name,
            "total_votes_cast": stmt.excluded.total_votes_cast,
            "polls_participated": stmt.excluded.polls_participated,
            "polls_created": stmt.excluded.polls_created,
            "last_active": stmt.excluded.last_active,
            "last_updated": stmt.excluded.last_updated,
        },
    )
    with get_engine().begin() as conn:
        conn.execute(stmt)
    logger.debug("Upserted %d rows into analytics_user_participation", len(records))


def get_watermark(entity_name: str) -> datetime | None:
    """Return the high-watermark timestamp for the given entity, or None."""
    with get_engine().connect() as conn:
        row = conn.execute(
            pipeline_watermarks.select().where(
                pipeline_watermarks.c.entity_name == entity_name
            )
        ).fetchone()
    return row.high_watermark if row else None


def set_watermark(entity_name: str, value: datetime) -> None:
    """Upsert the high-watermark for the given entity."""
    stmt = insert(pipeline_watermarks).values(
        entity_name=entity_name,
        high_watermark=value,
        updated_at=_now(),
    )
    stmt = stmt.on_conflict_do_update(
        index_elements=["entity_name"],
        set_={
            "high_watermark": stmt.excluded.high_watermark,
            "updated_at": stmt.excluded.updated_at,
        },
    )
    with get_engine().begin() as conn:
        conn.execute(stmt)
    logger.debug("Watermark for '%s' set to %s", entity_name, value)
