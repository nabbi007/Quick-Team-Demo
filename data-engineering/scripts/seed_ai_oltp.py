"""Seed OLTP tables with AI-generated local-dev data via LangChain + Groq.

Flow:
    generate -> validate -> load -> verify

Usage (from data-engineering/ with .env configured):
    uv run python scripts/seed_ai_oltp.py --profile medium
    uv run python scripts/seed_ai_oltp.py --dry-run
"""

from __future__ import annotations

import argparse
import logging
import random
import re
import sys
import time
from pathlib import Path

# Allow running as a script without installing package first.
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from decouple import config  # noqa: E402

from data_engineering.config import get_engine  # noqa: E402
from data_engineering.seeding.ai_oltp import (  # noqa: E402
    SEED_PROFILES,
    SeedRunStats,
    build_chunk_prompt,
    build_model_name,
    build_seed_agent,
    count_chunk_entities,
    generate_local_seed_chunk,
    generate_validated_chunk,
    get_seed_profile,
    insert_seed_chunk,
    verify_oltp_state,
)
from data_engineering.utils.logging import configure_logging  # noqa: E402

logger = logging.getLogger(__name__)

_DEFAULT_FALLBACK_MODEL = "groq:llama-3.3-70b-versatile"
_DEFAULT_RATE_LIMIT_RETRIES = 6
_RATE_LIMIT_WAIT_RE = re.compile(
    r"try again in\s+([0-9]+(?:\.[0-9]+)?)s",
    flags=re.IGNORECASE,
)

_TOPIC_HINTS = [
    "campus events and student life",
    "software engineering team rituals",
    "sports and wellness clubs",
    "community volunteering programs",
    "travel and weekend activities",
    "food preferences in office settings",
    "music, movies, and entertainment",
    "productivity and remote work habits",
    "startup ideas and innovation",
    "local community policy opinions",
]


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="AI seed OLTP tables for local development"
    )
    parser.add_argument(
        "--mode",
        default="append",
        choices=["append"],
        help="Write mode (append-only supported).",
    )
    parser.add_argument(
        "--profile",
        default="medium",
        choices=sorted(SEED_PROFILES.keys()),
        help="Seed profile size.",
    )
    parser.add_argument(
        "--chunks",
        type=int,
        default=None,
        help="Override number of generation chunks for the selected profile.",
    )
    parser.add_argument(
        "--provider",
        default="groq",
        choices=["groq"],
        help="LLM provider.",
    )
    parser.add_argument(
        "--model",
        default=None,
        help="Model name (with or without 'groq:' prefix).",
    )
    parser.add_argument(
        "--fallback-model",
        default=None,
        help="Fallback model when structured tool output fails.",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=42,
        help="Random seed used for prompt variety hints.",
    )
    parser.add_argument(
        "--max-retries",
        type=int,
        default=2,
        help="Validation retries per chunk.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Generate and validate only; skip database writes.",
    )
    return parser.parse_args()


def _is_model_output_error(exc: Exception) -> bool:
    message = str(exc).lower()
    tokens = (
        "tool_use_failed",
        "failed_generation",
        "function-call",
        "tool call",
        "structured output",
    )
    return any(token in message for token in tokens)


def _is_rate_limit_error(exc: Exception) -> bool:
    message = str(exc).lower()
    tokens = (
        "rate limit",
        "rate_limit_exceeded",
        "429",
        "tokens per minute",
    )
    return any(token in message for token in tokens)


def _extract_retry_wait_seconds(exc: Exception, attempt: int) -> float:
    message = str(exc)
    match = _RATE_LIMIT_WAIT_RE.search(message)
    if match:
        parsed = float(match.group(1))
        return max(0.5, min(parsed + 0.25, 20.0))
    return min(2.0 * attempt, 20.0)


def _apply_dry_run_attempted_counts(
    stats: SeedRunStats, counts: dict[str, int]
) -> None:
    stats.users.attempted += counts["users"]
    stats.polls.attempted += counts["polls"]
    stats.poll_options.attempted += counts["poll_options"]
    stats.votes.attempted += counts["votes"]


def _log_summary(
    stats: SeedRunStats, verification: dict[str, int] | None = None
) -> None:
    logger.info(
        "[summary] chunks generated=%d validated=%d",
        stats.generated_chunks,
        stats.validated_chunks,
    )
    logger.info(
        "[summary] users attempted=%d inserted=%d skipped=%d",
        stats.users.attempted,
        stats.users.inserted,
        stats.users.skipped,
    )
    logger.info(
        "[summary] polls attempted=%d inserted=%d skipped=%d",
        stats.polls.attempted,
        stats.polls.inserted,
        stats.polls.skipped,
    )
    logger.info(
        "[summary] poll_options attempted=%d inserted=%d skipped=%d",
        stats.poll_options.attempted,
        stats.poll_options.inserted,
        stats.poll_options.skipped,
    )
    logger.info(
        "[summary] votes attempted=%d inserted=%d skipped=%d",
        stats.votes.attempted,
        stats.votes.inserted,
        stats.votes.skipped,
    )
    if verification is not None:
        logger.info(
            "[verify] users=%d polls=%d poll_options=%d votes=%d "
            "department=%d department_members=%d poll_invites=%d",
            verification["users"],
            verification["polls"],
            verification["poll_options"],
            verification["votes"],
            verification.get("department", 0),
            verification.get("department_members", 0),
            verification.get("poll_invites", 0),
        )
        logger.info(
            "[verify] duplicate_vote_pairs=%d orphan_poll_options=%d orphan_votes=%d",
            verification["duplicate_vote_pairs"],
            verification["orphan_poll_options"],
            verification["orphan_votes"],
        )
        logger.info(
            "[verify] vote_count_mismatch_rows=%d",
            verification["vote_count_mismatch_rows"],
        )


def main() -> None:
    configure_logging()
    args = _parse_args()

    api_key = config("GROQ_API_KEY", default="").strip()
    if not api_key:
        raise ValueError("GROQ_API_KEY is required for AI seeding.")

    profile = get_seed_profile(args.profile, chunks=args.chunks)
    model_name = build_model_name(args.model or config("GROQ_MODEL", default=None))
    fallback_model = build_model_name(
        args.fallback_model
        or config("GROQ_FALLBACK_MODEL", default=_DEFAULT_FALLBACK_MODEL)
    )
    random.seed(args.seed)

    logger.info(
        (
            "Starting AI OLTP seeding: mode=%s profile=%s chunks=%d "
            "provider=%s model=%s fallback_model=%s dry_run=%s"
        ),
        args.mode,
        args.profile,
        profile.chunks,
        args.provider,
        model_name,
        fallback_model,
        args.dry_run,
    )

    agent = build_seed_agent(api_key=api_key, model_name=model_name)
    fallback_agent = None
    active_model = model_name
    stats = SeedRunStats()
    engine = get_engine()

    for chunk_index in range(1, profile.chunks + 1):
        topic_hint = _TOPIC_HINTS[(chunk_index - 1) % len(_TOPIC_HINTS)]
        prompt = build_chunk_prompt(
            profile=profile,
            chunk_index=chunk_index,
            rng_seed=args.seed + chunk_index,
            topic_hint=topic_hint,
        )
        rate_limit_attempts = 0
        while True:
            try:
                chunk = generate_validated_chunk(
                    agent=agent,
                    prompt=prompt,
                    max_retries=args.max_retries,
                    target_votes=profile.votes_per_chunk,
                    rng_seed=args.seed + chunk_index,
                    log=logger,
                )
                break
            except Exception as exc:
                if _is_rate_limit_error(exc):
                    rate_limit_attempts += 1
                    if rate_limit_attempts > _DEFAULT_RATE_LIMIT_RETRIES:
                        logger.warning(
                            (
                                "[generate] chunk=%d exhausted rate-limit retries; "
                                "using local synthetic fallback chunk"
                            ),
                            chunk_index,
                        )
                        chunk = generate_local_seed_chunk(
                            profile=profile,
                            chunk_index=chunk_index,
                            rng_seed=args.seed + chunk_index,
                            topic_hint=topic_hint,
                        )
                        break
                    wait_seconds = _extract_retry_wait_seconds(
                        exc,
                        attempt=rate_limit_attempts,
                    )
                    logger.warning(
                        (
                            "[generate] chunk=%d rate-limited on model=%s; "
                            "sleeping %.2fs before retry (%d/%d)"
                        ),
                        chunk_index,
                        active_model,
                        wait_seconds,
                        rate_limit_attempts,
                        _DEFAULT_RATE_LIMIT_RETRIES,
                    )
                    time.sleep(wait_seconds)
                    continue

                if active_model == fallback_model or not _is_model_output_error(exc):
                    if _is_model_output_error(exc):
                        logger.warning(
                            (
                                "[generate] chunk=%d model output stayed unstable on "
                                "fallback model; using local synthetic fallback chunk"
                            ),
                            chunk_index,
                        )
                        chunk = generate_local_seed_chunk(
                            profile=profile,
                            chunk_index=chunk_index,
                            rng_seed=args.seed + chunk_index,
                            topic_hint=topic_hint,
                        )
                        break
                    raise
                logger.warning(
                    (
                        "[generate] chunk=%d switching model from %s to %s "
                        "after provider structured-output failure: %s"
                    ),
                    chunk_index,
                    active_model,
                    fallback_model,
                    exc,
                )
                if fallback_agent is None:
                    fallback_agent = build_seed_agent(
                        api_key=api_key,
                        model_name=fallback_model,
                    )
                agent = fallback_agent
                active_model = fallback_model

        stats.generated_chunks += 1
        stats.validated_chunks += 1
        counts = count_chunk_entities(chunk)
        logger.info(
            "[validate] chunk=%d users=%d polls=%d options=%d votes=%d",
            chunk_index,
            counts["users"],
            counts["polls"],
            counts["poll_options"],
            counts["votes"],
        )

        if args.dry_run:
            _apply_dry_run_attempted_counts(stats, counts)
            continue

        users_before = stats.users.inserted
        polls_before = stats.polls.inserted
        options_before = stats.poll_options.inserted
        votes_before = stats.votes.inserted

        with engine.begin() as conn:
            insert_seed_chunk(conn=conn, chunk=chunk, stats=stats)

        logger.info(
            "[load] chunk=%d inserted users=%d polls=%d options=%d votes=%d",
            chunk_index,
            stats.users.inserted - users_before,
            stats.polls.inserted - polls_before,
            stats.poll_options.inserted - options_before,
            stats.votes.inserted - votes_before,
        )

    if args.dry_run:
        _log_summary(stats)
        logger.info("Dry run complete. No database rows were written.")
        return

    verification = verify_oltp_state(engine)
    _log_summary(stats, verification=verification)

    if (
        verification["duplicate_vote_pairs"] > 0
        or verification["orphan_poll_options"] > 0
        or verification["orphan_votes"] > 0
        or verification["vote_count_mismatch_rows"] > 0
    ):
        raise RuntimeError("Post-seed verification failed integrity checks.")

    logger.info("AI OLTP seeding completed successfully.")


if __name__ == "__main__":
    main()
