package org.tesomas.chessclub.service

import org.junit.jupiter.api.Test
import org.tesomas.chessclub.model.GameResult
import kotlin.test.assertEquals

class EloCalculatorTest {

    @Test
    fun `equal ratings give half the K-factor to the winner`() {
        assertEquals(RatingChange(10, -10), EloCalculator.ratingChange(1500, 1500, GameResult.WHITE_WINS))
        assertEquals(RatingChange(-10, 10), EloCalculator.ratingChange(1500, 1500, GameResult.BLACK_WINS))
    }

    @Test
    fun `a draw between equal ratings changes nothing`() {
        assertEquals(RatingChange.NONE, EloCalculator.ratingChange(1500, 1500, GameResult.DRAW))
    }

    @Test
    fun `an upset earns more than an expected win`() {
        // 400 points apart: the favourite is expected to score 10/11
        assertEquals(RatingChange(2, -2), EloCalculator.ratingChange(1900, 1500, GameResult.WHITE_WINS))
        assertEquals(RatingChange(-18, 18), EloCalculator.ratingChange(1900, 1500, GameResult.BLACK_WINS))
    }

    @Test
    fun `a draw moves points from the higher-rated player to the lower`() {
        assertEquals(RatingChange(-8, 8), EloCalculator.ratingChange(1900, 1500, GameResult.DRAW))
    }

    @Test
    fun `changes are always zero-sum`() {
        for (white in listOf(800, 1234, 1500, 2100)) for (black in listOf(900, 1499, 2500)) {
            for (result in GameResult.entries) {
                val change = EloCalculator.ratingChange(white, black, result)
                assertEquals(0, change.white + change.black, "$white vs $black, $result")
            }
        }
    }
}
