package org.tesomas.chessclub.repository

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.repository.findByIdOrNull
import org.tesomas.chessclub.TestFixtures.aPlayer
import org.tesomas.chessclub.TestcontainersConfiguration
import org.tesomas.chessclub.config.MongoConfig
import org.tesomas.chessclub.model.Player
import java.time.Instant
import java.time.LocalDate
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DataMongoTest
@Import(TestcontainersConfiguration::class, MongoConfig::class)
class PlayerRepositoryTest {

    @Autowired lateinit var playerRepository: PlayerRepository
    @Autowired lateinit var mongoTemplate: MongoTemplate

    @BeforeEach
    fun setUp() {
        playerRepository.deleteAll()
    }

    @Test
    fun `save assigns an id and findById round-trips every field`() {
        // Arrange
        val player = aPlayer(firstName = "Alireza", lastName = "Firouzja", rapidElo = 2700, classicalElo = 2760)

        // Act
        val saved = playerRepository.save(player)
        val found = playerRepository.findByIdOrNull(requireNotNull(saved.id))

        // Assert
        assertNotNull(saved.id)
        assertEquals(saved, found)
    }

    @Test
    fun `players are stored in the Players collection`() {
        // Act
        playerRepository.save(aPlayer())

        // Assert
        assertTrue(mongoTemplate.collectionExists("Players"))
        assertEquals(1, mongoTemplate.getCollection("Players").countDocuments())
        assertEquals("Players", mongoTemplate.getCollectionName(Player::class.java))
    }

    @Test
    fun `existsById and deleteById behave as expected`() {
        // Arrange
        val id = requireNotNull(playerRepository.save(aPlayer()).id)

        // Act
        val existedBefore = playerRepository.existsById(id)
        playerRepository.deleteById(id)

        // Assert
        assertTrue(existedBefore)
        assertEquals(false, playerRepository.existsById(id))
    }

    @Test
    fun `birthDate round-trips and is stored as a BSON date at UTC midnight`() {
        // Arrange
        val player = aPlayer(birthDate = LocalDate.of(1990, 11, 30))

        // Act
        val saved = playerRepository.save(player)
        val found = playerRepository.findByIdOrNull(requireNotNull(saved.id))

        // Assert
        assertEquals(LocalDate.of(1990, 11, 30), found?.birthDate)
        val raw = assertNotNull(mongoTemplate.getCollection("Players").find().first())
        assertEquals(Date.from(Instant.parse("1990-11-30T00:00:00Z")), raw.getDate("birthDate"))
    }

    @Test
    fun `a player without birthDate reads back as null`() {
        // Act
        val saved = playerRepository.save(aPlayer(birthDate = null))

        // Assert
        assertNull(playerRepository.findByIdOrNull(requireNotNull(saved.id))?.birthDate)
    }
}
