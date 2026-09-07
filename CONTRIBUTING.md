# Contributing to AI Code Assistant

Thanks for taking the time to contribute. This is a solo learning project first,
so the workflow is deliberately lightweight but consistent.

## Table of contents

- [Ground rules](#ground-rules)
- [Local setup](#local-setup)
- [Picking something to work on](#picking-something-to-work-on)
- [Branching](#branching)
- [Commit messages](#commit-messages)
- [Pull requests](#pull-requests)
- [Coding conventions](#coding-conventions)
- [Testing](#testing)
- [Security](#security)

---

## Ground rules

- One issue → one branch → one PR. Keep changes scoped to a single issue.
- `main` is always releasable. Never push directly to `main`; open a PR.
- Prefer small PRs that are easy to review over large ones that touch everything.
- If a change grows beyond its issue, stop and split it.

---

## Local setup

Prerequisites: JDK 21, PostgreSQL, Docker (for Kafka/Redis once they land).

```bash
git clone https://github.com/Akshatvyas05/ai-code-assistant.git
cd ai-code-assistant
createdb demo_db
./gradlew build      # compiles + runs tests
./gradlew bootRun    # starts the API on :8080
```

See [README.md](README.md#configuration) for configuration and environment
variables. Never commit real credentials or API keys.

---

## Picking something to work on

- Browse [open issues](https://github.com/Akshatvyas05/ai-code-assistant/issues).
- Issues are roughly ordered by the phases in the
  [Design & Development Plan](docs/DESIGN_AND_DEVELOPMENT_PLAN.md) — earlier phases
  unblock later ones, so prefer the lowest-numbered unblocked issue.
- Issues labeled `good first issue` are self-contained and well-scoped.
- Comment on the issue (or assign yourself) before starting so work isn't duplicated.
- If something you want to do isn't an issue yet, open one first and describe the
  intent and the "why".

---

## Branching

Branch off the latest `main`. Name branches `<issue-number>-<short-slug>`:

```
git switch main && git pull
git switch -c 11-kafka-topic-setup
```

This matches the existing history (e.g. `4-refresh-tokens-issue-store-rotate`).

---

## Commit messages

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <summary>

<optional body — the "why", not the "what">
```

- **type**: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `build`, `perf`
- **scope**: the area touched — `auth`, `kafka`, `redis`, `llm`, `sse`, `frontend`, `config`
- Reference the issue in the body or summary, e.g. `feat(kafka): add review topic (#11)`
- Imperative mood, no trailing period, summary under ~72 chars.

Examples from this repo's style:

```
feat(auth): refresh tokens - issue, store, rotate (#4)
feat(auth): JWT token integration (#1)
```

---

## Pull requests

Open the PR against `main`. In the description:

1. Link the issue: `Closes #NN`.
2. Summarize **what** changed and **why** this approach over alternatives — this
   doubles as the interview narrative tracked in
   [#32](https://github.com/Akshatvyas05/ai-code-assistant/issues/32).
3. Note anything intentionally left out or deferred to a follow-up issue.

### PR checklist

- [ ] Branch is named `<issue-number>-<slug>` and based on current `main`
- [ ] `./gradlew build` passes locally (compile + tests)
- [ ] New behavior has at least one test (see [Testing](#testing))
- [ ] No secrets, API keys, or real credentials in the diff
- [ ] Public API changes are reflected in `README.md` and (once it exists) the OpenAPI spec
- [ ] Config additions have a sensible default and are documented in `README.md`
- [ ] The PR description explains the "why"

Squash-merge is preferred so `main` stays one commit per issue.

---

## Coding conventions

- **Java**: standard Spring style. 4-space indentation, one class per file.
- **Layering**: `controller` → `service` → `repository`. Controllers stay thin —
  no business logic, no direct repository calls for anything non-trivial.
- **DTOs are records**. Never expose JPA entities directly over the wire.
- **Validation** goes on request DTOs with `jakarta.validation` annotations;
  `GlobalExceptionHandler` turns failures into RFC 7807 `ProblemDetail` responses.
  Add new exception handlers there rather than catching in controllers.
- **Errors**: throw a domain exception; let `GlobalExceptionHandler` map it.
  Don't return raw strings for error bodies (the current `register` path does this
  and should be migrated).
- **Configuration**: inject with `@Value` or `@ConfigurationProperties` — no new
  hard-coded secrets, ports, or timeouts.
- **Nullability**: prefer `Optional` on repository lookups, as the existing
  repositories do.
- Keep imports tidy; no wildcard imports except where the existing code already
  uses them in entities.

---

## Testing

- Framework: JUnit 5 via `spring-boot-starter-*-test`.
- Every PR that adds or changes behavior needs a test. Bug fixes need a test that
  fails before the fix.
- Prefer slice tests (`@WebMvcTest`, `@DataJpaTest`) for units; use
  `@SpringBootTest` for the end-to-end auth and pipeline flows tracked in
  [#8](https://github.com/Akshatvyas05/ai-code-assistant/issues/8) and
  [#22](https://github.com/Akshatvyas05/ai-code-assistant/issues/22).
- External systems (Kafka, Redis, the LLM API) should be faked or run via
  Testcontainers rather than hit for real in tests.

```bash
./gradlew test
```

---

## Security

- Do not open a public issue for a vulnerability. Contact the repository owner
  directly.
- The known hard-coded JWT secret and short token TTL are already tracked — see
  the plan and [#10](https://github.com/Akshatvyas05/ai-code-assistant/issues/10) /
  [#23](https://github.com/Akshatvyas05/ai-code-assistant/issues/23).
- Never log tokens, passwords, or raw uploaded code at `INFO` or above.
