#!/usr/bin/env bash
# Starts sentinel locally: checks prerequisites, then runs Postgres, Kafka and the app
# with Docker Compose and waits until the app answers on port 8080.
# Usage (from the project root):  scripts/start.sh
set -euo pipefail

# Always run from the project root, wherever the script is called from.
cd "$(dirname "$0")/.."

say()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m!!\033[0m  %s\n' "$*"; }
fail() { printf '\033[1;31mxx\033[0m  %s\n' "$*"; exit 1; }

# 1. Docker must be running.
say "Checking Docker..."
docker info >/dev/null 2>&1 || fail "Docker is not running. Open Docker Desktop, wait until it says running, then retry."

# 2. .env holds the database password and is never committed.
if [ ! -f .env ]; then
  [ -f .env.example ] || fail ".env and .env.example are both missing."
  cp .env.example .env
  warn "Created .env from .env.example. Change POSTGRES_PASSWORD in .env for anything beyond local testing."
fi

# 3. JWT keys: the app needs keys/public.pem; TestTokenIssuer needs keys/private.pem.
if [ ! -f keys/public.pem ] || [ ! -f keys/private.pem ]; then
  say "No JWT keys found, generating them..."
  scripts/gen_keys.sh
fi

# 4. Build the app image and start all services in the background.
say "Starting Postgres, Kafka and the app (this can take a minute on the first run)..."
docker compose up -d --build

# 5. Wait for the app. /actuator/health needs a token, so 401 already means "up and protecting".
say "Waiting for the app on http://localhost:8080 ..."
for _ in $(seq 1 60); do
  code=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/actuator/health || true)
  if [ "$code" = "401" ] || [ "$code" = "200" ]; then
    say "Sentinel is up (health endpoint answered $code)."
    docker compose ps
    echo
    echo "Get a token:   ./mvnw -q exec:java -Dexec.classpathScope=test \\"
    echo "                 -Dexec.mainClass=com.sentinel.sentinel.support.TestTokenIssuer \\"
    echo "                 -Dexec.args=\"--customer acme --role ingest\""
    echo "App logs:      docker compose logs -f app"
    echo "Stop:          docker compose down"
    exit 0
  fi
  sleep 2
done

warn "The app did not answer within 2 minutes. Last 30 log lines:"
docker compose logs --tail 30 app
exit 1
