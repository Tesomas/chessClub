# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

ChessClub is a Spring Boot 4 + Kotlin + MongoDB backend service. The codebase is in early stages — only the application entry point and a context-loads test exist so far.

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

# Test (all)
./gradlew test

# Test (single class)
./gradlew test --tests "org.tesomas.chessclub.ChessClubApplicationTests"

# Compile only
./gradlew compileKotlin

# Clean
./gradlew clean
```

On Windows use `gradlew.bat` or `.\gradlew` in PowerShell.

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

## Kotlin Conventions

- Prefer `val` over `var`; use `data class` for domain models
- Use `?.` and `?:` — never `!!`
- Use `suspend` functions in service/repository layers for async operations
- Use sealed interfaces for modeling closed result/state hierarchies
- Compiler flags already configured: `-Xjsr305=strict` (null safety for Java interop) and `-Xannotation-default-target=param-property` (needed for Spring constructor injection with Kotlin data classes)
