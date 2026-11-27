# MongoDB pre-loaded with ChessClub test data (40 players, 2,800 games).
# The seed script runs automatically the first time a container starts with an empty /data/db.
#
# It runs as a single-node replica set ("rs0"), because the app records a game and its rating
# updates in one transaction, and MongoDB only supports transactions on a replica set.
# replica-set.js initiates it before the seed runs; the health check reports healthy once this node
# is primary (and initiates the set itself for a data directory that was never initialised).
FROM mongo:7

ENV MONGO_INITDB_DATABASE=chessclub

COPY replica-set.js /docker-entrypoint-initdb.d/05-replica-set.js
COPY seed.js /docker-entrypoint-initdb.d/10-seed.js

HEALTHCHECK --interval=5s --timeout=5s --start-period=60s --retries=10 \
  CMD mongosh --quiet --eval "try { rs.status() } catch (e) { rs.initiate({ _id: 'rs0', members: [{ _id: 0, host: 'localhost:27017' }] }) } quit(db.hello().isWritablePrimary ? 0 : 1)"

CMD ["mongod", "--replSet", "rs0", "--bind_ip_all"]
