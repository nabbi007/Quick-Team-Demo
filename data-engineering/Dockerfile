# syntax=docker/dockerfile:1
# data-engineering with uv - see ai-reference-docs/Using-uv-in-Docker
FROM python:3.11-slim-bookworm AS builder

COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

WORKDIR /app

# Install dependencies first (cache layer)
RUN --mount=type=cache,target=/root/.cache/uv \
    --mount=type=bind,source=uv.lock,target=uv.lock \
    --mount=type=bind,source=pyproject.toml,target=pyproject.toml \
    uv sync --locked --no-install-project --no-dev

# Copy project and sync
COPY . /app
RUN --mount=type=cache,target=/root/.cache/uv \
    uv sync --locked --no-dev

# Production stage
FROM python:3.11-slim-bookworm

# postgresql-client: provides pg_isready for the Postgres readiness check in entrypoint.sh
# netcat-openbsd:    provides nc for the Kafka port readiness check in entrypoint.sh
RUN apt-get update && apt-get install -y --no-install-recommends \
        sudo \
        postgresql-client \
        netcat-openbsd \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r appuser && useradd -r -g appuser -m -s /bin/bash appuser \
    && echo "appuser ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers.d/appuser \
    && chmod 0440 /etc/sudoers.d/appuser

COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/
COPY --from=builder /app /app

RUN chown -R appuser:appuser /app \
    && chmod +x /app/entrypoint.sh

WORKDIR /app

ENV PATH="/app/.venv/bin:$PATH"

USER appuser

ENTRYPOINT ["/app/entrypoint.sh"]
