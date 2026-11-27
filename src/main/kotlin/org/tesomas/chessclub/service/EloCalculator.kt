package org.tesomas.chessclub.service

import org.tesomas.chessclub.model.GameResult
import kotlin.math.pow
import kotlin.math.roundToInt

/** Rating points each side gains (positive) or loses (negative) from one game. */
data class RatingChange(val white: Int, val black: Int) {
    companion object {
        val NONE = RatingChange(0, 0)
    }
}

/**
 * Standard ELO: `change = K * (score - expected)`, with `expected = 1 / (1 + 10^((opp - own) / 400))`.
 *
 * A single K-factor for everyone (the same one `docker/mongo-seed/seed.js` uses); no provisional period yet.
 * The change is rounded once for white and mirrored for black, so a game never creates or destroys points.
 */
object EloCalculator {
    const val K_FACTOR = 20

    fun expectedScore(rating: Int, opponentRating: Int): Double =
        1.0 / (1.0 + 10.0.pow((opponentRating - rating) / 400.0))

    fun ratingChange(whiteRating: Int, blackRating: Int, result: GameResult): RatingChange {
        val whiteScore = when (result) {
            GameResult.WHITE_WINS -> 1.0
            GameResult.BLACK_WINS -> 0.0
            GameResult.DRAW -> 0.5
        }
        val white = (K_FACTOR * (whiteScore - expectedScore(whiteRating, blackRating))).roundToInt()
        return RatingChange(white = white, black = -white)
    }
}
