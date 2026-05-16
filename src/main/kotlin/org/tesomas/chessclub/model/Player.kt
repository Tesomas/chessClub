package org.tesomas.chessclub.model

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "players")
@Schema(description = "Chess club member with ELO ratings across time controls")
data class Player(
    @Id
    @Schema(description = "Unique player identifier (MongoDB ObjectId)", example = "64a1b2c3d4e5f6a7b8c9d0e1", accessMode = Schema.AccessMode.READ_ONLY)
    val id: String? = null,

    @Schema(description = "Player's first name", example = "Magnus")
    val firstName: String,

    @Schema(description = "Player's last name", example = "Carlsen")
    val lastName: String,

    @Schema(description = "Rapid chess ELO rating", example = "2850", minimum = "0", maximum = "3500")
    val rapidElo: Int = 1200,

    @Schema(description = "Blitz chess ELO rating", example = "2830", minimum = "0", maximum = "3500")
    val blitzElo: Int = 1200,

    @Schema(description = "Bullet chess ELO rating", example = "2800", minimum = "0", maximum = "3500")
    val bulletElo: Int = 1200,

    @Schema(description = "Classical chess ELO rating", example = "2860", minimum = "0", maximum = "3500")
    val classicalElo: Int = 1200
)
