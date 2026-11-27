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
import org.tesomas.chessclub.model.GameType
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
    fun `create throws PlayerNotFoundException when the white player is missing`() {
        // Act
        val ex = assertThrows<PlayerNotFoundException> { service.create(aGameRequest("missing", "b1")) }

        // Assert
        assertEquals("Player missing not found", ex.message)
        verify(exactly = 0) { gameRepository.save(any<Game>()) }
    }

    @Test
    fun `create throws PlayerNotFoundException when the black player is missing`() {
        // Act
        val ex = assertThrows<PlayerNotFoundException> { service.create(aGameRequest("w1", "missing")) }

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

    // --- PGN size limit ----------------------------------------------------------------------

    @Test
    fun `create accepts a pgn of exactly 65536 characters`() {
        // Arrange
        val saved = slot<Game>()
        every { gameRepository.save(capture(saved)) } answers { saved.captured.copy(id = "generated") }

        // Act
        service.create(aGameRequest("w1", "b1", pgn = "x".repeat(GameService.MAX_PGN_LENGTH)))

        // Assert
        assertEquals(65_536, saved.captured.pgn?.length)
    }

    @Test
    fun `create rejects a pgn of 65537 characters before touching any repository`() {
        // Act
        val ex = assertThrows<InvalidGameException> {
            service.create(aGameRequest("w1", "b1", pgn = "x".repeat(65_537)))
        }

        // Assert
        assertEquals("pgn must be at most 65536 characters", ex.message)
        verify(exactly = 0) { playerRepository.findById(any()) }
        verify(exactly = 0) { gameRepository.save(any<Game>()) }
    }

    @Test
    fun `create still allows a null pgn`() {
        // Arrange
        val saved = slot<Game>()
        every { gameRepository.save(capture(saved)) } answers { saved.captured }

        // Act
        service.create(aGameRequest("w1", "b1", pgn = null))

        // Assert
        assertNull(saved.captured.pgn)
    }

    @Test
    fun `update accepts a pgn of exactly 65536 characters`() {
        // Arrange
        val pgn = "x".repeat(65_536)
        every { gameRepository.existsById("g1") } returns true
        every { gameRepository.save(any<Game>()) } answers { firstArg() }

        // Act
        val result = service.update("g1", aGame("w1", "b1", pgn = pgn))

        // Assert
        assertEquals(pgn, result?.pgn)
    }

    @Test
    fun `update rejects a pgn of 65537 characters before checking the game exists`() {
        // Act
        val ex = assertThrows<InvalidGameException> {
            service.update("g1", aGame("w1", "b1", pgn = "x".repeat(65_537)))
        }

        // Assert
        assertEquals("pgn must be at most 65536 characters", ex.message)
        verify(exactly = 0) { gameRepository.existsById(any()) }
        verify(exactly = 0) { gameRepository.save(any<Game>()) }
    }

    @Test
    fun `update still allows a null pgn`() {
        // Arrange
        every { gameRepository.existsById("g1") } returns true
        every { gameRepository.save(any<Game>()) } answers { firstArg() }

        // Act
        val result = service.update("g1", aGame("w1", "b1").copy(pgn = null))

        // Assert
        assertNull(result?.pgn)
    }
    // The limit counts UTF-16 code units (String.length), not bytes or code points.

    @Test
    fun `a pgn of 65536 multibyte BMP characters is accepted although it is far more than 65536 bytes`() {
        // Arrange: each of these is one UTF-16 code unit but 2 or 3 bytes in UTF-8
        every { gameRepository.save(any<Game>()) } answers { firstArg() }
        listOf("\u00e9", "\u4e2d").forEach { char ->
            val pgn = char.repeat(65_536)

            // Act
            val result = service.create(aGameRequest("w1", "b1", pgn = pgn))

            // Assert
            assertEquals(65_536, result.pgn?.length)
            assertTrue(pgn.toByteArray(Charsets.UTF_8).size > 65_536)
        }
    }

    @Test
    fun `32768 emoji are 65536 UTF-16 code units and are accepted`() {
        // Arrange: U+1F600 is a surrogate pair, i.e. two code units
        val emoji = "\uD83D\uDE00"
        val pgn = emoji.repeat(32_768)
        every { gameRepository.save(any<Game>()) } answers { firstArg() }

        // Act
        val result = service.create(aGameRequest("w1", "b1", pgn = pgn))

        // Assert
        assertEquals(65_536, pgn.length)
        assertEquals(32_768, pgn.codePointCount(0, pgn.length))
        assertEquals(pgn, result.pgn)
    }

    @Test
    fun `32769 emoji are 65538 UTF-16 code units and are rejected`() {
        // Arrange
        val pgn = "\uD83D\uDE00".repeat(32_769)

        // Act
        val ex = assertThrows<InvalidGameException> { service.create(aGameRequest("w1", "b1", pgn = pgn)) }

        // Assert
        assertEquals(65_538, pgn.length)
        assertEquals("pgn must be at most 65536 characters", ex.message)
        verify(exactly = 0) { gameRepository.save(any<Game>()) }
    }

    @Test
    fun `update counts UTF-16 code units the same way`() {
        // Arrange
        every { gameRepository.existsById("g1") } returns true
        every { gameRepository.save(any<Game>()) } answers { firstArg() }

        // Act + Assert
        assertEquals(65_536, service.update("g1", aGame("w1", "b1", pgn = "\uD83D\uDE00".repeat(32_768)))?.pgn?.length)
        assertThrows<InvalidGameException> { service.update("g1", aGame("w1", "b1", pgn = "\uD83D\uDE00".repeat(32_769))) }
    }
}
