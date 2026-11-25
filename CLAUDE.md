# Sentinel — Build plan (CLAUDE.md)

Nov 24, 2026 · @HC

**Now:** Phase 4 code done (71b71d8): consumer flags bad IPs and saves events. Pending: end-to-end DB check, 2 tests, NOTES.md items. Then Phase 5: detection rules. Loose ends: 2 README fixes, check\_ingest timeout. Done: `CurrentCustomer` helper. Security checks pass (scripts/check\_auth.sh, 9/9). Full copy of `CLAUDE.md`; the repo file stays the source of truth.

## Your role: tutor and reviewer, NOT the author

I am building this project myself to learn, and I must be able to explain and change every line in a live walkthrough. Act as a senior engineer pairing with me.

**Rules**

1. Do NOT write or edit files in `src/` unless I explicitly say "write this for me" for a specific piece. Default to explaining.
2. When I ask "how do I…": explain the concept, point to the relevant Spring/Kafka class or doc, and give a hint or pseudocode first. Show a short snippet (≤10 lines) only if I'm still stuck after the hint.
3. When I say "review": read my changes (`git diff`), then list bugs, security issues, missing validation and missing tests, most important first. Explain why, and let me fix them.
4. You MAY run commands to help me check my work: `mvn test`, `docker compose up`, `git status/diff/log`, curl calls. Tell me what you ran and what it showed.
5. Boilerplate I name explicitly (e.g. `docker-compose.yml`, `.gitignore`, `pom.xml` dependencies) you may generate, then walk me through it line by line.
6. Ask me questions to check my understanding before moving to the next step.
7. Never put secrets, keys or tokens in the repo. Warn me if I do.
8. Remind me to commit with a clear message after each step.

## The assignment (summary)

Build `sentinel` in Java + Spring Boot + Kafka:

- **Ingest:** `POST /api/v1/events`, a batch of login events (user, srcIp, success, time) and firewall events (srcIp, dstIp, dstPort, action, time) → Kafka topic. Malformed events → quarantine topic; must not break the pipeline.
- **Enrich:** mark events whose source IP is in a known-bad IP file (JSON/CSV).
- **Detect** (configurable thresholds): brute force = ≥10 failed logins for one account from one IP within 5 min, then a success; port scan = one source IP hits ≥50 distinct ports on one host within 2 min.
- **Alert:** severity, rule name, triggering events. Repeated matches for the same account/IP update one alert (no duplicates).
- **Review:** `GET /api/v1/alerts`, `GET /api/v1/alerts/{id}`, `PATCH /api/v1/alerts/{id}` (status OPEN / ACKNOWLEDGED / FALSE\_POSITIVE / RESOLVED + note). Every status change goes in an audit log.
- **Sample data:** script sending normal traffic + attack scenarios.

**Security:** JWT on every endpoint (validate signature, issuer, audience, expiry; mock IdP / test tokens OK). Roles `ingest` and `analyst` enforced server-side. Customer ID comes ONLY from the token; no cross-customer access even by guessing IDs. Validate + size-limit input, parameterized queries, never log secrets/tokens. No secrets in repo, container runs as non-root, run a dependency vulnerability scan.

**Deliverables:** readable git history; `docker compose up` starts everything (service, Kafka, DB, sample-data script); unit tests for both rules incl. a normal-traffic no-alert case, a customer-isolation test, a bad/expired-token test; README.md (run, test, example API calls); NOTES.md (\~1 page: design, security risks, what was left out, next steps).

## Design reference

`docs/DESIGN.pdf` is the source of truth for architecture, workflows (A token · B ingest · C detect+alert · D review), API, event types, data model and test scenarios. Keep code and this plan consistent with it; update the design doc when a decision changes.

## Step-by-step plan (tick as done)

## Phase 0 — Setup ✅

- [x] 1\. Tools: JDK 17, Maven 3.9, Docker Desktop, Git, IntelliJ, Python 3
- [x] 2\. Repo, `git init`, pushed to GitHub
- [x] 3\. Spring Initializr skeleton (Boot 4.2, Java 17, Maven wrapper)

## Phase 1 — Infrastructure ✅

- [x] 4\. docker-compose: Kafka 4.0 (KRaft) + Postgres 17; `.env` git-ignored, `.env.example` committed
- [x] 5\. Multi-stage Dockerfile, runs as non-root user `app`
- [x] 6\. App connects to Kafka + DB; `events` and `events-quarantine` topics created

## Phase 2 — Security foundation ✅

**7. Mock IdP ✅**

- [x] 7a. RSA key pair in `keys/` (git-ignored), plus `other-private.pem`
- [x] 7b. `scripts/gen_keys.sh`
- [x] 7c–7e. `TestTokenIssuer` with all claims and negative-test flags
- [x] 7f. Verified on jwt.io

**8. Sentinel verifies tokens**

- [x] 8a. `sentinel.security.jwt.*` + `JwtProperties`
- [x] 8b. `jwtDecoder`: public key, RS256 pinned
- [x] 8c. Validators: issuer + expiry, audience, `exp` must exist
- [x] 8d. `SecurityFilterChain`: authenticated, stateless, CSRF off
- [x] 8e. `JwtAuthenticationConverter`: `roles` → `ROLE_`
- [x] 8f. Curl checks: missing / expired / tampered / wrong iss / wrong aud / wrong key → 401; valid → 200
- [x] 8g. Docker: `public.pem` mounted read-only

**9. Roles**

- [x] 9a. `ingest` → `POST /api/v1/events`; `analyst` → `/api/v1/alerts/**`
- [x] 9b. Wrong role → 403

**10. Customer identity ✅**

- [x] 10a. ONE helper reads `customer_id` from the verified JWT
- [x] 10b. No controller takes customerId from body, query or header
- [x] 10c. Missing or blank `customer_id` → reject, never default

**11. Auth tests ✅ (TokenSecurityTest, 6/6)**

- [x] 11a. Tampered / wrong-key token → 401
- [x] 11b. Expired token → 401
- [x] 11c. (Nice to have) wrong role → 403

## Phase 3 — Ingest (Workflow B)

- [x] 12\. Event records: `LOGIN` and `FIREWALL` with a `type` field; Bean Validation; reject unknown fields such as customerId (400)
- [x] 13\. Size limits: 1–1000 events per batch (400), field lengths; body size filter left out (note in NOTES.md)
- [x] 14\. `POST /api/v1/events`: validate each event; valid → `events`, malformed → `events-quarantine`; return counts
- [x] 15\. Leak check: batch with customerId globex sent with an acme token → 400 or stored as acme. Test with Postman/curl using `sample-data/*.json`

## Phase 4 — Consume + enrich (Workflow C) — in progress: code done, 18d/18e/NOTES open

- [x] 16a. `V1__create_events.sql` (Flyway): one `events` table for both types, `username` column (not `user`, reserved), index (customer\_id, type, event\_time)
- [x] 17\. `bad-ips.json` + `BadIpList` (loaded once at startup, immutable, null-safe, fails startup if missing) + `BadIpListTest`
- [x] 18a. `StoredEvent` entity (private constructor; `fromLogin` / `fromFirewall`) + `EventRepository`
- [x] 18b. `EventConsumer` (`@KafkaListener` on `events`): parse → re-validate → flag known-bad IP → save with the message's customer\_id; unreadable or invalid messages → `events-quarantine`; DB errors propagate so Kafka retries. `EventConsumerTest` (5)
- [x] 18c. Consumer config: group `sentinel-enrich`, `auto-offset-reset: earliest`, String deserializers
- [ ] 18d. End-to-end check: `brute_force.json` → rows with `known_bad_ip = true`; `normal.json` → false
- [ ] 18e. Test: good IP → `knownBadIp = false`
- [ ] NOTES.md: retries exhausted → message skipped (prod: dead-letter topic); at-least-once → duplicate rows possible; IPv6 exact-match limit

## Phase 5 — Detect (Workflow C)

- [ ] 19\. Thresholds + windows in `application.yml`
- [ ] 20\. Brute force rule
- [ ] 21\. Port scan rule
- [ ] 22\. Decide: window state, event time vs arrival time
- [ ] 23\. Rule unit tests (all scenarios in DESIGN.pdf §10)

## Phase 6 — Alert (Workflow C)

- [ ] 24\. Flyway migrations: `events`, `alerts`, `alert_events`, `audit_log`
- [ ] 25\. Dedup: update open alert, else create
- [ ] 26\. Severity per rule, raised for known-bad IPs

## Phase 7 — Review (Workflow D)

- [ ] 27\. `GET` list, `GET {id}`, `PATCH {id}`; customer-scoped, other customer → 404
- [ ] 28\. Audit row in the same transaction
- [ ] 29\. Customer data leak test (globex analyst vs an acme alert): list excludes it; GET by id → 404; PATCH → 404 and unchanged; control as acme → 200. JUnit + check\_auth.sh

## Phase 8 — Sample data

- [ ] 30\. Script replays `sample-data/` as a compose service

## Phase 9 — Hardening

- [ ] 31\. No tokens in logs, parameterized queries, safe errors
- [ ] 32\. Dependency scan
- [ ] 33\. Fresh clone → `docker compose up` works

## Phase 10 — Docs + submission

- [ ] 34\. README.md
- [ ] 35\. NOTES.md
- [ ] 36\. Re-export DESIGN.pdf; walkthrough prep

## Guardrails (always)

- Never log the `Authorization` header or token strings.
- `private.pem` never committed, never mounted into the app container.
- Customer ID only from the token; every query filtered by it (`findByIdAndCustomerId`, never `findById`).
- Another customer's resource → 404, never 403. Alert IDs are UUIDs.
- No request class has a customer ID field; unknown fields are rejected.
