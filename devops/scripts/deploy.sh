#!/bin/bash
set -e
echo "Deploying QuickPoll..."
docker-compose down
docker-compose build --no-cache
docker-compose up -d
sleep 10
curl -f http://localhost:8080/api-docs || echo "Backend health check failed!"
echo "Deployment complete!"
