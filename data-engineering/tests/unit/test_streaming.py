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


def test_periodic_backfill_triggers_after_interval():
    """Periodic backfill should run when BACKFILL_INTERVAL_MINUTES elapsed."""
    mock_consumer = MagicMock()
    call_count = 0

    def fake_poll(timeout_ms=5000):
        nonlocal call_count
        call_count += 1
        if call_count > 2:
            raise KeyboardInterrupt
        return {}

    mock_consumer.poll = fake_poll

    # Simulate enough time elapsed (> 30 min = 1800s)
    monotonic_values = [0.0, 1801.0, 1801.0, 1801.0]
    monotonic_iter = iter(monotonic_values)

    with (
        patch(
            "data_engineering.pipeline.streaming.time.monotonic",
            side_effect=lambda: next(monotonic_iter),
        ),
        patch("data_engineering.pipeline.streaming.run_backfill") as mock_backfill,
    ):
        try:
            from data_engineering.pipeline.streaming import _consume_loop

            _consume_loop(mock_consumer)
        except (KeyboardInterrupt, StopIteration):
            pass

    mock_backfill.assert_called_once()


def test_periodic_backfill_does_not_trigger_before_interval():
    """Periodic backfill should NOT run before interval elapsed."""
    mock_consumer = MagicMock()
    call_count = 0

    def fake_poll(timeout_ms=5000):
        nonlocal call_count
        call_count += 1
        if call_count > 1:
            raise KeyboardInterrupt
        return {}

    mock_consumer.poll = fake_poll

    # Simulate short elapsed time (< 30 min)
    monotonic_values = [0.0, 60.0, 60.0]
    monotonic_iter = iter(monotonic_values)

    with (
        patch(
            "data_engineering.pipeline.streaming.time.monotonic",
            side_effect=lambda: next(monotonic_iter),
        ),
        patch("data_engineering.pipeline.streaming.run_backfill") as mock_backfill,
    ):
        try:
            from data_engineering.pipeline.streaming import _consume_loop

            _consume_loop(mock_consumer)
        except (KeyboardInterrupt, StopIteration):
            pass

    mock_backfill.assert_not_called()
