package org.tesomas.chessclub.service

import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Service
import org.tesomas.chessclub.model.PageResponse
import org.tesomas.chessclub.model.PlayerGameFilter
import org.tesomas.chessclub.model.PlayerGameView
import org.tesomas.chessclub.repository.PlayerGameQueryRepository
import org.tesomas.chessclub.repository.PlayerRepository

/** A player's game history, seen from that player's side of the board. */
@Service
class PlayerGameService(
    private val playerRepository: PlayerRepository,
    private val playerGameQueryRepository: PlayerGameQueryRepository
) {

    /** @throws PlayerNotFoundException when [playerId] does not exist */
    fun findGames(playerId: String, filter: PlayerGameFilter): PageResponse<PlayerGameView> {
        log.debug("Listing games of player {} with {}", playerId, filter)
        if (!playerRepository.existsById(playerId)) {
            // ApiExceptionHandler logs the WARN
            throw PlayerNotFoundException(playerId)
        }
        val page = playerGameQueryRepository.findPlayerGames(playerId, filter)
        log.debug("Player {} games: {} of {} on page {}", playerId, page.content.size, page.totalElements, page.page)
        return page.map { PlayerGameView.from(it, playerId) }
    }

    private companion object {
        private val log = LogManager.getLogger(PlayerGameService::class.java)
    }
}
