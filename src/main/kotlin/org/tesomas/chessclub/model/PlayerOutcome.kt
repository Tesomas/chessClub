package org.tesomas.chessclub.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Result of a game from one player's perspective")
enum class PlayerOutcome {
    WIN, LOSS, DRAW;

    /** The stored [GameResult] that means this outcome for a player of the given [color]. */
    fun toGameResult(color: PlayerColor): GameResult = when (this) {
        DRAW -> GameResult.DRAW
        WIN -> if (color == PlayerColor.WHITE) GameResult.WHITE_WINS else GameResult.BLACK_WINS
        LOSS -> if (color == PlayerColor.WHITE) GameResult.BLACK_WINS else GameResult.WHITE_WINS
    }

    companion object {
        /** Translates a stored [GameResult] into the outcome for the player who had [color]. */
        fun from(result: GameResult, color: PlayerColor): PlayerOutcome = when (result) {
            GameResult.DRAW -> DRAW
            GameResult.WHITE_WINS -> if (color == PlayerColor.WHITE) WIN else LOSS
            GameResult.BLACK_WINS -> if (color == PlayerColor.BLACK) WIN else LOSS
        }
    }
}
