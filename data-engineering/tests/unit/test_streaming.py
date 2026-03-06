"""Tests for Kafka streaming retry behaviour."""

from __future__ import annotations

from unittest.mock import MagicMock, patch

from kafka.errors import NoBrokersAvailable


def test_run_streaming_retries_on_no_brokers():
    """run_streaming should retry when Kafka is unavailable instead of crashing."""
    call_count = 0

    def fake_create_consumer():
        nonlocal call_count
        call_count += 1
        if call_count < 3:
            raise NoBrokersAvailable()
        raise KeyboardInterrupt  # break the infinite loop on 3rd attempt

    with (
        patch(
            "data_engineering.pipeline.streaming.create_consumer",
            side_effect=fake_create_consumer,
        ),
        patch("data_engineering.pipeline.streaming.time.sleep") as mock_sleep,
    ):
        try:
            from data_engineering.pipeline.streaming import run_streaming

            run_streaming()
        except KeyboardInterrupt:
            pass

    assert call_count == 3
    assert mock_sleep.call_count == 2
    # Verify exponential backoff: 5s, then 10s
    mock_sleep.assert_any_call(5)
    mock_sleep.assert_any_call(10)


def test_run_streaming_reconnects_on_lost_connection():
    """run_streaming should reconnect if Kafka connection drops during consumption."""
    attempt = 0

    def fake_create_consumer():
        nonlocal attempt
        attempt += 1
        if attempt >= 2:
            raise KeyboardInterrupt
        return MagicMock()

    def fake_consume_loop(consumer):
        raise NoBrokersAvailable()

    with (
        patch(
            "data_engineering.pipeline.streaming.create_consumer",
            side_effect=fake_create_consumer,
        ),
        patch(
            "data_engineering.pipeline.streaming._consume_loop",
            side_effect=fake_consume_loop,
        ),
        patch("data_engineering.pipeline.streaming.time.sleep") as mock_sleep,
    ):
        try:
            from data_engineering.pipeline.streaming import run_streaming

            run_streaming()
        except KeyboardInterrupt:
            pass

    assert attempt == 2
    mock_sleep.assert_called_once_with(5)
