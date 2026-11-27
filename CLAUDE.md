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
./gradlew test --tests "*ServiceTest" --tests "*ApiExceptionHandlerTest" --tests "*WebTest" --tests "org.tesomas.chessclub.model.*" --tests "*CriteriaTest"

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

# Dev server (port 4200, proxies /api → localhost:8081)
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
model/       — @Document data classes (Player, Game) + enums + player-games DTOs (PlayerGameFilter, PlayerGameView, PageResponse)
repository/  — MongoRepository interfaces (PlayerRepository, GameRepository) + PlayerGameQueryRepository (MongoTemplate)
service/     — Business logic (PlayerService, GameService, PlayerGameService); GameService validates player IDs exist on write
controller/  — REST controllers under /api/players, /api/players/{id}/games and /api/games
```

**Domain:**
- `Player` — club member with first/last name, four ELO fields (rapid, blitz, bullet, classical) and an optional `birthDate` (`LocalDate`, JSON `yyyy-MM-dd` or null)
- `Game` — recorded game linking two player IDs (soft refs), rating snapshots at game time, `GameType` enum, time-control string, date, and PGN string
- Player IDs in Game are soft references — `GameService` checks `playerRepository.existsById` at create/update time
- ELO is applied when a game is created (`GameService.create` → `EloCalculator`, K = 20, same formula as the seed): the game stores each player's rating *before* the game (`white/blackPlayerRating`) and the points gained/lost (`white/blackPlayerRatingChange`, zero-sum), and both players' rating for that game type is updated. Self-play games change nothing. Editing a game keeps its stored snapshot and changes and does not re-rate; deleting a game does not reverse its rating change.
- `LocalDate`s are stored as BSON dates at UTC midnight regardless of the server's time zone (driver java.time codecs, `config/MongoConfig`, proven by `MongoConfigTest`), matching `docker/mongo-seed/seed.js`. `@DataMongoTest` slices must `@Import(MongoConfig::class)`. Older builds stored local midnight; that reads back correctly only for data written west of UTC (all ours so far, US Central). Data written by an older build on a server east of UTC sits on the previous UTC day and would need normalizing before use.
- A self-play game (white and black are the same player) is listed once, viewed from the white side.

**Endpoints:** `GET/POST /api/players`, `GET/PUT/DELETE /api/players/{id}` — same pattern for `/api/games`

`GET /api/players/{id}/games` — the player's games from their perspective, as `PageResponse<PlayerGameView>`
(`content`, `page`, `size`, `totalElements`, `totalPages`; each item: `gameId`, `date`, `gameType`, `time`, `color`,
`outcome`, `playerRating`, `opponentId`, `opponentName`, `opponentRating`). Optional query params only:
`opponentName` (case-insensitive substring of the opponent's denormalized name; trimmed, blank = no filter, max 100
chars, no control characters; regex-escaped so it is matched literally), `from`/`to` (inclusive ISO dates within
1900-01-01..9999-12-31), `page` (0-based, default 0),
`size` (1–100, default 25), `sort` (`date|opponentName`, default `date`; the name sort is case-insensitive via a
lower-cased computed field, not a collation, so the `$match` can still use the indexes), `direction` (`asc|desc`, default `desc`;
ties broken by `_id`). Other params are ignored. 404 ProblemDetail for an unknown player, 400 ProblemDetail for invalid
params. Flow: `PlayerGameController` → `PlayerGameService` (404 check, maps `Game` → `PlayerGameView`) →
`PlayerGameQueryRepository` (`MongoTemplate` aggregation; `PlayerGameCriteria` builds the `$or` of a white and a black
branch). Validation lives in `PlayerGameFilter`'s constructor (`InvalidPlayerGameFilterException` → 400).
Indexes `{whitePlayerId:1,date:-1}` / `{blackPlayerId:1,date:-1}` (`@CompoundIndex` on `Game`, created at startup via
`spring.data.mongodb.auto-index-creation=true`, and by the seed) let each `$or` branch find the player's games by id
(and narrow by date range). They do not fully back the sort: the aggregation still sorts the matched games in memory
(always for `opponentName`, which is computed, and in practice for `date` too). That is fine at club scale (a few
hundred games per player); a large history would need a different approach.

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
