"""Unit tests for consumers module (DLQ writer + bucket health check)."""

from __future__ import annotations

import logging
from unittest.mock import MagicMock, patch

import botocore.exceptions
import pytest

from data_engineering.config import R2_DLQ_BUCKET
from data_engineering.ingestion.consumers import check_dlq_bucket, write_to_dlq


def _client_error(
    code: str = "NoSuchBucket", operation: str = "PutObject"
) -> botocore.exceptions.ClientError:
    return botocore.exceptions.ClientError(
        {"Error": {"Code": code, "Message": "error"}}, operation
    )


def _mock_s3() -> MagicMock:
    return MagicMock()


# ── write_to_dlq ──────────────────────────────────────────────────────────────


def test_write_to_dlq_calls_put_object() -> None:
    s3 = _mock_s3()
    with patch("data_engineering.ingestion.consumers.get_s3_client", return_value=s3):
        write_to_dlq({"event_type": "VOTE_CAST", "poll_id": 1})

    s3.put_object.assert_called_once()
    kw = s3.put_object.call_args.kwargs
    assert kw["Bucket"] == R2_DLQ_BUCKET
    assert kw["Key"].startswith("dlq/VOTE_CAST/")
    assert kw["Key"].endswith(".json")
    assert kw["ContentType"] == "application/json"


def test_write_to_dlq_body_is_valid_json() -> None:
    import json

    s3 = _mock_s3()
    event = {"event_type": "POLL_CREATED", "poll_id": 5}
    with patch("data_engineering.ingestion.consumers.get_s3_client", return_value=s3):
        write_to_dlq(event)

    body: bytes = s3.put_object.call_args.kwargs["Body"]
    parsed = json.loads(body.decode())
    assert parsed["poll_id"] == 5


def test_write_to_dlq_unknown_event_type() -> None:
    s3 = _mock_s3()
    with patch("data_engineering.ingestion.consumers.get_s3_client", return_value=s3):
        write_to_dlq("not-a-dict")

    key: str = s3.put_object.call_args.kwargs["Key"]
    assert key.startswith("dlq/unknown/")


def test_write_to_dlq_s3_error_does_not_raise() -> None:
    """A ClientError from R2 must not propagate — DLQ failure is non-fatal."""
    s3 = _mock_s3()
    s3.put_object.side_effect = _client_error()
    with patch("data_engineering.ingestion.consumers.get_s3_client", return_value=s3):
        write_to_dlq({"event_type": "VOTE_CAST"})  # must not raise


def test_write_to_dlq_s3_error_is_logged(caplog: pytest.LogCaptureFixture) -> None:
    s3 = _mock_s3()
    s3.put_object.side_effect = _client_error()
    with (
        patch("data_engineering.ingestion.consumers.get_s3_client", return_value=s3),
        caplog.at_level(logging.ERROR, logger="data_engineering.ingestion.consumers"),
    ):
        write_to_dlq({"event_type": "VOTE_CAST"})

    assert "Failed to upload event to R2 DLQ" in caplog.text


# ── check_dlq_bucket ──────────────────────────────────────────────────────────


def test_check_dlq_bucket_success_logs_info(caplog: pytest.LogCaptureFixture) -> None:
    s3 = _mock_s3()
    with (
        patch("data_engineering.ingestion.consumers.get_s3_client", return_value=s3),
        caplog.at_level(logging.INFO, logger="data_engineering.ingestion.consumers"),
    ):
        check_dlq_bucket()

    assert "reachable" in caplog.text


def test_check_dlq_bucket_error_logs_warning(caplog: pytest.LogCaptureFixture) -> None:
    s3 = _mock_s3()
    s3.head_bucket.side_effect = _client_error("404", "HeadBucket")
    with (
        patch("data_engineering.ingestion.consumers.get_s3_client", return_value=s3),
        caplog.at_level(logging.WARNING, logger="data_engineering.ingestion.consumers"),
    ):
        check_dlq_bucket()  # must not raise

    assert "not reachable" in caplog.text


def test_check_dlq_bucket_error_does_not_raise() -> None:
    s3 = _mock_s3()
    s3.head_bucket.side_effect = _client_error("AccessDenied", "HeadBucket")
    with patch("data_engineering.ingestion.consumers.get_s3_client", return_value=s3):
        check_dlq_bucket()  # must not raise
