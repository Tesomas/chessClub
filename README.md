# ChessClub

A tool for people who organize local chess clubs: track player ELO, record game history, and (planned) run
Swiss, round robin and elimination tournaments. It starts with a single admin user and is designed so
members can log in later. See [docs/GOALS.md](docs/GOALS.md) for the goals and design principles.

**Stack:** Kotlin 2.2 on Java 24, Spring Boot 4, Spring Data MongoDB, Log4j2, and an Angular frontend.

## Quick start

Prerequisites: JDK 24, a MongoDB instance (or Docker), and Node.js for the frontend.

```bash
# Backend, against MongoDB at mongodb://localhost:27017/chessclub (override with MONGODB_URI)
./gradlew bootRun

# Backend against a throwaway Testcontainers MongoDB (needs Docker, no local Mongo)
./gradlew bootTestRun

# Frontend (http://localhost:4200, proxies /api to localhost:8080)
cd frontend && npm install && npm start
```

On Windows use `gradlew.bat`.

## Tools and documentation

| What | Where |
|---|---|
| Product goals and multi-user design principles | [docs/GOALS.md](docs/GOALS.md) |
| Interactive API docs (Swagger UI) | `http://localhost:8080/swagger-ui.html` while the app runs |
| OpenAPI spec (JSON) | `http://localhost:8080/v3/api-docs` |
| Bruno API test suite | [api-tests/README.md](api-tests/README.md) |
| Frontend (Angular) | [frontend/README.md](frontend/README.md) |
| Build, test commands, architecture, logging, Kotlin conventions | [CLAUDE.md](CLAUDE.md) |

## Testing

```bash
./gradlew test       # unit, component and integration tests + JaCoCo report (build/reports/jacoco/test/html)
./gradlew apiTest    # Bruno API suite (see api-tests/README.md)
./gradlew testAll    # both
```

Docker must be running for all of these. The Docker-free subset (MockK unit tests and `@WebMvcTest` slices):

```bash
./gradlew test --tests "*ServiceTest" --tests "*ApiExceptionHandlerTest" --tests "*WebTest"
```

## Logging

Logs go to the console and `logs/chessClub.log`. Every request gets an `X-Request-Id`, echoed in the response
and included in each log line. Details are in [CLAUDE.md](CLAUDE.md#logging).
