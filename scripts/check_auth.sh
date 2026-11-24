#!/usr/bin/env bash
# Checks sentinel's security rules against the running app (steps 8f and 9b).
# For each case it mints a token with TestTokenIssuer, calls the app with curl,
# and compares the HTTP status with the expected one.
# Usage (from anywhere, app must be running):  scripts/check_auth.sh
set -euo pipefail
cd "$(dirname "$0")/.."

BASE_URL="${BASE_URL:-http://localhost:8080}"
HEALTH="$BASE_URL/actuator/health"
EVENTS="$BASE_URL/api/v1/events"

# --- Setup -----------------------------------------------------------------
curl -s -o /dev/null "$HEALTH" || { echo "App is not reachable at $BASE_URL. Run scripts/start.sh first."; exit 1; }

# A second, untrusted key for the wrong-signature case.
if [ ! -f keys/other-private.pem ]; then
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out keys/other-private.pem 2>/dev/null
fi

# Compile once and build the classpath once, so each token takes milliseconds, not a Maven run.
echo "Compiling TestTokenIssuer..."
./mvnw -q test-compile
./mvnw -q dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=target/test-classpath.txt
CP="target/test-classes:target/classes:$(cat target/test-classpath.txt)"

tok() { java -cp "$CP" com.sentinel.sentinel.support.TestTokenIssuer "$@"; }

# Flip one character in the middle of the signature, so the token no longer matches its signature.
tamper() {
  local t="$1" i=$(( ${#1} - 20 ))
  local c="${t:$i:1}"
  local r="A"; [ "$c" = "A" ] && r="B"
  printf '%s%s%s' "${t:0:$i}" "$r" "${t:$((i + 1))}"
}

# --- Checks ----------------------------------------------------------------
pass=0; fail=0
check() {  # check "<name>" <expected> <method> <url> [token]
  local name="$1" expected="$2" method="$3" url="$4" token="${5:-}"
  local args=(-s -o /dev/null -w '%{http_code}' -X "$method")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ "$method" = "POST" ] && args+=(-H "Content-Type: application/json" --data @sample-data/normal.json)
  local got; got=$(curl "${args[@]}" "$url")
  if [ "$got" = "$expected" ]; then
    printf '  PASS  %-42s expected %s, got %s\n' "$name" "$expected" "$got"; pass=$((pass + 1))
  else
    printf '  FAIL  %-42s expected %s, got %s\n' "$name" "$expected" "$got"; fail=$((fail + 1))
  fi
}

echo "Running checks against $BASE_URL"
check "no token"                         401 GET  "$HEALTH"
check "valid token"                      200 GET  "$HEALTH" "$(tok --customer acme --role ingest)"
check "expired token"                    401 GET  "$HEALTH" "$(tok --expired)"
check "tampered token"                   401 GET  "$HEALTH" "$(tamper "$(tok)")"
check "wrong issuer"                     401 GET  "$HEALTH" "$(tok --iss http://evil)"
check "wrong audience"                   401 GET  "$HEALTH" "$(tok --aud other-api)"
check "signed with wrong key"            401 GET  "$HEALTH" "$(tok --private-key keys/other-private.pem)"
check "analyst token on POST /events"    403 POST "$EVENTS" "$(tok --customer acme --role analyst)"
check "ingest token on POST /events"     404 POST "$EVENTS" "$(tok --customer acme --role ingest)"

echo
echo "$pass passed, $fail failed"
echo "(The last check expects 404 until the ingest controller exists; then change it to the real status.)"
[ "$fail" -eq 0 ]
