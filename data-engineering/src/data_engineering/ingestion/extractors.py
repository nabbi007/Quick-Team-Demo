"""Batch extractors that read from the shared OLTP PostgreSQL database."""

from __future__ import annotations

import pandas as pd
from sqlalchemy import text
from sqlalchemy.engine import Engine

from data_engineering.config import get_engine

# ── Full-table extracts (used by backfill on startup) ─────────────────────────


def extract_polls(engine: Engine | None = None) -> pd.DataFrame:
    """Extract all polls with creator display name."""
    engine = engine or get_engine()
    query = text("""
        SELECT p.id, p.title, p.active, p.multi_select, p.expires_at,
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
        SELECT p.id, p.title, p.active, p.multi_select, p.expires_at,
               p.created_at, p.creator_id, u.full_name AS creator_name
        FROM polls p
        JOIN users u ON p.creator_id = u.id
        WHERE p.id = :poll_id
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"poll_id": poll_id})


def extract_votes_by_poll(poll_id: int, engine: Engine | None = None) -> pd.DataFrame:
    """Extract all votes for a specific poll."""
    engine = engine or get_engine()
    query = text("""
        SELECT id, poll_id, option_id, user_id, created_at
        FROM votes
        WHERE poll_id = :poll_id
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"poll_id": poll_id})


def extract_options_by_poll(poll_id: int, engine: Engine | None = None) -> pd.DataFrame:
    """Extract all options for a specific poll."""
    engine = engine or get_engine()
    query = text("""
        SELECT id, poll_id, option_text
        FROM poll_options
        WHERE poll_id = :poll_id
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"poll_id": poll_id})


def extract_user_by_id(user_id: int, engine: Engine | None = None) -> pd.DataFrame:
    """Extract a single user."""
    engine = engine or get_engine()
    query = text("""
        SELECT id, full_name, email, created_at
        FROM users
        WHERE id = :user_id
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"user_id": user_id})


def extract_votes_by_user(user_id: int, engine: Engine | None = None) -> pd.DataFrame:
    """Extract all votes cast by a specific user."""
    engine = engine or get_engine()
    query = text("""
        SELECT id, poll_id, option_id, user_id, created_at
        FROM votes
        WHERE user_id = :user_id
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"user_id": user_id})


def extract_polls_by_creator(
    creator_id: int, engine: Engine | None = None
) -> pd.DataFrame:
    """Extract all polls created by a specific user."""
    engine = engine or get_engine()
    query = text("""
        SELECT id, title, active, created_at, creator_id
        FROM polls
        WHERE creator_id = :creator_id
    """)
    with engine.connect() as conn:
        return pd.read_sql(query, conn, params={"creator_id": creator_id})
