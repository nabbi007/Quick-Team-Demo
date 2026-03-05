"""Seed the database with additional realistic users, polls, and votes.

Idempotent — uses ON CONFLICT DO NOTHING so it's safe to run multiple times.
The backend's data.sql already seeds the minimal working set; this script adds
enough volume to make the analytics tables meaningful during development.

Usage (from data-engineering/ with .env present):
    uv run python scripts/seed_data.py
"""

from __future__ import annotations

import logging
import sys
from pathlib import Path

# Allow running as a script without installing
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from sqlalchemy import text  # noqa: E402

from data_engineering.config import get_engine  # noqa: E402
from data_engineering.utils.logging import configure_logging  # noqa: E402

logger = logging.getLogger(__name__)

# BCrypt of "password123"
_PW = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"

USERS = [
    (3, "alice@quickpoll.com", "Alice Mensah"),
    (4, "bob@quickpoll.com", "Bob Asante"),
    (5, "carol@quickpoll.com", "Carol Ofori"),
    (6, "dave@quickpoll.com", "Dave Boateng"),
    (7, "eve@quickpoll.com", "Eve Adjei"),
]

POLLS = [
    (4, "Favourite cloud provider?", "AWS / GCP / Azure", 1),
    (5, "Best database for analytics?", "Postgres / BigQuery / Snowflake", 1),
    (6, "Sprint retrospective: overall mood?", "Rate the sprint", 2),
    (7, "Next team outing venue?", "Where should we go?", 3),
]

OPTIONS = [
    (12, 4, "AWS"),
    (13, 4, "GCP"),
    (14, 4, "Azure"),
    (15, 4, "DigitalOcean"),
    (16, 5, "PostgreSQL"),
    (17, 5, "BigQuery"),
    (18, 5, "Snowflake"),
    (19, 6, "😀 Great"),
    (20, 6, "😐 Okay"),
    (21, 6, "😞 Tough"),
    (22, 7, "Beach"),
    (23, 7, "Mountains"),
    (24, 7, "City tour"),
]

# (vote_id, poll_id, option_id, user_id)
VOTES = [
    (6, 4, 12, 2),
    (7, 4, 13, 3),
    (8, 4, 14, 4),
    (9, 4, 12, 5),
    (10, 4, 13, 6),
    (11, 5, 16, 1),
    (12, 5, 17, 2),
    (13, 5, 18, 3),
    (14, 5, 16, 4),
    (15, 6, 19, 1),
    (16, 6, 19, 2),
    (17, 6, 20, 3),
    (18, 6, 21, 4),
    (19, 7, 22, 5),
    (20, 7, 23, 6),
    (21, 7, 24, 7),
]


def seed() -> None:
    engine = get_engine()
    with engine.begin() as conn:
        conn.execute(
            text("""
            INSERT INTO users (id, email, password, full_name, role, created_at)
            VALUES (:id, :email, :pw, :name, 'USER', NOW())
            ON CONFLICT (id) DO NOTHING
        """),
            [{"id": u[0], "email": u[1], "pw": _PW, "name": u[2]} for u in USERS],
        )
        logger.info("Seeded %d users", len(USERS))

        conn.execute(
            text("""
            INSERT INTO polls (id, title, description, creator_id, multi_select,
                               expires_at, active, created_at)
            VALUES (:id, :title, :desc, :creator, false,
                   NOW() + INTERVAL '30 days', true, NOW())
            ON CONFLICT (id) DO NOTHING
        """),
            [{"id": p[0], "title": p[1], "desc": p[2], "creator": p[3]} for p in POLLS],
        )
        logger.info("Seeded %d polls", len(POLLS))

        conn.execute(
            text("""
            INSERT INTO poll_options (id, poll_id, option_text, vote_count)
            VALUES (:id, :poll_id, :text, 0)
            ON CONFLICT (id) DO NOTHING
        """),
            [{"id": o[0], "poll_id": o[1], "text": o[2]} for o in OPTIONS],
        )
        logger.info("Seeded %d poll options", len(OPTIONS))

        conn.execute(
            text("""
            INSERT INTO votes (id, poll_id, option_id, user_id, created_at)
            VALUES (:id, :poll_id, :option_id, :user_id, NOW())
            ON CONFLICT DO NOTHING
        """),
            [
                {"id": v[0], "poll_id": v[1], "option_id": v[2], "user_id": v[3]}
                for v in VOTES
            ],
        )
        logger.info("Seeded %d votes", len(VOTES))

    logger.info("Seed complete.")


if __name__ == "__main__":
    configure_logging()
    seed()
