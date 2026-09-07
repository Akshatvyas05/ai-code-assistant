# AI Code Assistant — Design & Development Plan

_Last updated: 2026-09-07_

This document is the single source of truth for **what we are building, why, and in
what order**. It maps every planned unit of work to an existing GitHub issue and
records the design decisions ("why this, not the alternative") so they can be
explained later.

- [1. Product goal](#1-product-goal)
- [2. Scope](#2-scope)
- [3. Current state of the code](#3-current-state-of-the-code)
- [4. Target architecture](#4-target-architecture)
- [5. Data model](#5-data-model)
- [6. API surface](#6-api-surface)
- [7. Key design decisions](#7-key-design-decisions)
- [8. Development phases](#8-development-phases)
- [9. Issue map](#9-issue-map)
- [10. Risks & open questions](#10-risks--open-questions)
- [11. Definition of done for v1](#11-definition-of-done-for-v1)

---

## 1. Product goal

A developer pastes or uploads a piece of code. The service returns an LLM-generated
review: likely bugs, risky patterns, and concrete improvement suggestions. Results
stream in as they are generated and are cached so repeat submissions of identical
code are instant.

The secondary goal is to be a portfolio / interview project that demonstrates:
asynchronous processing with Kafka, caching and rate limiting with Redis, LLM
integration with proper failure handling, stateless JWT auth, and a small typed
React frontend.

## 2. Scope

**In scope for v1**

- Email/password accounts with JWT access + rotating refresh tokens.
- Single-file / single-snippet review submissions.
- Asynchronous review via Kafka; LLM call in the consumer.
- Redis result cache + per-user submission rate limiting.
- SSE streaming of review output to the browser.
- React + TypeScript frontend: upload form, streamed result view, history list.
- One-command local bring-up (Docker Compose for Postgres/Kafka/Redis).
- Deployed live demo.

**Explicitly out of scope for v1**

- Multi-file / whole-repo analysis, diffs, or Git integration.
- Organizations, teams, sharing, roles beyond a single `USER` authority.
- Multiple LLM providers at runtime (the abstraction exists; only one is wired).
- Payment / quotas beyond basic rate limiting.
- Real-time collaborative editing.

## 3. Current state of the code

Implemented and merged to `main`:

| Component | File(s) | Notes |
|-----------|---------|-------|
| App entrypoint | `AiCodeAssistantApplication.java` | Spring Boot 4.1.0, Java 21 |
| Registration | `AuthenticationController#registerUser` | BCrypt via `PasswordEncoder`, duplicate-email → `409`. Returns raw string on conflict — **to be migrated to `ProblemDetail`**. |
| Login | `AuthenticationController#login` | Verifies BCrypt hash, issues access token. |
| Access tokens | `JwtService` | HS256, **hard-coded secret**, **120 s TTL**. Both must move to config. |
| Security chain | `SecurityConfig`, `JwtAuthenticationFilter` | Stateless-ish; `/auth/**` and `/actuator/**` public, everything else authenticated. CSRF disabled. No explicit `SessionCreationPolicy.STATELESS` yet. No authorities loaded (empty list). |
| Refresh tokens | `RefreshTokenService`, `RefreshToken` entity, `RefreshTokenRepository` | Rotation + reuse-detection logic is written and sound. **Not called anywhere** — no `/auth/refresh` endpoint, and login/register never create a refresh token (so `AuthResponse.refreshToken` is always `null`). |
| Error handling | `GlobalExceptionHandler` | RFC 7807 `ProblemDetail` for `InvalidCredentialException` and validation errors. |
| Validation | `RegisterRequest`, `LoginRequest`, `RefreshTokenRequest` | `jakarta.validation` on records. |
| Protected sample | `TestController#getAuthenticatedUser` | `GET /test/me`. |
| Health | Actuator | `GET /actuator/health` with details. |
| Persistence | `application.properties` | Postgres, `ddl-auto=update`, `show-sql=true`. No migrations. |

Not started: Kafka, Redis, LLM integration, review/upload endpoints, results
storage, SSE, the entire frontend, Docker Compose, CI, deployment config, tests
beyond the generated context-load test.

**Branch note:** `origin/feature/redis` is a stale spike that removes the
refresh-token code present on `main`; do not build on it. `origin/feature/issue#1`
and `origin/1-feature-…` are merged. Treat `main` as the only live branch.

## 4. Target architecture

```
Browser (React + TS, React Query)
  │  upload snippet ──────────────► POST /review/upload ─┐
  │  open result stream ◄────────── GET /review/{id}/stream (SSE)
  ▼
Spring Boot backend
  ├─ AuthController         → Postgres (users, refresh_tokens)
  ├─ ReviewController
  │     ├─ rate-limit check (Redis)
  │     ├─ persist Review row (status = QUEUED)
  │     └─ produce ReviewRequested{reviewId, codeHash, code} → Kafka
  ├─ ReviewConsumer (Kafka consumer group)
  │     ├─ cache lookup by codeHash (Redis) → hit: publish result, done
  │     ├─ call LLM (streaming)
  │     │     ├─ transient error → retry w/ backoff (max N)
  │     │     └─ permanent / exhausted → DLQ topic, status = FAILED
  │     ├─ persist result + status = COMPLETED (Postgres)
  │     ├─ cache result by codeHash (Redis, TTL)
  │     └─ push chunks to the SSE emitter for reviewId
  └─ SSE registry: reviewId → SseEmitter (in-memory, single instance for v1)

Infra: Postgres · Kafka (+ DLQ) · Redis · LLM API
```

Deployment for v1 is a single backend instance, so the SSE emitter registry can
live in memory. If we ever scale horizontally, streaming has to move to a shared
transport (Redis pub/sub or a Kafka results topic the web layer consumes) — noted
as a known limitation for the interview narrative.

## 5. Data model

**`users`** _(exists)_ — `id`, `name`, `email` (unique), `password` (BCrypt),
`created_at`.

**`refresh_tokens`** _(exists)_ — `id`, `token` (unique, UUID), `expiry_date`,
`revoked`, `user_id` → FK. Rotation sets `revoked = true` and issues a new row;
reuse of a revoked token deletes all of that user's tokens.

**`reviews`** _(planned, [#20](https://github.com/Akshatvyas05/ai-code-assistant/issues/20))_

| column | type | notes |
|--------|------|-------|
| `id` | UUID / bigint | PK, returned to client, used as SSE channel key |
| `user_id` | FK → users | |
| `code_hash` | char(64) | SHA-256 of normalized source; cache key |
| `language` | varchar | optional hint from client |
| `status` | enum | `QUEUED`, `PROCESSING`, `COMPLETED`, `FAILED` |
| `result` | text / jsonb | full review once complete |
| `error` | text | populated when `FAILED` |
| `created_at` | timestamptz | index |
| `completed_at` | timestamptz | nullable |

Indexes: `(user_id, created_at desc)` for history, `(code_hash)` for cache backfill.

**Migrations:** adopt Flyway when the `reviews` table lands. Switch
`ddl-auto` to `validate` at the same time. (Decision D-7.)

## 6. API surface

| Method & path | Auth | Status | Issue |
|---------------|------|--------|-------|
| `POST /auth/register` | public | ✅ done | — |
| `POST /auth/login` | public | ✅ done | — |
| `POST /auth/refresh` | public (refresh token in body) | ⚠️ service ready, endpoint missing | [#4](https://github.com/Akshatvyas05/ai-code-assistant/issues/4) follow-up |
| `POST /auth/logout` | bearer | ❌ | [#6](https://github.com/Akshatvyas05/ai-code-assistant/issues/6) |
| `GET /actuator/health` | public | ✅ done | — |
| `GET /test/me` | bearer | ✅ done (remove before v1) | — |
| `POST /review/upload` | bearer | ❌ | [#13](https://github.com/Akshatvyas05/ai-code-assistant/issues/13) |
| `GET /review/{id}` | bearer, owner only | ❌ | [#20](https://github.com/Akshatvyas05/ai-code-assistant/issues/20) |
| `GET /review/{id}/stream` | bearer, owner only | ❌ SSE | [#21](https://github.com/Akshatvyas05/ai-code-assistant/issues/21), [#26](https://github.com/Akshatvyas05/ai-code-assistant/issues/26) |
| `GET /review` (history) | bearer | ❌ | frontend phase |
| OpenAPI / Swagger UI | public in dev | ❌ | [#9](https://github.com/Akshatvyas05/ai-code-assistant/issues/9) |

All error responses use RFC 7807 `ProblemDetail`. Rate-limited submissions return
`429` with a `Retry-After` header ([#18](https://github.com/Akshatvyas05/ai-code-assistant/issues/18)).

## 7. Key design decisions

| # | Decision | Rationale | Alternative rejected |
|---|----------|-----------|----------------------|
| D-1 | **Async review via Kafka**, not a synchronous request | LLM latency is seconds and failure-prone; decoupling keeps the API fast and gives a natural retry/DLQ boundary and independent consumer scaling | Synchronous call in the controller (simple but ties up request threads, no clean retry); in-JVM `@Async` executor (no durability, work lost on restart) |
| D-2 | **Stateless JWT access token + rotating opaque refresh token** | Access token needs no DB lookup on every request; refresh token is opaque + stored so it can be revoked and rotated, and reuse can be detected | Session cookies (stateful, needs sticky sessions); long-lived JWT only (can't revoke) |
| D-3 | **Refresh-token reuse ⇒ revoke all user tokens** | A replayed revoked token means the token was stolen; nuking the family forces re-login and limits blast radius | Ignore / just reject the one token (misses the theft signal) |
| D-4 | **Redis cache keyed by SHA-256 of normalized code** | Identical submissions are common (re-runs, shared snippets); a content hash makes the cache user-independent and cheap | Cache by `reviewId` (no dedup value); no cache (repeated LLM cost + latency) |
| D-5 | **Per-user rate limiting in Redis** (fixed or sliding window) | LLM calls cost money; protects budget and the upstream API quota; Redis gives atomic counters with TTL across instances | In-memory bucket (breaks with >1 instance); DB counter (write-heavy, slow) |
| D-6 | **SSE, not WebSockets, for result streaming** | Traffic is one-directional server→client, SSE is plain HTTP (works through proxies, auto-reconnect built in), simpler to implement and test | WebSockets (bidirectional complexity we don't need); polling (latency, wasted requests) |
| D-7 | **Flyway migrations once `reviews` lands; `ddl-auto=validate`** | Auto-DDL is fine for the current throwaway auth schema but unsafe once data matters | Keep `ddl-auto=update` (schema drift, no history, risky in prod) |
| D-8 | **LLM provider behind a `CodeReviewer` interface** | Keeps the consumer testable (fake in tests) and swappable; only one impl wired for v1 | Call the SDK directly from the consumer (untestable, locked in) |
| D-9 | **Config & secrets via environment variables / `@ConfigurationProperties`** | The hard-coded JWT secret is a real vulnerability; also needed for deployment | Keep values in `application.properties` (leaks secrets, no per-env config) |
| D-10 | **Docker Compose for local infra** | Postgres + Kafka + Redis by hand is a barrier to contribution and to your own re-setup | Document manual install steps only |

## 8. Development phases

Phases are ordered so each one unblocks the next. Roughly aligned with the
90-day tracker (project column, days ~1–40).

### Phase 0 — Foundations & auth hardening
Finish and lock down what already exists before building on it.

- Externalize JWT secret + access-token TTL to config; raise TTL to a sane value
  (e.g. 15 min) now that refresh exists. **(D-9)**
- Wire `POST /auth/refresh` to `RefreshTokenService.rotateRefreshToken`; make
  `login` / `register` actually create and return a refresh token.
- `POST /auth/logout` — revoke the presented refresh token; short access TTL
  covers the access side for v1. ([#6](https://github.com/Akshatvyas05/ai-code-assistant/issues/6))
- Explicit `SessionCreationPolicy.STATELESS`; load a `USER` authority; audit that
  no endpoint is unintentionally open. ([#5](https://github.com/Akshatvyas05/ai-code-assistant/issues/5), [#23](https://github.com/Akshatvyas05/ai-code-assistant/issues/23))
- Validation edge cases + consistent `ProblemDetail` bodies everywhere (migrate
  the register-conflict string). ([#7](https://github.com/Akshatvyas05/ai-code-assistant/issues/7))
- Integration tests for register → login → protected → refresh → logout, including
  rejection paths. ([#8](https://github.com/Akshatvyas05/ai-code-assistant/issues/8))
- springdoc-openapi + Swagger UI for the auth endpoints. ([#9](https://github.com/Akshatvyas05/ai-code-assistant/issues/9))
- Full read-through / refactor pass with narrative notes. ([#10](https://github.com/Akshatvyas05/ai-code-assistant/issues/10))
- Add Docker Compose (Postgres now; Kafka + Redis added in later phases). **(D-10)**

**Exit criteria:** full auth flow green in integration tests; no secrets in the
repo; Swagger UI lists every auth endpoint.

### Phase 1 — Kafka pipeline skeleton
Move a message end-to-end with no LLM yet.

- Add `spring-kafka`; Kafka + Zookeeper/KRaft in Compose. ([#11](https://github.com/Akshatvyas05/ai-code-assistant/issues/11))
- Create the `review-requested` topic; producer skeleton. ([#11](https://github.com/Akshatvyas05/ai-code-assistant/issues/11))
- Consumer group config; verify offset-commit behavior. ([#12](https://github.com/Akshatvyas05/ai-code-assistant/issues/12))
- `POST /review/upload`: accept code (multipart or JSON), persist a `QUEUED`
  `Review`, publish the event, return `202` + `reviewId`. ([#13](https://github.com/Akshatvyas05/ai-code-assistant/issues/13))
- Consumer picks up the event, logs the lifecycle, flips status to
  `PROCESSING`/`COMPLETED` with a stub result. ([#14](https://github.com/Akshatvyas05/ai-code-assistant/issues/14))

**Exit criteria:** uploading code produces a `Review` row that reaches
`COMPLETED` via the consumer; restarting the consumer resumes from the committed
offset.

### Phase 2 — LLM integration
- `CodeReviewer` interface + one real implementation (Anthropic Claude by
  default), prompt for bug detection + suggestions. **(D-8)** ([#15](https://github.com/Akshatvyas05/ai-code-assistant/issues/15))
- Retry with exponential backoff on transient errors (timeouts, 429, 5xx);
  route permanent failures / exhausted retries to a `review-dlq` topic and mark
  the `Review` `FAILED` with the error. ([#16](https://github.com/Akshatvyas05/ai-code-assistant/issues/16))
- Timeout budget per call; cost/token guardrails (max input size, truncation).

**Exit criteria:** a real review comes back for a valid snippet; a forced API
failure lands in the DLQ and the row is `FAILED` with a readable error.

### Phase 3 — Redis
- Add Redis to Compose + `spring-data-redis`. ([#17](https://github.com/Akshatvyas05/ai-code-assistant/issues/17))
- Cache completed results by `code_hash` with a TTL; consumer checks the cache
  before calling the LLM; define invalidation (TTL-only for v1). **(D-4)** ([#17](https://github.com/Akshatvyas05/ai-code-assistant/issues/17))
- Per-user submission rate limit (atomic `INCR` + `EXPIRE`); `429` + `Retry-After`.
  **(D-5)** ([#18](https://github.com/Akshatvyas05/ai-code-assistant/issues/18))
- Service-layer refactor pass: bean scopes/lifecycle, DI cleanup. ([#19](https://github.com/Akshatvyas05/ai-code-assistant/issues/19))

**Exit criteria:** second identical submission returns without an LLM call;
exceeding the limit returns `429`.

### Phase 4 — Storage & streaming
- Finalize the `reviews` schema; introduce Flyway; `ddl-auto=validate`. **(D-7)** ([#20](https://github.com/Akshatvyas05/ai-code-assistant/issues/20))
- `GET /review/{id}/stream`: `SseEmitter` per `reviewId`; consumer pushes chunks
  as the LLM streams; terminal event on `COMPLETED`/`FAILED`. **(D-6)** ([#21](https://github.com/Akshatvyas05/ai-code-assistant/issues/21))
- End-to-end integration test: upload → Kafka → (faked) LLM → Redis → result,
  including failure injection. ([#22](https://github.com/Akshatvyas05/ai-code-assistant/issues/22))
- Security filter chain audit now that new endpoints exist. ([#23](https://github.com/Akshatvyas05/ai-code-assistant/issues/23))

**Exit criteria:** a browser `EventSource` receives incremental output and a clean
end event; the full-pipeline test is green.

### Phase 5 — Frontend
- Vite + React + TypeScript in `frontend/`; React Query; typed API client with
  bearer-token handling + refresh-on-401. ([#24](https://github.com/Akshatvyas05/ai-code-assistant/issues/24))
- Upload UI: code textarea / file picker, language hint, submit, loading state. ([#25](https://github.com/Akshatvyas05/ai-code-assistant/issues/25))
- Result view: consume the SSE stream, render incrementally, handle
  error/empty/terminal states. ([#26](https://github.com/Akshatvyas05/ai-code-assistant/issues/26))
- History list backed by `GET /review`.

**Exit criteria:** from the deployed frontend, a logged-in user uploads a snippet
and watches the review stream in.

### Phase 6 — Ship v1
- README covering setup / architecture / local run. ([#27](https://github.com/Akshatvyas05/ai-code-assistant/issues/27))
- Polish pass: manual test, clean error/loading/empty states. ([#28](https://github.com/Akshatvyas05/ai-code-assistant/issues/28))
- Deploy backend + frontend; verify the live URL. ([#29](https://github.com/Akshatvyas05/ai-code-assistant/issues/29))
- Fix deploy-surfaced CORS / env-var / build issues. ([#30](https://github.com/Akshatvyas05/ai-code-assistant/issues/30))
- Finalize the architecture diagram; add to README. ([#31](https://github.com/Akshatvyas05/ai-code-assistant/issues/31))
- Write the per-component "why" narrative (auth, Kafka, Redis, LLM). ([#32](https://github.com/Akshatvyas05/ai-code-assistant/issues/32))
- Test coverage on the critical paths, backend + frontend. ([#33](https://github.com/Akshatvyas05/ai-code-assistant/issues/33))
- Demo GIF/video in the README. ([#34](https://github.com/Akshatvyas05/ai-code-assistant/issues/34))
- Track and resolve issues found while explaining the project. ([#35](https://github.com/Akshatvyas05/ai-code-assistant/issues/35))
- **From here, no new features** — reinforcement and polish only.

## 9. Issue map

| Phase | Issues | Theme |
|-------|--------|-------|
| 0 — Auth hardening | [#5](https://github.com/Akshatvyas05/ai-code-assistant/issues/5), [#6](https://github.com/Akshatvyas05/ai-code-assistant/issues/6), [#7](https://github.com/Akshatvyas05/ai-code-assistant/issues/7), [#8](https://github.com/Akshatvyas05/ai-code-assistant/issues/8), [#9](https://github.com/Akshatvyas05/ai-code-assistant/issues/9), [#10](https://github.com/Akshatvyas05/ai-code-assistant/issues/10) | refresh/logout endpoints, validation, tests, docs, config |
| 1 — Kafka | [#11](https://github.com/Akshatvyas05/ai-code-assistant/issues/11), [#12](https://github.com/Akshatvyas05/ai-code-assistant/issues/12), [#13](https://github.com/Akshatvyas05/ai-code-assistant/issues/13), [#14](https://github.com/Akshatvyas05/ai-code-assistant/issues/14) | topic, producer, consumer, upload endpoint |
| 2 — LLM | [#15](https://github.com/Akshatvyas05/ai-code-assistant/issues/15), [#16](https://github.com/Akshatvyas05/ai-code-assistant/issues/16) | LLM call, retry/DLQ |
| 3 — Redis | [#17](https://github.com/Akshatvyas05/ai-code-assistant/issues/17), [#18](https://github.com/Akshatvyas05/ai-code-assistant/issues/18), [#19](https://github.com/Akshatvyas05/ai-code-assistant/issues/19) | cache, rate limit, service refactor |
| 4 — Storage & SSE | [#20](https://github.com/Akshatvyas05/ai-code-assistant/issues/20), [#21](https://github.com/Akshatvyas05/ai-code-assistant/issues/21), [#22](https://github.com/Akshatvyas05/ai-code-assistant/issues/22), [#23](https://github.com/Akshatvyas05/ai-code-assistant/issues/23) | schema/migrations, SSE, e2e test, security audit |
| 5 — Frontend | [#24](https://github.com/Akshatvyas05/ai-code-assistant/issues/24), [#25](https://github.com/Akshatvyas05/ai-code-assistant/issues/25), [#26](https://github.com/Akshatvyas05/ai-code-assistant/issues/26) | scaffold, upload UI, SSE view |
| 6 — Ship v1 | [#27](https://github.com/Akshatvyas05/ai-code-assistant/issues/27), [#28](https://github.com/Akshatvyas05/ai-code-assistant/issues/28), [#29](https://github.com/Akshatvyas05/ai-code-assistant/issues/29), [#30](https://github.com/Akshatvyas05/ai-code-assistant/issues/30), [#31](https://github.com/Akshatvyas05/ai-code-assistant/issues/31), [#32](https://github.com/Akshatvyas05/ai-code-assistant/issues/32), [#33](https://github.com/Akshatvyas05/ai-code-assistant/issues/33), [#34](https://github.com/Akshatvyas05/ai-code-assistant/issues/34), [#35](https://github.com/Akshatvyas05/ai-code-assistant/issues/35) | docs, deploy, diagram, narrative, coverage |

Issues not yet filed but implied by this plan: raise-TTL / externalize-secret
(part of #10 scope), Docker Compose, `GET /review` history endpoint, Flyway
adoption, CI workflow (`./gradlew build` on PRs), remove `TestController` before v1.

## 10. Risks & open questions

| Risk / question | Impact | Mitigation / decision needed |
|-----------------|--------|------------------------------|
| In-memory SSE registry breaks with >1 backend instance | Streaming fails on scale-out | v1 runs one instance; document the limitation; Redis pub/sub or Kafka results topic if scaling |
| LLM cost runs away under load | Budget | Rate limiting (D-5), max input size, cache (D-4), a hard daily cap |
| `ddl-auto=update` already in use | Schema drift | Switch to Flyway + `validate` in Phase 4 (D-7); accept risk until then |
| Uploaded code is untrusted input | Prompt injection, PII in logs | Never execute uploaded code; never log it above `DEBUG`; treat LLM output as data |
| Kafka + Redis + Postgres local footprint | Contributor friction | Docker Compose (D-10); document minimum resources |
| Which LLM provider / model | Narrative + cost | Default to Anthropic Claude; keep `CodeReviewer` abstraction (D-8) |
| Exactly-once vs at-least-once processing | Duplicate reviews | At-least-once + idempotent consumer keyed on `reviewId`; a duplicate just re-serves the cached result |

## 11. Definition of done for v1

- All Phase 0–6 issues closed.
- `./gradlew build` green; critical paths covered by tests (auth flow, full
  pipeline, frontend upload + result rendering).
- No secrets in the repository; all config externalized.
- Docker Compose brings up Postgres + Kafka + Redis; `./gradlew bootRun` +
  `npm run dev` gives a working local stack.
- Backend and frontend deployed; the live URL performs an upload → streamed
  result successfully.
- README has setup instructions, the finalized architecture diagram, and a demo
  GIF/video.
- A written "why this, not the alternative" note exists for auth, Kafka, Redis,
  and LLM integration.
