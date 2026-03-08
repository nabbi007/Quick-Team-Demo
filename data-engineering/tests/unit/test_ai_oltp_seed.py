"""Unit tests for AI OLTP seeding helpers."""

from __future__ import annotations

from datetime import datetime

import pytest
from sqlalchemy import create_engine, text

from data_engineering.seeding.ai_oltp import (
    SeedChunk,
    SeedPoll,
    SeedPollOption,
    SeedRunStats,
    SeedUser,
    SeedVote,
    build_model_name,
    count_chunk_entities,
    get_seed_profile,
    insert_seed_chunk,
    validate_and_normalize_chunk,
    verify_oltp_state,
)


def _valid_chunk() -> SeedChunk:
    return SeedChunk(
        users=[
            SeedUser(ref="u1", full_name="Alice Mensah", email="alice@quickpoll.local"),
            SeedUser(ref="u2", full_name="Bob Asante", email="bob@quickpoll.local"),
        ],
        polls=[
            SeedPoll(
                ref="p1",
                title="Weekend plans",
                question="What do you enjoy most on weekends?",
                description=(
                    "Pick the option that matches your typical weekend routine."
                ),
                creator_ref="u1",
                options=[
                    SeedPollOption(ref="o1", option_text="Hiking"),
                    SeedPollOption(ref="o2", option_text="Reading"),
                ],
            )
        ],
        votes=[
            SeedVote(user_ref="u1", poll_ref="p1", option_ref="o1"),
            SeedVote(user_ref="u1", poll_ref="p1", option_ref="o2"),
            SeedVote(user_ref="u2", poll_ref="p1", option_ref="o2"),
        ],
    )


def test_build_model_name_adds_groq_prefix() -> None:
    assert build_model_name("llama-3.1-8b-instant") == "groq:llama-3.1-8b-instant"
    assert (
        build_model_name("groq:llama-3.3-70b-versatile")
        == "groq:llama-3.3-70b-versatile"
    )


def test_get_seed_profile_with_chunk_override() -> None:
    profile = get_seed_profile("small", chunks=3)
    assert profile.chunks == 3
    assert profile.users_per_chunk > 0


def test_get_seed_profile_invalid_name_raises() -> None:
    with pytest.raises(ValueError, match="Unsupported profile"):
        get_seed_profile("unknown")


def test_validate_chunk_deduplicates_poll_user_vote_pairs() -> None:
    chunk = _valid_chunk()
    normalized = validate_and_normalize_chunk(chunk)
    assert len(normalized.votes) == 2
    assert {(vote.poll_ref, vote.user_ref) for vote in normalized.votes} == {
        ("p1", "u1"),
        ("p1", "u2"),
    }


def test_validate_chunk_rejects_missing_creator_ref() -> None:
    chunk = _valid_chunk()
    chunk.polls[0].creator_ref = "missing_user"
    with pytest.raises(ValueError, match="creator_ref"):
        validate_and_normalize_chunk(chunk)


def test_validate_chunk_rejects_option_poll_mismatch() -> None:
    chunk = SeedChunk(
        users=[SeedUser(ref="u1", full_name="Alice", email="alice@quickpoll.local")],
        polls=[
            SeedPoll(
                ref="p1",
                title="Question one",
                question="Question 1?",
                description="Description 1",
                creator_ref="u1",
                options=[SeedPollOption(ref="o1", option_text="Yes")],
            ),
            SeedPoll(
                ref="p2",
                title="Question two",
                question="Question 2?",
                description="Description 2",
                creator_ref="u1",
                options=[SeedPollOption(ref="o2", option_text="No")],
            ),
        ],
        votes=[SeedVote(user_ref="u1", poll_ref="p1", option_ref="o2")],
    )
    with pytest.raises(ValueError, match="does not belong"):
        validate_and_normalize_chunk(chunk)


def test_validate_chunk_normalizes_timestamp_to_naive_utc() -> None:
    chunk = _valid_chunk()
    chunk.users[0].created_at = datetime.fromisoformat("2026-01-01T12:30:00+00:00")
    normalized = validate_and_normalize_chunk(chunk)
    assert normalized.users[0].created_at is not None
    assert normalized.users[0].created_at.tzinfo is None


def test_count_chunk_entities_returns_expected_counts() -> None:
    chunk = _valid_chunk()
    counts = count_chunk_entities(chunk)
    assert counts == {"users": 2, "polls": 1, "poll_options": 2, "votes": 3}


def _create_sqlite_seed_schema() -> object:
    engine = create_engine("sqlite+pysqlite:///:memory:", future=True)
    with engine.begin() as conn:
        conn.execute(
            text(
                """
                CREATE TABLE users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    full_name TEXT,
                    email TEXT,
                    password TEXT,
                    role TEXT,
                    created_at TIMESTAMP
                )
                """
            )
        )
        conn.execute(
            text(
                """
                CREATE TABLE polls (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT,
                    question TEXT,
                    description TEXT,
                    active BOOLEAN,
                    multi_select BOOLEAN,
                    creator_id INTEGER,
                    created_at TIMESTAMP,
                    expires_at TIMESTAMP
                )
                """
            )
        )
        conn.execute(
            text(
                """
                CREATE TABLE department (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT
                )
                """
            )
        )
        conn.execute(
            text(
                """
                CREATE TABLE department_members (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    email TEXT,
                    department_id INTEGER
                )
                """
            )
        )
        conn.execute(
            text(
                """
                CREATE TABLE poll_options (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    option_text TEXT,
                    vote_count INTEGER DEFAULT 0,
                    poll_id INTEGER
                )
                """
            )
        )
        conn.execute(
            text(
                """
                CREATE TABLE poll_invites (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    poll_id INTEGER,
                    department_member_id INTEGER,
                    invited_at TIMESTAMP,
                    vote_status TEXT
                )
                """
            )
        )
        conn.execute(
            text(
                """
                CREATE TABLE votes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER,
                    poll_id INTEGER,
                    option_id INTEGER,
                    created_at TIMESTAMP
                )
                """
            )
        )
    return engine


def test_insert_seed_chunk_handles_existing_emails_without_unique_constraints() -> None:
    engine = _create_sqlite_seed_schema()
    stats = SeedRunStats()

    first_chunk = validate_and_normalize_chunk(_valid_chunk())
    second_chunk = validate_and_normalize_chunk(
        SeedChunk(
            users=[
                SeedUser(
                    ref="u3",
                    full_name="Alice Mensah",
                    email="alice@quickpoll.local",
                ),
                SeedUser(
                    ref="u4",
                    full_name="Kojo Boateng",
                    email="kojo@quickpoll.local",
                ),
            ],
            polls=[
                SeedPoll(
                    ref="p2",
                    title="Snack preference",
                    question="Which snack helps you focus most?",
                    description="Pick one preferred snack.",
                    creator_ref="u3",
                    options=[
                        SeedPollOption(ref="o3", option_text="Peanuts"),
                        SeedPollOption(ref="o4", option_text="Fruit"),
                    ],
                )
            ],
            votes=[
                SeedVote(user_ref="u3", poll_ref="p2", option_ref="o3"),
                SeedVote(user_ref="u4", poll_ref="p2", option_ref="o4"),
            ],
        )
    )

    with engine.begin() as conn:
        insert_seed_chunk(conn=conn, chunk=first_chunk, stats=stats)
    with engine.begin() as conn:
        insert_seed_chunk(conn=conn, chunk=second_chunk, stats=stats)

    verification = verify_oltp_state(engine)
    assert stats.users.skipped >= 1
    assert verification["users"] == 3
    assert verification["vote_count_mismatch_rows"] == 0


def test_verify_oltp_state_detects_vote_count_mismatch() -> None:
    engine = _create_sqlite_seed_schema()
    stats = SeedRunStats()
    chunk = validate_and_normalize_chunk(_valid_chunk())

    with engine.begin() as conn:
        insert_seed_chunk(conn=conn, chunk=chunk, stats=stats)
        conn.execute(text("UPDATE poll_options SET vote_count = vote_count + 3"))

    verification = verify_oltp_state(engine)
    assert verification["vote_count_mismatch_rows"] > 0


def test_insert_seed_chunk_populates_department_tables() -> None:
    engine = _create_sqlite_seed_schema()
    stats = SeedRunStats()
    chunk = validate_and_normalize_chunk(_valid_chunk())

    with engine.begin() as conn:
        insert_seed_chunk(conn=conn, chunk=chunk, stats=stats)

    verification = verify_oltp_state(engine)
    assert verification["department"] > 0
    assert verification["department_members"] > 0
