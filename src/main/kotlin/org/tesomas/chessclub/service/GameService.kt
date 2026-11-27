package org.tesomas.chessclub.service

import org.apache.logging.log4j.LogManager
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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

    /**
     * Records the game and applies its ELO result: the game stores both players' ratings before the game
     * plus the points each gained or lost, and both players' rating for the game's type is updated.
     * A self-play game (same player on both sides) is recorded without changing any rating.
     *
     * Runs in one MongoDB transaction: the game and both rating updates are committed together or not at all.
     * The players are read inside the transaction too, so two games saved at the same moment for the same
     * player cannot both start from the old rating; the later one fails with a write conflict instead.
     */
    @Transactional
    fun create(request: GameCreateRequest): Game {
        val (white, black) = resolvePlayers(request.whitePlayerId, request.blackPlayerId)
        val whiteRating = white.eloForType(request.gameType)
        val blackRating = black.eloForType(request.gameType)
        val change = if (request.whitePlayerId == request.blackPlayerId) RatingChange.NONE
        else EloCalculator.ratingChange(whiteRating, blackRating, request.result)

        val saved = gameRepository.save(
            Game(
                whitePlayerId = request.whitePlayerId,
                blackPlayerId = request.blackPlayerId,
                whitePlayerName = white.fullName,
                blackPlayerName = black.fullName,
                whitePlayerRating = whiteRating,
                blackPlayerRating = blackRating,
                whitePlayerRatingChange = change.white,
                blackPlayerRatingChange = change.black,
                gameType = request.gameType,
                result = request.result,
                time = request.time,
                date = request.date,
                pgn = request.pgn
            )
        )
        log.info("Created game {}", saved.id)

        if (change != RatingChange.NONE) {
            playerRepository.save(white.withElo(request.gameType, whiteRating + change.white))
            playerRepository.save(black.withElo(request.gameType, blackRating + change.black))
            log.info(
                "Applied {} rating change from game {}: player {} {}, player {} {}",
                request.gameType, saved.id, white.id, change.white, black.id, change.black
            )
        }
        return saved
    }

    /**
     * Replaces the game's details. The rating snapshot and rating changes are owned by the server and kept
     * from the stored game; editing a game does not re-rate it or touch the players' ratings.
     */
    fun update(id: String, game: Game): Game? {
        val existing = gameRepository.findByIdOrNull(id) ?: run {
            log.warn("Update failed: game {} not found", id)
            return null
        }
        val (white, black) = resolvePlayers(game.whitePlayerId, game.blackPlayerId)
        val saved = gameRepository.save(
            game.copy(
                id = id,
                whitePlayerName = white.fullName,
                blackPlayerName = black.fullName,
                whitePlayerRating = existing.whitePlayerRating,
                blackPlayerRating = existing.blackPlayerRating,
                whitePlayerRatingChange = existing.whitePlayerRatingChange,
                blackPlayerRatingChange = existing.blackPlayerRatingChange
            )
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

    private companion object {
        private val log = LogManager.getLogger(GameService::class.java)
    }

    private fun Player.eloForType(gameType: GameType): Int = when (gameType) {
        GameType.RAPID -> rapidElo
        GameType.BLITZ -> blitzElo
        GameType.BULLET -> bulletElo
        GameType.CLASSICAL -> classicalElo
    }

    private fun Player.withElo(gameType: GameType, elo: Int): Player = when (gameType) {
        GameType.RAPID -> copy(rapidElo = elo)
        GameType.BLITZ -> copy(blitzElo = elo)
        GameType.BULLET -> copy(bulletElo = elo)
        GameType.CLASSICAL -> copy(classicalElo = elo)
    }
}