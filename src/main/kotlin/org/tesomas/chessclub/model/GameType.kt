package org.tesomas.chessclub.model

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Chess time control category: RAPID (10–30 min), BLITZ (3–5 min), BULLET (1–2 min), CLASSICAL (60+ min)")
enum class GameType {
    RAPID, BLITZ, BULLET, CLASSICAL
}
