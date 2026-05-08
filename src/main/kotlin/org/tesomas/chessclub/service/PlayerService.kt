package org.tesomas.chessclub.service

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.tesomas.chessclub.model.Player
import org.tesomas.chessclub.repository.PlayerRepository

@Service
class PlayerService(private val playerRepository: PlayerRepository) {

    fun findAll(): List<Player> = playerRepository.findAll()

    fun findById(id: String): Player? = playerRepository.findByIdOrNull(id)

    fun create(player: Player): Player = playerRepository.save(player.copy(id = null))

    fun update(id: String, player: Player): Player? {
        if (!playerRepository.existsById(id)) return null
        return playerRepository.save(player.copy(id = id))
    }

    fun delete(id: String): Boolean {
        if (!playerRepository.existsById(id)) return false
        playerRepository.deleteById(id)
        return true
    }
}
