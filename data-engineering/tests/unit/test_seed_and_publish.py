"""Unit tests for scripts/seed_and_publish.py helper functions."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from unittest.mock import MagicMock

import pytest

# Make scripts importable
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "scripts"))

import seed_and_publish as sp  # noqa: E402

# ── Data generation tests ────────────────────────────────────────────────────


class TestGenerateUsers:
    def test_count_and_ids(self):
        users = sp.generate_users(count=5, start_id=100)
        assert len(users) == 5
        assert [u["id"] for u in users] == [100, 101, 102, 103, 104]

    def test_fields_present(self):
        users = sp.generate_users(count=1, start_id=1)
        u = users[0]
        assert "id" in u
        assert "email" in u
        assert "full_name" in u
        assert u["email"].endswith("@quickpoll.test")

    def test_empty(self):
        assert sp.generate_users(count=0, start_id=1) == []


class TestGeneratePolls:
    def test_count_and_ids(self):
        polls = sp.generate_polls(count=3, start_id=1000, user_ids=[1, 2])
        assert len(polls) == 3
        assert [p["id"] for p in polls] == [1000, 1001, 1002]

    def test_options_range(self):
        """Each poll should have 3-5 options."""
        polls = sp.generate_polls(count=20, start_id=1, user_ids=[1])
        for p in polls:
            assert 3 <= len(p["options"]) <= 5

    def test_creator_from_pool(self):
        user_ids = [10, 20, 30]
        polls = sp.generate_polls(count=10, start_id=1, user_ids=user_ids)
        for p in polls:
            assert p["creator_id"] in user_ids

    def test_fields_present(self):
        polls = sp.generate_polls(count=1, start_id=1, user_ids=[1])
        p = polls[0]
        expected_fields = (
            "id",
            "title",
            "question",
            "description",
            "creator_id",
            "multi_select",
            "active",
            "options",
            "created_at",
            "expires_at",
        )
        for field in expected_fields:
            assert field in p, f"Missing field: {field}"


class TestGenerateVotes:
    def test_unique_user_per_poll(self):
        """Each user should vote at most once per poll."""
        user_ids = [1, 2, 3, 4, 5]
        polls = sp.generate_polls(count=2, start_id=1, user_ids=user_ids)
        options, votes = sp.generate_votes(
            polls,
            votes_per_poll=5,
            start_vote_id=1,
            start_option_id=100,
            user_ids=user_ids,
        )
        for poll in polls:
            poll_votes = [v for v in votes if v["poll_id"] == poll["id"]]
            voter_ids = [v["user_id"] for v in poll_votes]
            assert len(voter_ids) == len(set(voter_ids)), "Duplicate voter in same poll"

    def test_option_ids_sequential(self):
        polls = sp.generate_polls(count=1, start_id=1, user_ids=[1])
        options, _ = sp.generate_votes(
            polls, votes_per_poll=1, start_vote_id=1, start_option_id=500, user_ids=[1]
        )
        ids = [o["id"] for o in options]
        assert ids == list(range(500, 500 + len(ids)))

    def test_vote_ids_sequential(self):
        user_ids = [1, 2, 3]
        polls = sp.generate_polls(count=1, start_id=1, user_ids=user_ids)
        _, votes = sp.generate_votes(
            polls,
            votes_per_poll=3,
            start_vote_id=100,
            start_option_id=1,
            user_ids=user_ids,
        )
        ids = [v["id"] for v in votes]
        assert ids == list(range(100, 100 + len(ids)))

    def test_votes_reference_valid_options(self):
        user_ids = [1, 2, 3]
        polls = sp.generate_polls(count=3, start_id=1, user_ids=user_ids)
        options, votes = sp.generate_votes(
            polls,
            votes_per_poll=3,
            start_vote_id=1,
            start_option_id=1,
            user_ids=user_ids,
        )
        option_ids = {o["id"] for o in options}
        for v in votes:
            assert v["option_id"] in option_ids

    def test_empty_polls(self):
        options, votes = sp.generate_votes([], 5, 1, 1, [1])
        assert options == []
        assert votes == []


# ── Kafka event schema tests ────────────────────────────────────────────────


class TestKafkaEventSchemas:
    def _make_poll(self) -> dict:
        return {
            "id": 42,
            "title": "Team tooling preference",
            "creator_id": 7,
            "question": "Test poll question?",
            "multi_select": False,
            "active": True,
            "expires_at": "2026-12-31T23:59:59+00:00",
            "created_at": "2026-01-01T00:00:00+00:00",
        }

    def _make_vote(self) -> dict:
        return {
            "id": 999,
            "poll_id": 42,
            "option_id": 10,
            "user_id": 7,
            "voted_at": "2026-01-01T12:00:00+00:00",
        }

    def test_poll_created_event_has_all_fields(self):
        producer = MagicMock()
        sp.publish_poll_created(producer, self._make_poll())
        producer.send.assert_called_once()
        _, kwargs = producer.send.call_args
        event = kwargs["value"]
        required = {
            "event_type",
            "poll_id",
            "creator_id",
            "occurred_at",
        }
        assert required.issubset(set(event.keys()))
        assert event["event_type"] == "POLL_CREATED"
        assert event["poll_id"] == 42

    def test_vote_cast_event_has_all_fields(self):
        producer = MagicMock()
        sp.publish_vote_cast(producer, self._make_vote())
        producer.send.assert_called_once()
        _, kwargs = producer.send.call_args
        event = kwargs["value"]
        required = {
            "event_type",
            "vote_id",
            "poll_id",
            "option_id",
            "user_id",
            "voted_at",
        }
        assert required.issubset(set(event.keys()))
        assert event["event_type"] == "VOTE_CAST"
        assert event["vote_id"] == 999

    def test_poll_event_sent_to_correct_topic(self):
        producer = MagicMock()
        sp.publish_poll_created(producer, self._make_poll())
        args, _ = producer.send.call_args
        assert args[0] == sp.KAFKA_TOPIC_POLL_EVENTS

    def test_vote_event_sent_to_correct_topic(self):
        producer = MagicMock()
        sp.publish_vote_cast(producer, self._make_vote())
        args, _ = producer.send.call_args
        assert args[0] == sp.KAFKA_TOPIC_VOTE_EVENTS


# ── OLTP insert function tests ──────────────────────────────────────────────


class TestOLTPInserts:
    def test_insert_users_empty(self):
        conn = MagicMock()
        assert sp.insert_users(conn, []) == 0
        conn.execute.assert_not_called()

    def test_insert_polls_empty(self):
        conn = MagicMock()
        assert sp.insert_polls(conn, []) == 0

    def test_insert_options_empty(self):
        conn = MagicMock()
        assert sp.insert_options(conn, []) == 0

    def test_insert_votes_empty(self):
        conn = MagicMock()
        assert sp.insert_votes(conn, []) == 0

    def test_insert_users_calls_execute(self):
        conn = MagicMock()
        conn.execute.return_value = MagicMock(rowcount=2)
        users = sp.generate_users(2, 100)
        result = sp.insert_users(conn, users)
        assert result == 2
        conn.execute.assert_called_once()


# ── CLI validation tests ────────────────────────────────────────────────────


class TestValidateArgs:
    def _make_args(self, **overrides):
        defaults = {
            "users": 8,
            "polls": 5,
            "votes_per_poll": 10,
            "stream_delay": 0,
        }
        defaults.update(overrides)
        return argparse.Namespace(**defaults)

    def test_valid_args_pass(self):
        sp._validate_args(self._make_args())

    def test_zero_users_rejected(self):
        with pytest.raises(SystemExit, match="--users must be >= 1"):
            sp._validate_args(self._make_args(users=0))

    def test_negative_polls_rejected(self):
        with pytest.raises(SystemExit, match="--polls must be >= 0"):
            sp._validate_args(self._make_args(polls=-1))

    def test_negative_votes_per_poll_rejected(self):
        with pytest.raises(SystemExit, match="--votes-per-poll must be >= 0"):
            sp._validate_args(self._make_args(votes_per_poll=-1))

    def test_negative_stream_delay_rejected(self):
        with pytest.raises(SystemExit, match="--stream-delay must be >= 0"):
            sp._validate_args(self._make_args(stream_delay=-1))
