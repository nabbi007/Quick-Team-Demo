"""Unit tests for scripts/seed_data.py integrity and safety helpers."""

from __future__ import annotations

import sys
from pathlib import Path
from unittest.mock import MagicMock

import pytest

# Make scripts importable
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "scripts"))

import seed_data as sd  # noqa: E402


def test_seed_users_cover_poll_creators_and_voters() -> None:
    user_ids = {u[0] for u in sd.USERS}
    required_ids = {p[4] for p in sd.POLLS} | {v[3] for v in sd.VOTES}
    assert required_ids.issubset(user_ids)


def test_seed_options_and_votes_reference_existing_parents() -> None:
    poll_ids = {p[0] for p in sd.POLLS}
    option_ids = {o[0] for o in sd.OPTIONS}
    user_emails = {u[1] for u in sd.USERS}
    department_ids = {d[0] for d in sd.DEPARTMENTS}
    department_member_ids = {dm[0] for dm in sd.DEPARTMENT_MEMBERS}

    option_poll_ids = {o[1] for o in sd.OPTIONS}
    vote_poll_ids = {v[1] for v in sd.VOTES}
    vote_option_ids = {v[2] for v in sd.VOTES}
    member_department_ids = {dm[2] for dm in sd.DEPARTMENT_MEMBERS}
    member_emails = {dm[1] for dm in sd.DEPARTMENT_MEMBERS}
    invite_poll_ids = {i[1] for i in sd.POLL_INVITES}
    invite_member_ids = {i[2] for i in sd.POLL_INVITES}

    assert option_poll_ids.issubset(poll_ids)
    assert vote_poll_ids.issubset(poll_ids)
    assert vote_option_ids.issubset(option_ids)
    assert member_department_ids.issubset(department_ids)
    assert member_emails.issubset(user_emails)
    assert invite_poll_ids.issubset(poll_ids)
    assert invite_member_ids.issubset(department_member_ids)


def test_validate_seed_references_raises_on_missing_creator(monkeypatch) -> None:
    broken_polls = list(sd.POLLS) + [
        (999, "Broken poll", "Broken question", "Broken desc", 999)
    ]
    monkeypatch.setattr(sd, "POLLS", broken_polls)
    with pytest.raises(ValueError, match="creator_id"):
        sd._validate_seed_references()


def test_reset_sequences_executes_for_all_tables() -> None:
    conn = MagicMock()
    sd._reset_sequences(conn)

    assert conn.execute.call_count == 7
    statements = [str(call.args[0]) for call in conn.execute.call_args_list]
    merged = "\n".join(statements)

    assert "pg_get_serial_sequence('users', 'id')" in merged
    assert "pg_get_serial_sequence('department', 'id')" in merged
    assert "pg_get_serial_sequence('department_members', 'id')" in merged
    assert "pg_get_serial_sequence('polls', 'id')" in merged
    assert "pg_get_serial_sequence('poll_options', 'id')" in merged
    assert "pg_get_serial_sequence('poll_invites', 'id')" in merged
    assert "pg_get_serial_sequence('votes', 'id')" in merged
