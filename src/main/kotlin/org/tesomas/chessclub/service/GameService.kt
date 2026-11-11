package org.tesomas.chessclub.service

import org.apache.logging.log4j.LogManager
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.tesomas.chessclub.model.Game
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

    fun create(game: Game): Game {
        val (white, black) = resolvePlayers(game.whitePlayerId, game.blackPlayerId)
        val saved = gameRepository.save(
            game.copy(id = null, whitePlayerName = white.fullName, blackPlayerName = black.fullName)
        )
        log.info("Created game {} (white={}, black={})", saved.id, game.whitePlayerId, game.blackPlayerId)
        return saved
    }

    fun update(id: String, game: Game): Game? {
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

    private companion object {
        private val log = LogManager.getLogger(GameService::class.java)
    }
}
