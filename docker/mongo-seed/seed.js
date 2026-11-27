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
// rating snapshot on each game is the player's rating before that game, and the
// player documents hold the final ratings.
//
// Every game also gets a PGN: real historical moves (see PGN_POOL) with headers built
// from the seeded game. The moves are demo data and do not match the seeded players.

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

// --- PGNs -------------------------------------------------------------------------------------
// Real, public-domain historical games, grouped by result. Their moves are demo data only: they do
// not correspond to the seeded players, ratings or time controls. Every pool game was replayed with
// chess.js when the pool was built and all moves are legal; move text is chess.js's SAN export.
// Sources: famous games as commonly published, and "The Blue Book of Chess" (1898, Project
// Gutenberg #16377) where marked. The pool holds move text only; headers are built per seeded game.
const PGN_POOL = {
  "1-0": [
    // Morphy - Duke of Brunswick & Count Isouard, Paris 1858 (Opera Game)
    "1. e4 e5 2. Nf3 d6 3. d4 Bg4 4. dxe5 Bxf3 5. Qxf3 dxe5 6. Bc4 Nf6 7. Qb3 Qe7 8. Nc3 c6 9. Bg5 b5 10. Nxb5 cxb5 11. Bxb5+ Nbd7 12. O-O-O Rd8 13. Rxd7 Rxd7 14. Rd1 Qe6 15. Bxd7+ Nxd7 16. Qb8+ Nxb8 17. Rd8#",
    // Anderssen - Kieseritzky, London 1851 (Immortal Game)
    "1. e4 e5 2. f4 exf4 3. Bc4 Qh4+ 4. Kf1 b5 5. Bxb5 Nf6 6. Nf3 Qh6 7. d3 Nh5 8. Nh4 Qg5 9. Nf5 c6 10. g4 Nf6 11. Rg1 cxb5 12. h4 Qg6 13. h5 Qg5 14. Qf3 Ng8 15. Bxf4 Qf6 16. Nc3 Bc5 17. Nd5 Qxb2 18. Bd6 Bxg1 19. e5 Qxa1+ 20. Ke2 Na6 21. Nxg7+ Kd8 22. Qf6+ Nxf6 23. Be7#",
    // Anderssen - Dufresne, Berlin 1852 (Evergreen Game)
    "1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. b4 Bxb4 5. c3 Ba5 6. d4 exd4 7. O-O d3 8. Qb3 Qf6 9. e5 Qg6 10. Re1 Nge7 11. Ba3 b5 12. Qxb5 Rb8 13. Qa4 Bb6 14. Nbd2 Bb7 15. Ne4 Qf5 16. Bxd3 Qh5 17. Nf6+ gxf6 18. exf6 Rg8 19. Rad1 Qxf3 20. Rxe7+ Nxe7 21. Qxd7+ Kxd7 22. Bf5+ Ke8 23. Bd7+ Kf8 24. Bxe7#",
    // Legall - Saint Brie, Paris 1750 (Legall's Mate)
    "1. e4 e5 2. Nf3 d6 3. Bc4 Bg4 4. Nc3 g6 5. Nxe5 Bxd1 6. Bxf7+ Ke7 7. Nd5#",
    // Reti - Tartakower, Vienna 1910
    "1. e4 c6 2. d4 d5 3. Nc3 dxe4 4. Nxe4 Nf6 5. Qd3 e5 6. dxe5 Qa5+ 7. Bd2 Qxe5 8. O-O-O Nxe4 9. Qd8+ Kxd8 10. Bg5+ Kc7 11. Bd8#",
    // Ed. Lasker - Thomas, London 1912
    "1. d4 e6 2. Nf3 f5 3. Nc3 Nf6 4. Bg5 Be7 5. Bxf6 Bxf6 6. e4 fxe4 7. Nxe4 b6 8. Ne5 O-O 9. Bd3 Bb7 10. Qh5 Qe7 11. Qxh7+ Kxh7 12. Nxf6+ Kh6 13. Neg4+ Kg5 14. h4+ Kf4 15. g3+ Kf3 16. Be2+ Kg2 17. Rh2+ Kg1 18. Kd2#",
    // Adams - Torre, New Orleans 1920
    "1. e4 e5 2. Nf3 d6 3. d4 exd4 4. Qxd4 Nc6 5. Bb5 Bd7 6. Bxc6 Bxc6 7. Nc3 Nf6 8. O-O Be7 9. Nd5 Bxd5 10. exd5 O-O 11. Bg5 c6 12. c4 cxd5 13. cxd5 Re8 14. Rfe1 a5 15. Re2 Rc8 16. Rae1 Qd7 17. Bxf6 Bxf6 18. Qg4 Qb5 19. Qc4 Qd7 20. Qc7 Qb5 21. a4 Qxa4 22. Re4 Qb5 23. Qxb7",
    // Steinitz - von Bardeleben, Hastings 1895
    "1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. c3 Nf6 5. d4 exd4 6. cxd4 Bb4+ 7. Nc3 d5 8. exd5 Nxd5 9. O-O Be6 10. Bg5 Be7 11. Bxd5 Bxd5 12. Nxd5 Qxd5 13. Bxe7 Nxe7 14. Re1 f6 15. Qe2 Qd7 16. Rac1 c6 17. d5 cxd5 18. Nd4 Kf7 19. Ne6 Rhc8 20. Qg4 g6 21. Ng5+ Ke8 22. Rxe7+ Kf8 23. Rf7+ Kg8 24. Rg7+ Kh8 25. Rxh7+",
    // Lasker - Bauer, Amsterdam 1889
    "1. f4 d5 2. e3 Nf6 3. b3 e6 4. Bb2 Be7 5. Bd3 b6 6. Nc3 Bb7 7. Nf3 Nbd7 8. O-O O-O 9. Ne2 c5 10. Ng3 Qc7 11. Ne5 Nxe5 12. Bxe5 Qc6 13. Qe2 a6 14. Nh5 Nxh5 15. Bxh7+ Kxh7 16. Qxh5+ Kg8 17. Bxg7 Kxg7 18. Qg4+ Kh7 19. Rf3 e5 20. Rh3+ Qh6 21. Rxh6+ Kxh6 22. Qd7 Bf6 23. Qxb7 Kg7 24. Rf1 Rab8 25. Qd7 Rfd8 26. Qg4+ Kf8 27. fxe5 Bg7 28. e6 Rb7 29. Qg6 f6 30. Rxf6+ Bxf6 31. Qxf6+ Ke8 32. Qh8+ Ke7 33. Qg7+",
    // Botvinnik - Capablanca, AVRO 1938
    "1. d4 Nf6 2. c4 e6 3. Nc3 Bb4 4. e3 d5 5. a3 Bxc3+ 6. bxc3 c5 7. cxd5 exd5 8. Bd3 O-O 9. Ne2 b6 10. O-O Ba6 11. Bxa6 Nxa6 12. Bb2 Qd7 13. a4 Rfe8 14. Qd3 c4 15. Qc2 Nb8 16. Rae1 Nc6 17. Ng3 Na5 18. f3 Nb3 19. e4 Qxa4 20. e5 Nd7 21. Qf2 g6 22. f4 f5 23. exf6 Nxf6 24. f5 Rxe1 25. Rxe1 Re8 26. Re6 Rxe6 27. fxe6 Kg7 28. Qf4 Qe8 29. Qe5 Qe7 30. Ba3 Qxa3 31. Nh5+ gxh5 32. Qg5+ Kf8 33. Qxf6+ Kg8 34. e7 Qc1+ 35. Kf2 Qc2+ 36. Kg3 Qd3+ 37. Kh4 Qe4+ 38. Kxh5 Qe2+ 39. Kh4 Qe4+ 40. g4 Qe1+ 41. Kh5",
    // Torre - Ed. Lasker, Moscow 1925 (the windmill)
    "1. d4 Nf6 2. Nf3 e6 3. Bg5 c5 4. e3 cxd4 5. exd4 Be7 6. Nbd2 d6 7. c3 Nbd7 8. Bd3 b6 9. Nc4 Bb7 10. Qe2 Qc7 11. O-O O-O 12. Rfe1 Rfe8 13. Rad1 Nf8 14. Bc1 Nd5 15. Ng5 b5 16. Na3 b4 17. cxb4 Nxb4 18. Qh5 Bxg5 19. Bxg5 Nxd3 20. Rxd3 Qa5 21. b4 Qf5 22. Rg3 h6 23. Nc4 Qd5 24. Ne3 Qb5 25. Bf6 Qxh5 26. Rxg7+ Kh8 27. Rxf7+ Kg8 28. Rg7+ Kh8 29. Rxb7+ Kg8 30. Rg7+ Kh8 31. Rg5+ Kh7 32. Rxh5 Kg6 33. Rh3 Kxf6 34. Rxh6+ Kg5 35. Rh3 Reb8 36. Rg3+ Kf6 37. Rf3+ Kg6 38. a3 a5 39. bxa5 Rxa5 40. Nc4 Rd5 41. Rf4 Nd7 42. Rxe6+ Kg5 43. g3",
    // Lasker - Steinitz (The Blue Book of Chess, 1898, game 26)
    "1. e4 e5 2. Nf3 Nc6 3. Bb5 d6 4. d4 Bd7 5. Nc3 Nge7 6. Be3 Ng6 7. Qd2 Be7 8. O-O-O a6 9. Be2 exd4 10. Nxd4 Nxd4 11. Qxd4 Bf6 12. Qd2 Bc6 13. Nd5 O-O 14. g4 Re8 15. g5 Bxd5 16. Qxd5 Re5 17. Qd2 Bxg5 18. f4 Rxe4 19. fxg5 Qe7 20. Rdf1 Rxe3 21. Bc4 Nh8 22. h4 c6 23. g6 d5 24. gxh7+ Kxh7 25. Bd3+ Kg8 26. h5 Re8 27. h6 g6 28. h7+ Kg7 29. Kb1 Qe5 30. a3 c5 31. Qf2 c4 32. Qh4 f6 33. Bf5 Kf7 34. Rhg1 gxf5 35. Qh5+ Ke7 36. Rg8 Kd6 37. Rxf5 Qe6 38. Rxe8 Qxe8 39. Rxf6+ Kc5 40. Qh6 Re7 41. Qh2 Qd7 42. Qg1+ d4 43. Qg5+ Qd5 44. Rf5 Qxf5 45. Qxf5+ Kd6 46. Qf6+",
    // Marshall - Janowsky (The Blue Book of Chess, 1898, game 70)
    "1. d4 d5 2. c4 e6 3. Nc3 Nf6 4. Bg5 Nbd7 5. Nf3 Be7 6. e3 O-O 7. Rc1 Re8 8. Bd3 dxc4 9. Bxc4 a6 10. O-O b5 11. Bd3 Bb7 12. Qe2 c5 13. dxc5 Nxc5 14. Bc2 Nd5 15. Bxe7 Qxe7 16. Nxd5 Bxd5 17. b3 Rac8 18. e4 Bb7 19. b4 Nd7 20. Rfd1 Nf8 21. a3 Rc3 22. Bd3 Rec8 23. Rxc3 Rxc3 24. Qb2 Rc8 25. Rc1 Rd8 26. Bb1 Nd7 27. Qd4 Nf6 28. Qe5 Ng4 29. Qf4 Nf6 30. h3 Ne8 31. Ne5 Nd6 32. Ng4 Nc4 33. e5 Kh8 34. Nf6 gxf6 35. Qh4",
    // Lasker - Tarrasch (The Blue Book of Chess, 1898, game 71)
    "1. e4 e6 2. d4 d5 3. Nc3 Nf6 4. Bg5 Bb4 5. exd5 Qxd5 6. Nf3 c5 7. Bxf6 gxf6 8. Qd2 Bxc3 9. Qxc3 Nd7 10. Rd1 Rg8 11. dxc5 Qxc5 12. Qd2 Qb6 13. c3 a6 14. Qc2 f5 15. g3 Nc5 16. Bg2 Qc7 17. Qe2 b5 18. O-O Bb7 19. c4 b4 20. Qd2 Rb8 21. Qh6 Bxf3 22. Bxf3 Qe5 23. Rfe1 Qxb2 24. Qf4 Rc8 25. Qd6 f6 26. Bh5+ Rg6 27. Bxg6+ hxg6 28. Rxe6+",
  ],
  "0-1": [
    // Levitsky - Marshall, Breslau 1912 (gold coins game)
    "1. d4 e6 2. e4 d5 3. Nc3 c5 4. Nf3 Nc6 5. exd5 exd5 6. Be2 Nf6 7. O-O Be7 8. Bg5 O-O 9. dxc5 Be6 10. Nd4 Bxc5 11. Nxe6 fxe6 12. Bg4 Qd6 13. Bh3 Rae8 14. Qd2 Bb4 15. Bxf6 Rxf6 16. Rad1 Qc5 17. Qe2 Bxc3 18. bxc3 Qxc3 19. Rxd5 Nd4 20. Qh5 Ref8 21. Re5 Rh6 22. Qg5 Rxh3 23. Rc5 Qg3",
    // Paulsen - Morphy, New York 1857
    "1. e4 e5 2. Nf3 Nc6 3. Nc3 Nf6 4. Bb5 Bc5 5. O-O O-O 6. Nxe5 Re8 7. Nxc6 dxc6 8. Bc4 b5 9. Be2 Nxe4 10. Nxe4 Rxe4 11. Bf3 Re6 12. c3 Qd3 13. b4 Bb6 14. a4 bxa4 15. Qxa4 Bd7 16. Ra2 Rae8 17. Qa6 Qxf3 18. gxf3 Rg6+ 19. Kh1 Bh3 20. Rd1 Bg2+ 21. Kg1 Bxf3+ 22. Kf1 Bg2+ 23. Kg1 Bh3+ 24. Kh1 Bxf2 25. Qf1 Bxf1 26. Rxf1 Re2 27. Ra1 Rh6 28. d4 Be3",
    // Rotlewi - Rubinstein, Lodz 1907
    "1. d4 d5 2. Nf3 e6 3. e3 c5 4. c4 Nc6 5. Nc3 Nf6 6. dxc5 Bxc5 7. a3 a6 8. b4 Bd6 9. Bb2 O-O 10. Qd2 Qe7 11. Bd3 dxc4 12. Bxc4 b5 13. Bd3 Rd8 14. Qe2 Bb7 15. O-O Ne5 16. Nxe5 Bxe5 17. f4 Bc7 18. e4 Rac8 19. e5 Bb6+ 20. Kh1 Ng4 21. Be4 Qh4 22. g3 Rxc3 23. gxh4 Rd2 24. Qxd2 Bxe4+ 25. Qg2 Rh3",
    // Schulten - Morphy, New York 1857
    "1. e4 e5 2. f4 d5 3. exd5 e4 4. Nc3 Nf6 5. d3 Bb4 6. Bd2 e3 7. Bxe3 O-O 8. Bd2 Bxc3 9. bxc3 Re8+ 10. Be2 Bg4 11. c4 c6 12. dxc6 Nxc6 13. Kf1 Rxe2 14. Nxe2 Nd4 15. Qb1 Bxe2+ 16. Kf2 Ng4+ 17. Kg1 Nf3+ 18. gxf3 Qd4+ 19. Kg2 Qf2+ 20. Kh3 Qxf3+ 21. Kh4 Nh6 22. Qg1 Nf5+ 23. Kg5 Qh5#",
    // Saemisch - Nimzowitsch, Copenhagen 1923 (Immortal Zugzwang)
    "1. d4 Nf6 2. c4 e6 3. Nf3 b6 4. g3 Bb7 5. Bg2 Be7 6. Nc3 O-O 7. O-O d5 8. Ne5 c6 9. cxd5 cxd5 10. Bf4 a6 11. Rc1 b5 12. Qb3 Nc6 13. Nxc6 Bxc6 14. h3 Qd7 15. Kh2 Nh5 16. Bd2 f5 17. Qd1 b4 18. Nb1 Bb5 19. Rg1 Bd6 20. e4 fxe4 21. Qxh5 Rxf2 22. Qg5 Raf8 23. Kh1 R8f5 24. Qe3 Bd3 25. Rce1 h6",
    // Gluecksberg - Najdorf, Warsaw 1929 (Polish Immortal)
    "1. d4 f5 2. c4 Nf6 3. Nc3 e6 4. Nf3 d5 5. e3 c6 6. Bd3 Bd6 7. O-O O-O 8. Ne2 Nbd7 9. Ng5 Bxh2+ 10. Kh1 Ng4 11. f4 Qe8 12. g3 Qh5 13. Kg2 Bg1 14. Nxg1 Qh2+ 15. Kf3 e5 16. dxe5 Ndxe5+ 17. fxe5 Nxe5+ 18. Kf4 Ng6+ 19. Kf3 f4 20. exf4 Bg4+ 21. Kxg4 Ne5+ 22. fxe5 h5#",
    // Reti - Alekhine, Baden-Baden 1925
    "1. g3 e5 2. Nf3 e4 3. Nd4 d5 4. d3 exd3 5. Qxd3 Nf6 6. Bg2 Bb4+ 7. Bd2 Bxd2+ 8. Nxd2 O-O 9. c4 Na6 10. cxd5 Nb4 11. Qc4 Nbxd5 12. N2b3 c6 13. O-O Re8 14. Rfd1 Bg4 15. Rd2 Qc8 16. Nc5 Bh3 17. Bf3 Bg4 18. Bg2 Bh3 19. Bf3 Bg4 20. Bh1 h5 21. b4 a6 22. Rc1 h4 23. a4 hxg3 24. hxg3 Qc7 25. b5 axb5 26. axb5 Re3 27. Nf3 cxb5 28. Qxb5 Nc3 29. Qxb7 Qxb7 30. Nxb7 Nxe2+ 31. Kh2 Ne4 32. Rc4 Nxf2 33. Bg2 Be6 34. Rcc2 Ng4+ 35. Kh3 Ne5+ 36. Kh2 Rxf3 37. Rxe2 Ng4+ 38. Kh3 Ne3+ 39. Kh2 Nxc2 40. Bxf3 Nd4",
    // Pillsbury - Lasker, St. Petersburg 1896
    "1. d4 d5 2. c4 e6 3. Nc3 Nf6 4. Nf3 c5 5. Bg5 cxd4 6. Qxd4 Nc6 7. Qh4 Be7 8. O-O-O Qa5 9. e3 Bd7 10. Kb1 h6 11. cxd5 exd5 12. Nd4 O-O 13. Bxf6 Bxf6 14. Qh5 Nxd4 15. exd4 Be6 16. f4 Rac8 17. f5 Rxc3 18. fxe6 Ra3 19. exf7+ Rxf7 20. bxa3 Qb6+ 21. Bb5 Qxb5+ 22. Ka1 Rc7 23. Rd2 Rc4 24. Rhd1 Rc3 25. Qf5 Qc4 26. Kb2 Rxa3 27. Qe6+ Kh7 28. Kxa3 Qc3+ 29. Ka4 b5+ 30. Kxb5 Qc4+ 31. Ka5 Bd8+ 32. Qb6 Bxb6#",
    // De la Bourdonnais - McDonnell (The Blue Book of Chess, 1898, game 36)
    "1. e4 e5 2. Bc4 Bc5 3. Qe2 Nf6 4. d3 Nc6 5. c3 Ne7 6. f4 exf4 7. d4 Bb6 8. Bxf4 d6 9. Bd3 Ng6 10. Be3 O-O 11. h3 Re8 12. Nd2 Qe7 13. O-O-O c5 14. Kb1 cxd4 15. cxd4 a5 16. Ngf3 Bd7 17. g4 h6 18. Rdg1 a4 19. g5 hxg5 20. Bxg5 a3 21. b3 Bc6 22. Rg4 Ba5 23. h4 Bxd2 24. Nxd2 Ra5 25. h5 Rxg5 26. Rxg5 Nf4 27. Qf3 Nxd3 28. d5 Nxd5 29. Rhg1 Nc3+ 30. Ka1 Bxe4 31. Rxg7+ Kh8 32. Qg3 Bg6 33. hxg6 Qe1+ 34. Rxe1 Rxe1+ 35. Qxe1 Nxe1 36. Rh7+ Kg8 37. gxf7+ Kxh7 38. f8=Q Nc2#",
    // McDonnell - La Bourdonnais (The Blue Book of Chess, 1898, game 62)
    "1. e4 e5 2. f4 exf4 3. Bc4 Qh4+ 4. Kf1 g5 5. Nc3 Bg7 6. d4 d6 7. Be2 Nc6 8. e5 Nge7 9. Nb5 O-O 10. Nxc7 Rb8 11. Nf3 Qh6 12. exd6 Nf5 13. c3 Ng3+ 14. hxg3 Qxh1+ 15. Kf2 fxg3+ 16. Kxg3 Qxd1 17. Bxd1 h6 18. b3 b5 19. Be3 f5 20. d5 f4+ 21. Kh2 fxe3 22. dxc6 g4 23. Nd4 Be5+ 24. Kg1 Bxd6 25. Ncxb5 Bc5 26. b4 Bb6 27. Nd6 Bxd4 28. cxd4 Rxb4 29. Nxc8 Rxc8 30. d5 Kf7 31. Bb3 Ke7 32. Kf1 Re4 33. Ke2 Rf8 34. Kd3 Re5 35. Re1 Kd6 36. Rxe3 Rxe3+ 37. Kxe3 h5 38. Ke4 h4 39. Bd1 h3 40. gxh3 gxh3 41. Bf3 h2 42. Bg2 Rf1",
    // Pillsbury - Schlechter (The Blue Book of Chess, 1898, game 69)
    "1. d4 d5 2. c4 e6 3. Nc3 Nf6 4. Bg5 Be7 5. Nf3 Nbd7 6. e3 b6 7. Rc1 Bb7 8. cxd5 exd5 9. Bd3 O-O 10. O-O c5 11. Bb1 Ne4 12. Bf4 Nxc3 13. Rxc3 c4 14. Ne5 f5 15. Kh1 Nxe5 16. Bxe5 Bd6 17. f4 Bc8 18. Qh5 a6 19. Rf3 Ra7 20. Rh3 g6 21. Qh6 Bxe5 22. fxe5 Rg7 23. Rf3 b5 24. Rc1 Qe7 25. Rcf1 Rff7 26. h4 Be6 27. g4 Qd7 28. gxf5 gxf5 29. Qh5 Rg6 30. Bxf5 Bxf5 31. Rxf5 Rxf5 32. Rxf5 b4 33. Qf3 c3 34. bxc3 bxc3 35. Rf8+ Kg7 36. Rb8 Qe7 37. Qf4 h5 38. e6 Rxe6 39. Rc8 Re4 40. Rc7 Rxf4 41. Rxe7+ Rf7 42. Re5 c2 43. Rg5+ Kh6 44. Rg1 Rb7",
    // Anderssen - Morphy (The Blue Book of Chess, 1898, game 84)
    "1. a3 e5 2. c4 Nf6 3. Nc3 d5 4. cxd5 Nxd5 5. e3 Be6 6. Nf3 Bd6 7. Be2 O-O 8. d4 Nxc3 9. bxc3 e4 10. Nd2 f5 11. f4 g5 12. Bc4 Bxc4 13. Nxc4 gxf4 14. exf4 Qe8 15. O-O Qc6 16. Qb3 Qd5 17. Rb1 b6 18. Qa2 c6 19. Qe2 Nd7 20. Ne3 Qe6 21. c4 Nf6 22. Rb3 Kf7 23. Bb2 Rac8 24. Kh1 Rg8 25. d5 cxd5 26. cxd5 Qd7 27. Nc4 Ke7 28. Bxf6+ Kxf6 29. Qb2+ Kf7 30. Rh3 Rg7 31. Qd4 Kg8 32. Rh6 Bf8 33. d6 Rf7 34. Rh3 Qa4 35. Rc1 Rc5 36. Rg3+ Bg7 37. h3 Kh8 38. Rxg7 Rxg7 39. Rc3 e3 40. Rxe3 Rxc4 41. Qf6 Rc1+ 42. Kh2 Qxf4+",
  ],
  "1/2-1/2": [
    // Hamppe - Meitner, Vienna 1872 (Immortal Draw)
    "1. e4 e5 2. Nc3 Bc5 3. Na4 Bxf2+ 4. Kxf2 Qh4+ 5. Ke3 Qf4+ 6. Kd3 d5 7. Kc3 Qxe4 8. Kb3 Na6 9. a3 Qxa4+ 10. Kxa4 Nc5+ 11. Kb4 a5+ 12. Kxc5 Ne7 13. Bb5+ Kd8 14. Bc6 b6+ 15. Kb5 Nxc6 16. Kxc6 Bb7+ 17. Kb5 Ba6+ 18. Kc6 Bb7+",
    // Anderssen - Morphy (The Blue Book of Chess, 1898, game 25)
    "1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. d3 Bc5 6. c3 b5 7. Bc2 d5 8. exd5 Nxd5 9. h3 O-O 10. O-O h6 11. d4 exd4 12. cxd4 Bb6 13. Nc3 Ndb4 14. Bb1 Be6 15. a3 Nd5 16. Ne2 Nf6 17. Be3 Re8 18. Ng3 Bc4 19. Nf5 Bxf1 20. Qxf1 Ne7 21. N3h4 Nxf5 22. Nxf5 Qd7 23. Bxh6 gxh6 24. Qc1 Bxd4 25. Qxh6 Re1+ 26. Kh2 Ne4 27. Bxe4 Rxe4 28. Qg5+ Kf8 29. Qh6+ Ke8 30. Nxd4 Qd6+ 31. Qxd6 cxd6 32. Rd1 Kf8 33. Rd2 Rae8 34. g4 R8e5 35. f3 Re1 36. h4 Rd5 37. Kg3 a5 38. h5 Kg8 39. Kf2 Re8 40. Kg3 Kh7 41. Kf4 Re7 42. Kg3 f6 43. Kf4 Re8 44. Kg3 Re7",
    // Lewis - unknown (The Blue Book of Chess, 1898, game 54)
    "1. e4 e5 2. f4 exf4 3. Nf3 g5 4. Bc4 g4 5. O-O gxf3 6. Qxf3 Bh6 7. d4 Qe7 8. Bxf4 Nc6 9. Bxh6 Nxh6 10. Qh5 Qf8 11. Rf6 Qg7 12. Qxh6 Qxh6 13. Rxh6 Nxd4 14. Na3 c6 15. Rd6 Ne6 16. Rad1 Rg8 17. Bxe6 fxe6 18. Nc4 Rg5 19. Kf2 Ke7 20. Ne3 a5 21. Kf3 b5 22. Ng4 Ra7 23. Kf4 Rc5 24. c3 b4 25. cxb4 axb4 26. Ne5 Rc2 27. a4 Rxb2 28. Rxc6 Rf2+ 29. Ke3 Rf8 30. Rdc1 Ba6 31. Rc7 Rxc7 32. Rxc7 Kd6 33. Ra7 Kxe5 34. Rxa6 Rb8 35. Ra5+ d5 36. exd5 exd5 37. Kd3 Rc8 38. Rb5 Rc3+ 39. Kd2 Rc4 40. a5 Rf4 41. a6 Rf2+ 42. Kd3 Ra2 43. Rxb4 Rxa6",
    // Clements - Lewis (The Blue Book of Chess, 1898, game 76)
    "1. e4 c5 2. d4 e6 3. Nf3 d5 4. exd5 exd5 5. Bb5+ Nc6 6. O-O Nf6 7. Ne5 Qb6 8. Bxc6+ bxc6 9. Re1 Be6 10. Qe2 cxd4 11. Nxf7 Ne4 12. Nxh8 O-O-O 13. Qf3 Bc5 14. Rxe4 dxe4 15. Qxe4 Re8 16. b4 Bd7 17. Qxe8+ Bxe8 18. bxc5 Qxc5 19. Ba3 Qxc2 20. h3 d3 21. Bb4 Qb2 22. Bc3 Qc1+ 23. Kh2 c5 24. Bd2 Qc2 25. a4 Bc6 26. Nf7 Qd1 27. Nd6+ Kd7 28. Nc4 Be4 29. Bc3 Qc2 30. Ncd2 Bd5 31. Bxg7 c4 32. Bc3 Qd1 33. Na3 Qh5 34. Nb5 Qg6 35. f3 h5 36. Re1 h4 37. Re5 Bc6 38. Nxa7 Bxa4 39. Nxc4 Bb3 40. Rd5+ Kc7 41. Be5+ Kb7 42. Rd7+ Ka6 43. Rd6+ Qxd6 44. Bxd6 Bxc4 45. Bb4 Kxa7 46. Kg1 Kb6 47. Bd2 Kc5 48. Kf2 Kd5 49. Be1 Ke5 50. Ke3 Kf5 51. Bxh4 d2 52. Kxd2 Kf4 53. Ke1 Bd3 54. Kf2 Kf5 55. g3 Bc4 56. g4+ Kg6 57. f4 Bd5 58. f5+ Kg7 59. g5 Be4 60. f6+ Kg6 61. Kg3 Bd5 62. Kg4 Be6+ 63. Kg3",
  ],
};
const RESULT_TOKENS = { WHITE_WINS: "1-0", BLACK_WINS: "0-1", DRAW: "1/2-1/2" };
const PGN_LINE_WIDTH = 80;

function pad2(n) {
  return String(n).padStart(2, "0");
}

function pgnDate(date) {
  return date.getUTCFullYear() + "." + pad2(date.getUTCMonth() + 1) + "." + pad2(date.getUTCDate());
}

// PGN TimeControl is in seconds: "3+2" -> "180+2", "1+0" -> "60", "90+30" -> "5400+30".
function pgnTimeControl(time) {
  const [minutes, increment] = time.split("+").map(Number);
  const base = String(minutes * 60);
  return increment ? base + "+" + increment : base;
}

function pgnTag(name, value) {
  const escaped = String(value).replace(/\\/g, "\\\\").replace(/"/g, '\\"');
  return "[" + name + ' "' + escaped + '"]';
}

// Greedy word wrap so no movetext line exceeds PGN_LINE_WIDTH characters.
function wrapMovetext(text) {
  const lines = [];
  let line = "";
  text.split(" ").forEach((word) => {
    if (line && line.length + 1 + word.length > PGN_LINE_WIDTH) {
      lines.push(line);
      line = word;
    } else {
      line = line ? line + " " + word : word;
    }
  });
  if (line) lines.push(line);
  return lines.join("\n");
}

function buildPgn(game, movetext) {
  const token = RESULT_TOKENS[game.result];
  const tags = [
    pgnTag("Event", "ChessClub Test Data"),
    pgnTag("Site", "ChessClub"),
    pgnTag("Date", pgnDate(game.date)),
    pgnTag("White", game.whitePlayerName),
    pgnTag("Black", game.blackPlayerName),
    pgnTag("Result", token),
    pgnTag("WhiteElo", game.whitePlayerRating),
    pgnTag("BlackElo", game.blackPlayerRating),
    pgnTag("TimeControl", pgnTimeControl(game.time)),
  ];
  return tags.join("\n") + "\n\n" + wrapMovetext(movetext + " " + token) + "\n";
}

// Cycles through each result's pool in game order. It draws nothing from the PRNG, so players,
// pairings, results, ratings, dates and birth dates stay exactly as they were before PGNs existed.
function withPgns(games) {
  const used = { "1-0": 0, "0-1": 0, "1/2-1/2": 0 };
  return games.map((game) => {
    const token = RESULT_TOKENS[game.result];
    const pool = PGN_POOL[token];
    const movetext = pool[used[token]++ % pool.length];
    const { _class, ...fields } = game;
    return { ...fields, pgn: buildPgn(game, movetext), _class };
  });
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
      const delta = K_FACTOR * (whiteScore(result) - expectedScore(whiteRating, blackRating));

      ratings[w] = Math.round(whiteRating + delta);
      ratings[b] = Math.round(blackRating - delta);

      games.push({
        whitePlayerId: players[w].id.toHexString(),
        blackPlayerId: players[b].id.toHexString(),
        whitePlayerName: fullName(players[w]),
        blackPlayerName: fullName(players[b]),
        whitePlayerRating: whiteRating,
        blackPlayerRating: blackRating,
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
  database[GAME_COLLECTION].insertMany(withPgns(games));
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
