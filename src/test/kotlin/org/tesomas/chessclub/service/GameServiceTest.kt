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
import org.tesomas.chessclub.TestFixtures.aPlayer
import org.tesomas.chessclub.model.Game
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
    fun `create clears the id and denormalizes player names from player records`() {
        // Arrange
        val saved = slot<Game>()
        every { gameRepository.save(capture(saved)) } answers { saved.captured.copy(id = "generated") }
        val input = aGame("w1", "b1", id = "client-id", whitePlayerName = "spoofed", blackPlayerName = "spoofed")

        // Act
        val result = service.create(input)

        // Assert
        assertNull(saved.captured.id)
        assertEquals("Magnus Carlsen", saved.captured.whitePlayerName)
        assertEquals("Hikaru Nakamura", saved.captured.blackPlayerName)
        assertEquals("generated", result.id)
    }

    @Test
    fun `create throws PlayerNotFoundException when the white player is missing`() {
        // Act
        val ex = assertThrows<PlayerNotFoundException> { service.create(aGame("missing", "b1")) }

        // Assert
        assertEquals("Player missing not found", ex.message)
        verify(exactly = 0) { gameRepository.save(any<Game>()) }
    }

    @Test
    fun `create throws PlayerNotFoundException when the black player is missing`() {
        // Act
        val ex = assertThrows<PlayerNotFoundException> { service.create(aGame("w1", "missing")) }

        // Assert
        assertEquals("Player missing not found", ex.message)
        verify(exactly = 0) { gameRepository.save(any<Game>()) }
    }

    @Test
    fun `update keeps the path id and refreshes denormalized names`() {
        // Arrange
        val saved = slot<Game>()
        every { gameRepository.existsById("g1") } returns true
        every { gameRepository.save(capture(saved)) } answers { saved.captured }

        // Act
        val result = service.update("g1", aGame("w1", "b1", id = "other-id", pgn = "1. d4 d5"))

        // Assert
        assertEquals("g1", result?.id)
        assertEquals("1. d4 d5", result?.pgn)
        assertEquals("Magnus Carlsen", saved.captured.whitePlayerName)
        assertEquals("Hikaru Nakamura", saved.captured.blackPlayerName)
    }

    @Test
    fun `update returns null without resolving players when the game is missing`() {
        // Arrange
        every { gameRepository.existsById("missing") } returns false

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
        every { gameRepository.existsById("g1") } returns true

        // Act
        val ex = assertThrows<PlayerNotFoundException> { service.update("g1", aGame("w1", "missing")) }

        // Assert
        assertEquals("Player missing not found", ex.message)
        verify(exactly = 0) { gameRepository.save(any<Game>()) }
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
