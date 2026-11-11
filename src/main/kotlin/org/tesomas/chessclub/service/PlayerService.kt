package org.tesomas.chessclub.service

import org.apache.logging.log4j.LogManager
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.tesomas.chessclub.model.Player
import org.tesomas.chessclub.repository.PlayerRepository

@Service
class PlayerService(private val playerRepository: PlayerRepository) {

    fun findAll(): List<Player> {
        log.debug("Listing all players")
        return playerRepository.findAll()
    }

    fun findById(id: String): Player? {
        log.debug("Looking up player {}", id)
        return playerRepository.findByIdOrNull(id)
            .also { if (it == null) log.warn("Player {} not found", id) }
    }

    fun create(player: Player): Player {
        val saved = playerRepository.save(player.copy(id = null))
        log.info("Created player {}", saved.id)
        log.debug("Created player {}: {} {}", saved.id, saved.firstName, saved.lastName)
        return saved
    }

    fun update(id: String, player: Player): Player? {
        if (!playerRepository.existsById(id)) {
            log.warn("Update failed: player {} not found", id)
            return null
        }
        val saved = playerRepository.save(player.copy(id = id))
        log.info("Updated player {}", id)
        return saved
    }

    fun delete(id: String): Boolean {
        if (!playerRepository.existsById(id)) {
            log.warn("Delete failed: player {} not found", id)
            return false
        }
        playerRepository.deleteById(id)
        log.info("Deleted player {}", id)
        return true
    }

    private companion object {
        private val log = LogManager.getLogger(PlayerService::class.java)
    }
}
