"""Batch extractors that read from the shared OLTP PostgreSQL database."""

from __future__ import annotations

from datetime import datetime
from typing import Any

import pandas as pd
from sqlalchemy import text
from sqlalchemy.engine import Engine

from data_engineering.config import get_engine

# ── Full-table extracts (used by backfill on startup) ─────────────────────────


def _as_py_int(value: Any) -> int:
    """Coerce numpy/pandas numeric scalars into native Python int."""
    return int(value)


def _as_py_int_list(values: list[int]) -> list[int]:
    """Coerce numpy/pandas numeric lists into native Python ints."""
    return [int(v) for v in values]


def extract_polls(engine: Engine | None = None) -> pd.DataFrame:
    """Extract all polls with creator display name."""
    engine = engine or get_engine()
    query = text("""
        SELECT p.id,
               COALESCE(to_jsonb(p)->>'title', to_jsonb(p)->>'question') AS title,
               p.active, p.multi_select, p.expires_at,
               p.created_at, p.creator_id, u.full_name AS creator_name
        FROM polls p
        JOIN users u ON p.creator_id = u.id
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn)


def extract_votes(engine: Engine | None = None) -> pd.DataFrame:
    """Extract all votes."""
    engine = engine or get_engine()
    query = text("""
        SELECT id, poll_id, option_id, user_id, created_at
        FROM votes
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn)


def extract_options(engine: Engine | None = None) -> pd.DataFrame:
    """Extract all poll options."""
    engine = engine or get_engine()
    query = text("""
        SELECT id, poll_id, option_text
        FROM poll_options
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn)


def extract_users(engine: Engine | None = None) -> pd.DataFrame:
    """Extract all users."""
    engine = engine or get_engine()
    query = text("""
        SELECT id, full_name, email, created_at
        FROM users
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn)


def get_total_users(engine: Engine | None = None) -> int:
    """Return the current count of registered users."""
    engine = engine or get_engine()
    with engine.connect() as conn:
        result = conn.execute(text("SELECT COUNT(*) FROM users"))
        return result.scalar() or 0


# ── Scoped extracts (used by streaming for single-poll recompute) ────────────


def extract_poll_by_id(poll_id: int, engine: Engine | None = None) -> pd.DataFrame:
    """Extract a single poll with creator name."""
    engine = engine or get_engine()
    query = text("""
        SELECT p.id,
               COALESCE(to_jsonb(p)->>'title', to_jsonb(p)->>'question') AS title,
               p.active, p.multi_select, p.expires_at,
               p.created_at, p.creator_id, u.full_name AS creator_name
        FROM polls p
        JOIN users u ON p.creator_id = u.id
        WHERE p.id = :poll_id
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"poll_id": _as_py_int(poll_id)})


def extract_votes_by_poll(poll_id: int, engine: Engine | None = None) -> pd.DataFrame:
    """Extract all votes for a specific poll."""
    engine = engine or get_engine()
    query = text("""
        SELECT id, poll_id, option_id, user_id, created_at
        FROM votes
        WHERE poll_id = :poll_id
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"poll_id": _as_py_int(poll_id)})


def extract_options_by_poll(poll_id: int, engine: Engine | None = None) -> pd.DataFrame:
    """Extract all options for a specific poll."""
    engine = engine or get_engine()
    query = text("""
        SELECT id, poll_id, option_text
        FROM poll_options
        WHERE poll_id = :poll_id
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"poll_id": _as_py_int(poll_id)})


def extract_user_by_id(user_id: int, engine: Engine | None = None) -> pd.DataFrame:
    """Extract a single user."""
    engine = engine or get_engine()
    query = text("""
        SELECT id, full_name, email, created_at
        FROM users
        WHERE id = :user_id
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"user_id": _as_py_int(user_id)})


def extract_votes_by_user(user_id: int, engine: Engine | None = None) -> pd.DataFrame:
    """Extract all votes cast by a specific user."""
    engine = engine or get_engine()
    query = text("""
        SELECT id, poll_id, option_id, user_id, created_at
        FROM votes
        WHERE user_id = :user_id
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"user_id": _as_py_int(user_id)})


def extract_polls_by_creator(
    creator_id: int, engine: Engine | None = None
) -> pd.DataFrame:
    """Extract all polls created by a specific user."""
    engine = engine or get_engine()
    query = text("""
        SELECT id,
               COALESCE(
                   to_jsonb(polls)->>'title',
                   to_jsonb(polls)->>'question'
               ) AS title,
               active, created_at, creator_id
        FROM polls
        WHERE creator_id = :creator_id
    """)
    with engine.connect() as conn:
        return pd.read_sql(
            query,
            conn,
            params={"creator_id": _as_py_int(creator_id)},
        )


# ── Incremental extracts (used by backfill for delta detection) ───────────────


def extract_polls_since(since: datetime, engine: Engine | None = None) -> pd.DataFrame:
    """Extract polls created after the given timestamp."""
    engine = engine or get_engine()
    query = text("""
        SELECT p.id,
               COALESCE(to_jsonb(p)->>'title', to_jsonb(p)->>'question') AS title,
               p.active, p.multi_select, p.expires_at,
               p.created_at, p.creator_id, u.full_name AS creator_name
        FROM polls p
        JOIN users u ON p.creator_id = u.id
        WHERE p.created_at > :since
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"since": since})


def extract_votes_since(since: datetime, engine: Engine | None = None) -> pd.DataFrame:
    """Extract votes created after the given timestamp."""
    engine = engine or get_engine()
    query = text("""
        SELECT id, poll_id, option_id, user_id, created_at
        FROM votes
        WHERE created_at > :since
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"since": since})


def extract_users_since(since: datetime, engine: Engine | None = None) -> pd.DataFrame:
    """Extract users created after the given timestamp."""
    engine = engine or get_engine()
    query = text("""
        SELECT id, full_name, email, created_at
        FROM users
        WHERE created_at > :since
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"since": since})


# ── Batch extracts (used by incremental backfill for scoped recompute) ────────


def extract_polls_by_ids(
    poll_ids: list[int], engine: Engine | None = None
) -> pd.DataFrame:
    """Extract multiple polls by ID with creator names."""
    if not poll_ids:
        return pd.DataFrame()
    engine = engine or get_engine()
    query = text("""
        SELECT p.id,
               COALESCE(to_jsonb(p)->>'title', to_jsonb(p)->>'question') AS title,
               p.active, p.multi_select, p.expires_at,
               p.created_at, p.creator_id, u.full_name AS creator_name
        FROM polls p
        JOIN users u ON p.creator_id = u.id
        WHERE p.id = ANY(:ids)
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"ids": _as_py_int_list(poll_ids)})


def extract_votes_by_polls(
    poll_ids: list[int], engine: Engine | None = None
) -> pd.DataFrame:
    """Extract all votes for multiple polls."""
    if not poll_ids:
        return pd.DataFrame()
    engine = engine or get_engine()
    query = text("""
        SELECT id, poll_id, option_id, user_id, created_at
        FROM votes
        WHERE poll_id = ANY(:ids)
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"ids": _as_py_int_list(poll_ids)})


def extract_options_by_polls(
    poll_ids: list[int], engine: Engine | None = None
) -> pd.DataFrame:
    """Extract all options for multiple polls."""
    if not poll_ids:
        return pd.DataFrame()
    engine = engine or get_engine()
    query = text("""
        SELECT id, poll_id, option_text
        FROM poll_options
        WHERE poll_id = ANY(:ids)
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"ids": _as_py_int_list(poll_ids)})


def extract_users_by_ids(
    user_ids: list[int], engine: Engine | None = None
) -> pd.DataFrame:
    """Extract multiple users by ID."""
    if not user_ids:
        return pd.DataFrame()
    engine = engine or get_engine()
    query = text("""
        SELECT id, full_name, email, created_at
        FROM users
        WHERE id = ANY(:ids)
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"ids": _as_py_int_list(user_ids)})


def extract_votes_by_users(
    user_ids: list[int], engine: Engine | None = None
) -> pd.DataFrame:
    """Extract all votes cast by multiple users."""
    if not user_ids:
        return pd.DataFrame()
    engine = engine or get_engine()
    query = text("""
        SELECT id, poll_id, option_id, user_id, created_at
        FROM votes
        WHERE user_id = ANY(:ids)
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"ids": _as_py_int_list(user_ids)})


def extract_polls_by_creators(
    creator_ids: list[int], engine: Engine | None = None
) -> pd.DataFrame:
    """Extract all polls created by multiple users."""
    if not creator_ids:
        return pd.DataFrame()
    engine = engine or get_engine()
    query = text("""
        SELECT id,
               COALESCE(
                   to_jsonb(polls)->>'title',
                   to_jsonb(polls)->>'question'
               ) AS title,
               active, created_at, creator_id
        FROM polls
        WHERE creator_id = ANY(:ids)
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"ids": _as_py_int_list(creator_ids)})
