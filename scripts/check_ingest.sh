#!/usr/bin/env bash
# Checks ingest counts, top-level customerId rejection, and Kafka customer keys.
# Usage (from anywhere, app and Kafka must be running): scripts/check_ingest.sh
set -euo pipefail
cd "$(dirname "$0")/.."

BASE_URL="${BASE_URL:-http://localhost:8080}"
EVENTS="$BASE_URL/api/v1/events"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

fail() { echo "FAIL  $*" >&2; exit 1; }

command -v curl >/dev/null || fail "curl is required."
command -v python3 >/dev/null || fail "Python 3 is required."
command -v docker >/dev/null || fail "Docker Compose is required."

if ! curl -fsS -o /dev/null "$BASE_URL/actuator/health" 2>/dev/null; then
  # The endpoint requires authentication; a 401 still confirms the app is reachable.
  code="$(curl -sS -o /dev/null -w '%{http_code}' "$BASE_URL/actuator/health" || true)"
  [ "$code" = "401" ] || fail "App is not reachable at $BASE_URL. Run scripts/start.sh first."
fi

docker compose exec -T kafka /opt/kafka/bin/kafka-broker-api-versions.sh \
  --bootstrap-server localhost:9092 >/dev/null 2>&1 \
  || fail "Kafka is not reachable through Docker Compose. Run scripts/start.sh first."

echo "Compiling TestTokenIssuer..."
./mvnw -q test-compile
./mvnw -q dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=target/test-classpath.txt
CP="target/test-classes:target/classes:$(cat target/test-classpath.txt)"
TOKEN="$(java -cp "$CP" com.sentinel.sentinel.support.TestTokenIssuer --customer acme --role ingest)"

check_counts() {
  local name="$1" file="$2" expected_accepted="$3" expected_quarantined="$4"
  local response="$TEMP_DIR/response.json"
  local status
  status="$(curl -sS -o "$response" -w '%{http_code}' -X POST "$EVENTS" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    --data-binary "@$file" || true)"
  [ "$status" = "200" ] || fail "$name expected HTTP 200, got $status: $(cat "$response")"
  python3 - "$response" "$expected_accepted" "$expected_quarantined" <<'PY' || fail "$name returned unexpected ingest counts: $(cat "$response")"
import json
import sys

with open(sys.argv[1], encoding="utf-8") as response_file:
    response = json.load(response_file)
accepted, quarantined = map(int, sys.argv[2:])
assert response["accepted"] == accepted, response
assert response["quarantined"] == quarantined, response
assert len(response["rejections"]) == quarantined, response
PY
  echo "PASS  $name: accepted $expected_accepted, quarantined $expected_quarantined"
}

# Capture each partition's end offset so only this run's records are checked.
docker compose exec -T kafka /opt/kafka/bin/kafka-get-offsets.sh \
  --bootstrap-server localhost:9092 --topic events --time -1 >"$TEMP_DIR/offsets"
[ -s "$TEMP_DIR/offsets" ] || fail "Could not read the events topic offsets."

check_counts "normal.json" sample-data/normal.json 8 0
check_counts "malformed.json" sample-data/malformed.json 1 5

python3 - sample-data/normal.json "$TEMP_DIR/spoofed.json" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source_file:
    payload = json.load(source_file)
payload["customerId"] = "globex"
with open(sys.argv[2], "w", encoding="utf-8") as output_file:
    json.dump(payload, output_file)
PY

status="$(curl -sS -o "$TEMP_DIR/spoofed-response.json" -w '%{http_code}' -X POST "$EVENTS" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  --data-binary "@$TEMP_DIR/spoofed.json" || true)"
[ "$status" = "400" ] || fail "Top-level customerId expected HTTP 400, got $status: $(cat "$TEMP_DIR/spoofed-response.json")"
echo "PASS  top-level customerId is rejected with HTTP 400"

: >"$TEMP_DIR/records"
while IFS=: read -r topic partition offset; do
  [ "$topic" = "events" ] || continue
  docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 \
    --topic events \
    --partition "$partition" \
    --offset "$offset" \
    --timeout-ms 1000 \
    --property print.key=true \
    --property key.separator='|' \
    >>"$TEMP_DIR/records" 2>/dev/null || true
done <"$TEMP_DIR/offsets"

python3 - "$TEMP_DIR/records" <<'PY' || fail "New events-topic records were missing, mis-keyed, or malformed."
import json
import sys

with open(sys.argv[1], encoding="utf-8") as records_file:
    records = [line.rstrip("\n") for line in records_file if line.strip()]

assert len(records) == 9, f"expected 9 new valid event records, got {len(records)}"
for record in records:
    key, separator, value = record.partition("|")
    assert separator, f"record has no key/value separator: {record}"
    assert key == "acme", f"expected customer key 'acme', got {key!r}"
    message = json.loads(value)
    assert message["customerId"] == "acme", message
PY
echo "PASS  all 9 new events-topic messages are keyed acme (none globex)"

echo "Ingest checks passed."
