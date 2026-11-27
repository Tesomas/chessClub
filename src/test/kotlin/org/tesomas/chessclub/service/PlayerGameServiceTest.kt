package org.tesomas.chessclub.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.tesomas.chessclub.TestFixtures.aGame
import org.tesomas.chessclub.model.GameResult
import org.tesomas.chessclub.model.GameType
import org.tesomas.chessclub.model.PageResponse
import org.tesomas.chessclub.model.PlayerColor
import org.tesomas.chessclub.model.PlayerGameFilter
import org.tesomas.chessclub.model.PlayerGameView
import org.tesomas.chessclub.model.PlayerOutcome
import org.tesomas.chessclub.repository.PlayerGameQueryRepository
import org.tesomas.chessclub.repository.PlayerRepository
import java.time.LocalDate
import kotlin.test.assertEquals

class PlayerGameServiceTest {

    private val playerRepository = mockk<PlayerRepository>()
    private val queryRepository = mockk<PlayerGameQueryRepository>()
    private val service = PlayerGameService(playerRepository, queryRepository)

    private val asWhite = aGame(
        "p1", "p2", id = "g1", whitePlayerName = "Alice Anders", blackPlayerName = "Ben Brooks",
        gameType = GameType.BLITZ, result = GameResult.WHITE_WINS, time = "3+2",
        date = LocalDate.of(2026, 10, 1), whitePlayerRating = 1500, blackPlayerRating = 1480
    )
    private val asBlack = aGame(
        "p3", "p1", id = "g2", whitePlayerName = "Carla Castillo", blackPlayerName = "Alice Anders",
        gameType = GameType.RAPID, result = GameResult.WHITE_WINS, time = "10+0",
        date = LocalDate.of(2026, 9, 1), whitePlayerRating = 1620, blackPlayerRating = 1490
    )

    @Test
    fun `throws PlayerNotFoundException and never queries when the player does not exist`() {
        // Arrange
        every { playerRepository.existsById("missing") } returns false

        // Act
        val ex = assertThrows<PlayerNotFoundException> { service.findGames("missing", PlayerGameFilter()) }

        // Assert
        assertEquals("Player missing not found", ex.message)
        verify(exactly = 0) { queryRepository.findPlayerGames(any(), any()) }
    }

    @Test
    fun `passes the filter through and maps games from the player's perspective on both colours`() {
        // Arrange
        val filter = PlayerGameFilter(page = 1, size = 2, opponentName = "an")
        every { playerRepository.existsById("p1") } returns true
        every { queryRepository.findPlayerGames("p1", filter) } returns
            PageResponse.of(listOf(asWhite, asBlack), page = 1, size = 2, totalElements = 5)

        // Act
        val result = service.findGames("p1", filter)

        // Assert
        assertEquals(1, result.page)
        assertEquals(2, result.size)
        assertEquals(5, result.totalElements)
        assertEquals(3, result.totalPages)
        assertEquals(
            listOf(
                PlayerGameView(
                    gameId = "g1", date = LocalDate.of(2026, 10, 1), gameType = GameType.BLITZ, time = "3+2",
                    color = PlayerColor.WHITE, outcome = PlayerOutcome.WIN, playerRating = 1500,
                    playerRatingChange = 0, opponentId = "p2", opponentName = "Ben Brooks", opponentRating = 1480,
                    opponentRatingChange = 0
                ),
                PlayerGameView(
                    gameId = "g2", date = LocalDate.of(2026, 9, 1), gameType = GameType.RAPID, time = "10+0",
                    color = PlayerColor.BLACK, outcome = PlayerOutcome.LOSS, playerRating = 1490,
                    playerRatingChange = 0, opponentId = "p3", opponentName = "Carla Castillo", opponentRating = 1620,
                    opponentRatingChange = 0
                )
            ),
            result.content
        )
    }

    @Test
    fun `returns an empty page when the player exists but has no matching games`() {
        // Arrange
        every { playerRepository.existsById("p1") } returns true
        every { queryRepository.findPlayerGames("p1", any()) } returns PageResponse.of(emptyList(), 0, 25, 0)

        // Act
        val result = service.findGames("p1", PlayerGameFilter())

        // Assert
        assertEquals(emptyList(), result.content)
        assertEquals(0, result.totalPages)
    }
}
