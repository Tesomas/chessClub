package org.tesomas.chessclub.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "The colour a player had in a game")
enum class PlayerColor {
    WHITE, BLACK
}
