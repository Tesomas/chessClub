package org.tesomas.chessclub.model

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "A game seen from one player's perspective")
data class PlayerGameView(
    @Schema(description = "MongoDB ObjectId of the game", example = "64a1b2c3d4e5f6a7b8c9d0e1")
    val gameId: String,

    @Schema(description = "Date the game was played", example = "2026-10-01")
    val date: LocalDate,

    @Schema(description = "Time control category of the game")
    val gameType: GameType,

    @Schema(description = "Time control string", example = "3+2")
    val time: String,

    @Schema(description = "Colour the player had")
    val color: PlayerColor,

    @Schema(description = "Result for the player")
    val outcome: PlayerOutcome,

    @Schema(description = "The player's ELO rating at game time", example = "1500")
    val playerRating: Int,

    @Schema(description = "ELO points the player gained (positive) or lost (negative) from this game", example = "8")
    val playerRatingChange: Int,

    @Schema(description = "MongoDB ObjectId of the opponent", example = "64a1b2c3d4e5f6a7b8c9d0e2")
    val opponentId: String,

    @Schema(description = "Opponent's display name at game time (denormalized snapshot)", example = "Bob Brooks")
    val opponentName: String,

    @Schema(description = "Opponent's ELO rating at game time", example = "1480")
    val opponentRating: Int,

    @Schema(description = "ELO points the opponent gained (positive) or lost (negative) from this game", example = "-8")
    val opponentRatingChange: Int
) {
    companion object {
        /** Maps [game] to the view of [playerId]; the player is taken as black unless they are white. */
        fun from(game: Game, playerId: String): PlayerGameView {
            val isWhite = game.whitePlayerId == playerId
            val color = if (isWhite) PlayerColor.WHITE else PlayerColor.BLACK
            return PlayerGameView(
                gameId = requireNotNull(game.id) { "a stored game must have an id" },
                date = game.date,
                gameType = game.gameType,
                time = game.time,
                color = color,
                outcome = PlayerOutcome.from(game.result, color),
                playerRating = if (isWhite) game.whitePlayerRating else game.blackPlayerRating,
                playerRatingChange = if (isWhite) game.whitePlayerRatingChange else game.blackPlayerRatingChange,
                opponentId = if (isWhite) game.blackPlayerId else game.whitePlayerId,
                opponentName = if (isWhite) game.blackPlayerName else game.whitePlayerName,
                opponentRating = if (isWhite) game.blackPlayerRating else game.whitePlayerRating,
                opponentRatingChange = if (isWhite) game.blackPlayerRatingChange else game.whitePlayerRatingChange
            )
        }
    }
}
