"""Shared pytest fixtures for unit tests."""

from __future__ import annotations

import pandas as pd
import pytest


@pytest.fixture
def polls_df() -> pd.DataFrame:
    return pd.DataFrame(
        {
            "id": [1, 2],
            "title": ["Best cloud?", "Favourite DB?"],
            "creator_name": ["Alice", "Bob"],
            "active": [True, False],
            "creator_id": [1, 2],
            "created_at": pd.to_datetime(["2026-01-01", "2026-01-02"]),
        }
    )


@pytest.fixture
def votes_df() -> pd.DataFrame:
    return pd.DataFrame(
        {
            "id": [1, 2, 3, 4],
            "poll_id": [1, 1, 2, 1],
            "option_id": [1, 2, 3, 1],
            "user_id": [10, 11, 10, 12],
            "created_at": pd.to_datetime(
                [
                    "2026-01-01 08:00",
                    "2026-01-01 08:30",
                    "2026-01-01 09:00",
                    "2026-01-01 09:15",
                ]
            ),
        }
    )


@pytest.fixture
def options_df() -> pd.DataFrame:
    return pd.DataFrame(
        {
            "id": [1, 2, 3],
            "poll_id": [1, 1, 2],
            "option_text": ["AWS", "GCP", "PostgreSQL"],
        }
    )


@pytest.fixture
def users_df() -> pd.DataFrame:
    return pd.DataFrame(
        {
            "id": [10, 11, 12],
            "full_name": ["Alice", "Bob", "Carol"],
            "email": ["a@x.com", "b@x.com", "c@x.com"],
            "created_at": pd.to_datetime(["2025-12-01", "2025-12-02", "2025-12-03"]),
        }
    )
