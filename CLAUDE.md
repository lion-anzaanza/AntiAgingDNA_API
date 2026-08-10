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
- **Scoring**: `service/scoring/` implements the Notion 기획 3부작 formulas — `ItemScores` (§8 normalization), `BaselineCalculator` (§A-3, onboarding → area baselines), `DiaryScorer` (§B-2, one day's diary → area scores), `ScoreCombiner` (§5, `C_c(t) = (1−α)·baseline + α·mean7`, `α(n)=n/(n+7)`). All four are pure static functions; `service/ScoringService.java` is the only bean and owns persistence. Tunable parameters live in `scoring.*` properties (`config/ScoringProperties.java`), never as code constants — they are slated for recalibration and `daily_score.scoring_version` records which set produced each row. **Do not invent anchor values**: every number traces to a table in the 기획, and items whose anchors are missing (e.g. 기분 회복 활동) are deliberately left out of scoring rather than guessed. `daily_score` is a derived read model — always reconstructible from `dna_info` + `diary`, filled read-through by `ScoringService.scoreOn`. Any write to a diary must call `ScoringService.recalculate(userId, date)` in the same transaction (see `DiaryService`).
- **Diary**: `DiaryService`/`DiaryController` are a **draft** — the diary domain belongs to another developer, and this implementation exists so the scoring pipeline can be exercised against real data. The API shape (`PUT /api/diaries/{date}` upsert) is provisional. `DiaryRequest` accepts a superset of the mockup: the five 기획-confirmed-but-unmocked fields are optional, so a mockup-only client still works. Updates go through `Diary.replaceWith` (full replace, not PATCH — a missing field means "cleared", which is what scoring needs); `DiaryReplaceCoverageTest` guards against forgetting a field there.
- **Missing vs zero**: throughout scoring, a null item means "not recorded" and is excluded then renormalized (기획 일지 §5) — never scored as 0. The one exception is 운동 "안 함", which is a real 0.
- **Errors**: `controller/ApiExceptionHandler.java` extends `ResponseEntityExceptionHandler` and returns RFC 9457 `application/problem+json` for everything. Don't enable `spring.mvc.problemdetails` — it registers a competing advice that wins on validation errors.
- **CORS**: wide-open CORS policy is defined centrally in `config/CorsConfig.java` (all origins/methods/headers allowed, credentials enabled) — don't add per-controller `@CrossOrigin` on top of it.
- **Deployment**: Dockerized (multi-stage `Dockerfile`, builds a `bootJar`). CI/CD is GitHub Actions (`.github/workflows/deploy.yml`): on push to `main`, builds a multi-arch (amd64/arm64) image, pushes to Docker Hub, then SSHes into the EC2 host and redeploys the container. Infra details (host, containers, nginx, DNS, required GitHub secrets) are documented in `docs/INFRA_INFO.md` — check it before touching deploy config.
- **Docs**: `docs/diagram/ERD.drawio` is the entity-relationship diagram; keep it in sync with entities as they're added (a drawio-editing skill is available for this — see `docs/diagram/SKILL.md`). `docs/ui/` holds UI mockups (auth flow, diary) for the client this API serves.
