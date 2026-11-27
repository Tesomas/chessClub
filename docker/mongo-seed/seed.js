// Seeds the "chessclub" database with 40 test players and a full game history.
//
// Runs once, via mongo's /docker-entrypoint-initdb.d hook, the first time a container
// starts with an empty data directory.
//
// Per player: 50 bullet, 40 blitz, 30 rapid, 20 classical games, all against other
// test players. Pairings come from a round-robin (circle method), so every player plays
// exactly one game per round and the per-type counts come out exact.
//
// Everything is driven by a seeded PRNG, so every build produces the same data
// (game ObjectIds aside). Ratings are simulated with Elo updates game by game: the
// rating snapshot on each game is the player's rating before that game, the game records the
// points each side gained or lost, and the player documents hold the final ratings.

const DB_NAME = "chessclub";
const PLAYER_COLLECTION = "Players"; // matches @Document(collection = "Players")
const GAME_COLLECTION = "games";
const PLAYER_CLASS = "org.tesomas.chessclub.model.Player";
const GAME_CLASS = "org.tesomas.chessclub.model.Game";

const SEED = 20261126;
const K_FACTOR = 20;
const WHITE_ADVANTAGE = 35;
const HISTORY_END = Date.UTC(2026, 10, 1); // 2026-11-01
const HISTORY_DAYS = 365;
const DAY_MS = 24 * 60 * 60 * 1000;

const GAME_TYPES = [
  { type: "BULLET", field: "bulletElo", gamesPerPlayer: 50, time: "1+0", drawRate: 0.05 },
  { type: "BLITZ", field: "blitzElo", gamesPerPlayer: 40, time: "3+2", drawRate: 0.08 },
  { type: "RAPID", field: "rapidElo", gamesPerPlayer: 30, time: "10+0", drawRate: 0.12 },
  { type: "CLASSICAL", field: "classicalElo", gamesPerPlayer: 20, time: "90+30", drawRate: 0.2 },
];

const FIRST_NAMES = [
  "Alice", "Ben", "Carla", "Dmitri", "Elena", "Farid", "Grace", "Hiro", "Ines", "Jamal",
  "Kira", "Leo", "Maya", "Nikolai", "Olivia", "Pavel", "Quinn", "Rosa", "Sam", "Tara",
  "Umar", "Vera", "Wes", "Ximena", "Yusuf", "Zoe", "Arjun", "Bianca", "Caleb", "Dana",
  "Emil", "Freya", "Gustav", "Hana", "Ivan", "Julia", "Kofi", "Lena", "Marco", "Nadia",
];

const LAST_NAMES = [
  "Anders", "Brooks", "Castillo", "Duval", "Eriksen", "Fischer", "Garcia", "Hoffman", "Ivanova", "Jensen",
  "Kowalski", "Lindqvist", "Morales", "Novak", "Okafor", "Petrov", "Quintero", "Rossi", "Sato", "Tanaka",
  "Ulrich", "Vasquez", "Weber", "Xu", "Yilmaz", "Zielinski", "Abbott", "Becker", "Chen", "Dimitrov",
  "Esposito", "Fontaine", "Gallagher", "Haddad", "Iqbal", "Jovanovic", "Khan", "Larsen", "Mendes", "Nakamura",
];

const PLAYER_COUNT = FIRST_NAMES.length;

// Every other player (even index) gets a birth date; the rest have none, like real sign-ups.
const BIRTH_YEAR_MIN = 1955;
const BIRTH_YEAR_SPAN = 56; // 1955..2010

// Derived from the index rather than the PRNG so adding birth dates leaves the game history unchanged.
// Stored as a BSON Date at UTC midnight, which is how the app stores LocalDate (see MongoConfig).
function birthDate(index) {
  if (index % 2 !== 0) return null;
  const year = BIRTH_YEAR_MIN + ((index * 7) % BIRTH_YEAR_SPAN);
  const month = (index * 5) % 12; // 0-based for Date.UTC
  const day = 1 + ((index * 11) % 28); // 1..28 is valid in every month
  return new Date(Date.UTC(year, month, day));
}

// mulberry32: small deterministic PRNG returning floats in [0, 1).
function createRandom(seed) {
  let state = seed >>> 0;
  return function next() {
    state = (state + 0x6d2b79f5) >>> 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const random = createRandom(SEED);

function randomInt(min, max) {
  return min + Math.floor(random() * (max - min + 1));
}

function shuffled(items) {
  const copy = items.slice();
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy;
}

// Deterministic, readable player ids: 64a000000000000000000001 ... 64a000000000000000000028
function playerObjectId(index) {
  return ObjectId("64a" + (index + 1).toString(16).padStart(21, "0"));
}

function buildPlayers() {
  return FIRST_NAMES.map((firstName, i) => {
    const base = randomInt(900, 2200);
    const ratings = {};
    GAME_TYPES.forEach((gt) => {
      ratings[gt.field] = base + randomInt(-120, 120);
    });
    return {
      id: playerObjectId(i),
      firstName,
      lastName: LAST_NAMES[i],
      ratings,
    };
  });
}

// Circle-method round robin: returns the [white, black] index pairs for one round.
// Over PLAYER_COUNT - 1 consecutive rounds every pair meets exactly once.
function roundPairings(order, round) {
  const n = order.length;
  const rotating = order.slice(1);
  const shift = round % (n - 1);
  const rotated = rotating.slice(shift).concat(rotating.slice(0, shift));
  const seats = [order[0]].concat(rotated);
  const cycleFlip = Math.floor(round / (n - 1)) % 2 === 1;

  const pairs = [];
  for (let i = 0; i < n / 2; i++) {
    const a = seats[i];
    const b = seats[n - 1 - i];
    const aIsWhite = ((round + i) % 2 === 0) !== cycleFlip;
    pairs.push(aIsWhite ? [a, b] : [b, a]);
  }
  return pairs;
}

function expectedScore(rating, opponentRating) {
  return 1 / (1 + Math.pow(10, (opponentRating - rating) / 400));
}

function playResult(whiteRating, blackRating, drawRate) {
  if (random() < drawRate) return "DRAW";
  const whiteExpected = expectedScore(whiteRating + WHITE_ADVANTAGE, blackRating);
  return random() < whiteExpected ? "WHITE_WINS" : "BLACK_WINS";
}

function whiteScore(result) {
  if (result === "WHITE_WINS") return 1;
  if (result === "BLACK_WINS") return 0;
  return 0.5;
}

function roundDate(round, rounds) {
  const daysBeforeEnd = Math.round(((rounds - 1 - round) * HISTORY_DAYS) / rounds);
  return new Date(HISTORY_END - daysBeforeEnd * DAY_MS);
}

function fullName(player) {
  return player.firstName + " " + player.lastName;
}

// Plays every round for one game type, updating `ratings` (index -> rating) as it goes.
function simulateGameType(players, gameType) {
  const ratings = players.map((p) => p.ratings[gameType.field]);
  const order = shuffled(players.map((_, i) => i));
  const games = [];

  for (let round = 0; round < gameType.gamesPerPlayer; round++) {
    const date = roundDate(round, gameType.gamesPerPlayer);
    roundPairings(order, round).forEach(([w, b]) => {
      const whiteRating = ratings[w];
      const blackRating = ratings[b];
      const result = playResult(whiteRating, blackRating, gameType.drawRate);
      // Same formula and rounding as the app's EloCalculator: round white's change once and mirror it.
      const whiteChange = Math.round(
        K_FACTOR * (whiteScore(result) - expectedScore(whiteRating, blackRating))
      );

      ratings[w] = whiteRating + whiteChange;
      ratings[b] = blackRating - whiteChange;

      games.push({
        whitePlayerId: players[w].id.toHexString(),
        blackPlayerId: players[b].id.toHexString(),
        whitePlayerName: fullName(players[w]),
        blackPlayerName: fullName(players[b]),
        whitePlayerRating: whiteRating,
        blackPlayerRating: blackRating,
        whitePlayerRatingChange: whiteChange,
        blackPlayerRatingChange: -whiteChange,
        gameType: gameType.type,
        result,
        time: gameType.time,
        date,
        _class: GAME_CLASS,
      });
    });
  }

  return { games, finalRatings: ratings };
}

function seed() {
  const database = db.getSiblingDB(DB_NAME);
  const players = buildPlayers();
  const finalRatings = {};
  let games = [];

  GAME_TYPES.forEach((gameType) => {
    const outcome = simulateGameType(players, gameType);
    finalRatings[gameType.field] = outcome.finalRatings;
    games = games.concat(outcome.games);
  });

  const playerDocs = players.map((p, i) => {
    const doc = { _id: p.id, firstName: p.firstName, lastName: p.lastName };
    GAME_TYPES.forEach((gt) => {
      doc[gt.field] = finalRatings[gt.field][i];
    });
    const born = birthDate(i);
    if (born) doc.birthDate = born;
    doc._class = PLAYER_CLASS;
    return doc;
  });

  database[PLAYER_COLLECTION].insertMany(playerDocs);
  database[GAME_COLLECTION].insertMany(games);
  // Same keys and (default) names as the @CompoundIndex declarations on Game, so the app's
  // auto-index-creation finds them already in place. They serve GET /api/players/{id}/games.
  database[GAME_COLLECTION].createIndex({ whitePlayerId: 1, date: -1 });
  database[GAME_COLLECTION].createIndex({ blackPlayerId: 1, date: -1 });

  print(
    "[seed] Inserted " + playerDocs.length + " players and " + games.length +
      " games into " + DB_NAME
  );
}

if (PLAYER_COUNT % 2 !== 0 || LAST_NAMES.length !== PLAYER_COUNT) {
  throw new Error("[seed] Need an even number of players with matching first/last name lists");
}

seed();
