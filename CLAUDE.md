# CLAUDE.md — sentinel (Threat Detection Service assignment)

## Your role: tutor and reviewer, NOT the author
I am building this project myself to learn, and I must be able to explain and
change every line in a live walkthrough. Act as a senior engineer pairing with me.

**Rules**
1. Do NOT write or edit files in `src/` unless I explicitly say "write this for me"
   for a specific piece. Default to explaining.
2. When I ask "how do I…": explain the concept, point to the relevant
   Spring/Kafka class or doc, and give a hint or pseudocode first. Show a short
   snippet (≤10 lines) only if I'm still stuck after the hint.
3. When I say "review": read my changes (`git diff`), then list bugs, security
   issues, missing validation, and missing tests — most important first. Explain
   *why*, and let me fix them.
4. You MAY run commands to help me check my work: `mvn test`, `docker compose up`,
   `git status/diff/log`, curl calls. Tell me what you ran and what it showed.
5. Boilerplate I name explicitly (e.g. `docker-compose.yml`, `.gitignore`,
   `pom.xml` dependencies) you may generate — then walk me through it line by line.
6. Ask me questions to check my understanding before moving to the next step.
7. Never put secrets, keys, or tokens in the repo. Warn me if I do.
8. Remind me to commit with a clear message after each step.

## The assignment (summary)
Build `sentinel` in Java + Spring Boot + Kafka:
- **Ingest**: `POST /api/v1/events` — batch of login events (user, srcIp, success,
  time) and firewall events (srcIp, dstIp, dstPort, action, time) → Kafka topic.
  Malformed events → quarantine topic, must not break the pipeline.
- **Enrich**: mark events whose source IP is in a known-bad IP file (JSON/CSV).
- **Detect** (configurable thresholds):
  - Brute force: ≥10 failed logins for one account from one IP within 5 min, then a success.
  - Port scan: one source IP hits ≥50 distinct ports on one host within 2 min.
- **Alert**: severity, rule name, triggering events. Repeated matches for same
  account/IP update one alert (no duplicates).
- **Review**: `GET /api/v1/alerts`, `GET /api/v1/alerts/{id}`,
  `PATCH /api/v1/alerts/{id}` (status OPEN/ACKNOWLEDGED/FALSE_POSITIVE/RESOLVED + note).
  Every status change goes in an audit log.
- **Sample data**: script sending normal traffic + attack scenarios.

**Security**: JWT on every endpoint (validate signature, issuer, audience, expiry;
mock IdP / test tokens OK). Roles `ingest` and `analyst` enforced server-side.
Customer ID comes ONLY from the token; no cross-customer access even by guessing IDs.
Validate + size-limit input, parameterized queries, never log secrets/tokens.
No secrets in repo, container runs as non-root, run a dependency vulnerability scan.

**Deliverables**: readable git history; `docker compose up` starts everything
(service, Kafka, DB, sample-data script); unit tests for both rules incl. a
normal-traffic no-alert case, a customer-isolation test, a bad/expired-token test;
README.md (run, test, example API calls); NOTES.md (~1 page: design, security
risks, what was left out, next steps).

## Design reference
`docs/DESIGN.pdf` is the source of truth for architecture, workflows (A token · B ingest ·
C detect+alert · D review), API, event types, data model and test scenarios.
Keep code and this plan consistent with it; update the design doc when a decision changes.

## Step-by-step plan (track progress here — tick as done)
### Phase 0 — Setup ✅
- [x] 1. Tools: JDK 17 (Homebrew), Maven 3.9, Docker Desktop, Git, IntelliJ, Python 3
- [x] 2. Repo, `git init`, pushed to GitHub
- [x] 3. Spring Initializr skeleton (Boot 4.2, Java 17, Maven wrapper)

### Phase 1 — Infrastructure ✅
- [x] 4. docker-compose: Kafka 4.0 (KRaft) + Postgres 17; `.env` gitignored, `.env.example` committed;
      ports bound to 127.0.0.1; Kafka internal `kafka:9092`, host `localhost:9094`
- [x] 5. Multi-stage Dockerfile, runs as non-root user `app`
- [x] 6. App connects to Kafka + DB; `KafkaTopicConfig` creates `events` and `events-quarantine` (3 partitions)

### Phase 2 — Security foundation (Workflow A + token checks)
Decisions: mock IdP = `TestTokenIssuer` (test scope, never shipped); RS256 (app holds only
the public key); sentinel is a stateless resource server and never issues tokens.

**7. Mock IdP — issue tokens ✅**
- [x] 7a. RSA 2048 key pair in `keys/` (git-ignored); `keys/other-private.pem` for wrong-key tests
- [x] 7b. `scripts/gen_keys.sh`
- [x] 7c–7e. `TestTokenIssuer`: claims iss, aud, sub, iat, exp (1h), jti, customer_id, roles;
      flags --customer, --role, --sub, --expired, --iss, --aud, --private-key
- [x] 7f. Verified on jwt.io

**8. Sentinel verifies tokens**
- [x] 8a. `sentinel.security.jwt.*` in `application.yml` + `JwtProperties` record (validated)
- [x] 8b. `SecurityConfig.jwtDecoder`: public key via `RsaKeyConverters.x509()`, RS256 pinned
- [ ] 8c. Validators: expiry + issuer (`JwtValidators.createDefaultWithIssuer`), audience,
      `exp` must exist
- [ ] 8d. `SecurityFilterChain`: all requests authenticated, JWT resource server, STATELESS, CSRF off;
      decide `/actuator/health` (permit by exact path or keep protected)
- [ ] 8e. `JwtAuthenticationConverter`: authorities from `roles`, prefix `ROLE_`
- [ ] 8f. Manual curl tests: missing / expired / tampered / wrong iss / wrong aud / wrong key → 401; valid → 200
- [ ] 8g. Docker: mount `./keys/public.pem:/keys/public.pem:ro` (public key ONLY),
      `JWT_PUBLIC_KEY_LOCATION=file:/keys/public.pem`, retest

**9. Roles**
- [ ] 9a. `ingest` → `POST /api/v1/events`; `analyst` → `GET/PATCH /api/v1/alerts/**`
- [ ] 9b. Wrong role → 403 (missing/invalid token → 401)

**10. Customer identity**
- [ ] 10a. ONE helper reads `customer_id` from the verified JWT
- [ ] 10b. No controller ever takes customerId from body/query/header
- [ ] 10c. Missing/blank `customer_id` → reject, never default

**11. Auth tests** (MockMvc + `TestTokenIssuer`, no containers)
- [ ] 11a. Tampered / wrong-key token → 401
- [ ] 11b. Expired token → 401
- [ ] 11c. (Nice to have) wrong role → 403

### Phase 3 — Ingest (Workflow B)
- [ ] 12. Event records with a `type` field: `LOGIN` (user, sourceIp, success, timestamp) and
      `FIREWALL` (sourceIp, destinationIp, destinationPort, action, timestamp); Bean Validation
      (IP format, port 0–65535, required fields)
- [ ] 13. Size limits: max events per batch (413), max body size, max string lengths
- [ ] 14. `POST /api/v1/events`: validate EACH event; valid → `events`, malformed → `events-quarantine`,
      keyed by customer_id; respond with accepted/rejected counts. One bad event never fails the batch.
- [ ] 15. `sample-data/*.json` example batches; test with Postman/curl (token from `TestTokenIssuer`)

### Phase 4 — Consume + enrich (Workflow C, part 1)
- [ ] 16. Consumer on `events`; unparseable messages → quarantine, never crash the consumer
- [ ] 17. `bad-ips.json` loaded at startup; mark matching events `knownBad`
- [ ] 18. Persist events with customer_id (`events` table)

### Phase 5 — Detect (Workflow C, part 2)
- [ ] 19. Thresholds + windows in `application.yml` (@ConfigurationProperties)
- [ ] 20. Brute force: key (customer, user, sourceIp); ≥10 failures in 5 min, then a success
- [ ] 21. Port scan: key (customer, sourceIp, destinationIp); ≥50 distinct ports in 2 min
- [ ] 22. Decide + document: window state (memory vs DB), event time vs arrival time
- [ ] 23. Rule unit tests — all scenarios in DESIGN.pdf §10 (incl. 9 vs 10, 49 vs 50, outside window,
      split across IPs/users, same ports repeated, normal traffic → no alert)

### Phase 6 — Alert (Workflow C, part 3)
- [ ] 24. Flyway migrations: `events`, `alerts` (UUID id), `alert_events`, `audit_log` — all with customer_id
- [ ] 25. Dedup: update the open alert for (customer, rule, dedup_key), else create one
- [ ] 26. Severity per rule; raised if a known-bad IP is involved

### Phase 7 — Review (Workflow D)
- [ ] 27. `GET /api/v1/alerts` (paged), `GET /{id}`, `PATCH /{id}` (status + note); every query
      filtered by customer; other customer's id → 404
- [ ] 28. Audit row (actor = `sub`, old/new status, note, time) in the same transaction
- [ ] 29. Isolation test: customer B's token cannot read customer A's alert → 404

### Phase 8 — Sample data
- [ ] 30. Script replays `sample-data/` batches: normal traffic, brute force, port scan, malformed events;
      runs as a compose service

### Phase 9 — Hardening
- [ ] 31. No tokens/secrets in logs, parameterized queries, safe error messages
- [ ] 32. Dependency scan (OWASP Dependency-Check or Trivy); note results
- [ ] 33. Fresh clone → `cp .env.example .env` → `scripts/gen_keys.sh` → `docker compose up` works

### Phase 10 — Docs + submission
- [ ] 34. README.md: run, test, example curl calls
- [ ] 35. NOTES.md (~1 page): design choices, security risks + handling, left out, next steps
- [ ] 36. Re-export `docs/DESIGN.pdf`; walkthrough prep: read every file, practise one small change

**Guardrails (always)**
- Never log the `Authorization` header or token strings.
- `private.pem` never committed, never mounted into the app container.
- Customer ID only from the token; every query filtered by it.
