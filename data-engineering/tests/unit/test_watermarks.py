"""Tests for watermark read/write helpers."""

from __future__ import annotations

from datetime import datetime
from unittest.mock import MagicMock, patch

_WRITERS_MOD = "data_engineering.loading.writers"


@patch(f"{_WRITERS_MOD}.get_engine")
def test_get_watermark_returns_none_for_missing_entity(mock_engine):
    """get_watermark should return None when no row exists."""
    mock_conn = MagicMock()
    mock_conn.execute.return_value.fetchone.return_value = None
    mock_engine.return_value.connect.return_value.__enter__ = MagicMock(
        return_value=mock_conn
    )
    mock_engine.return_value.connect.return_value.__exit__ = MagicMock(
        return_value=False
    )

    from data_engineering.loading.writers import get_watermark

    result = get_watermark("polls")
    assert result is None


@patch(f"{_WRITERS_MOD}.get_engine")
def test_get_watermark_returns_datetime_when_row_exists(mock_engine):
    """get_watermark should return the high_watermark datetime from the row."""
    ts = datetime(2026, 1, 15, 12, 0, 0)
    mock_row = MagicMock()
    mock_row.high_watermark = ts
    mock_conn = MagicMock()
    mock_conn.execute.return_value.fetchone.return_value = mock_row
    mock_engine.return_value.connect.return_value.__enter__ = MagicMock(
        return_value=mock_conn
    )
    mock_engine.return_value.connect.return_value.__exit__ = MagicMock(
        return_value=False
    )

    from data_engineering.loading.writers import get_watermark

    result = get_watermark("polls")
    assert result == ts


@patch(f"{_WRITERS_MOD}.get_engine")
def test_set_watermark_executes_upsert(mock_engine):
    """set_watermark should execute an upsert statement."""
    mock_conn = MagicMock()
    mock_engine.return_value.begin.return_value.__enter__ = MagicMock(
        return_value=mock_conn
    )
    mock_engine.return_value.begin.return_value.__exit__ = MagicMock(return_value=False)

    from data_engineering.loading.writers import set_watermark

    set_watermark("votes", datetime(2026, 2, 1, 10, 0, 0))

    mock_conn.execute.assert_called_once()
