package org.tesomas.chessclub.service

import com.ninjasquad.springmockk.MockkSpyBean
import io.mockk.every
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.repository.findByIdOrNull
import org.tesomas.chessclub.TestFixtures.aGameRequest
import org.tesomas.chessclub.TestFixtures.aPlayer
import org.tesomas.chessclub.TestcontainersConfiguration
import org.tesomas.chessclub.model.Player
import org.tesomas.chessclub.repository.GameRepository
import org.tesomas.chessclub.repository.PlayerRepository
import kotlin.test.assertEquals

/** Recording a game is all-or-nothing against a real (replica-set) MongoDB. */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class GameServiceTransactionTest {

    @Autowired lateinit var gameService: GameService
    @Autowired lateinit var gameRepository: GameRepository
    @Autowired lateinit var playerRepository: PlayerRepository

    // Repositories write through MongoTemplate; a spy there lets one write fail mid-transaction.
    @MockkSpyBean lateinit var mongoTemplate: MongoTemplate

    private lateinit var white: Player
    private lateinit var black: Player

    @BeforeEach
    fun setUp() {
        gameRepository.deleteAll()
        playerRepository.deleteAll()
        white = playerRepository.save(aPlayer(firstName = "Ann", lastName = "Lee", rapidElo = 1500))
        black = playerRepository.save(aPlayer(firstName = "Bob", lastName = "Brooks", rapidElo = 1500))
    }

    @Test
    fun `a failure while rating the second player rolls back the game and the first player's rating`() {
        // Arrange: the white player's rating update succeeds, then the black player's fails
        every { mongoTemplate.save(match<Player> { it.id == black.id }, any<String>()) } throws IllegalStateException("boom")

        // Act
        assertThrows<IllegalStateException> {
            gameService.create(aGameRequest(requireNotNull(white.id), requireNotNull(black.id)))
        }

        // Assert
        assertEquals(0, gameRepository.count())
        assertEquals(1500, playerRepository.findByIdOrNull(requireNotNull(white.id))?.rapidElo)
        assertEquals(1500, playerRepository.findByIdOrNull(requireNotNull(black.id))?.rapidElo)
    }

    @Test
    fun `a successful save commits the game and both ratings`() {
        // Act
        val game = gameService.create(aGameRequest(requireNotNull(white.id), requireNotNull(black.id)))

        // Assert
        assertEquals(1, gameRepository.count())
        assertEquals(1500 + game.whitePlayerRatingChange, playerRepository.findByIdOrNull(requireNotNull(white.id))?.rapidElo)
        assertEquals(1500 + game.blackPlayerRatingChange, playerRepository.findByIdOrNull(requireNotNull(black.id))?.rapidElo)
    }
}
