# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Backend API server for "안티에이징 DNA" (Anti-Aging DNA) — a Spring Boot REST API. Domain so far covers user accounts, a lifestyle/sleep diary, and DNA test result data (see `docs/diagram/ERD.drawio` for the entity model: `user`, `diary`, `dna_info`). The codebase is currently an early-stage skeleton (health check endpoint + CORS config only) — expect to be building out entities, repositories, and controllers for these domains from scratch.

## Commands

```bash
# Build
./gradlew build

# Run the app locally (requires DB_URL, DB_USERNAME, DB_PASSWORD env vars — see below)
./gradlew bootRun

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "cloud.anzaanza.antiagingdna.AntiagingdnaApplicationTests"

# Run a single test method
./gradlew test --tests "cloud.anzaanza.antiagingdna.AntiagingdnaApplicationTests.contextLoads"
```

On Windows use `gradlew.bat` instead of `./gradlew`.

There is no linter configured in this project.

## Architecture

- **Stack**: Spring Boot 4.1.0, Java 21, Spring Data JPA, MySQL (`mysql-connector-j`), springdoc-openapi (Swagger UI), Lombok.
- **Base package**: `cloud.anzaanza.antiagingdna`, organized by layer (`config`, `controller`, and presumably `service`/`repository`/`entity` as they're added).
- **Database**: MySQL, connection configured entirely via env vars (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) in `src/main/resources/application.properties` — no local defaults, so these must be set to run the app at all (locally or in Docker). `spring.jpa.hibernate.ddl-auto=update` — schema is auto-migrated from entities, there are no migration scripts.
- **CORS**: wide-open CORS policy is defined centrally in `config/CorsConfig.java` (all origins/methods/headers allowed, credentials enabled) — don't add per-controller `@CrossOrigin` on top of it.
- **Deployment**: Dockerized (multi-stage `Dockerfile`, builds a `bootJar`). CI/CD is GitHub Actions (`.github/workflows/deploy.yml`): on push to `main`, builds a multi-arch (amd64/arm64) image, pushes to Docker Hub, then SSHes into the EC2 host and redeploys the container. Infra details (host, containers, nginx, DNS, required GitHub secrets) are documented in `docs/INFRA_INFO.md` — check it before touching deploy config.
- **Docs**: `docs/diagram/ERD.drawio` is the entity-relationship diagram; keep it in sync with entities as they're added (a drawio-editing skill is available for this — see `docs/diagram/SKILL.md`). `docs/ui/` holds UI mockups (auth flow, diary) for the client this API serves.
