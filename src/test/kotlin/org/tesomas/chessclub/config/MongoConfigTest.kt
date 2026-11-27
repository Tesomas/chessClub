package org.tesomas.chessclub.config

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.repository.findByIdOrNull
import org.tesomas.chessclub.TestFixtures.aGame
import org.tesomas.chessclub.TestFixtures.aPlayer
import org.tesomas.chessclub.TestcontainersConfiguration
import org.tesomas.chessclub.model.PlayerGameFilter
import org.tesomas.chessclub.repository.GameRepository
import org.tesomas.chessclub.repository.PlayerGameQueryRepository
import org.tesomas.chessclub.repository.PlayerRepository
import java.time.Instant
import java.time.LocalDate
import java.util.Date
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * With [MongoConfig], a LocalDate is stored as UTC midnight and read back unchanged no matter the
 * JVM's default zone, both west (America/Chicago) and east (Europe/Berlin) of UTC.
 */
@DataMongoTest
@Import(TestcontainersConfiguration::class, MongoConfig::class)
class MongoConfigTest {

    @Autowired lateinit var mongoTemplate: MongoTemplate
    @Autowired lateinit var playerRepository: PlayerRepository
    @Autowired lateinit var gameRepository: GameRepository

    private val originalZone: TimeZone = TimeZone.getDefault()

    @BeforeEach
    fun clean() {
        playerRepository.deleteAll()
        gameRepository.deleteAll()
    }

    @AfterEach
    fun restoreZone() = TimeZone.setDefault(originalZone)

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["America/Chicago", "Europe/Berlin", "Pacific/Kiritimati", "Pacific/Pago_Pago"])
    fun `birthDate is stored as UTC midnight and round-trips in a non-UTC default zone`(zone: String) {
        // Arrange
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        val birthDate = LocalDate.of(1990, 11, 30)

        // Act
        val saved = playerRepository.save(aPlayer(birthDate = birthDate))
        val found = playerRepository.findByIdOrNull(requireNotNull(saved.id))

        // Assert
        assertEquals(birthDate, found?.birthDate)
        val raw = assertNotNull(mongoTemplate.getCollection("Players").find().first())
        assertEquals(Date.from(Instant.parse("1990-11-30T00:00:00Z")), raw.getDate("birthDate"))
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["America/Chicago", "Europe/Berlin", "Pacific/Kiritimati", "Pacific/Pago_Pago"])
    fun `game dates are stored as UTC midnight and date filters stay exact in a non-UTC default zone`(zone: String) {
        // Arrange
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        gameRepository.saveAll(
            listOf("2026-03-09", "2026-03-10", "2026-03-11").map {
                aGame("p", "a", id = it, date = LocalDate.parse(it))
            }
        )
        val repository = PlayerGameQueryRepository(mongoTemplate)
        val day = LocalDate.of(2026, 3, 10)

        // Act
        val page = repository.findPlayerGames("p", PlayerGameFilter(from = day, to = day))

        // Assert
        assertEquals(listOf("2026-03-10"), page.content.map { it.id })
        assertEquals(day, page.content.single().date)
        val raw = assertNotNull(mongoTemplate.getCollection("games").find(org.bson.Document("_id", "2026-03-10")).first())
        assertEquals(Date.from(Instant.parse("2026-03-10T00:00:00Z")), raw.getDate("date"))
    }
}
