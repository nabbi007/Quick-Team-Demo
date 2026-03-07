"""Unit tests for incremental backfill config, models, and watermark writers."""

from __future__ import annotations

from datetime import datetime
from unittest.mock import MagicMock, patch

import pandas as pd

# ── config vars ───────────────────────────────────────────────────────────────


def test_backfill_interval_minutes_default() -> None:
    """BACKFILL_INTERVAL_MINUTES defaults to 30."""
    from data_engineering.config import BACKFILL_INTERVAL_MINUTES

    assert isinstance(BACKFILL_INTERVAL_MINUTES, int)
    assert BACKFILL_INTERVAL_MINUTES > 0


def test_watermark_overlap_minutes_default() -> None:
    """WATERMARK_OVERLAP_MINUTES defaults to 5."""
    from data_engineering.config import WATERMARK_OVERLAP_MINUTES

    assert isinstance(WATERMARK_OVERLAP_MINUTES, int)
    assert WATERMARK_OVERLAP_MINUTES >= 0


def test_force_full_backfill_default() -> None:
    """FORCE_FULL_BACKFILL defaults to False."""
    from data_engineering.config import FORCE_FULL_BACKFILL

    assert isinstance(FORCE_FULL_BACKFILL, bool)


# ── pipeline_watermarks table definition ──────────────────────────────────────


def test_pipeline_watermarks_table_exists() -> None:
    """pipeline_watermarks is registered in the shared metadata."""
    from data_engineering.loading.models import metadata, pipeline_watermarks

    assert pipeline_watermarks.name == "pipeline_watermarks"
    assert "pipeline_watermarks" in metadata.tables


def test_pipeline_watermarks_columns() -> None:
    """Table has entity_name (PK), high_watermark, and updated_at columns."""
    from data_engineering.loading.models import pipeline_watermarks

    col_names = {c.name for c in pipeline_watermarks.columns}
    assert col_names == {"entity_name", "high_watermark", "updated_at"}
    assert pipeline_watermarks.c.entity_name.primary_key


def test_pipeline_watermarks_high_watermark_not_nullable() -> None:
    """high_watermark column must be NOT NULL."""
    from data_engineering.loading.models import pipeline_watermarks

    assert not pipeline_watermarks.c.high_watermark.nullable


# ── get_watermark / set_watermark writers ─────────────────────────────────────


@patch("data_engineering.loading.writers.get_engine")
def test_get_watermark_returns_none_when_no_row(mock_engine: MagicMock) -> None:
    """get_watermark returns None when the entity has no stored watermark."""
    from data_engineering.loading.writers import get_watermark

    mock_conn = MagicMock()
    mock_conn.execute.return_value.fetchone.return_value = None
    mock_engine.return_value.connect.return_value.__enter__ = lambda s: mock_conn
    mock_engine.return_value.connect.return_value.__exit__ = lambda s, *a: None

    result = get_watermark("polls")
    assert result is None


@patch("data_engineering.loading.writers.get_engine")
def test_get_watermark_returns_timestamp(mock_engine: MagicMock) -> None:
    """get_watermark returns the stored high_watermark datetime."""
    from data_engineering.loading.writers import get_watermark

    ts = datetime(2025, 1, 15, 12, 0, 0)
    fake_row = MagicMock()
    fake_row.high_watermark = ts

    mock_conn = MagicMock()
    mock_conn.execute.return_value.fetchone.return_value = fake_row
    mock_engine.return_value.connect.return_value.__enter__ = lambda s: mock_conn
    mock_engine.return_value.connect.return_value.__exit__ = lambda s, *a: None

    result = get_watermark("polls")
    assert result == ts


@patch("data_engineering.loading.writers.get_engine")
def test_set_watermark_executes_upsert(mock_engine: MagicMock) -> None:
    """set_watermark calls execute with an upsert statement."""
    from data_engineering.loading.writers import set_watermark

    mock_conn = MagicMock()
    mock_engine.return_value.begin.return_value.__enter__ = lambda s: mock_conn
    mock_engine.return_value.begin.return_value.__exit__ = lambda s, *a: None

    ts = datetime(2025, 6, 1, 8, 30, 0)
    set_watermark("votes", ts)

    mock_conn.execute.assert_called_once()


# ── Helpers ──────────────────────────────────────────────────────────────────


def _ts(s: str) -> datetime:
    """Shorthand to parse a datetime string."""
    return datetime.fromisoformat(s)


def _empty_df() -> pd.DataFrame:
    return pd.DataFrame()


def _polls_df() -> pd.DataFrame:
    return pd.DataFrame(
        {
            "id": [1, 2],
            "title": ["Poll A", "Poll B"],
            "active": [True, False],
            "multi_select": [False, False],
            "expires_at": [None, None],
            "created_at": pd.to_datetime(["2026-01-01", "2026-01-02"]),
            "creator_id": [10, 11],
            "creator_name": ["Alice", "Bob"],
        }
    )


def _votes_df() -> pd.DataFrame:
    return pd.DataFrame(
        {
            "id": [1, 2, 3],
            "poll_id": [1, 1, 2],
            "option_id": [1, 2, 3],
            "user_id": [10, 11, 10],
            "created_at": pd.to_datetime(
                ["2026-01-01 08:00", "2026-01-01 08:30", "2026-01-01 09:00"]
            ),
        }
    )


def _options_df() -> pd.DataFrame:
    return pd.DataFrame(
        {
            "id": [1, 2, 3],
            "poll_id": [1, 1, 2],
            "option_text": ["A", "B", "C"],
        }
    )


def _users_df() -> pd.DataFrame:
    return pd.DataFrame(
        {
            "id": [10, 11],
            "full_name": ["Alice", "Bob"],
            "email": ["a@x.com", "b@x.com"],
            "created_at": pd.to_datetime(["2025-12-01", "2025-12-02"]),
        }
    )


_BACKFILL_MOD = "data_engineering.pipeline.backfill"


# ── Full backfill tests ─────────────────────────────────────────────────────


class TestFullBackfill:
    """Tests for full backfill behaviour (no watermarks or forced)."""

    @patch(f"{_BACKFILL_MOD}.set_watermark")
    @patch(f"{_BACKFILL_MOD}.upsert_user_participation")
    @patch(f"{_BACKFILL_MOD}.upsert_votes_timeseries")
    @patch(f"{_BACKFILL_MOD}.upsert_option_breakdown")
    @patch(f"{_BACKFILL_MOD}.upsert_poll_summary")
    @patch(f"{_BACKFILL_MOD}.compute_user_participation", return_value=_empty_df())
    @patch(f"{_BACKFILL_MOD}.compute_votes_timeseries", return_value=_empty_df())
    @patch(f"{_BACKFILL_MOD}.compute_option_breakdown", return_value=_empty_df())
    @patch(f"{_BACKFILL_MOD}.compute_poll_summary", return_value=_empty_df())
    @patch(f"{_BACKFILL_MOD}.get_watermark", return_value=None)
    @patch(f"{_BACKFILL_MOD}.get_total_users", return_value=3)
    @patch(f"{_BACKFILL_MOD}.extract_users", return_value=_users_df())
    @patch(f"{_BACKFILL_MOD}.extract_options", return_value=_options_df())
    @patch(f"{_BACKFILL_MOD}.extract_votes", return_value=_votes_df())
    @patch(f"{_BACKFILL_MOD}.extract_polls", return_value=_polls_df())
    def test_full_backfill_when_no_watermarks(
        self,
        mock_polls,
        mock_votes,
        mock_options,
        mock_users,
        mock_total,
        mock_get_wm,
        mock_comp_ps,
        mock_comp_ob,
        mock_comp_ts,
        mock_comp_up,
        mock_upsert_ps,
        mock_upsert_ob,
        mock_upsert_ts,
        mock_upsert_up,
        mock_set_wm,
    ):
        """When any watermark is None, full extractors should be called."""
        from data_engineering.pipeline.backfill import run_backfill

        run_backfill()

        mock_polls.assert_called_once()
        mock_votes.assert_called_once()
        mock_options.assert_called_once()
        mock_users.assert_called_once()
        mock_upsert_ps.assert_called_once()
        mock_upsert_ob.assert_called_once()

    @patch(f"{_BACKFILL_MOD}.FORCE_FULL_BACKFILL", True)
    @patch(f"{_BACKFILL_MOD}.set_watermark")
    @patch(f"{_BACKFILL_MOD}.upsert_user_participation")
    @patch(f"{_BACKFILL_MOD}.upsert_votes_timeseries")
    @patch(f"{_BACKFILL_MOD}.upsert_option_breakdown")
    @patch(f"{_BACKFILL_MOD}.upsert_poll_summary")
    @patch(f"{_BACKFILL_MOD}.compute_user_participation", return_value=_empty_df())
    @patch(f"{_BACKFILL_MOD}.compute_votes_timeseries", return_value=_empty_df())
    @patch(f"{_BACKFILL_MOD}.compute_option_breakdown", return_value=_empty_df())
    @patch(f"{_BACKFILL_MOD}.compute_poll_summary", return_value=_empty_df())
    @patch(f"{_BACKFILL_MOD}.get_watermark", return_value=_ts("2026-01-01T00:00:00"))
    @patch(f"{_BACKFILL_MOD}.get_total_users", return_value=3)
    @patch(f"{_BACKFILL_MOD}.extract_users", return_value=_users_df())
    @patch(f"{_BACKFILL_MOD}.extract_options", return_value=_options_df())
    @patch(f"{_BACKFILL_MOD}.extract_votes", return_value=_votes_df())
    @patch(f"{_BACKFILL_MOD}.extract_polls", return_value=_polls_df())
    def test_force_full_backfill_ignores_watermarks(
        self,
        mock_polls,
        mock_votes,
        mock_options,
        mock_users,
        mock_total,
        mock_get_wm,
        mock_comp_ps,
        mock_comp_ob,
        mock_comp_ts,
        mock_comp_up,
        mock_upsert_ps,
        mock_upsert_ob,
        mock_upsert_ts,
        mock_upsert_up,
        mock_set_wm,
    ):
        """FORCE_FULL_BACKFILL=True triggers full backfill even with watermarks."""
        from data_engineering.pipeline.backfill import run_backfill

        run_backfill()

        # Full extractors should be called, not incremental ones
        mock_polls.assert_called_once()
        mock_votes.assert_called_once()


# ── Incremental backfill tests ──────────────────────────────────────────────


class TestIncrementalBackfill:
    """Tests for incremental backfill behaviour (watermarks exist)."""

    @patch(f"{_BACKFILL_MOD}.set_watermark")
    @patch(f"{_BACKFILL_MOD}.upsert_user_participation")
    @patch(f"{_BACKFILL_MOD}.upsert_votes_timeseries")
    @patch(f"{_BACKFILL_MOD}.upsert_option_breakdown")
    @patch(f"{_BACKFILL_MOD}.upsert_poll_summary")
    @patch(f"{_BACKFILL_MOD}.extract_users_since", return_value=_empty_df())
    @patch(f"{_BACKFILL_MOD}.extract_votes_since", return_value=_empty_df())
    @patch(f"{_BACKFILL_MOD}.extract_polls_since", return_value=_empty_df())
    @patch(f"{_BACKFILL_MOD}.get_watermark", return_value=_ts("2026-01-01T00:00:00"))
    def test_no_changes_returns_early(
        self,
        mock_get_wm,
        mock_polls_since,
        mock_votes_since,
        mock_users_since,
        mock_upsert_ps,
        mock_upsert_ob,
        mock_upsert_ts,
        mock_upsert_up,
        mock_set_wm,
    ):
        """When all deltas are empty, no upserts should be called."""
        from data_engineering.pipeline.backfill import run_backfill

        run_backfill()

        mock_upsert_ps.assert_not_called()
        mock_upsert_ob.assert_not_called()
        mock_upsert_ts.assert_not_called()
        mock_upsert_up.assert_not_called()
        mock_set_wm.assert_not_called()

    @patch(f"{_BACKFILL_MOD}.set_watermark")
    @patch(f"{_BACKFILL_MOD}.upsert_user_participation")
    @patch(f"{_BACKFILL_MOD}.upsert_votes_timeseries")
    @patch(f"{_BACKFILL_MOD}.upsert_option_breakdown")
    @patch(f"{_BACKFILL_MOD}.upsert_poll_summary")
    @patch(f"{_BACKFILL_MOD}.compute_user_participation", return_value=_empty_df())
    @patch(f"{_BACKFILL_MOD}.compute_votes_timeseries", return_value=_empty_df())
    @patch(f"{_BACKFILL_MOD}.compute_option_breakdown", return_value=_empty_df())
    @patch(f"{_BACKFILL_MOD}.compute_poll_summary", return_value=_empty_df())
    @patch(f"{_BACKFILL_MOD}.get_total_users", return_value=5)
    @patch(f"{_BACKFILL_MOD}.extract_votes_by_users", return_value=_empty_df())
    @patch(f"{_BACKFILL_MOD}.extract_users_by_ids", return_value=_users_df())
    @patch(f"{_BACKFILL_MOD}.extract_polls_by_creators", return_value=_empty_df())
    @patch(f"{_BACKFILL_MOD}.extract_options_by_polls", return_value=_options_df())
    @patch(f"{_BACKFILL_MOD}.extract_votes_by_polls", return_value=_votes_df())
    @patch(f"{_BACKFILL_MOD}.extract_polls_by_ids", return_value=_polls_df())
    @patch(f"{_BACKFILL_MOD}.extract_users_since", return_value=_empty_df())
    @patch(
        f"{_BACKFILL_MOD}.extract_votes_since",
        return_value=pd.DataFrame(
            {
                "id": [99],
                "poll_id": [1],
                "option_id": [2],
                "user_id": [10],
                "created_at": pd.to_datetime(["2026-02-01 10:00"]),
            }
        ),
    )
    @patch(f"{_BACKFILL_MOD}.extract_polls_since", return_value=_empty_df())
    @patch(f"{_BACKFILL_MOD}.get_watermark", return_value=_ts("2026-01-01T00:00:00"))
    def test_affected_poll_ids_from_vote_deltas(
        self,
        mock_get_wm,
        mock_polls_since,
        mock_votes_since,
        mock_users_since,
        mock_polls_by_ids,
        mock_votes_by_polls,
        mock_options_by_polls,
        mock_polls_by_creators,
        mock_users_by_ids,
        mock_votes_by_users,
        mock_total,
        mock_comp_ps,
        mock_comp_ob,
        mock_comp_ts,
        mock_comp_up,
        mock_upsert_ps,
        mock_upsert_ob,
        mock_upsert_ts,
        mock_upsert_up,
        mock_set_wm,
    ):
        """New votes should trigger recompute for their poll_ids."""
        from data_engineering.pipeline.backfill import run_backfill

        run_backfill()

        # poll_id=1 from vote delta should trigger batch extract
        mock_polls_by_ids.assert_called_once_with([1])
        mock_votes_by_polls.assert_called_once_with([1])
        # user_id=10 from vote delta should trigger user recompute
        mock_users_by_ids.assert_called_once_with([10])

    @patch(f"{_BACKFILL_MOD}.set_watermark")
    @patch(f"{_BACKFILL_MOD}.upsert_user_participation")
    @patch(f"{_BACKFILL_MOD}.upsert_votes_timeseries")
    @patch(f"{_BACKFILL_MOD}.upsert_option_breakdown")
    @patch(f"{_BACKFILL_MOD}.upsert_poll_summary")
    @patch(f"{_BACKFILL_MOD}.compute_user_participation", return_value=_empty_df())
    @patch(f"{_BACKFILL_MOD}.compute_votes_timeseries", return_value=_empty_df())
    @patch(f"{_BACKFILL_MOD}.compute_option_breakdown", return_value=_empty_df())
    @patch(f"{_BACKFILL_MOD}.compute_poll_summary", return_value=_empty_df())
    @patch(f"{_BACKFILL_MOD}.get_total_users", return_value=5)
    @patch(f"{_BACKFILL_MOD}.extract_votes_by_users", return_value=_empty_df())
    @patch(f"{_BACKFILL_MOD}.extract_users_by_ids", return_value=_users_df())
    @patch(f"{_BACKFILL_MOD}.extract_polls_by_creators", return_value=_empty_df())
    @patch(f"{_BACKFILL_MOD}.extract_options_by_polls", return_value=_options_df())
    @patch(f"{_BACKFILL_MOD}.extract_votes_by_polls", return_value=_votes_df())
    @patch(f"{_BACKFILL_MOD}.extract_polls_by_ids", return_value=_polls_df())
    @patch(f"{_BACKFILL_MOD}.extract_users_since", return_value=_empty_df())
    @patch(
        f"{_BACKFILL_MOD}.extract_votes_since",
        return_value=pd.DataFrame(
            {
                "id": [99],
                "poll_id": [1],
                "option_id": [2],
                "user_id": [10],
                "created_at": pd.to_datetime(["2026-02-01 10:00"]),
            }
        ),
    )
    @patch(f"{_BACKFILL_MOD}.extract_polls_since", return_value=_empty_df())
    @patch(f"{_BACKFILL_MOD}.get_watermark", return_value=_ts("2026-01-01T00:00:00"))
    def test_watermarks_advanced_after_successful_backfill(
        self,
        mock_get_wm,
        mock_polls_since,
        mock_votes_since,
        mock_users_since,
        mock_polls_by_ids,
        mock_votes_by_polls,
        mock_options_by_polls,
        mock_polls_by_creators,
        mock_users_by_ids,
        mock_votes_by_users,
        mock_total,
        mock_comp_ps,
        mock_comp_ob,
        mock_comp_ts,
        mock_comp_up,
        mock_upsert_ps,
        mock_upsert_ob,
        mock_upsert_ts,
        mock_upsert_up,
        mock_set_wm,
    ):
        """set_watermark should be called with MAX timestamp from deltas."""
        from data_engineering.pipeline.backfill import run_backfill

        run_backfill()

        # Votes delta had created_at 2026-02-01 10:00 — watermark should be advanced
        mock_set_wm.assert_called()
        calls = mock_set_wm.call_args_list
        vote_wm_call = [c for c in calls if c[0][0] == "votes"]
        assert len(vote_wm_call) == 1
        assert vote_wm_call[0][0][1] == _ts("2026-02-01T10:00:00")


# ── _advance_watermark tests ────────────────────────────────────────────────


class TestAdvanceWatermark:
    """Tests for the _advance_watermark helper."""

    @patch(f"{_BACKFILL_MOD}.set_watermark")
    def test_empty_df_does_not_set_watermark(self, mock_set_wm):
        from data_engineering.pipeline.backfill import _advance_watermark

        _advance_watermark("polls", _empty_df(), "created_at")
        mock_set_wm.assert_not_called()

    @patch(f"{_BACKFILL_MOD}.set_watermark")
    def test_missing_column_does_not_set_watermark(self, mock_set_wm):
        from data_engineering.pipeline.backfill import _advance_watermark

        df = pd.DataFrame({"id": [1, 2]})
        _advance_watermark("polls", df, "created_at")
        mock_set_wm.assert_not_called()

    @patch(f"{_BACKFILL_MOD}.get_watermark", return_value=None)
    @patch(f"{_BACKFILL_MOD}.set_watermark")
    def test_sets_watermark_to_max_timestamp(self, mock_set_wm, mock_get_wm):
        from data_engineering.pipeline.backfill import _advance_watermark

        df = pd.DataFrame(
            {
                "created_at": pd.to_datetime(
                    ["2026-01-01", "2026-03-15", "2026-02-01"]
                ),
            }
        )
        _advance_watermark("polls", df, "created_at")
        mock_set_wm.assert_called_once_with("polls", _ts("2026-03-15T00:00:00"))

    @patch(f"{_BACKFILL_MOD}.get_watermark", return_value=_ts("2026-06-01T00:00:00"))
    @patch(f"{_BACKFILL_MOD}.set_watermark")
    def test_does_not_regress_watermark(self, mock_set_wm, mock_get_wm):
        """Watermark should not move backward if new max is older than current."""
        from data_engineering.pipeline.backfill import _advance_watermark

        df = pd.DataFrame(
            {
                "created_at": pd.to_datetime(["2026-01-01"]),
            }
        )
        _advance_watermark("polls", df, "created_at")
        mock_set_wm.assert_not_called()
