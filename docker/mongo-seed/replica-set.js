// Runs before seed.js (/docker-entrypoint-initdb.d runs files in name order), on the image's temporary
// init-time mongod, which already has --replSet rs0. Initiating here lets the seed write, and the
// replica-set config is stored in the data directory, so the real mongod starts as primary of rs0.
// The app needs a replica set: it records a game and its rating updates in one transaction.
rs.initiate({ _id: "rs0", members: [{ _id: 0, host: "localhost:27017" }] });

for (let i = 0; i < 60 && !db.hello().isWritablePrimary; i++) {
  sleep(500);
}
if (!db.hello().isWritablePrimary) {
  throw new Error("replica set rs0 did not elect a primary");
}
