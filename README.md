# AI Code Assistant

An asynchronous, LLM-backed code review service. A user uploads a code snippet or
file; the backend queues the work through Kafka, a consumer sends it to an LLM for
bug detection and improvement suggestions, results are cached in Redis and streamed
back to the browser over Server-Sent Events (SSE).

> **Status: early development.** Authentication is implemented. The review pipeline
> (upload → Kafka → LLM → Redis → SSE) and the React frontend are not built yet.
> See the [Design & Development Plan](docs/DESIGN_AND_DEVELOPMENT_PLAN.md) and the
> [open issues](https://github.com/Akshatvyas05/ai-code-assistant/issues).

---

## Table of contents

- [What works today](#what-works-today)
- [Target architecture](#target-architecture)
- [Tech stack](#tech-stack)
- [Getting started](#getting-started)
- [Configuration](#configuration)
- [API reference](#api-reference)
- [Project layout](#project-layout)
- [Roadmap](#roadmap)
- [Contributing](#contributing)

---

## What works today

| Area | State |
|------|-------|
| User registration (`POST /auth/register`) | ✅ Implemented — BCrypt hashing, duplicate-email check |
| Login (`POST /auth/login`) | ✅ Implemented — issues a signed JWT access token |
| JWT access tokens | ✅ Implemented (`JwtService`, HS256) |
| Stateless security filter chain | ✅ Implemented (`SecurityConfig`, `JwtAuthenticationFilter`) |
| Refresh tokens (issue / store / rotate / reuse-detection) | ⚠️ Service layer written (`RefreshTokenService`); **not yet wired to a `/auth/refresh` endpoint** |
| Protected endpoint example (`GET /test/me`) | ✅ Implemented |
| Health check (`GET /actuator/health`) | ✅ Implemented |
| Global error handling (RFC 7807 `ProblemDetail`) | ✅ Implemented |
| Bean-validation on request bodies | ✅ Implemented |
| Kafka producer / consumer | ❌ Not started ([#11](https://github.com/Akshatvyas05/ai-code-assistant/issues/11)–[#15](https://github.com/Akshatvyas05/ai-code-assistant/issues/15)) |
| LLM integration + retry/DLQ | ❌ Not started ([#15](https://github.com/Akshatvyas05/ai-code-assistant/issues/15), [#16](https://github.com/Akshatvyas05/ai-code-assistant/issues/16)) |
| Redis caching + per-user rate limiting | ❌ Not started ([#17](https://github.com/Akshatvyas05/ai-code-assistant/issues/17), [#18](https://github.com/Akshatvyas05/ai-code-assistant/issues/18)) |
| SSE streaming | ❌ Not started ([#21](https://github.com/Akshatvyas05/ai-code-assistant/issues/21), [#26](https://github.com/Akshatvyas05/ai-code-assistant/issues/26)) |
| React + TypeScript frontend | ❌ Not started ([#24](https://github.com/Akshatvyas05/ai-code-assistant/issues/24)–[#26](https://github.com/Akshatvyas05/ai-code-assistant/issues/26)) |

---

## Target architecture

```
                       ┌─────────────┐
                       │   Browser   │  React + TS, React Query
                       │  (frontend) │  upload form · SSE result view
                       └──────┬──────┘
                              │  HTTPS (JWT bearer)
                              ▼
┌───────────────────────────────────────────────────────────────┐
│                     Spring Boot backend                        │
│                                                               │
│  /auth/**  ──► AuthController ──► UserRepository (Postgres)    │
│                                                               │
│  POST /review/upload                                           │
│     └─► ReviewController ──► produce ReviewRequested ──►┐      │
│                                                        │      │
│  GET /review/{id}/stream (SSE) ◄── push updates ◄──┐   │      │
│                                                    │   │      │
│  ┌──────────────── ReviewConsumer ────────────────┐│   │      │
│  │  consume ReviewRequested                       ││   │      │
│  │   ├─ check Redis cache (hash of code) ─────────┼┼───┘      │
│  │   ├─ call LLM API (stream tokens)              ││          │
│  │   ├─ retry transient / DLQ on permanent fail   ││          │
│  │   ├─ persist result (Postgres)                 ││          │
│  │   └─ cache result in Redis (TTL)               ││          │
│  └───────────────────────────────────────────────┘│          │
└────────────────────┬──────────────────┬────────────┴──────────┘
                     ▼                  ▼
              ┌────────────┐     ┌────────────┐     ┌────────────┐
              │  Postgres  │     │   Kafka    │     │   Redis    │
              │ users,     │     │ review     │     │ result     │
              │ refresh_   │     │ topics +   │     │ cache +    │
              │ tokens,    │     │ DLQ        │     │ rate limit │
              │ results    │     │            │     │            │
              └────────────┘     └────────────┘     └────────────┘
                                        │
                                        ▼
                                 ┌────────────┐
                                 │  LLM API   │  (Anthropic Claude / OpenAI)
                                 └────────────┘
```

A finalized diagram is tracked in [#31](https://github.com/Akshatvyas05/ai-code-assistant/issues/31) /
[#27](https://github.com/Akshatvyas05/ai-code-assistant/issues/27).

### Why async?

LLM calls take seconds and can fail or rate-limit. Decoupling the upload request
from the review work via Kafka keeps the API responsive, gives us a natural retry
and dead-letter boundary, and lets the consumer scale independently. The full
"why this, not the alternative" write-up per component is tracked in
[#32](https://github.com/Akshatvyas05/ai-code-assistant/issues/32).

---

## Tech stack

| Layer | Choice | Notes |
|-------|--------|-------|
| Language | Java 21 | Toolchain pinned in `build.gradle` |
| Framework | Spring Boot 4.1.0 | Web MVC, Data JPA, Security, Validation, Actuator |
| Build | Gradle (wrapper committed) | `./gradlew` |
| Database | PostgreSQL | `spring.jpa.hibernate.ddl-auto=update` for now; migrations planned ([#20](https://github.com/Akshatvyas05/ai-code-assistant/issues/20)) |
| Auth | JJWT 0.12.6 (HS256) | Access token + rotating refresh token |
| Messaging | Apache Kafka | Planned — local via Docker |
| Cache / rate limit | Redis | Planned — local via Docker |
| LLM | Anthropic Claude (default) | Provider abstraction planned |
| Frontend | React + TypeScript, React Query, Vite | Planned — separate `frontend/` module |

---

## Getting started

### Prerequisites

- JDK 21 (`java -version` should report 21)
- PostgreSQL 14+ running locally
- Docker (for Kafka and Redis, once those land)

### 1. Create the database

```bash
createdb demo_db
```

The default connection is `jdbc:postgresql://localhost:5432/demo_db` with user
`postgres` / password `postgres`. Override via environment variables (see
[Configuration](#configuration)).

### 2. Run the backend

```bash
./gradlew bootRun
```

The API starts on `http://localhost:8080`.

### 3. Verify

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP", ...}
```

### 4. Run the tests

```bash
./gradlew test
```

---

## Configuration

`src/main/resources/application.properties` holds development defaults. **Do not
commit real secrets.** The following should be externalized before any deployment
(tracked in [#30](https://github.com/Akshatvyas05/ai-code-assistant/issues/30)):

| Property | Default | Notes |
|----------|---------|-------|
| `server.port` | `8080` | |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/demo_db` | |
| `spring.datasource.username` / `.password` | `postgres` / `postgres` | Move to env vars |
| `app.jwt.refresh-expiration-ms` | `604800000` (7 days) | Used by `RefreshTokenService` |

> **Known issue:** the JWT signing secret and access-token TTL (120 s) are
> currently hard-coded in `JwtService`. Externalizing them is part of the
> security hardening work — see the plan and [#10](https://github.com/Akshatvyas05/ai-code-assistant/issues/10) /
> [#23](https://github.com/Akshatvyas05/ai-code-assistant/issues/23).

---

## API reference

### `POST /auth/register`

```json
// request
{ "name": "Ada", "email": "ada@example.com", "password": "secret123" }

// 201 Created
{
  "accessToken": "eyJ...",
  "refreshToken": null,
  "tokenType": "Bearer",
  "user": { "id": 1, "name": "Ada", "email": "ada@example.com", "createdAt": "..." }
}
```

- `409 Conflict` if the email is already registered.
- `400 Bad Request` (`ProblemDetail` with a `errors` map) on validation failure.

### `POST /auth/login`

```json
// request
{ "email": "ada@example.com", "password": "secret123" }

// 200 OK — same AuthResponse shape as register
```

- `401 Unauthorized` (`ProblemDetail`) on bad credentials.

### `GET /test/me` *(protected)*

```
Authorization: Bearer <accessToken>

// 200 OK
{ "message": "Successfully accessed protected endpoint!", "authenticatedEmail": "ada@example.com" }
```

### `GET /actuator/health`

Public. Returns component health details.

> `POST /auth/refresh`, `POST /auth/logout`, `POST /review/upload`, and
> `GET /review/{id}/stream` are planned — see the roadmap.

---

## Project layout

```
src/main/java/com/akshat/ai_code_assistant/
├── AiCodeAssistantApplication.java
├── config/          SecurityConfig, JwtAuthenticationFilter
├── controller/      AuthenticationController, TestController
├── dto/             Register/Login/RefreshToken requests, Auth/User responses
├── entity/          User, RefreshToken
├── exception/       GlobalExceptionHandler, InvalidCredentialException
├── repository/      UserRepository, RefreshTokenRepository
└── service/         JwtService, RefreshTokenService
```

> **Note on branches:** `origin/feature/redis` is an early divergent spike that
> predates the merged refresh-token work and should not be treated as current.
> Active work happens on issue branches off `main`.

---

## Roadmap

Development is organized into phases. Full detail, sequencing, and the
issue-by-issue breakdown are in the
[Design & Development Plan](docs/DESIGN_AND_DEVELOPMENT_PLAN.md).

1. **Auth hardening** — wire `/auth/refresh` & `/auth/logout`, externalize secrets,
   validation edge cases, integration tests, OpenAPI docs.
   ([#5](https://github.com/Akshatvyas05/ai-code-assistant/issues/5)–[#10](https://github.com/Akshatvyas05/ai-code-assistant/issues/10), [#23](https://github.com/Akshatvyas05/ai-code-assistant/issues/23))
2. **Kafka pipeline** — topics, producer, consumer group, upload endpoint.
   ([#11](https://github.com/Akshatvyas05/ai-code-assistant/issues/11)–[#14](https://github.com/Akshatvyas05/ai-code-assistant/issues/14))
3. **LLM integration** — call the model from the consumer, retry & dead-letter.
   ([#15](https://github.com/Akshatvyas05/ai-code-assistant/issues/15), [#16](https://github.com/Akshatvyas05/ai-code-assistant/issues/16))
4. **Redis** — cache review results, per-user rate limiting.
   ([#17](https://github.com/Akshatvyas05/ai-code-assistant/issues/17)–[#19](https://github.com/Akshatvyas05/ai-code-assistant/issues/19))
5. **Storage & streaming** — results schema/migrations, SSE.
   ([#20](https://github.com/Akshatvyas05/ai-code-assistant/issues/20), [#21](https://github.com/Akshatvyas05/ai-code-assistant/issues/21), [#22](https://github.com/Akshatvyas05/ai-code-assistant/issues/22))
6. **Frontend** — React Query scaffold, upload UI, SSE result view.
   ([#24](https://github.com/Akshatvyas05/ai-code-assistant/issues/24)–[#26](https://github.com/Akshatvyas05/ai-code-assistant/issues/26))
7. **Ship v1** — polish, deploy, docs, diagram, test coverage.
   ([#27](https://github.com/Akshatvyas05/ai-code-assistant/issues/27)–[#35](https://github.com/Akshatvyas05/ai-code-assistant/issues/35))

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for branch naming, commit conventions, the
PR checklist, and local setup. Issues labeled
[`good first issue`](https://github.com/Akshatvyas05/ai-code-assistant/labels/good%20first%20issue)
are a good entry point.

## License

No license file yet. Until one is added, this code is "all rights reserved" by the
repository owner.
