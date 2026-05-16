package org.tesomas.chessclub.model

import java.time.LocalDate

data class GameCreateRequest(
    val whitePlayerId: String,
    val blackPlayerId: String,
    val gameType: GameType,
    val result: GameResult,
    val time: String,
    val date: LocalDate,
    val pgn: String? = null
)
