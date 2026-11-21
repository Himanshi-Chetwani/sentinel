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

## Step-by-step plan (track progress here — tick as done)
### Phase 0 — Setup
- [ ] 1. Install JDK 21, Maven, Docker Desktop, Git, IDE, Python 3
- [ ] 2. Create repo, `git init`
- [ ] 3. Spring Initializr skeleton (Web, Security, OAuth2 Resource Server, Kafka, JPA, Validation, PostgreSQL, Flyway, Testcontainers)
### Phase 1 — Infrastructure
- [ ] 4. docker-compose: Kafka (KRaft) + Postgres; `.env` gitignored, `.env.example` committed
- [ ] 5. Multi-stage Dockerfile, non-root user
- [ ] 6. App connects to Kafka + DB
### Phase 2 — Security foundation
- [ ] 7. Mock IdP: RSA keypair (not in repo) + token generator (customer_id, roles, iss, aud, exp)
- [ ] 8. Resource server validates signature, issuer, audience, expiry
- [ ] 9. Role → endpoint mapping
- [ ] 10. Single helper that reads customer ID from the token
- [ ] 11. Test: bad/expired token → 401
### Phase 3 — Ingest
- [ ] 12. Event models + validation
- [ ] 13. Size limits (batch size, body size, string lengths)
- [ ] 14. POST /events → events topic / quarantine topic
- [ ] 15. Poison-message-safe consumer (ErrorHandlingDeserializer + DLT)
### Phase 4 — Enrich
- [ ] 16. bad-ips.json + enrichment flag
- [ ] 17. Persist events with customer_id
### Phase 5 — Detect
- [ ] 18. Thresholds in application.yml (@ConfigurationProperties)
- [ ] 19. Brute-force rule
- [ ] 20. Port-scan rule
- [ ] 21. Unit tests for both rules incl. normal traffic → no alert
### Phase 6 — Alert
- [ ] 22. Flyway migration for alerts table
- [ ] 23. Dedup / upsert on (customer, rule, key)
- [ ] 24. Severity logic
### Phase 7 — Review
- [ ] 25. GET list / GET by id / PATCH, customer-scoped (other customer's id → 404)
- [ ] 26. audit_log table, written in same transaction
- [ ] 27. Test: customer isolation
### Phase 8 — Sample data
- [ ] 28. Python script: tokens, normal traffic, brute force, port scan, malformed events
### Phase 9 — Hardening
- [ ] 29. No secrets in logs, parameterized queries, safe error messages
- [ ] 30. Dependency scan (OWASP Dependency-Check or Trivy)
- [ ] 31. Fresh clone → docker compose up works end to end
### Phase 10 — Docs
- [ ] 32. README.md
- [ ] 33. NOTES.md
- [ ] 34. Walkthrough prep: read every file, practise one small change
