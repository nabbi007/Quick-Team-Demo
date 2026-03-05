"""Pure Pandas transformations that compute KPIs for all four analytics tables."""

from __future__ import annotations

import pandas as pd


def compute_poll_summary(
    polls_df: pd.DataFrame,
    votes_df: pd.DataFrame,
    total_users: int,
) -> pd.DataFrame:
    """
    Compute one summary row per poll.

    Output columns: poll_id, title, creator_name, status,
                    total_votes, unique_voters, participation_rate, created_at
    """
    if polls_df.empty:
        return pd.DataFrame()

    result = polls_df.copy().rename(columns={"id": "poll_id"})
    result["status"] = result["active"].map({True: "ACTIVE", False: "CLOSED"})

    if not votes_df.empty:
        agg = (
            votes_df.groupby("poll_id")["user_id"]
            .agg(total_votes="count", unique_voters="nunique")
            .reset_index()
        )
        result = result.merge(agg, on="poll_id", how="left")
    else:
        result["total_votes"] = 0
        result["unique_voters"] = 0

    result["total_votes"] = result["total_votes"].fillna(0).astype(int)
    result["unique_voters"] = result["unique_voters"].fillna(0).astype(int)

    denom = total_users if total_users > 0 else 1
    result["participation_rate"] = (result["unique_voters"] / denom * 100).round(2)

    return result[
        [
            "poll_id",
            "title",
            "creator_name",
            "status",
            "total_votes",
            "unique_voters",
            "participation_rate",
            "created_at",
        ]
    ]


def compute_option_breakdown(
    options_df: pd.DataFrame,
    votes_df: pd.DataFrame,
) -> pd.DataFrame:
    """
    Compute vote count and percentage share for every poll option.

    Output columns: option_id, poll_id, option_text, vote_count, vote_percentage
    """
    if options_df.empty:
        return pd.DataFrame()

    result = options_df.copy().rename(columns={"id": "option_id"})

    if not votes_df.empty:
        counts = votes_df.groupby("option_id").size().reset_index(name="vote_count")
        result = result.merge(counts, on="option_id", how="left")
    else:
        result["vote_count"] = 0

    result["vote_count"] = result["vote_count"].fillna(0).astype(int)

    poll_totals = result.groupby("poll_id")["vote_count"].transform("sum")
    result["vote_percentage"] = (
        result["vote_count"] / poll_totals.replace(0, 1) * 100
    ).round(2)

    return result[
        ["option_id", "poll_id", "option_text", "vote_count", "vote_percentage"]
    ]


def compute_votes_timeseries(votes_df: pd.DataFrame) -> pd.DataFrame:
    """
    Bucket votes into hourly windows per poll.

    Output columns: poll_id, bucket_time, votes_in_bucket
    """
    if votes_df.empty:
        return pd.DataFrame(columns=["poll_id", "bucket_time", "votes_in_bucket"])

    df = votes_df.copy()
    df["bucket_time"] = pd.to_datetime(df["created_at"]).dt.floor("h")
    result = (
        df.groupby(["poll_id", "bucket_time"])
        .size()
        .reset_index(name="votes_in_bucket")
    )
    return result


def compute_user_participation(
    users_df: pd.DataFrame,
    votes_df: pd.DataFrame,
    polls_df: pd.DataFrame,
) -> pd.DataFrame:
    """
    Compute engagement stats for every user.

    Output columns: user_id, user_name, total_votes_cast,
                    polls_participated, polls_created, last_active
    """
    if users_df.empty:
        return pd.DataFrame()

    result = users_df.copy().rename(columns={"id": "user_id", "full_name": "user_name"})

    if not votes_df.empty:
        vote_agg = (
            votes_df.groupby("user_id")
            .agg(
                total_votes_cast=("id", "count"),
                polls_participated=("poll_id", "nunique"),
                last_active=("created_at", "max"),
            )
            .reset_index()
        )
        result = result.merge(vote_agg, on="user_id", how="left")
    else:
        result["total_votes_cast"] = 0
        result["polls_participated"] = 0
        result["last_active"] = pd.NaT

    if not polls_df.empty:
        polls_created = (
            polls_df.groupby("creator_id").size().reset_index(name="polls_created")
        )
        result = result.merge(
            polls_created, left_on="user_id", right_on="creator_id", how="left"
        )
    else:
        result["polls_created"] = 0

    result["total_votes_cast"] = result["total_votes_cast"].fillna(0).astype(int)
    result["polls_participated"] = result["polls_participated"].fillna(0).astype(int)
    result["polls_created"] = result["polls_created"].fillna(0).astype(int)

    # Convert NaT → None so PostgreSQL receives NULL, not "NaT"
    result["last_active"] = result["last_active"].astype(object)
    result.loc[result["last_active"].isna(), "last_active"] = None

    return result[
        [
            "user_id",
            "user_name",
            "total_votes_cast",
            "polls_participated",
            "polls_created",
            "last_active",
        ]
    ]
