package org.tesomas.chessclub.model

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDate

@Document(collection = "games")
@Schema(description = "A recorded chess game between two club members")
data class Game(
    @Id
    @Schema(description = "Unique game identifier (MongoDB ObjectId)", example = "64a1b2c3d4e5f6a7b8c9d0e1", accessMode = Schema.AccessMode.READ_ONLY)
    val id: String? = null,

    @Schema(description = "MongoDB ObjectId of the player with the white pieces", example = "64a1b2c3d4e5f6a7b8c9d0e2")
    val whitePlayerId: String,

    @Schema(description = "MongoDB ObjectId of the player with the black pieces", example = "64a1b2c3d4e5f6a7b8c9d0e3")
    val blackPlayerId: String,

    @Schema(description = "Display name of the white player at game time (denormalized snapshot)", example = "Magnus Carlsen", accessMode = Schema.AccessMode.READ_ONLY)
    val whitePlayerName: String = "",

    @Schema(description = "Display name of the black player at game time (denormalized snapshot)", example = "Fabiano Caruana", accessMode = Schema.AccessMode.READ_ONLY)
    val blackPlayerName: String = "",

    @Schema(description = "ELO rating of the white player at the time the game was played", example = "2850")
    val whitePlayerRating: Int,

    @Schema(description = "ELO rating of the black player at the time the game was played", example = "2820")
    val blackPlayerRating: Int,

    @Schema(description = "Time control category of the game")
    val gameType: GameType,

    @Schema(description = "Time control string (e.g. '10+0', '3+2', '1+0', '90+30')", example = "10+0")
    val time: String,

    @Schema(description = "Date the game was played", example = "2024-11-25")
    val date: LocalDate,

    @Schema(description = "Portable Game Notation (PGN) of the full game", example = "1. e4 e5 2. Nf3 Nc6 3. Bb5 *")
    val pgn: String
)
