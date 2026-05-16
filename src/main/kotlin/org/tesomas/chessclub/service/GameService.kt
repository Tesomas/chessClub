package org.tesomas.chessclub.service

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

    fun findAll(): List<Game> = gameRepository.findAll()

    fun findById(id: String): Game? = gameRepository.findByIdOrNull(id)

    fun create(request: GameCreateRequest): Game {
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

    fun update(id: String, game: Game): Game? {
        if (!gameRepository.existsById(id)) return null
        val (white, black) = resolvePlayers(game.whitePlayerId, game.blackPlayerId)
        return gameRepository.save(game.copy(id = id, whitePlayerName = white.fullName, blackPlayerName = black.fullName))
    }

    fun delete(id: String): Boolean {
        if (!gameRepository.existsById(id)) return false
        gameRepository.deleteById(id)
        return true
    }

    private fun resolvePlayers(whiteId: String, blackId: String): Pair<Player, Player> {
        val white = playerRepository.findByIdOrNull(whiteId)
            ?: throw IllegalArgumentException("White player $whiteId not found")
        val black = playerRepository.findByIdOrNull(blackId)
            ?: throw IllegalArgumentException("Black player $blackId not found")
        return white to black
    }

    private val Player.fullName get() = "$firstName $lastName"

    private fun Player.eloForType(gameType: GameType): Int = when (gameType) {
        GameType.RAPID -> rapidElo
        GameType.BLITZ -> blitzElo
        GameType.BULLET -> bulletElo
        GameType.CLASSICAL -> classicalElo
    }
}
