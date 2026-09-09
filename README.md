# AI Code Assistant

An AI-powered code review service: upload code, get automated review feedback via an async pipeline (Kafka → LLM → cached results), consumed by a React frontend.

## Repo structure

```
backend/     Spring Boot (Java 21, Gradle) — REST API, auth, data layer
frontend/    React + TypeScript + Vite — UI, React Query, API client
.github/workflows/pr-ci.yml   CI: backend tests/build, frontend lint/build, dependency scan, CodeQL, secret scan
```

## Progress

### ✅ Done
- [x] JWT authentication + protected API endpoints (#1)
- [x] Refresh tokens — issue, store, rotate (#4)
- [x] React Query setup skeleton: Vite + TS scaffold, React Query provider, API client (#24)
- [x] PR CI pipeline: backend tests (with Postgres service), build verify, frontend lint/build, OWASP dependency check, CodeQL, Gitleaks secret scan

### 🚧 In progress / Up next
- [ ] Spring Security filter chain + role-based access (#5)
- [ ] Logout / token invalidation (#6)
- [ ] Input validation edge cases (#7)
- [ ] Integration tests for full auth flow (#8)
- [ ] API docs for auth endpoints (#9)
- [ ] Full auth review + refactor pass (#10)

### 📋 Backlog
- [ ] Kafka topic setup + producer skeleton (#11)
- [ ] Kafka consumer group wiring (#12)
- [ ] Upload endpoint triggers Kafka event (#13)
- [ ] Consumer skeleton processes events (#14)
- [ ] LLM API call wired into consumer (#15)
- [ ] Retry/error handling for LLM calls (#16)
- [ ] Redis caching for review results (#17)
- [ ] Rate limiting via Redis per user (#18)
- [ ] Service layer refactor using Spring internals (#19)
- [ ] Result storage schema finalized (#20)
- [ ] SSE streaming response setup (#21)
- [ ] End-to-end pipeline integration test (#22)
- [ ] Security filter chain audit (#23)
- [ ] Frontend upload UI (#25)
- [ ] SSE/streaming frontend integration (#26)
- [ ] README + architecture diagram draft (#27)
- [ ] Polish + bug fixes pass (#28)
- [ ] Deploy v1 (#29)
- [ ] Fix deploy-related frontend issues (#30)
- [ ] Architecture diagram finalized (#31)
- [ ] Add basic test coverage (#33)
- [ ] README finalize + demo GIF/video (#34)

## Getting started

### Backend
```
cd backend
./gradlew bootRun
```
Requires a local Postgres at `localhost:5432/demo_db` (user/password: `postgres`), matching [backend/src/main/resources/application.properties](backend/src/main/resources/application.properties).

### Frontend
```
cd frontend
cp .env.example .env
npm install
npm run dev
```

## CI

Every PR runs: backend unit tests (against a Postgres service container), a full build verify, frontend lint + build, an OWASP dependency vulnerability scan, CodeQL static analysis, and a Gitleaks secret scan. See [.github/workflows/pr-ci.yml](.github/workflows/pr-ci.yml).
