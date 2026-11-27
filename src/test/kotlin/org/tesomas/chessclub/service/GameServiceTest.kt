package org.tesomas.chessclub.service

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.tesomas.chessclub.TestFixtures.aGame
import org.tesomas.chessclub.TestFixtures.aGameRequest
import org.tesomas.chessclub.TestFixtures.aPlayer
import org.tesomas.chessclub.model.Game
import org.tesomas.chessclub.model.GameResult
import org.tesomas.chessclub.model.GameType
import org.tesomas.chessclub.model.Player
import org.tesomas.chessclub.repository.GameRepository
import org.tesomas.chessclub.repository.PlayerRepository
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameServiceTest {

    private val gameRepository = mockk<GameRepository>()
    private val playerRepository = mockk<PlayerRepository>()
    private val service = GameService(gameRepository, playerRepository)

    private val white = aPlayer(id = "w1", firstName = "Magnus", lastName = "Carlsen")
    private val black = aPlayer(id = "b1", firstName = "Hikaru", lastName = "Nakamura")

    @BeforeEach
    fun stubPlayers() {
        every { playerRepository.findById("w1") } returns Optional.of(white)
        every { playerRepository.findById("b1") } returns Optional.of(black)
        every { playerRepository.findById("missing") } returns Optional.empty()
        every { playerRepository.save(any<Player>()) } answers { firstArg() }
    }

    @Test
    fun `findAll returns all games from the repository`() {
        // Arrange
        val games = listOf(aGame("w1", "b1", id = "g1"))
        every { gameRepository.findAll() } returns games

        // Act
        val result = service.findAll()

        // Assert
        assertEquals(games, result)
    }

    @Test
    fun `findById returns the game when it exists`() {
        // Arrange
        val game = aGame("w1", "b1", id = "g1")
        every { gameRepository.findById("g1") } returns Optional.of(game)

        // Act
        val result = service.findById("g1")

        // Assert
        assertEquals(game, result)
    }

    @Test
    fun `findById returns null when the game is missing`() {
        // Arrange
        every { gameRepository.findById("missing") } returns Optional.empty()

        // Act
        val result = service.findById("missing")

        // Assert
        assertNull(result)
    }

    @Test
    fun `create denormalizes player names and rating snapshots from player records`() {
        // Arrange
        val saved = slot<Game>()
        every { gameRepository.save(capture(saved)) } answers { saved.captured.copy(id = "generated") }
        val request = aGameRequest("w1", "b1", gameType = GameType.BLITZ)

        // Act
        val result = service.create(request)

        // Assert
        assertNull(saved.captured.id)
        assertEquals("Magnus Carlsen", saved.captured.whitePlayerName)
        assertEquals("Hikaru Nakamura", saved.captured.blackPlayerName)
        assertEquals(white.blitzElo, saved.captured.whitePlayerRating)
        assertEquals(black.blitzElo, saved.captured.blackPlayerRating)
        assertEquals(request.result, saved.captured.result)
        assertEquals("generated", result.id)
    }

    @Test
    fun `create records rating changes and updates both players' rating for the game type`() {
        // Arrange
        val w = aPlayer(id = "w2", firstName = "Ann", lastName = "Lee", blitzElo = 1500, rapidElo = 1700)
        val b = aPlayer(id = "b2", firstName = "Bob", lastName = "Brooks", blitzElo = 1500, rapidElo = 1300)
        every { playerRepository.findById("w2") } returns Optional.of(w)
        every { playerRepository.findById("b2") } returns Optional.of(b)
        val saved = slot<Game>()
        every { gameRepository.save(capture(saved)) } answers { saved.captured.copy(id = "g1") }
        val players = mutableListOf<Player>()
        every { playerRepository.save(capture(players)) } answers { firstArg() }

        // Act
        val result = service.create(aGameRequest("w2", "b2", gameType = GameType.BLITZ, result = GameResult.BLACK_WINS))

        // Assert
        assertEquals(1500, result.whitePlayerRating)
        assertEquals(1500, result.blackPlayerRating)
        assertEquals(-10, result.whitePlayerRatingChange)
        assertEquals(10, result.blackPlayerRatingChange)
        assertEquals(listOf(w.copy(blitzElo = 1490), b.copy(blitzElo = 1510)), players)
    }

    @Test
    fun `create stores no rating change and leaves the player alone for a self-play game`() {
        // Arrange
        val saved = slot<Game>()
        every { gameRepository.save(capture(saved)) } answers { saved.captured.copy(id = "g1") }

        // Act
        val result = service.create(aGameRequest("w1", "w1"))

        // Assert
        assertEquals(0, result.whitePlayerRatingChange)
        assertEquals(0, result.blackPlayerRatingChange)
        verify(exactly = 0) { playerRepository.save(any<Player>()) }
    }

    @Test
    fun `create throws PlayerNotFoundException when the white player is missing`() {
        // Act
        val ex = assertThrows<PlayerNotFoundException> { service.create(aGameRequest("missing", "b1")) }

        // Assert
        assertEquals("Player missing not found", ex.message)
        verify(exactly = 0) { gameRepository.save(any<Game>()) }
        verify(exactly = 0) { playerRepository.save(any<Player>()) }
    }

    @Test
    fun `create throws PlayerNotFoundException when the black player is missing`() {
        // Act
        val ex = assertThrows<PlayerNotFoundException> { service.create(aGameRequest("w1", "missing")) }

        // Assert
        assertEquals("Player missing not found", ex.message)
        verify(exactly = 0) { gameRepository.save(any<Game>()) }
        verify(exactly = 0) { playerRepository.save(any<Player>()) }
    }

    @Test
    fun `update keeps the path id, the stored rating snapshot and changes, and refreshes denormalized names`() {
        // Arrange
        val saved = slot<Game>()
        val stored = aGame(
            "w1", "b1", id = "g1", whitePlayerRating = 1500, blackPlayerRating = 1480,
            whitePlayerRatingChange = 9, blackPlayerRatingChange = -9
        )
        every { gameRepository.findById("g1") } returns Optional.of(stored)
        every { gameRepository.save(capture(saved)) } answers { saved.captured }

        // Act
        val result = service.update("g1", aGame("w1", "b1", id = "other-id", pgn = "1. d4 d5"))

        // Assert
        assertEquals("g1", result?.id)
        assertEquals(1500, result?.whitePlayerRating)
        assertEquals(1480, result?.blackPlayerRating)
        assertEquals(9, result?.whitePlayerRatingChange)
        assertEquals(-9, result?.blackPlayerRatingChange)
        verify(exactly = 0) { playerRepository.save(any<Player>()) }
        assertEquals("1. d4 d5", result?.pgn)
        assertEquals("Magnus Carlsen", saved.captured.whitePlayerName)
        assertEquals("Hikaru Nakamura", saved.captured.blackPlayerName)
    }

    @Test
    fun `update returns null without resolving players when the game is missing`() {
        // Arrange
        every { gameRepository.findById("missing") } returns Optional.empty()

        // Act
        val result = service.update("missing", aGame("w1", "b1"))

        // Assert
        assertNull(result)
        verify(exactly = 0) { playerRepository.findById(any()) }
        verify(exactly = 0) { gameRepository.save(any<Game>()) }
    }

    @Test
    fun `update throws PlayerNotFoundException when a referenced player is missing`() {
        // Arrange
        every { gameRepository.findById("g1") } returns Optional.of(aGame("w1", "b1", id = "g1"))

        // Act
        val ex = assertThrows<PlayerNotFoundException> { service.update("g1", aGame("w1", "missing")) }

        // Assert
        assertEquals("Player missing not found", ex.message)
        verify(exactly = 0) { gameRepository.save(any<Game>()) }
        verify(exactly = 0) { playerRepository.save(any<Player>()) }
    }

    @Test
    fun `delete removes the game and returns true when it exists`() {
        // Arrange
        every { gameRepository.existsById("g1") } returns true
        every { gameRepository.deleteById("g1") } just runs

        // Act
        val result = service.delete("g1")

        // Assert
        assertTrue(result)
        verify(exactly = 1) { gameRepository.deleteById("g1") }
    }

    @Test
    fun `delete returns false when the game is missing`() {
        // Arrange
        every { gameRepository.existsById("missing") } returns false

        // Act
        val result = service.delete("missing")

        // Assert
        assertFalse(result)
        verify(exactly = 0) { gameRepository.deleteById(any()) }
    }
}
