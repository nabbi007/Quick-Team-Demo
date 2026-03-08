"""Unit tests for transformation module."""

from __future__ import annotations

import pandas as pd
import pytest

from data_engineering.transformation.transformers import (
    compute_option_breakdown,
    compute_poll_summary,
    compute_user_participation,
    compute_votes_timeseries,
)

# ── compute_poll_summary ─────────────────────────────────────────────────────


def test_poll_summary_empty_polls() -> None:
    result = compute_poll_summary(pd.DataFrame(), pd.DataFrame(), total_users=5)
    assert result.empty


def test_poll_summary_columns(polls_df, votes_df) -> None:
    result = compute_poll_summary(polls_df, votes_df, total_users=10)
    assert set(result.columns) == {
        "poll_id",
        "title",
        "creator_name",
        "status",
        "total_votes",
        "unique_voters",
        "participation_rate",
        "created_at",
    }


def test_poll_summary_vote_counts(polls_df, votes_df) -> None:
    result = compute_poll_summary(polls_df, votes_df, total_users=10)
    row1 = result[result["poll_id"] == 1].iloc[0]
    assert row1["total_votes"] == 3  # votes for poll 1
    assert row1["unique_voters"] == 3


def test_poll_summary_status_mapping(polls_df, votes_df) -> None:
    result = compute_poll_summary(polls_df, votes_df, total_users=10)
    assert result[result["poll_id"] == 1]["status"].iloc[0] == "ACTIVE"
    assert result[result["poll_id"] == 2]["status"].iloc[0] == "CLOSED"


def test_poll_summary_no_votes(polls_df) -> None:
    result = compute_poll_summary(polls_df, pd.DataFrame(), total_users=5)
    assert (result["total_votes"] == 0).all()


def test_poll_summary_participation_rate(polls_df, votes_df) -> None:
    result = compute_poll_summary(polls_df, votes_df, total_users=10)
    # unique_voters for poll 1 = 3, total_users = 10  →  30.0
    row1 = result[result["poll_id"] == 1].iloc[0]
    assert row1["participation_rate"] == pytest.approx(30.0)


# ── compute_option_breakdown ─────────────────────────────────────────────────


def test_option_breakdown_empty_options() -> None:
    result = compute_option_breakdown(pd.DataFrame(), pd.DataFrame())
    assert result.empty


def test_option_breakdown_columns(options_df, votes_df) -> None:
    result = compute_option_breakdown(options_df, votes_df)
    assert set(result.columns) == {
        "option_id",
        "poll_id",
        "option_text",
        "vote_count",
        "vote_percentage",
    }


def test_option_breakdown_counts(options_df, votes_df) -> None:
    result = compute_option_breakdown(options_df, votes_df)
    opt1 = result[result["option_id"] == 1].iloc[
        0
    ]  # option 1: votes with option_id=1 (×2)
    opt2 = result[result["option_id"] == 2].iloc[
        0
    ]  # option 2: votes with option_id=2 (×1)
    assert opt1["vote_count"] == 2
    assert opt2["vote_count"] == 1


def test_option_breakdown_percentages_sum_to_100(options_df, votes_df) -> None:
    result = compute_option_breakdown(options_df, votes_df)
    for poll_id in result["poll_id"].unique():
        total = result[result["poll_id"] == poll_id]["vote_percentage"].sum()
        assert total == pytest.approx(100.0, abs=0.1)


def test_option_breakdown_no_votes(options_df) -> None:
    result = compute_option_breakdown(options_df, pd.DataFrame())
    assert (result["vote_count"] == 0).all()


# ── compute_votes_timeseries ─────────────────────────────────────────────────


def test_timeseries_empty_votes() -> None:
    result = compute_votes_timeseries(pd.DataFrame())
    assert result.empty
    assert list(result.columns) == ["poll_id", "bucket_time", "votes_in_bucket"]


def test_timeseries_buckets(votes_df) -> None:
    result = compute_votes_timeseries(votes_df)
    assert "bucket_time" in result.columns
    # votes_df has 3 votes for poll 1 spread across 08:xx and 09:xx → 2 buckets
    # plus 1 vote for poll 2 at 09:xx → 1 bucket
    assert len(result) == 3


def test_timeseries_vote_count(votes_df) -> None:
    result = compute_votes_timeseries(votes_df)
    # poll 1, 08:00 bucket → 2 votes (08:00 and 08:30)
    row = result[(result["poll_id"] == 1) & (result["bucket_time"].dt.hour == 8)]
    assert row["votes_in_bucket"].iloc[0] == 2


# ── compute_user_participation ───────────────────────────────────────────────


def test_user_participation_empty_users() -> None:
    result = compute_user_participation(pd.DataFrame(), pd.DataFrame(), pd.DataFrame())
    assert result.empty


def test_user_participation_columns(users_df, votes_df, polls_df) -> None:
    result = compute_user_participation(users_df, votes_df, polls_df)
    assert set(result.columns) >= {
        "user_id",
        "user_name",
        "total_votes_cast",
        "polls_participated",
        "polls_created",
    }


def test_user_participation_vote_counts(users_df, votes_df, polls_df) -> None:
    result = compute_user_participation(users_df, votes_df, polls_df)
    # user_id=10 cast 2 votes (option_id 1 for poll 1, option_id 3 for poll 2)
    u10 = result[result["user_id"] == 10].iloc[0]
    assert u10["total_votes_cast"] == 2
    assert u10["polls_participated"] == 2


def test_user_participation_no_votes(users_df, polls_df) -> None:
    result = compute_user_participation(users_df, pd.DataFrame(), polls_df)
    assert (result["total_votes_cast"] == 0).all()
