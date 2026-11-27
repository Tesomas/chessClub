# API tests (Bruno)

End-to-end tests for the ChessClub REST API, written as a [Bruno](https://www.usebruno.com/) collection in
`bruno/` and run headless with the Bruno CLI (`@usebruno/cli`, pinned in `package.json`).

## Running

Requirements: Node 20+, and Docker running (the app is started against a throwaway Testcontainers MongoDB).

```bash
# First time only
(cd api-tests && npm install)

# From the repo root
./gradlew apiTest          # or: ./gradlew testAll   (unit/integration tests, then these)

# Or directly, without Gradle
cd api-tests && npm run test:api
```

`run-api-tests.mjs` does the whole cycle:

1. Starts the app with `gradlew bootTestRun` on port **18080** (override with `API_TEST_PORT=<port>`).
   It refuses to start if that port is already in use.
2. Waits up to 3 minutes for `GET /actuator/health` to report `UP`.
3. Runs the `players`, `games`, `docs` and `teardown` folders against the `local` environment.
4. Always stops the app, and exits with Bruno's exit code.

The JUnit report is written to `build/test-results/bruno/results.xml`.

### Against an app you already started

```bash
./gradlew bootRun                                   # app on :8080
cd api-tests/bruno
npx bru run players games docs teardown --env local --env-var baseUrl=http://localhost:8080
```

`npm run bru` does the same against the default `baseUrl` (`http://localhost:18080`). This talks to whatever
database the app is using, so prefer `bootTestRun` for a throwaway one.

### Using the Bruno desktop app

Open `api-tests/bruno` as a collection in Bruno and select the `local` environment.

## Layout

```
bruno/
  bruno.json               collection definition
  collection.bru           collection-level test: every response must arrive within 2 seconds
  environments/local.bru   variables: baseUrl (http://localhost:18080), unknownId (a well-formed id that does not exist)
  players/                 Player CRUD, 404s and malformed-JSON handling
  games/                   Game CRUD, including unknown white/black player ids
  docs/                    OpenAPI spec is served
  teardown/                deletes the players the games folder created
run-api-tests.mjs          self-contained runner described above
```

## Conventions

- **Order matters.** Requests in a folder run by `seq` and the files are numbered (`01-`, `02-`, ...).
  Later requests rely on variables set by earlier ones (for example `bru.setVar("playerId", res.getBody().id)`
  in a post-response script), so renumber carefully.
- **Folders run in the order given** in `BRUNO_FOLDERS` in `run-api-tests.mjs` (and the `bru` npm script).
  A new folder must be added to both, and `teardown` should stay last.
- **Clean up after yourself.** Each folder should delete what it creates, so the suite can be re-run against
  the same database.
- **Assert the status code and the response shape** in `tests { test(...) }` blocks.
  Cover the failure cases too (404, 400), not just the happy path.
- **Never hard-code the host.** Use `{{baseUrl}}`.

## Adding a request

1. Add a `NN-description.bru` file to the relevant folder (copy a neighbor such as
   `players/02-create-player.bru`), with the next `seq`.
2. Assert the status, content type and body fields. Save any ids later requests need with `bru.setVar`.
3. If it creates data, add a matching delete (in the same folder, or in `teardown/`).
4. Run `./gradlew apiTest`.
