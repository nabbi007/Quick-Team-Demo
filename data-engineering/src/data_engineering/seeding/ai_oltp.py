"""AI-driven local OLTP seeding helpers (Groq + LangChain structured output)."""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from datetime import UTC, datetime, timedelta
from typing import Any

from pydantic import BaseModel, Field
from sqlalchemy import text
from sqlalchemy.engine import Connection, Engine

logger = logging.getLogger(__name__)

# BCrypt hash for "password123" (same hash used by backend test data)
DEFAULT_PASSWORD_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
DEFAULT_GROQ_MODEL = "groq:llama-3.1-8b-instant"


class SeedUser(BaseModel):
    """Synthetic user record for OLTP users table."""

    ref: str = Field(description="Temporary reference for FK mapping in this chunk")
    full_name: str
    email: str
    role: str = "USER"
    created_at: datetime | None = None


class SeedPollOption(BaseModel):
    """Synthetic poll option linked to a poll ref."""

    ref: str = Field(description="Temporary reference for FK mapping in this chunk")
    option_text: str


class SeedPoll(BaseModel):
    """Synthetic poll with nested options."""

    ref: str = Field(description="Temporary reference for FK mapping in this chunk")
    title: str
    question: str
    description: str
    active: bool = True
    multi_select: bool = False
    creator_ref: str
    created_at: datetime | None = None
    expires_at: datetime | None = None
    options: list[SeedPollOption] = Field(default_factory=list)


class SeedVote(BaseModel):
    """Synthetic vote record that references user/poll/option refs."""

    user_ref: str
    poll_ref: str
    option_ref: str
    created_at: datetime | None = None


class SeedChunk(BaseModel):
    """One generated chunk of local OLTP seed data."""

    users: list[SeedUser] = Field(default_factory=list)
    polls: list[SeedPoll] = Field(default_factory=list)
    votes: list[SeedVote] = Field(default_factory=list)


@dataclass(frozen=True)
class SeedProfile:
    """Generation size profile."""

    chunks: int
    users_per_chunk: int
    polls_per_chunk: int
    votes_per_chunk: int


SEED_PROFILES: dict[str, SeedProfile] = {
    "small": SeedProfile(
        chunks=4, users_per_chunk=10, polls_per_chunk=4, votes_per_chunk=20
    ),
    "medium": SeedProfile(
        chunks=10,
        users_per_chunk=25,
        polls_per_chunk=8,
        votes_per_chunk=80,
    ),
    "large": SeedProfile(
        chunks=20,
        users_per_chunk=40,
        polls_per_chunk=10,
        votes_per_chunk=200,
    ),
}


@dataclass
class TableStats:
    """Inserted/skipped counters for one table."""

    attempted: int = 0
    inserted: int = 0
    skipped: int = 0


@dataclass
class SeedRunStats:
    """Top-level run stats used by script logging."""

    users: TableStats = field(default_factory=TableStats)
    polls: TableStats = field(default_factory=TableStats)
    poll_options: TableStats = field(default_factory=TableStats)
    votes: TableStats = field(default_factory=TableStats)
    generated_chunks: int = 0
    validated_chunks: int = 0


def _model_validate(model_cls: type[BaseModel], data: Any) -> BaseModel:
    if hasattr(model_cls, "model_validate"):
        return model_cls.model_validate(data)
    return model_cls.parse_obj(data)


def build_model_name(model_name: str | None) -> str:
    """Normalize model name to include Groq prefix."""
    if model_name is None or not model_name.strip():
        return DEFAULT_GROQ_MODEL
    clean = model_name.strip()
    if ":" in clean:
        return clean
    return f"groq:{clean}"


def get_seed_profile(profile_name: str, chunks: int | None = None) -> SeedProfile:
    """Return configured profile with optional chunk override."""
    key = profile_name.strip().lower()
    if key not in SEED_PROFILES:
        raise ValueError(f"Unsupported profile: {profile_name}")
    profile = SEED_PROFILES[key]
    if chunks is None:
        return profile
    if chunks < 1:
        raise ValueError("chunks must be >= 1")
    return SeedProfile(
        chunks=chunks,
        users_per_chunk=profile.users_per_chunk,
        polls_per_chunk=profile.polls_per_chunk,
        votes_per_chunk=profile.votes_per_chunk,
    )


def build_chunk_prompt(
    *,
    profile: SeedProfile,
    chunk_index: int,
    rng_seed: int,
    topic_hint: str,
) -> str:
    """Build generation prompt for one structured chunk."""
    return f"""
You are generating realistic local-development seed data for a polling application.

Output exactly one object that matches the structured schema for SeedChunk.
Do not output prose.

Chunk index: {chunk_index}
Seed hint: {rng_seed}
Theme hint: {topic_hint}

Target sizes for this chunk:
- users: {profile.users_per_chunk}
- polls: {profile.polls_per_chunk}
- votes: {profile.votes_per_chunk}

Rules:
- Use unique refs within this chunk:
  user refs like "c{chunk_index}_u_01", poll refs like "c{chunk_index}_p_01",
  option refs like "c{chunk_index}_o_01".
- Use realistic names and unique lowercase emails in domain "@quickpoll.local".
- Roles should mostly be USER, with at most 1 ADMIN in a chunk.
- Every poll must include:
  title, question, description, active, multi_select, creator_ref, options.
- Each poll must have 2-5 options and meaningful option text.
- votes must reference valid user_ref, poll_ref, option_ref.
- Keep only one vote per (poll_ref, user_ref) pair.
- Provide created_at timestamps across recent days and expires_at after created_at.
""".strip()


def build_seed_agent(*, api_key: str, model_name: str) -> Any:
    """Create LangChain agent configured for structured output."""
    if not api_key.strip():
        raise ValueError("GROQ_API_KEY is required.")

    from langchain.chat_models import init_chat_model

    model = init_chat_model(
        model_name,
        api_key=api_key,
        temperature=0.0,
        max_retries=0,
        streaming=False,
    )
    if hasattr(model, "with_structured_output"):
        return model.with_structured_output(SeedChunk)

    from langchain.agents import create_agent
    from langchain.agents.structured_output import ToolStrategy

    return create_agent(model=model, tools=[], response_format=ToolStrategy(SeedChunk))


def ensure_seed_chunk(payload: Any) -> SeedChunk:
    """Normalize structured response payload into SeedChunk model."""
    if isinstance(payload, SeedChunk):
        return payload
    return _model_validate(SeedChunk, payload)


def stream_seed_chunk(agent: Any, prompt: str) -> SeedChunk:
    """Request one chunk via invoke, supporting agent and model interfaces."""
    state = {"messages": [{"role": "user", "content": prompt}]}
    direct_error: Exception | None = None

    # Preferred path for model.with_structured_output(...)
    try:
        direct = agent.invoke(prompt)
        if isinstance(direct, dict) and "structured_response" in direct:
            return ensure_seed_chunk(direct["structured_response"])
        return ensure_seed_chunk(direct)
    except Exception as exc:  # noqa: BLE001
        direct_error = exc

    try:
        result = agent.invoke(state)
    except Exception:  # noqa: BLE001
        if direct_error is not None:
            raise direct_error from None
        raise

    if isinstance(result, dict) and "structured_response" in result:
        return ensure_seed_chunk(result["structured_response"])

    if direct_error is not None:
        raise direct_error from None

    return ensure_seed_chunk(result)


def generate_validated_chunk(
    *,
    agent: Any,
    prompt: str,
    max_retries: int,
    log: logging.Logger | None = None,
) -> SeedChunk:
    """Generate + validate one chunk with bounded retries."""
    last_error = ""
    for attempt in range(1, max_retries + 2):
        run_prompt = prompt
        if last_error:
            compact_error = _compact_error_message(last_error)
            run_prompt = (
                f"{prompt}\n\nPrevious output failed validation with error:\n"
                f"{compact_error}\n\nReturn corrected structured output only."
            )
        try:
            raw_chunk = stream_seed_chunk(agent, run_prompt)
            return validate_and_normalize_chunk(raw_chunk)
        except Exception as exc:  # noqa: BLE001
            last_error = str(exc)
            if attempt > max_retries:
                raise
            if log is not None:
                log.warning(
                    "[generate] Chunk validation failed on attempt %d/%d: %s",
                    attempt,
                    max_retries + 1,
                    last_error,
                )
    raise RuntimeError("Unreachable generation retry state")


def _compact_error_message(message: str, max_len: int = 280) -> str:
    if "failed_generation" in message:
        return "Provider function-call failed. Return a smaller schema-valid object."
    compact = " ".join(message.split())
    if len(compact) <= max_len:
        return compact
    return f"{compact[:max_len]}..."


def _normalize_datetime(value: datetime | str | None, fallback: datetime) -> datetime:
    if value is None:
        dt = fallback
    elif isinstance(value, datetime):
        dt = value
    elif isinstance(value, str):
        normalized = value.replace("Z", "+00:00")
        dt = datetime.fromisoformat(normalized)
    else:
        raise ValueError(f"Unsupported datetime value type: {type(value)}")

    if dt.tzinfo is not None:
        dt = dt.astimezone(UTC).replace(tzinfo=None)
    return dt


def validate_and_normalize_chunk(chunk: SeedChunk) -> SeedChunk:
    """Run schema-level and relational validations; dedupe conflicting votes."""
    now = datetime.utcnow()
    if not chunk.users:
        raise ValueError("Chunk must contain at least one user")
    if not chunk.polls:
        raise ValueError("Chunk must contain at least one poll")
    if not chunk.votes:
        raise ValueError("Chunk must contain at least one vote")

    user_ids: set[str] = set()
    emails: set[str] = set()
    users_by_ref: dict[str, SeedUser] = {}
    normalized_users: list[SeedUser] = []
    for user in chunk.users:
        ref = user.ref.strip()
        if not ref:
            raise ValueError("users.ref cannot be empty")
        if ref in user_ids:
            raise ValueError(f"Duplicate users.ref found: {ref}")
        user_ids.add(ref)

        email = user.email.strip().lower()
        if "@" not in email:
            raise ValueError(f"Invalid user email: {email}")
        if email in emails:
            raise ValueError(f"Duplicate user email in chunk: {email}")
        emails.add(email)

        role = user.role.strip().upper() if user.role else "USER"
        if not role:
            role = "USER"

        normalized = SeedUser(
            ref=ref,
            full_name=user.full_name.strip(),
            email=email,
            role=role,
            created_at=_normalize_datetime(user.created_at, now),
        )
        users_by_ref[ref] = normalized
        normalized_users.append(normalized)

    poll_ids: set[str] = set()
    option_ids: set[str] = set()
    option_poll_map: dict[str, str] = {}
    poll_by_ref: dict[str, SeedPoll] = {}
    normalized_polls: list[SeedPoll] = []
    for poll in chunk.polls:
        poll_ref = poll.ref.strip()
        if not poll_ref:
            raise ValueError("polls.ref cannot be empty")
        if poll_ref in poll_ids:
            raise ValueError(f"Duplicate polls.ref found: {poll_ref}")
        poll_ids.add(poll_ref)

        creator_ref = poll.creator_ref.strip()
        if creator_ref not in users_by_ref:
            raise ValueError(f"poll.creator_ref not found in users: {creator_ref}")

        if not poll.options:
            raise ValueError(f"poll {poll_ref} must contain at least one option")

        created_at = _normalize_datetime(
            poll.created_at,
            now - timedelta(days=3),
        )
        expires_at = _normalize_datetime(
            poll.expires_at,
            created_at + timedelta(days=30),
        )
        if expires_at <= created_at:
            expires_at = created_at + timedelta(days=30)

        normalized_options: list[SeedPollOption] = []
        for option in poll.options:
            option_ref = option.ref.strip()
            if not option_ref:
                raise ValueError("poll_options.ref cannot be empty")
            if option_ref in option_ids:
                raise ValueError(f"Duplicate poll_options.ref found: {option_ref}")
            option_ids.add(option_ref)
            option_poll_map[option_ref] = poll_ref
            normalized_options.append(
                SeedPollOption(ref=option_ref, option_text=option.option_text.strip())
            )

        normalized_poll = SeedPoll(
            ref=poll_ref,
            title=poll.title.strip(),
            question=poll.question.strip(),
            description=poll.description.strip(),
            active=bool(poll.active),
            multi_select=bool(poll.multi_select),
            creator_ref=creator_ref,
            created_at=created_at,
            expires_at=expires_at,
            options=normalized_options,
        )
        normalized_polls.append(normalized_poll)
        poll_by_ref[poll_ref] = normalized_poll

    seen_vote_pairs: set[tuple[str, str]] = set()
    normalized_votes: list[SeedVote] = []
    for vote in chunk.votes:
        user_ref = vote.user_ref.strip()
        poll_ref = vote.poll_ref.strip()
        option_ref = vote.option_ref.strip()
        if user_ref not in users_by_ref:
            raise ValueError(f"vote.user_ref not found in users: {user_ref}")
        if poll_ref not in poll_by_ref:
            raise ValueError(f"vote.poll_ref not found in polls: {poll_ref}")
        if option_ref not in option_poll_map:
            raise ValueError(f"vote.option_ref not found in poll options: {option_ref}")
        if option_poll_map[option_ref] != poll_ref:
            raise ValueError(
                "vote.option_ref="
                f"{option_ref} does not belong to vote.poll_ref={poll_ref}"
            )

        pair = (poll_ref, user_ref)
        if pair in seen_vote_pairs:
            continue
        seen_vote_pairs.add(pair)

        poll_created = poll_by_ref[poll_ref].created_at or now
        created_at = _normalize_datetime(
            vote.created_at, poll_created + timedelta(hours=1)
        )
        if created_at < poll_created:
            created_at = poll_created

        normalized_votes.append(
            SeedVote(
                user_ref=user_ref,
                poll_ref=poll_ref,
                option_ref=option_ref,
                created_at=created_at,
            )
        )

    if not normalized_votes:
        raise ValueError("No valid votes remain after validation")

    return SeedChunk(
        users=normalized_users,
        polls=normalized_polls,
        votes=normalized_votes,
    )


def count_chunk_entities(chunk: SeedChunk) -> dict[str, int]:
    """Return key entity counts for logging and dry-run summaries."""
    return {
        "users": len(chunk.users),
        "polls": len(chunk.polls),
        "poll_options": sum(len(poll.options) for poll in chunk.polls),
        "votes": len(chunk.votes),
    }


def insert_seed_chunk(
    *,
    conn: Connection,
    chunk: SeedChunk,
    stats: SeedRunStats,
    password_hash: str = DEFAULT_PASSWORD_HASH,
) -> None:
    """Insert one normalized chunk using append-only semantics."""
    user_ids_by_ref: dict[str, int] = {}
    poll_ids_by_ref: dict[str, int] = {}
    option_ids_by_ref: dict[str, int] = {}

    for user in chunk.users:
        stats.users.attempted += 1
        existing_id = conn.execute(
            text("SELECT id FROM users WHERE email = :email"),
            {"email": user.email},
        ).scalar()
        if existing_id is not None:
            stats.users.skipped += 1
            user_ids_by_ref[user.ref] = int(existing_id)
            continue

        inserted_id = conn.execute(
            text(
                """
                INSERT INTO users (full_name, email, password, role, created_at)
                VALUES (:full_name, :email, :password, :role, :created_at)
                RETURNING id
                """
            ),
            {
                "full_name": user.full_name,
                "email": user.email,
                "password": password_hash,
                "role": user.role,
                "created_at": user.created_at,
            },
        ).scalar_one()

        stats.users.inserted += 1
        user_ids_by_ref[user.ref] = int(inserted_id)

    for poll in chunk.polls:
        stats.polls.attempted += 1
        creator_id = user_ids_by_ref[poll.creator_ref]
        poll_id = conn.execute(
            text(
                """
                INSERT INTO polls (
                    title, question, description, active, multi_select,
                    creator_id, created_at, expires_at
                )
                VALUES (
                    :title, :question, :description, :active, :multi_select,
                    :creator_id, :created_at, :expires_at
                )
                RETURNING id
                """
            ),
            {
                "title": poll.title,
                "question": poll.question,
                "description": poll.description,
                "active": poll.active,
                "multi_select": poll.multi_select,
                "creator_id": creator_id,
                "created_at": poll.created_at,
                "expires_at": poll.expires_at,
            },
        ).scalar_one()
        poll_ids_by_ref[poll.ref] = int(poll_id)
        stats.polls.inserted += 1

        for option in poll.options:
            stats.poll_options.attempted += 1
            option_id = conn.execute(
                text(
                    """
                    INSERT INTO poll_options (option_text, vote_count, poll_id)
                    VALUES (:option_text, 0, :poll_id)
                    RETURNING id
                    """
                ),
                {"option_text": option.option_text, "poll_id": poll_id},
            ).scalar_one()
            option_ids_by_ref[option.ref] = int(option_id)
            stats.poll_options.inserted += 1

    for vote in chunk.votes:
        stats.votes.attempted += 1
        poll_id = poll_ids_by_ref[vote.poll_ref]
        user_id = user_ids_by_ref[vote.user_ref]
        option_id = option_ids_by_ref[vote.option_ref]

        existing_vote_id = conn.execute(
            text(
                """
                SELECT id
                FROM votes
                WHERE poll_id = :poll_id AND user_id = :user_id
                LIMIT 1
                """
            ),
            {"poll_id": poll_id, "user_id": user_id},
        ).scalar()
        if existing_vote_id is not None:
            stats.votes.skipped += 1
            continue

        conn.execute(
            text(
                """
                INSERT INTO votes (user_id, poll_id, option_id, created_at)
                VALUES (:user_id, :poll_id, :option_id, :created_at)
                RETURNING id
                """
            ),
            {
                "user_id": user_id,
                "poll_id": poll_id,
                "option_id": option_id,
                "created_at": vote.created_at,
            },
        ).scalar_one()
        stats.votes.inserted += 1
        conn.execute(
            text("UPDATE poll_options SET vote_count = vote_count + 1 WHERE id = :id"),
            {"id": option_id},
        )


def verify_oltp_state(engine: Engine) -> dict[str, int]:
    """Return post-seed quality checks and key table counts."""
    with engine.connect() as conn:
        users_count = int(
            conn.execute(text("SELECT COUNT(*) FROM users")).scalar() or 0
        )
        polls_count = int(
            conn.execute(text("SELECT COUNT(*) FROM polls")).scalar() or 0
        )
        options_count = int(
            conn.execute(text("SELECT COUNT(*) FROM poll_options")).scalar() or 0
        )
        votes_count = int(
            conn.execute(text("SELECT COUNT(*) FROM votes")).scalar() or 0
        )

        duplicate_vote_pairs = int(
            conn.execute(
                text(
                    """
                    SELECT COUNT(*) FROM (
                        SELECT poll_id, user_id, COUNT(*) AS dup_count
                        FROM votes
                        GROUP BY poll_id, user_id
                        HAVING COUNT(*) > 1
                    ) AS dupes
                    """
                )
            ).scalar()
            or 0
        )

        orphan_poll_options = int(
            conn.execute(
                text(
                    """
                    SELECT COUNT(*)
                    FROM poll_options po
                    LEFT JOIN polls p ON p.id = po.poll_id
                    WHERE p.id IS NULL
                    """
                )
            ).scalar()
            or 0
        )

        orphan_votes = int(
            conn.execute(
                text(
                    """
                    SELECT COUNT(*)
                    FROM votes v
                    LEFT JOIN users u ON u.id = v.user_id
                    LEFT JOIN polls p ON p.id = v.poll_id
                    LEFT JOIN poll_options po ON po.id = v.option_id
                    WHERE u.id IS NULL OR p.id IS NULL OR po.id IS NULL
                    """
                )
            ).scalar()
            or 0
        )

        vote_count_mismatch_rows = int(
            conn.execute(
                text(
                    """
                    SELECT COUNT(*)
                    FROM poll_options po
                    LEFT JOIN (
                        SELECT option_id, COUNT(*) AS actual_votes
                        FROM votes
                        GROUP BY option_id
                    ) v ON v.option_id = po.id
                    WHERE po.vote_count <> COALESCE(v.actual_votes, 0)
                    """
                )
            ).scalar()
            or 0
        )

    return {
        "users": users_count,
        "polls": polls_count,
        "poll_options": options_count,
        "votes": votes_count,
        "duplicate_vote_pairs": duplicate_vote_pairs,
        "orphan_poll_options": orphan_poll_options,
        "orphan_votes": orphan_votes,
        "vote_count_mismatch_rows": vote_count_mismatch_rows,
    }
