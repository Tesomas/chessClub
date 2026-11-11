package org.tesomas.chessclub.repository

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.repository.findByIdOrNull
import org.tesomas.chessclub.TestFixtures.aGame
import org.tesomas.chessclub.TestcontainersConfiguration
import org.tesomas.chessclub.model.Game
import org.tesomas.chessclub.model.GameType
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@DataMongoTest
@Import(TestcontainersConfiguration::class)
class GameRepositoryTest {

    @Autowired lateinit var gameRepository: GameRepository
    @Autowired lateinit var mongoTemplate: MongoTemplate

    @BeforeEach
    fun setUp() {
        gameRepository.deleteAll()
    }

    @Test
    fun `save and findById round-trip LocalDate and GameType`() {
        // Arrange
        val game = aGame("w1", "b1", gameType = GameType.CLASSICAL, date = LocalDate.of(2023, 12, 31))

        // Act
        val saved = gameRepository.save(game)
        val found = gameRepository.findByIdOrNull(requireNotNull(saved.id))

        // Assert
        assertNotNull(found)
        assertEquals(saved, found)
        assertEquals(GameType.CLASSICAL, found.gameType)
        assertEquals(LocalDate.of(2023, 12, 31), found.date)
    }

    @Test
    fun `games are stored in the games collection with gameType as its enum name`() {
        // Act
        gameRepository.save(aGame("w1", "b1", gameType = GameType.BULLET))

        // Assert
        assertEquals("games", mongoTemplate.getCollectionName(Game::class.java))
        val raw = assertNotNull(mongoTemplate.getCollection("games").find().first())
        assertEquals("BULLET", raw.getString("gameType"))
    }

    @Test
    fun `findByWhitePlayerId and findByBlackPlayerId filter by colour`() {
        // Arrange
        gameRepository.saveAll(
            listOf(
                aGame("alice", "bob"),
                aGame("bob", "alice"),
                aGame("alice", "carol")
            )
        )

        // Act
        val aliceAsWhite = gameRepository.findByWhitePlayerId("alice")
        val aliceAsBlack = gameRepository.findByBlackPlayerId("alice")

        // Assert
        assertEquals(2, aliceAsWhite.size)
        assertEquals(setOf("bob", "carol"), aliceAsWhite.map { it.blackPlayerId }.toSet())
        assertEquals(1, aliceAsBlack.size)
        assertEquals("bob", aliceAsBlack.single().whitePlayerId)
    }
}
