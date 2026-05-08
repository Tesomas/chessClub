package org.tesomas.chessclub.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDate

@Document(collection = "games")
data class Game(
    @Id val id: String? = null,
    val whitePlayerId: String,
    val blackPlayerId: String,
    val whitePlayerName: String = "",
    val blackPlayerName: String = "",
    val whitePlayerRating: Int,
    val blackPlayerRating: Int,
    val gameType: GameType,
    val time: String,
    val date: LocalDate,
    val pgn: String
)
