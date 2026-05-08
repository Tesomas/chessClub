package org.tesomas.chessclub.repository

import org.springframework.data.mongodb.repository.MongoRepository
import org.tesomas.chessclub.model.Game

interface GameRepository : MongoRepository<Game, String> {
    fun findByWhitePlayerId(whitePlayerId: String): List<Game>
    fun findByBlackPlayerId(blackPlayerId: String): List<Game>
}
