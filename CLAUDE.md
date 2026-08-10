# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Backend API server for "안티에이징 DNA" / LifeDNA — a Spring Boot REST API. The schema covers user accounts (`user`, `user_agreement`), the onboarding diagnosis (`dna_info`), the daily diary (`diary`), and derived scores (`daily_score`) — see `docs/diagram/ERD.drawio`. Entities, enums, and repositories exist for all five tables. Auth (signup/login) is implemented; diary and scoring endpoints are not.

## Commands

```bash
# Build
./gradlew build

# Run the app locally (requires DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET env vars — see below)
./gradlew bootRun

# Run all tests
# NOTE: AntiagingdnaApplicationTests.contextLoads always fails — @SpringBootTest needs a real
# MySQL and there is none in CI or locally. Every other test is DB-free by design.
./gradlew test

# Run a single test class
./gradlew test --tests "cloud.anzaanza.antiagingdna.AntiagingdnaApplicationTests"

# Run a single test method
./gradlew test --tests "cloud.anzaanza.antiagingdna.AntiagingdnaApplicationTests.contextLoads"
```

On Windows use `gradlew.bat` instead of `./gradlew`.

There is no linter configured in this project.

## Architecture

- **Stack**: Spring Boot 4.1.0, Java 21, Spring Data JPA, MySQL (`mysql-connector-j`), Flyway, Spring Security + OAuth2 Resource Server (JWT), springdoc-openapi (Swagger UI), Lombok. Note Boot 4 uses **Jackson 3** (`tools.jackson`) — the auto-configured `ObjectMapper` is not `com.fasterxml`.
- **Base package**: `cloud.anzaanza.antiagingdna`, organized by layer (`config`, `controller`, `service`, `repository`, `entity`, `dto`, `exception`).
- **Database**: MySQL, connection configured entirely via env vars (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) in `src/main/resources/application.properties` — no local defaults, so these must be set to run the app at all. **Flyway (`src/main/resources/db/migration/`) is the single source of the schema** and `spring.jpa.hibernate.ddl-auto=validate` makes the app refuse to boot when entity mappings and tables disagree. Change the schema by adding a `V<n>__*.sql`, never by editing entities alone. Boot 4 splits autoconfiguration per module — `org.springframework.boot:spring-boot-flyway` must be on the classpath, `flyway-core` alone silently does nothing.
- **Auth**: JWT bearer tokens. `config/SecurityConfig.java` holds the filter chain (stateless, public endpoints listed there) and the `JwtEncoder`/`JwtDecoder`/`PasswordEncoder` beans; token issuance is `service/TokenService.java`. Signing key comes from the `JWT_SECRET` env var (≥32 bytes, no default). Token validation is Spring Security's `oauth2ResourceServer` — do not hand-write a JWT filter.
- **Errors**: `controller/ApiExceptionHandler.java` extends `ResponseEntityExceptionHandler` and returns RFC 9457 `application/problem+json` for everything. Don't enable `spring.mvc.problemdetails` — it registers a competing advice that wins on validation errors.
- **CORS**: wide-open CORS policy is defined centrally in `config/CorsConfig.java` (all origins/methods/headers allowed, credentials enabled) — don't add per-controller `@CrossOrigin` on top of it.
- **Deployment**: Dockerized (multi-stage `Dockerfile`, builds a `bootJar`). CI/CD is GitHub Actions (`.github/workflows/deploy.yml`): on push to `main`, builds a multi-arch (amd64/arm64) image, pushes to Docker Hub, then SSHes into the EC2 host and redeploys the container. Infra details (host, containers, nginx, DNS, required GitHub secrets) are documented in `docs/INFRA_INFO.md` — check it before touching deploy config.
- **Docs**: `docs/diagram/ERD.drawio` is the entity-relationship diagram; keep it in sync with entities as they're added (a drawio-editing skill is available for this — see `docs/diagram/SKILL.md`). `docs/ui/` holds UI mockups (auth flow, diary) for the client this API serves.
