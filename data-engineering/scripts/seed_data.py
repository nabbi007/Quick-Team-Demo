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
    (1, "admin@quickpoll.com", "Admin User"),
    (2, "user@quickpoll.com", "Regular User"),
    (3, "alice@quickpoll.com", "Alice Mensah"),
    (4, "bob@quickpoll.com", "Bob Asante"),
    (5, "carol@quickpoll.com", "Carol Ofori"),
    (6, "dave@quickpoll.com", "Dave Boateng"),
    (7, "eve@quickpoll.com", "Eve Adjei"),
]

POLLS = [
    (4, "Cloud", "Favourite cloud provider?", "AWS / GCP / Azure", 1),
    (
        5,
        "Database",
        "Best database for analytics?",
        "Postgres / BigQuery / Snowflake",
        1,
    ),
    (6, "Retro", "Sprint retrospective: overall mood?", "Rate the sprint", 2),
    (7, "Outing", "Next team outing venue?", "Where should we go?", 3),
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

DEPARTMENTS = [
    (1, "Engineering"),
    (2, "Product"),
    (3, "Operations"),
]

# (department_member_id, email, department_id)
DEPARTMENT_MEMBERS = [
    (1, "admin@quickpoll.com", 1),
    (2, "user@quickpoll.com", 1),
    (3, "alice@quickpoll.com", 2),
    (4, "bob@quickpoll.com", 2),
    (5, "carol@quickpoll.com", 3),
    (6, "dave@quickpoll.com", 1),
    (7, "eve@quickpoll.com", 3),
]

# (poll_invite_id, poll_id, department_member_id, vote_status)
POLL_INVITES = [
    (1, 4, 2, "PENDING"),
    (2, 4, 3, "PENDING"),
    (3, 4, 4, "PENDING"),
    (4, 5, 5, "PENDING"),
    (5, 5, 6, "PENDING"),
    (6, 5, 7, "PENDING"),
    (7, 6, 1, "PENDING"),
    (8, 6, 3, "PENDING"),
    (9, 6, 5, "PENDING"),
    (10, 7, 2, "PENDING"),
    (11, 7, 4, "PENDING"),
    (12, 7, 6, "PENDING"),
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


def _validate_seed_references() -> None:
    """Fail fast if static seed rows are internally inconsistent."""
    user_ids = {u[0] for u in USERS}
    user_emails = {u[1] for u in USERS}
    poll_ids = {p[0] for p in POLLS}
    option_ids = {o[0] for o in OPTIONS}
    department_ids = {d[0] for d in DEPARTMENTS}
    department_member_ids = {dm[0] for dm in DEPARTMENT_MEMBERS}

    missing_poll_creators = sorted({p[4] for p in POLLS} - user_ids)
    if missing_poll_creators:
        raise ValueError(
            f"POLLS references unknown creator_id values: {missing_poll_creators}"
        )

    missing_option_polls = sorted({o[1] for o in OPTIONS} - poll_ids)
    if missing_option_polls:
        raise ValueError(
            f"OPTIONS references unknown poll_id values: {missing_option_polls}"
        )

    missing_member_departments = sorted(
        {dm[2] for dm in DEPARTMENT_MEMBERS} - department_ids
    )
    if missing_member_departments:
        raise ValueError(
            "DEPARTMENT_MEMBERS references unknown department_id values: "
            f"{missing_member_departments}"
        )

    missing_member_users = sorted({dm[1] for dm in DEPARTMENT_MEMBERS} - user_emails)
    if missing_member_users:
        raise ValueError(
            f"DEPARTMENT_MEMBERS references unknown user emails: {missing_member_users}"
        )

    vote_poll_ids = {v[1] for v in VOTES}
    missing_vote_polls = sorted(vote_poll_ids - poll_ids)
    if missing_vote_polls:
        raise ValueError(
            f"VOTES references unknown poll_id values: {missing_vote_polls}"
        )

    vote_option_ids = {v[2] for v in VOTES}
    missing_vote_options = sorted(vote_option_ids - option_ids)
    if missing_vote_options:
        raise ValueError(
            f"VOTES references unknown option_id values: {missing_vote_options}"
        )

    vote_user_ids = {v[3] for v in VOTES}
    missing_vote_users = sorted(vote_user_ids - user_ids)
    if missing_vote_users:
        raise ValueError(
            f"VOTES references unknown user_id values: {missing_vote_users}"
        )

    missing_invite_polls = sorted({i[1] for i in POLL_INVITES} - poll_ids)
    if missing_invite_polls:
        raise ValueError(
            f"POLL_INVITES references unknown poll_id values: {missing_invite_polls}"
        )

    missing_invite_members = sorted(
        {i[2] for i in POLL_INVITES} - department_member_ids
    )
    if missing_invite_members:
        raise ValueError(
            "POLL_INVITES references unknown department_member_id values: "
            f"{missing_invite_members}"
        )


def _reset_sequences(conn) -> None:
    """Advance identity sequences to current MAX(id) after explicit-ID inserts."""
    for table in (
        "users",
        "department",
        "department_members",
        "polls",
        "poll_options",
        "poll_invites",
        "votes",
    ):
        conn.execute(
            text(f"""
            SELECT setval(
                pg_get_serial_sequence('{table}', 'id'),
                COALESCE((SELECT MAX(id) FROM {table}), 1),
                true
            )
        """)
        )


def seed() -> None:
    _validate_seed_references()

    engine = get_engine()
    with engine.begin() as conn:
        users_result = conn.execute(
            text("""
            INSERT INTO users (id, email, password, full_name, role, created_at)
            VALUES (:id, :email, :pw, :name, 'USER', NOW())
            ON CONFLICT (id) DO NOTHING
        """),
            [{"id": u[0], "email": u[1], "pw": _PW, "name": u[2]} for u in USERS],
        )
        logger.info(
            "Seeded users: requested=%d inserted=%d",
            len(USERS),
            max(users_result.rowcount, 0),
        )

        departments_result = conn.execute(
            text("""
            INSERT INTO department (id, name)
            VALUES (:id, :name)
            ON CONFLICT (id) DO NOTHING
        """),
            [{"id": d[0], "name": d[1]} for d in DEPARTMENTS],
        )
        logger.info(
            "Seeded departments: requested=%d inserted=%d",
            len(DEPARTMENTS),
            max(departments_result.rowcount, 0),
        )

        department_members_result = conn.execute(
            text("""
            INSERT INTO department_members (id, email, department_id)
            VALUES (:id, :email, :department_id)
            ON CONFLICT (id) DO NOTHING
        """),
            [
                {"id": dm[0], "email": dm[1], "department_id": dm[2]}
                for dm in DEPARTMENT_MEMBERS
            ],
        )
        logger.info(
            "Seeded department members: requested=%d inserted=%d",
            len(DEPARTMENT_MEMBERS),
            max(department_members_result.rowcount, 0),
        )

        polls_result = conn.execute(
            text("""
            INSERT INTO polls (
                id, title, question, description, creator_id, multi_select,
                expires_at, active, created_at
            )
            VALUES (
                :id, :title, :question, :desc, :creator, :multi_select,
                NOW() + INTERVAL '30 days', true, NOW()
            )
            ON CONFLICT (id) DO NOTHING
        """),
            [
                {
                    "id": p[0],
                    "title": p[1],
                    "question": p[2],
                    "desc": p[3],
                    "creator": p[4],
                    "multi_select": False,
                }
                for p in POLLS
            ],
        )
        logger.info(
            "Seeded polls: requested=%d inserted=%d",
            len(POLLS),
            max(polls_result.rowcount, 0),
        )

        options_result = conn.execute(
            text("""
            INSERT INTO poll_options (id, poll_id, option_text, vote_count)
            VALUES (:id, :poll_id, :text, 0)
            ON CONFLICT (id) DO NOTHING
        """),
            [{"id": o[0], "poll_id": o[1], "text": o[2]} for o in OPTIONS],
        )
        logger.info(
            "Seeded poll options: requested=%d inserted=%d",
            len(OPTIONS),
            max(options_result.rowcount, 0),
        )

        votes_result = conn.execute(
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
        logger.info(
            "Seeded votes: requested=%d inserted=%d",
            len(VOTES),
            max(votes_result.rowcount, 0),
        )

        poll_invites_result = conn.execute(
            text("""
            INSERT INTO poll_invites (
                id, poll_id, department_member_id, invited_at, vote_status
            )
            VALUES (:id, :poll_id, :department_member_id, NOW(), :vote_status)
            ON CONFLICT (id) DO NOTHING
        """),
            [
                {
                    "id": i[0],
                    "poll_id": i[1],
                    "department_member_id": i[2],
                    "vote_status": i[3],
                }
                for i in POLL_INVITES
            ],
        )
        logger.info(
            "Seeded poll invites: requested=%d inserted=%d",
            len(POLL_INVITES),
            max(poll_invites_result.rowcount, 0),
        )

        _reset_sequences(conn)
        logger.info(
            "Reset identity sequences for users, department, department_members, "
            "polls, poll_options, poll_invites, votes"
        )

    logger.info("Seed complete.")


if __name__ == "__main__":
    configure_logging()
    seed()
