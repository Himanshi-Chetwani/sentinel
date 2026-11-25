# Sentinel — Threat Detection Service

Security event pipeline in Java 17, Spring Boot and Kafka. Design: [`docs/DESIGN.pdf`](docs/DESIGN.pdf).

## Prerequisites

- Docker Desktop (running)
- JDK 17 and the Maven wrapper (`./mvnw`, included)
- `openssl` and `curl` (preinstalled on macOS and most Linux)

## 1. Start everything: `scripts/start.sh`

```bash
scripts/start.sh
```

What it does, in order:

1. **Checks Docker is running.** If not, it stops and asks you to start Docker Desktop.
2. **Creates `.env`** from `.env.example` if it is missing (database settings; never committed).
3. **Creates the JWT keys** in `keys/` with `scripts/gen_keys.sh` if they are missing (git-ignored).
4. **Starts Postgres, Kafka and the app** with `docker compose up -d --build`.
5. **Waits until the app answers** on `http://localhost:8080` (up to 2 minutes). A `401` from
   `/actuator/health` counts as ready: the app is up and rejecting requests without a token.

Stop everything with `docker compose down`. App logs: `docker compose logs -f app`.

## 2. Check the security rules: `scripts/check_auth.sh`

```bash
scripts/check_auth.sh
```

What it does:

1. Confirms the app is reachable.
2. Creates a second, untrusted key (`keys/other-private.pem`) for the wrong-key case.
3. Compiles `TestTokenIssuer` (the mock identity provider, test code only) once.
4. For each case, mints a token, calls the app with `curl`, and compares the HTTP status with
   the expected one.
5. Prints `PASS`/`FAIL` per case and a summary; exits with an error if any case fails.

| Case | Token | Request | Expected |
| --- | --- | --- | --- |
| No token | none | `GET /actuator/health` | 401 |
| Valid token | `--customer acme --role ingest` | `GET /actuator/health` | 200 |
| Expired | `--expired` | `GET /actuator/health` | 401 |
| Tampered | valid token with one signature character changed | `GET /actuator/health` | 401 |
| Wrong issuer | `--iss http://evil` | `GET /actuator/health` | 401 |
| Wrong audience | `--aud other-api` | `GET /actuator/health` | 401 |
| Wrong signing key | `--private-key keys/other-private.pem` | `GET /actuator/health` | 401 |
| Wrong role | `--role analyst` | `POST /api/v1/events` | 403 |
| Right role | `--role ingest` | `POST /api/v1/events` | 200 |

## 3. The same checks by hand

Get a token from the mock identity provider:

```bash
TOKEN=$(./mvnw -q exec:java -Dexec.classpathScope=test \
  -Dexec.mainClass=com.sentinel.sentinel.support.TestTokenIssuer \
  -Dexec.args="--customer acme --role ingest")
```

Other arguments: `--expired`, `--iss <issuer>`, `--aud <audience>`, `--sub <subject>`,
`--private-key <path>`.

Call the app:

```bash
# No token -> 401
curl -i http://localhost:8080/actuator/health

# With a token -> 200 {"status":"UP"} for a valid token, 401 for a bad one
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/actuator/health

# Send a batch of events (ingest role) -> 403 with an analyst token
curl -i -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  --data @sample-data/normal.json \
  http://localhost:8080/api/v1/events
```

For a `401`, the `WWW-Authenticate` response header says why (for example `Jwt expired`).
In Postman: Authorization tab, type **Bearer Token**, paste the token.

The ingest endpoint accepts batches of `LOGIN` and `FIREWALL` events. Valid events are published
to the `events` Kafka topic; invalid events are published to `events-quarantine` and returned as
indexed rejections in the response.

## HTTP status codes

| Code | Meaning | When sentinel returns it |
| --- | --- | --- |
| 200 OK | Request succeeded | Valid token, allowed role, endpoint exists |
| 401 Unauthorized | Identity not proven | No token, or token expired, tampered, signed with the wrong key, or with the wrong issuer or audience |
| 403 Forbidden | Identity proven, action not allowed | Valid token with the wrong role (e.g. `analyst` posting events) |
| 400 Bad Request | Invalid request body | Malformed JSON or an empty event batch |
| 404 Not Found | Nothing at this address | Another customer's alert, so IDs cannot be probed |

Checks run in this order: token (401) → role (403) → request validation (400) → success (200).
