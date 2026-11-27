package org.tesomas.chessclub.model

import org.junit.jupiter.api.Test
import org.tesomas.chessclub.TestFixtures.aGame
import java.time.LocalDate
import kotlin.test.assertEquals

/** Covers the colour/outcome translations and the Game -> PlayerGameView mapping. */
class PlayerPerspectiveTest {

    @Test
    fun `outcome from the white side`() {
        assertEquals(PlayerOutcome.WIN, PlayerOutcome.from(GameResult.WHITE_WINS, PlayerColor.WHITE))
        assertEquals(PlayerOutcome.LOSS, PlayerOutcome.from(GameResult.BLACK_WINS, PlayerColor.WHITE))
        assertEquals(PlayerOutcome.DRAW, PlayerOutcome.from(GameResult.DRAW, PlayerColor.WHITE))
    }

    @Test
    fun `outcome from the black side`() {
        assertEquals(PlayerOutcome.WIN, PlayerOutcome.from(GameResult.BLACK_WINS, PlayerColor.BLACK))
        assertEquals(PlayerOutcome.LOSS, PlayerOutcome.from(GameResult.WHITE_WINS, PlayerColor.BLACK))
        assertEquals(PlayerOutcome.DRAW, PlayerOutcome.from(GameResult.DRAW, PlayerColor.BLACK))
    }

    @Test
    fun `outcome translates back to the game result for each colour`() {
        assertEquals(GameResult.WHITE_WINS, PlayerOutcome.WIN.toGameResult(PlayerColor.WHITE))
        assertEquals(GameResult.BLACK_WINS, PlayerOutcome.WIN.toGameResult(PlayerColor.BLACK))
        assertEquals(GameResult.BLACK_WINS, PlayerOutcome.LOSS.toGameResult(PlayerColor.WHITE))
        assertEquals(GameResult.WHITE_WINS, PlayerOutcome.LOSS.toGameResult(PlayerColor.BLACK))
        assertEquals(GameResult.DRAW, PlayerOutcome.DRAW.toGameResult(PlayerColor.WHITE))
        assertEquals(GameResult.DRAW, PlayerOutcome.DRAW.toGameResult(PlayerColor.BLACK))
    }

    @Test
    fun `view from the white player's perspective`() {
        // Arrange
        val game = aGame(
            "p1", "p2", id = "g1", whitePlayerName = "Alice Anders", blackPlayerName = "Ben Brooks",
            gameType = GameType.BLITZ, result = GameResult.WHITE_WINS, time = "3+2",
            date = LocalDate.of(2026, 10, 1), whitePlayerRating = 1500, blackPlayerRating = 1480,
            whitePlayerRatingChange = 9, blackPlayerRatingChange = -9
        )

        // Act
        val view = PlayerGameView.from(game, "p1")

        // Assert
        assertEquals(
            PlayerGameView(
                gameId = "g1", date = LocalDate.of(2026, 10, 1), gameType = GameType.BLITZ, time = "3+2",
                color = PlayerColor.WHITE, outcome = PlayerOutcome.WIN, playerRating = 1500,
                playerRatingChange = 9, opponentId = "p2", opponentName = "Ben Brooks", opponentRating = 1480,
                opponentRatingChange = -9
            ),
            view
        )
    }

    @Test
    fun `view from the black player's perspective`() {
        // Arrange
        val game = aGame(
            "p1", "p2", id = "g1", whitePlayerName = "Alice Anders", blackPlayerName = "Ben Brooks",
            result = GameResult.WHITE_WINS, whitePlayerRating = 1500, blackPlayerRating = 1480,
            whitePlayerRatingChange = 9, blackPlayerRatingChange = -9
        )

        // Act
        val view = PlayerGameView.from(game, "p2")

        // Assert
        assertEquals(PlayerColor.BLACK, view.color)
        assertEquals(PlayerOutcome.LOSS, view.outcome)
        assertEquals(1480, view.playerRating)
        assertEquals(-9, view.playerRatingChange)
        assertEquals("p1", view.opponentId)
        assertEquals("Alice Anders", view.opponentName)
        assertEquals(1500, view.opponentRating)
        assertEquals(9, view.opponentRatingChange)
    }

    @Test
    fun `a self-play game is viewed from the white side`() {
        // Arrange
        val game = aGame(
            "p1", "p1", id = "g1", whitePlayerName = "Alice Anders", blackPlayerName = "Alice Anders",
            result = GameResult.WHITE_WINS, whitePlayerRating = 1500, blackPlayerRating = 1490
        )

        // Act
        val view = PlayerGameView.from(game, "p1")

        // Assert
        assertEquals(PlayerColor.WHITE, view.color)
        assertEquals(PlayerOutcome.WIN, view.outcome)
        assertEquals(1500, view.playerRating)
        assertEquals("p1", view.opponentId)
        assertEquals(1490, view.opponentRating)
    }

    @Test
    fun `page response computes total pages and keeps metadata when mapped`() {
        // Act
        val page = PageResponse.of(listOf(1, 2), page = 1, size = 4, totalElements = 6)
        val mapped = page.map { it.toString() }

        // Assert
        assertEquals(2, page.totalPages)
        assertEquals(PageResponse(listOf("1", "2"), 1, 4, 6, 2), mapped)
        assertEquals(0, PageResponse.of(emptyList<Int>(), 0, 25, 0).totalPages)
        assertEquals(1, PageResponse.of(listOf(1), 0, 25, 25).totalPages)
        assertEquals(2, PageResponse.of(listOf(1), 0, 25, 26).totalPages)
    }
}
