# Spring Boot 4 Project Guidance

> This file mirrors [AGENTS.md](AGENTS.md) for Claude Code — keep the two in sync.
> Skills under `.claude/skills/` mirror `.codex/skills/` (minus Codex-only `agents/` metadata);
> when a skill changes, update both copies.

## Project Context

This repository is the JPetStore modernization workspace:

- Legacy application: Spring 3.2 / MyBatis 3 / Spring Security 3.1 / JSP, targets Java 6 (to be archived under `legacy/`).
- Modern rewrite (not yet created): Java 21 + Spring Boot 4 + Spring Security + MyBatis + MySQL 8 + Vue 3 + TypeScript, modular monolith.

Read these in order before starting any work; they are the authority for scope and process:

1. [docs/spec.md](docs/spec.md) — business requirements baseline (ACC/CAT/CRT/ORD/INV/PER rules, D01–D12 defects, Q01–Q15 confirmed decisions).
2. [docs/plan.md](docs/plan.md) — technical design (module boundaries, REST contract, MySQL schema, testing strategy).
3. [docs/tasks.md](docs/tasks.md) — the T001–T447 task list, execution protocol, and progress record (see 当前执行记录 for current state).

Follow the tasks.md execution protocol: TDD (RED before GREEN), one logical file per task, record command + result evidence for every completed task, never mark unverified work as done.

## Stack

- Spring Boot 4.x, Spring Framework 7, Java 21
- Maven wrapper: `./mvnw`
- Jakarta EE 11, Jackson 3, and Boot 4 modular starters
- Use dedicated technology test starters rather than the classic test starter

## Commands

- Build: `./mvnw verify`
- Unit tests: `./mvnw test`
- Single test: `./mvnw -Dtest=ClassName test`
- Native tests: use the configured native profile; do not invent one

## Engineering Rules

- Inspect neighboring code and the dependency tree before choosing an API or starter.
- Keep controllers as adapters and transactions in application services.
- Use DTOs; do not expose Hibernate entities as external contracts.
- Use `tools.jackson` APIs for Jackson customization.
- Use `@MockitoBean`/`@MockitoSpyBean` and explicit test auto-configuration.
- Add Flyway migrations for schema changes and never edit applied migrations.
- Preserve error, security, pagination, nullability, and observability contracts.

## Skills

Skills live in `.claude/skills/` and are auto-discovered; invoke the relevant one by name with the Skill tool before implementing. Typical combinations:

- Upgrade: `spring-boot-migration`, `testing-pyramid`
- Endpoint: `rest-api-conventions`, `api-versioning`, `testing-pyramid`
- Persistence: `spring-data-jpa`, `transactional-patterns`, `flyway-migrations`
- Production: `production-observability`, `container-native-deployment`

## Verification

Run focused tests and then `./mvnw verify`. For native changes, test the produced native executable.
Report any verification that could not run.
