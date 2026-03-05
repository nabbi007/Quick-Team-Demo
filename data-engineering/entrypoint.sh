#!/bin/bash
# entrypoint.sh — Wait for Postgres and Kafka to be ready, then start the pipeline.
# Two-layer protection: docker-compose healthchecks hold the container until
# dependencies are healthy; this script does a final in-container readiness check.
set -e

echo "Waiting for PostgreSQL at ${DB_HOST}:${DB_PORT}..."
until pg_isready -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -q; do
    sleep 2
done
echo "PostgreSQL is ready."

echo "Waiting for Kafka at ${KAFKA_BOOTSTRAP_SERVERS}..."
KAFKA_HOST=$(echo "${KAFKA_BOOTSTRAP_SERVERS}" | cut -d: -f1)
KAFKA_PORT=$(echo "${KAFKA_BOOTSTRAP_SERVERS}" | cut -d: -f2)
until nc -z "${KAFKA_HOST}" "${KAFKA_PORT}"; do
    sleep 2
done
echo "Kafka is ready."

echo "Starting QuickPoll analytics pipeline..."
exec python main.py
