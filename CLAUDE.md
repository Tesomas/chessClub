# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

ChessClub is a Spring Boot 4 + Kotlin + MongoDB backend service, a tool for people organizing local chess clubs: track player ELO, record game history, and run Swiss, round robin, and elimination tournaments. It starts with a single admin user, but the design must allow members to log in later to record games and enter tournaments. See `docs/GOALS.md` for the full goals and the multi-user design principles.

## Tech Stack

- **Kotlin 2.2.21** on **Java 24** (Kotlin does not support Java 26 yet)
- **Spring Boot 4.0.6** with Kotlin Spring plugin
- **Spring Data MongoDB** for persistence
- **JUnit 5** for testing (via `kotlin-test-junit5`)
- **Gradle** (Kotlin DSL) as the build tool

## Commands

```bash
# Build
./gradlew build

# Run
./gradlew bootRun

# Run against a throwaway Testcontainers MongoDB (no local Mongo needed)
./gradlew bootTestRun

# Unit + component + integration tests, then JaCoCo report (build/reports/jacoco/test/html/index.html)
./gradlew test

# Bruno API suite: boots the app + Mongo container itself, runs api-tests/bruno, stops the app
# JUnit report: build/test-results/bruno/results.xml. First time: (cd api-tests && npm install)
./gradlew apiTest

# Both of the above
./gradlew testAll

# Docker-free subset (MockK unit tests + @WebMvcTest slices)
./gradlew test --tests "*ServiceTest" --tests "*ApiExceptionHandlerTest" --tests "*WebTest"

# Test (single class)
./gradlew test --tests "org.tesomas.chessclub.service.GameServiceTest"

# Compile only
./gradlew compileKotlin

# Clean
./gradlew clean
```

On Windows use `gradlew.bat` or `.\gradlew` in PowerShell.

**Docker Desktop must be running** for `test`, `apiTest`, `testAll` and `bootTestRun`. Repository tests, `@SpringBootTest`
integration tests and the Bruno runner use Testcontainers (`mongo:7`) via `TestcontainersConfiguration`.
`apiTest` runs the app on port 18080 (must be free); set `API_TEST_PORT=<port>` to use another port.

Test layout (`src/test/kotlin/org/tesomas/chessclub/`): `service/*ServiceTest` (MockK unit), `controller/*WebTest`
(`@WebMvcTest` + `@MockkBean`), `repository/*RepositoryTest` (`@DataMongoTest`), `controller/*ControllerTest` and
`config/SwaggerConfigTest` (full `@SpringBootTest`). Shared builders live in `TestFixtures` (`aPlayer()`, `aGame()`).

## Frontend Commands (Angular)

Run these from the `frontend/` directory:

```bash
# Install dependencies (first time only)
npm install

# Dev server (port 4200, proxies /api → localhost:8080)
npm start

# Production build
npm run build

# Run tests
npm test
```

## Architecture

Package root: `org.tesomas.chessclub`

Standard Spring Boot layering:

```
model/       — @Document data classes (Player, Game) + GameType enum
repository/  — MongoRepository interfaces (PlayerRepository, GameRepository)
service/     — Business logic (PlayerService, GameService); GameService validates player IDs exist on write
controller/  — REST controllers under /api/players and /api/games
```

**Domain:**
- `Player` — club member with first/last name and four ELO fields (rapid, blitz, bullet, classical)
- `Game` — recorded game linking two player IDs (soft refs), rating snapshots at game time, `GameType` enum, time-control string, date, and PGN string
- Player IDs in Game are soft references — `GameService` checks `playerRepository.existsById` at create/update time

**Endpoints:** `GET/POST /api/players`, `GET/PUT/DELETE /api/players/{id}` — same pattern for `/api/games`

MongoDB URI defaults to `mongodb://localhost:27017/chessclub`; override with `MONGODB_URI` env var.

## Logging

Log4j2 (Logback is excluded build-wide), configured in `src/main/resources/log4j2-spring.xml`.

- Output: console + `logs/chessClub.log` (rolled daily and at 10 MB to `chessClub-<date>-<n>.log.gz`, 14 kept). Override the directory with the `LOG_DIR` system property or env var; Gradle tests write to `build/test-logs/`.
- Every request gets a `requestId` (from a safe incoming `X-Request-Id` header, else a UUID) that appears in each log line and is echoed in the `X-Request-Id` response header (`web/RequestLoggingFilter`).
- Change levels: `logging.level.<package>=DEBUG` in a properties file (overrides the XML), edit `log4j2-spring.xml` (reloaded within 30 s), or at runtime in the `local` profile via `POST /actuator/loggers/org.tesomas.chessclub` with `{"configuredLevel":"DEBUG"}`.
- Services log INFO on create/update/delete (ids only), DEBUG on reads, WARN on not-found. Never log PGN or whole entities at INFO.

## Kotlin Conventions

- Prefer `val` over `var`; use `data class` for domain models
- Use `?.` and `?:` — never `!!`
- Use `suspend` functions in service/repository layers for async operations
- Use sealed interfaces for modeling closed result/state hierarchies
- Compiler flags already configured: `-Xjsr305=strict` (null safety for Java interop) and `-Xannotation-default-target=param-property` (needed for Spring constructor injection with Kotlin data classes)
