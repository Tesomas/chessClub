package org.tesomas.chessclub.service

import org.apache.logging.log4j.LogManager
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.tesomas.chessclub.model.Game
import org.tesomas.chessclub.model.GameCreateRequest
import org.tesomas.chessclub.model.GameType
import org.tesomas.chessclub.model.Player
import org.tesomas.chessclub.repository.GameRepository
import org.tesomas.chessclub.repository.PlayerRepository

@Service
class GameService(
    private val gameRepository: GameRepository,
    private val playerRepository: PlayerRepository
) {

    fun findAll(): List<Game> {
        log.debug("Listing all games")
        return gameRepository.findAll()
    }

    fun findById(id: String): Game? {
        log.debug("Looking up game {}", id)
        return gameRepository.findByIdOrNull(id)
            .also { if (it == null) log.warn("Game {} not found", id) }
    }

    /** @throws InvalidGameException when the PGN exceeds [MAX_PGN_LENGTH] UTF-16 code units */
    fun create(request: GameCreateRequest): Game {
        validatePgn(request.pgn)
        val (white, black) = resolvePlayers(request.whitePlayerId, request.blackPlayerId)
        return gameRepository.save(
            Game(
                whitePlayerId = request.whitePlayerId,
                blackPlayerId = request.blackPlayerId,
                whitePlayerName = white.fullName,
                blackPlayerName = black.fullName,
                whitePlayerRating = white.eloForType(request.gameType),
                blackPlayerRating = black.eloForType(request.gameType),
                gameType = request.gameType,
                result = request.result,
                time = request.time,
                date = request.date,
                pgn = request.pgn
            )
        )
    }

    /** @throws InvalidGameException when the PGN exceeds [MAX_PGN_LENGTH] UTF-16 code units (checked before the game lookup) */
    fun update(id: String, game: Game): Game? {
        validatePgn(game.pgn)
        if (!gameRepository.existsById(id)) {
            log.warn("Update failed: game {} not found", id)
            return null
        }
        val (white, black) = resolvePlayers(game.whitePlayerId, game.blackPlayerId)
        val saved = gameRepository.save(
            game.copy(id = id, whitePlayerName = white.fullName, blackPlayerName = black.fullName)
        )
        log.info("Updated game {}", id)
        return saved
    }

    fun delete(id: String): Boolean {
        if (!gameRepository.existsById(id)) {
            log.warn("Delete failed: game {} not found", id)
            return false
        }
        gameRepository.deleteById(id)
        log.info("Deleted game {}", id)
        return true
    }

    private fun resolvePlayers(whiteId: String, blackId: String): Pair<Player, Player> {
        val white = findPlayerOrThrow(whiteId)
        val black = findPlayerOrThrow(blackId)
        return white to black
    }

    private fun findPlayerOrThrow(playerId: String): Player =
        playerRepository.findByIdOrNull(playerId) ?: run {
            // ApiExceptionHandler logs the WARN; keep this at DEBUG to avoid duplicate warnings
            log.debug("Referenced player {} not found", playerId)
            throw PlayerNotFoundException(playerId)
        }

    private val Player.fullName get() = "$firstName $lastName"

    private fun validatePgn(pgn: String?) {
        if (pgn != null && pgn.length > MAX_PGN_LENGTH) {
            // ApiExceptionHandler logs the WARN; never log the PGN itself
            throw InvalidGameException("pgn must be at most $MAX_PGN_LENGTH characters")
        }
    }

    companion object {
        /**
     * At most 65,536 characters, counted as UTF-16 code units (`String.length`), not bytes: a character
     * outside the BMP (e.g. an emoji) counts twice, and the UTF-8 size can be up to 3x larger. Far above any
     * real game's PGN, small enough to keep documents and responses sane.
     */
        const val MAX_PGN_LENGTH = 65_536

        private val log = LogManager.getLogger(GameService::class.java)
    }

    private fun Player.eloForType(gameType: GameType): Int = when (gameType) {
        GameType.RAPID -> rapidElo
        GameType.BLITZ -> blitzElo
        GameType.BULLET -> bulletElo
        GameType.CLASSICAL -> classicalElo
    }
}