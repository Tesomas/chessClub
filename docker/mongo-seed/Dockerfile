# MongoDB pre-loaded with ChessClub test data (40 players, 2,800 games).
# The seed script runs automatically the first time a container starts with an empty /data/db.
FROM mongo:7

ENV MONGO_INITDB_DATABASE=chessclub

COPY seed.js /docker-entrypoint-initdb.d/10-seed.js
