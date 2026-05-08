package org.tesomas.chessclub.repository

import org.springframework.data.mongodb.repository.MongoRepository
import org.tesomas.chessclub.model.Player

interface PlayerRepository : MongoRepository<Player, String>
