package org.tesomas.chessclub.service

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.tesomas.chessclub.TestFixtures.aPlayer
import org.tesomas.chessclub.model.Player
import org.tesomas.chessclub.repository.PlayerRepository
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerServiceTest {

    private val playerRepository = mockk<PlayerRepository>()
    private val service = PlayerService(playerRepository)

    @Test
    fun `findAll returns all players from the repository`() {
        // Arrange
        val players = listOf(aPlayer(id = "p1"), aPlayer(id = "p2", firstName = "Hikaru"))
        every { playerRepository.findAll() } returns players

        // Act
        val result = service.findAll()

        // Assert
        assertEquals(players, result)
    }

    @Test
    fun `findById returns the player when it exists`() {
        // Arrange
        val player = aPlayer(id = "p1")
        every { playerRepository.findById("p1") } returns Optional.of(player)

        // Act
        val result = service.findById("p1")

        // Assert
        assertEquals(player, result)
    }

    @Test
    fun `findById returns null when the player is missing`() {
        // Arrange
        every { playerRepository.findById("missing") } returns Optional.empty()

        // Act
        val result = service.findById("missing")

        // Assert
        assertNull(result)
    }

    @Test
    fun `create clears any client-supplied id before saving`() {
        // Arrange
        val saved = slot<Player>()
        every { playerRepository.save(capture(saved)) } answers { saved.captured.copy(id = "generated") }

        // Act
        val result = service.create(aPlayer(id = "client-id"))

        // Assert
        assertNull(saved.captured.id)
        assertEquals("generated", result.id)
        assertEquals("Magnus", result.firstName)
    }

    @Test
    fun `update saves the player under the path id`() {
        // Arrange
        val saved = slot<Player>()
        every { playerRepository.existsById("p1") } returns true
        every { playerRepository.save(capture(saved)) } answers { saved.captured }

        // Act
        val result = service.update("p1", aPlayer(id = "other-id", rapidElo = 2750))

        // Assert
        assertEquals("p1", saved.captured.id)
        assertEquals("p1", result?.id)
        assertEquals(2750, result?.rapidElo)
    }

    @Test
    fun `update returns null and does not save when the player is missing`() {
        // Arrange
        every { playerRepository.existsById("missing") } returns false

        // Act
        val result = service.update("missing", aPlayer())

        // Assert
        assertNull(result)
        verify(exactly = 0) { playerRepository.save(any<Player>()) }
    }

    @Test
    fun `delete removes the player and returns true when it exists`() {
        // Arrange
        every { playerRepository.existsById("p1") } returns true
        every { playerRepository.deleteById("p1") } just runs

        // Act
        val result = service.delete("p1")

        // Assert
        assertTrue(result)
        verify(exactly = 1) { playerRepository.deleteById("p1") }
    }

    @Test
    fun `delete returns false and does not delete when the player is missing`() {
        // Arrange
        every { playerRepository.existsById("missing") } returns false

        // Act
        val result = service.delete("missing")

        // Assert
        assertFalse(result)
        verify(exactly = 0) { playerRepository.deleteById(any()) }
    }
}
