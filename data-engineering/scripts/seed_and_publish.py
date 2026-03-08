"""Seed OLTP tables with random data AND publish matching Kafka events.

Simulates the backend's dual-write behaviour (DB + Kafka) so the full
analytics pipeline can be tested end-to-end without the real backend.

Usage (from data-engineering/ with .env configured):
    uv run python scripts/seed_and_publish.py
    uv run python scripts/seed_and_publish.py --polls 10 --votes-per-poll 20
    uv run python scripts/seed_and_publish.py --stream-delay 2   # trickle mode
    uv run python scripts/seed_and_publish.py --no-kafka          # OLTP only
"""

from __future__ import annotations

import argparse
import json
import logging
import random
import sys
import time
from datetime import UTC, datetime, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from kafka import KafkaProducer  # noqa: E402
from kafka.errors import NoBrokersAvailable  # noqa: E402
from sqlalchemy import text  # noqa: E402

from data_engineering.config import (  # noqa: E402
    KAFKA_BOOTSTRAP_SERVERS,
    KAFKA_TOPIC_POLL_EVENTS,
    KAFKA_TOPIC_VOTE_EVENTS,
    get_engine,
)
from data_engineering.utils.logging import configure_logging  # noqa: E402

logger = logging.getLogger(__name__)

# ── Random data pools ────────────────────────────────────────────────────────

_FIRST_NAMES = [
    "Ama",
    "Kofi",
    "Akua",
    "Kwame",
    "Yaa",
    "Kojo",
    "Abena",
    "Kwesi",
    "Efua",
    "Kweku",
    "Adwoa",
    "Yaw",
    "Afua",
    "Fiifi",
    "Esi",
    "Nana",
]
_LAST_NAMES = [
    "Mensah",
    "Asante",
    "Ofori",
    "Boateng",
    "Adjei",
    "Antwi",
    "Osei",
    "Agyemang",
    "Appiah",
    "Badu",
    "Darko",
    "Kumi",
    "Owusu",
    "Tetteh",
]
_POLL_TEMPLATES = [
    (
        "Best programming language for {topic}?",
        ["Python", "JavaScript", "Go", "Rust", "Java"],
    ),
    (
        "Favourite {topic} tool?",
        ["VS Code", "IntelliJ", "Neovim", "Sublime", "Cursor"],
    ),
    (
        "Preferred {topic} approach?",
        ["Agile", "Waterfall", "Kanban", "Scrum", "XP"],
    ),
    (
        "What should we do for {topic}?",
        ["Option A", "Option B", "Option C", "Option D"],
    ),
    ("Rate the {topic} experience", ["Excellent", "Good", "Average", "Poor"]),
    (
        "When should we schedule {topic}?",
        ["Monday", "Wednesday", "Friday", "Weekend"],
    ),
    (
        "Which {topic} provider?",
        ["AWS", "GCP", "Azure", "DigitalOcean", "Hetzner"],
    ),
    (
        "Team preference for {topic}?",
        ["Slack", "Teams", "Discord", "Email", "Zoom"],
    ),
]
_TOPICS = [
    "backend dev",
    "data pipeline",
    "sprint planning",
    "code review",
    "team lunch",
    "hackathon",
    "onboarding",
    "deployment",
    "monitoring",
    "testing",
    "documentation",
    "CI/CD",
    "database",
    "caching",
]
_PW_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"


# ── Data generation ──────────────────────────────────────────────────────────


def _iso_utc_now() -> str:
    """Return an ISO-8601 UTC timestamp with Z suffix."""
    return datetime.now(UTC).isoformat().replace("+00:00", "Z")


def generate_users(count: int, start_id: int) -> list[dict]:
    """Generate random user dicts starting at *start_id*."""
    users = []
    for i in range(count):
        uid = start_id + i
        first = random.choice(_FIRST_NAMES)
        last = random.choice(_LAST_NAMES)
        users.append(
            {
                "id": uid,
                "email": f"{first.lower()}.{last.lower()}.{uid}@quickpoll.test",
                "full_name": f"{first} {last}",
            }
        )
    return users


def generate_polls(count: int, start_id: int, user_ids: list[int]) -> list[dict]:
    """Generate random poll dicts with 3-5 options each."""
    polls = []
    for i in range(count):
        pid = start_id + i
        template, base_options = random.choice(_POLL_TEMPLATES)
        topic = random.choice(_TOPICS)
        question = template.format(topic=topic)
        title = f"Poll about {topic}"
        num_options = random.randint(3, min(5, len(base_options)))
        options = random.sample(base_options, num_options)
        created_at = _iso_utc_now()
        polls.append(
            {
                "id": pid,
                "title": title,
                "question": question,
                "description": f"A poll about {topic}",
                "creator_id": random.choice(user_ids),
                "multi_select": bool(random.choice([False, False, True])),
                "active": True,
                "options": [{"text": opt} for opt in options],
                "created_at": created_at,
                "expires_at": (datetime.now(UTC) + timedelta(days=30))
                .isoformat()
                .replace("+00:00", "Z"),
            }
        )
    return polls


def generate_votes(
    polls: list[dict],
    votes_per_poll: int,
    start_vote_id: int,
    start_option_id: int,
    user_ids: list[int],
) -> tuple[list[dict], list[dict]]:
    """Generate option records and vote records for the given polls.

    Returns (all_options, all_votes). Option IDs are assigned sequentially
    from *start_option_id*; vote IDs from *start_vote_id*.
    """
    all_options: list[dict] = []
    all_votes: list[dict] = []
    opt_id = start_option_id
    vote_id = start_vote_id

    for poll in polls:
        option_ids_for_poll: list[int] = []
        for opt in poll["options"]:
            all_options.append(
                {"id": opt_id, "poll_id": poll["id"], "option_text": opt["text"]}
            )
            option_ids_for_poll.append(opt_id)
            opt_id += 1

        # Each user votes at most once per poll
        voters = random.sample(user_ids, min(votes_per_poll, len(user_ids)))
        for uid in voters:
            chosen_option = random.choice(option_ids_for_poll)
            all_votes.append(
                {
                    "id": vote_id,
                    "poll_id": poll["id"],
                    "option_id": chosen_option,
                    "user_id": uid,
                    "voted_at": _iso_utc_now(),
                }
            )
            vote_id += 1

    return all_options, all_votes


# ── OLTP writers ─────────────────────────────────────────────────────────────


def insert_users(conn, users: list[dict]) -> int:
    """Insert users into OLTP; returns number of rows actually inserted."""
    if not users:
        return 0
    result = conn.execute(
        text("""
            INSERT INTO users (id, email, password, full_name, role, created_at)
            VALUES (:id, :email, :pw, :full_name, 'USER', NOW())
            ON CONFLICT (id) DO NOTHING
        """),
        [{"pw": _PW_HASH, **u} for u in users],
    )
    return result.rowcount


def insert_polls(conn, polls: list[dict]) -> int:
    if not polls:
        return 0
    result = conn.execute(
        text("""
            INSERT INTO polls (id, title, question, description, creator_id,
                               multi_select, expires_at, active, created_at)
            VALUES (:id, :title, :question, :description, :creator_id, :multi_select,
                    :expires_at ::timestamptz, :active,
                    :created_at ::timestamptz)
            ON CONFLICT (id) DO NOTHING
        """),
        [
            {
                "id": p["id"],
                "title": p["title"],
                "question": p["question"],
                "description": p["description"],
                "creator_id": p["creator_id"],
                "multi_select": p["multi_select"],
                "active": p["active"],
                "expires_at": p["expires_at"],
                "created_at": p["created_at"],
            }
            for p in polls
        ],
    )
    return result.rowcount


def insert_options(conn, options: list[dict]) -> int:
    if not options:
        return 0
    result = conn.execute(
        text("""
            INSERT INTO poll_options (id, poll_id, option_text, vote_count)
            VALUES (:id, :poll_id, :option_text, 0)
            ON CONFLICT (id) DO NOTHING
        """),
        options,
    )
    return result.rowcount


def insert_votes(conn, votes: list[dict]) -> int:
    if not votes:
        return 0
    result = conn.execute(
        text("""
            INSERT INTO votes (id, poll_id, option_id, user_id, created_at)
            VALUES (:id, :poll_id, :option_id, :user_id, :voted_at ::timestamptz)
            ON CONFLICT DO NOTHING
        """),
        votes,
    )
    return result.rowcount


def update_vote_counts(conn, options: list[dict], votes: list[dict]) -> None:
    """Reconcile poll_options.vote_count with actual vote rows."""
    option_ids = {o["id"] for o in options}
    if not option_ids:
        return
    conn.execute(
        text("""
            UPDATE poll_options po
            SET vote_count = sub.cnt
            FROM (
                SELECT option_id, COUNT(*) AS cnt
                FROM votes
                WHERE option_id = ANY(:ids)
                GROUP BY option_id
            ) sub
            WHERE po.id = sub.option_id
        """),
        {"ids": list(option_ids)},
    )


# ── Kafka publishers ────────────────────────────────────────────────────────


def make_producer() -> KafkaProducer:
    return KafkaProducer(
        bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
        value_serializer=lambda v: json.dumps(v).encode("utf-8"),
    )


def publish_poll_created(producer: KafkaProducer, poll: dict) -> None:
    # Required keys for poll_events: event_type, poll_id, creator_id, occurred_at.
    # Additional poll metadata is included as optional enrichment.
    event = {
        "event_type": "POLL_CREATED",
        "poll_id": poll["id"],
        "title": poll["title"],
        "creator_id": poll["creator_id"],
        "occurred_at": _iso_utc_now(),
        "question": poll["question"],
        "multi_select": poll["multi_select"],
        "expires_at": poll["expires_at"],
        "active": poll["active"],
        "created_at": poll["created_at"],
    }
    producer.send(KAFKA_TOPIC_POLL_EVENTS, value=event)


def publish_vote_cast(producer: KafkaProducer, vote: dict) -> None:
    event = {
        "event_type": "VOTE_CAST",
        "vote_id": vote["id"],
        "poll_id": vote["poll_id"],
        "option_id": vote["option_id"],
        "user_id": vote["user_id"],
        "voted_at": vote["voted_at"],
    }
    producer.send(KAFKA_TOPIC_VOTE_EVENTS, value=event)


# ── ID helpers ───────────────────────────────────────────────────────────────


def _next_id(conn, table: str, floor: int = 100) -> int:
    """Return MAX(id)+1 from *table*, with a minimum of *floor*."""
    row = conn.execute(text(f"SELECT COALESCE(MAX(id), 0) FROM {table}")).scalar()
    return max(int(row) + 1, floor)


# ── CLI ──────────────────────────────────────────────────────────────────────


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Seed OLTP + publish Kafka events for pipeline testing"
    )
    parser.add_argument(
        "--polls", type=int, default=5, help="Number of polls to create"
    )
    parser.add_argument(
        "--votes-per-poll", type=int, default=10, help="Max votes per poll"
    )
    parser.add_argument("--users", type=int, default=8, help="Number of new users")
    parser.add_argument(
        "--stream-delay",
        type=float,
        default=0,
        help="Seconds between vote events (0 = batch mode)",
    )
    parser.add_argument(
        "--kafka",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="Publish events to Kafka (default: yes)",
    )
    parser.add_argument("--seed", type=int, default=None, help="Random seed")
    return parser.parse_args()


def _validate_args(args: argparse.Namespace) -> None:
    """Exit with a clear message if CLI arguments are invalid."""
    if args.users < 1:
        raise SystemExit("error: --users must be >= 1")
    if args.polls < 0:
        raise SystemExit("error: --polls must be >= 0")
    if args.votes_per_poll < 0:
        raise SystemExit("error: --votes-per-poll must be >= 0")
    if args.stream_delay < 0:
        raise SystemExit("error: --stream-delay must be >= 0")


def main() -> None:
    configure_logging()
    args = _parse_args()
    _validate_args(args)

    if args.seed is not None:
        random.seed(args.seed)

    engine = get_engine()

    # Determine starting IDs to avoid collisions
    with engine.connect() as conn:
        user_start = _next_id(conn, "users", floor=100)
        poll_start = _next_id(conn, "polls", floor=1000)
        option_start = _next_id(conn, "poll_options", floor=5000)
        vote_start = _next_id(conn, "votes", floor=10000)

    # Generate data
    users = generate_users(args.users, user_start)
    user_ids = [u["id"] for u in users]
    polls = generate_polls(args.polls, poll_start, user_ids)
    options, votes = generate_votes(
        polls, args.votes_per_poll, vote_start, option_start, user_ids
    )

    logger.info(
        "Generated: %d users, %d polls, %d options, %d votes",
        len(users),
        len(polls),
        len(options),
        len(votes),
    )

    # Insert into OLTP
    with engine.begin() as conn:
        u = insert_users(conn, users)
        p = insert_polls(conn, polls)
        o = insert_options(conn, options)
        v = insert_votes(conn, votes)
        update_vote_counts(conn, options, votes)

    logger.info("OLTP inserts: %d users, %d polls, %d options, %d votes", u, p, o, v)

    # Warn if some rows were skipped (ID collisions from concurrent runs)
    generated_total = len(users) + len(polls) + len(options) + len(votes)
    inserted_total = u + p + o + v
    if inserted_total < generated_total:
        logger.warning(
            "Some rows were skipped (ON CONFLICT): generated=%d inserted=%d. "
            "Kafka events will still be published for all generated data.",
            generated_total,
            inserted_total,
        )

    # Publish to Kafka
    if not args.kafka:
        logger.info("Kafka publishing disabled (--no-kafka). Done.")
        return

    try:
        producer = make_producer()
    except NoBrokersAvailable:
        logger.error(
            "Cannot connect to Kafka at %s. OLTP data was inserted but "
            "Kafka events were NOT published. Start Kafka and re-run, or "
            "use --no-kafka.",
            KAFKA_BOOTSTRAP_SERVERS,
        )
        return

    logger.info("Connected to Kafka at %s", KAFKA_BOOTSTRAP_SERVERS)

    for poll in polls:
        publish_poll_created(producer, poll)
        logger.info("Published POLL_CREATED: poll_id=%d", poll["id"])

    stream_delay = args.stream_delay
    for i, vote in enumerate(votes, 1):
        publish_vote_cast(producer, vote)
        if stream_delay > 0:
            logger.info(
                "Published VOTE_CAST %d/%d: poll_id=%d user_id=%d (next in %.1fs)",
                i,
                len(votes),
                vote["poll_id"],
                vote["user_id"],
                stream_delay,
            )
            time.sleep(stream_delay)
        elif i % 10 == 0 or i == len(votes):
            logger.info("Published VOTE_CAST %d/%d", i, len(votes))

    producer.flush()
    producer.close()
    logger.info(
        "Done. Published %d POLL_CREATED + %d VOTE_CAST events.",
        len(polls),
        len(votes),
    )


if __name__ == "__main__":
    main()
