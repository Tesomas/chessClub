// Docker-free checks for seed.js: runs it against a stubbed mongosh `db`/`ObjectId`/`print`.
// Run with (from the repo root; the quoted glob is needed on Node 22, a bare directory is not expanded):
//   node --test "docker/mongo-seed/*.test.mjs"
// (Move legality of the PGN pool was verified once with chess.js when the pool was built.)
import { test } from "node:test";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const SEED_SOURCE = readFileSync(new URL("./seed.js", import.meta.url), "utf8");

// sha256 of the players and games (without `pgn`) produced by the seed before PGNs were added.
// If this changes, something other than the PGNs changed in the seeded data.
const PRE_PGN_FINGERPRINT = "c60e692ad34a3c0fb7935d25f02632837d85898ca312693cd0e5534536ad14d2";

const RESULT_TOKENS = { WHITE_WINS: "1-0", BLACK_WINS: "0-1", DRAW: "1/2-1/2" };
const EXPECTED_PER_PLAYER = { BULLET: 50, BLITZ: 40, RAPID: 30, CLASSICAL: 20 };
const EXPECTED_TIME_CONTROLS = { "1+0": "60", "3+2": "180+2", "10+0": "600", "90+30": "5400+30" };

/** Runs [source] (seed.js unless patched) in a fresh context; `context` lets tests read its top-level consts. */
function runSeed(source = SEED_SOURCE) {
  const collections = {};
  const database = new Proxy({}, {
    get: (_, name) => {
      const c = (collections[name] ??= { docs: [], indexes: [] });
      return { insertMany: (docs) => c.docs.push(...docs), createIndex: (keys) => c.indexes.push(keys) };
    },
  });
  const printed = [];
  const context = vm.createContext({
    db: { getSiblingDB: () => database },
    ObjectId: (hex) => ({ toHexString: () => hex, toJSON: () => `ObjectId(${hex})` }),
    print: (message) => printed.push(message),
  });
  vm.runInContext(source, context);
  return { players: collections.Players.docs, games: collections.games.docs, collections, printed, context };
}

// PGN tag pairs per the PGN spec: inside the quotes, `\"` is a quote and `\\` a backslash.
const TAG_LINE = /^\[(\w+) "((?:[^"\\\n]|\\.)*)"\]$/gm;

function headers(pgn) {
  const tags = {};
  for (const [, name, value] of pgn.matchAll(TAG_LINE)) tags[name] = value.replace(/\\(.)/g, "$1");
  return tags;
}

/** "2026-11-01T00:00:00.000Z" -> "2026.11.01", derived independently of seed.js's own formatter. */
function isoToPgnDate(date) {
  return date.toISOString().slice(0, 10).replaceAll("-", ".");
}

const seeded = runSeed();

test("inserts 40 players and 2800 games", () => {
  assert.equal(seeded.players.length, 40);
  assert.equal(seeded.games.length, 2800);
  assert.match(seeded.printed.join("\n"), /Inserted 40 players and 2800 games/);
});

test("every player keeps 50 bullet, 40 blitz, 30 rapid and 20 classical games", () => {
  for (const player of seeded.players) {
    const id = player._id.toHexString();
    const counts = {};
    for (const g of seeded.games) {
      if (g.whitePlayerId === id || g.blackPlayerId === id) counts[g.gameType] = (counts[g.gameType] ?? 0) + 1;
    }
    assert.deepEqual(counts, EXPECTED_PER_PLAYER, `${player.firstName} ${player.lastName}`);
  }
});

test("everything except the new pgn field is unchanged from the pre-PGN seed", () => {
  const games = seeded.games.map(({ pgn, ...rest }) => rest);
  const fingerprint = createHash("sha256").update(JSON.stringify({ players: seeded.players, games })).digest("hex");
  assert.equal(fingerprint, PRE_PGN_FINGERPRINT);
});

test("every game has a pgn whose Result header and final token match its result", () => {
  for (const g of seeded.games) {
    assert.equal(typeof g.pgn, "string");
    const token = RESULT_TOKENS[g.result];
    assert.equal(headers(g.pgn).Result, token);
    assert.equal(g.pgn.trim().split(/\s+/).at(-1), token, "the movetext ends with the result token");
  }
});

test("pgn headers describe the seeded game", () => {
  for (const g of seeded.games) {
    const tags = headers(g.pgn);
    assert.equal(tags.Event, "ChessClub Test Data");
    assert.equal(tags.Site, "ChessClub");
    assert.equal(tags.Date, isoToPgnDate(g.date));
    assert.equal(tags.White, g.whitePlayerName);
    assert.equal(tags.Black, g.blackPlayerName);
    assert.equal(tags.WhiteElo, String(g.whitePlayerRating));
    assert.equal(tags.BlackElo, String(g.blackPlayerRating));
    assert.equal(tags.TimeControl, EXPECTED_TIME_CONTROLS[g.time]);
  }
});

test("pgn is the seven-tag-roster order plus ratings and time control, a blank line, then movetext", () => {
  const order = ["Event", "Site", "Date", "White", "Black", "Result", "WhiteElo", "BlackElo", "TimeControl"];
  for (const g of seeded.games) {
    const [headerBlock, movetext, ...rest] = g.pgn.split("\n\n");
    assert.equal(rest.length, 0, "exactly one blank line");
    assert.deepEqual(headerBlock.split("\n").map((line) => line.match(/^\[(\w+) /)[1]), order);
    assert.match(movetext, /^1\. /);
    assert.ok(movetext.split("\n").every((line) => line.length <= 80), "movetext lines are at most 80 characters");
    assert.ok(g.pgn.endsWith("\n"), "pgn ends with a newline");
  }
});

test("the pgn pool is spread over the games and every pool game is used", () => {
  const distinctMovetexts = new Set(seeded.games.map((g) => g.pgn.split("\n\n")[1]));
  assert.equal(distinctMovetexts.size, 30);
  const draws = new Set(seeded.games.filter((g) => g.result === "DRAW").map((g) => g.pgn.split("\n\n")[1]));
  assert.ok(draws.size >= 4);
});

test("pgns stay well under the API's 65,536-character limit", () => {
  assert.ok(seeded.games.every((g) => g.pgn.length < 65_536));
});

test("Date headers match hand-written literals for the first and last seeded games", () => {
  const first = seeded.games[0];
  const last = seeded.games.at(-1);
  assert.match(first.pgn, /^\[Date "2025\.11\.08"\]$/m);
  assert.equal(first.date.toISOString(), "2025-11-08T00:00:00.000Z");
  assert.match(last.pgn, /^\[Date "2026\.11\.01"\]$/m);
  assert.equal(last.date.toISOString(), "2026-11-01T00:00:00.000Z");
});

test("tag values with quotes and backslashes are escaped per the PGN spec", () => {
  // Arrange: give player 0 (Alice) a last name that needs escaping; names do not feed the PRNG
  const lastName = 'O"Brien\\x';
  const patched = SEED_SOURCE.replace('"Anders",', JSON.stringify(lastName) + ",");
  assert.notEqual(patched, SEED_SOURCE, "patch must apply");

  // Act
  const { games } = runSeed(patched);

  // Assert
  const asWhite = games.find((g) => g.whitePlayerName === `Alice ${lastName}`);
  const asBlack = games.find((g) => g.blackPlayerName === `Alice ${lastName}`);
  assert.ok(asWhite && asBlack, "Alice plays both colours");
  assert.ok(asWhite.pgn.split("\n").includes(String.raw`[White "Alice O\"Brien\\x"]`), asWhite.pgn);
  assert.ok(asBlack.pgn.split("\n").includes(String.raw`[Black "Alice O\"Brien\\x"]`), asBlack.pgn);
  assert.equal(headers(asWhite.pgn).White, `Alice ${lastName}`);
  assert.equal(headers(asBlack.pgn).Black, `Alice ${lastName}`);
  assert.equal(Object.keys(headers(asWhite.pgn)).length, 9, "every tag line still parses");
});

test("pool lint: every movetext is a plain, plausible game without a result token", () => {
  const pool = vm.runInContext("PGN_POOL", seeded.context);
  assert.deepEqual(Object.keys(pool), ["1-0", "0-1", "1/2-1/2"]);
  assert.deepEqual(Object.values(pool).map((games) => games.length), [14, 12, 4]);
  const all = Object.values(pool).flat();
  assert.equal(new Set(all).size, all.length, "no duplicate games");
  for (const movetext of all) {
    const start = movetext.slice(0, 30);
    assert.ok(movetext.startsWith("1. "), `starts with "1. ": ${start}`);
    assert.doesNotMatch(movetext, /(^|\s)(1-0|0-1|1\/2-1\/2|\*)(?=\s|$)/, `no result token: ${start}`);
    assert.doesNotMatch(movetext, /[{}()[\]\n]|\$\d/, `no comments, variations, NAGs or newlines: ${start}`);
    const plies = movetext.split(" ").filter((token) => !/^\d+\.$/.test(token));
    assert.ok(plies.length > 5, `more than 5 plies: ${start}`);
  }
});

test("seeding is deterministic across runs", () => {
  const again = runSeed();
  assert.equal(JSON.stringify(again.collections), JSON.stringify(seeded.collections));
});
