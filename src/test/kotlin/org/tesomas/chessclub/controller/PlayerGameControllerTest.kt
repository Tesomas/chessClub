package org.tesomas.chessclub.controller

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.tesomas.chessclub.TestFixtures.aGame
import org.tesomas.chessclub.TestFixtures.aPlayer
import org.tesomas.chessclub.TestcontainersConfiguration
import org.tesomas.chessclub.model.GameResult
import org.tesomas.chessclub.model.GameType
import org.tesomas.chessclub.model.Player
import org.tesomas.chessclub.repository.GameRepository
import org.tesomas.chessclub.repository.PlayerRepository
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration::class)
class PlayerGameControllerTest {

    @Autowired lateinit var restTemplate: TestRestTemplate
    @Autowired lateinit var gameRepository: GameRepository
    @Autowired lateinit var playerRepository: PlayerRepository

    private val objectMapper = ObjectMapper()

    private lateinit var alice: Player
    private lateinit var bob: Player
    private lateinit var carla: Player

    private val Player.pid get() = requireNotNull(id) { "player must be persisted" }

    @BeforeEach
    fun setUp() {
        gameRepository.deleteAll()
        playerRepository.deleteAll()
        alice = playerRepository.save(aPlayer(firstName = "Alice", lastName = "Anders"))
        bob = playerRepository.save(aPlayer(firstName = "Bob", lastName = "Brooks"))
        carla = playerRepository.save(aPlayer(firstName = "Carla", lastName = "Castillo"))
        gameRepository.saveAll(
            listOf(
                game(alice, bob, GameResult.WHITE_WINS, GameType.BLITZ, "2026-10-01", 1500, 1480),
                game(bob, alice, GameResult.WHITE_WINS, GameType.BLITZ, "2026-10-02", 1490, 1510),
                game(carla, alice, GameResult.BLACK_WINS, GameType.RAPID, "2026-10-03", 1600, 1520),
                game(bob, carla, GameResult.DRAW, GameType.BLITZ, "2026-10-04", 1500, 1600)
            )
        )
    }

    private fun game(
        white: Player, black: Player, result: GameResult, type: GameType, date: String, wr: Int, br: Int
    ) = aGame(
        white.pid, black.pid, whitePlayerName = "${white.firstName} ${white.lastName}",
        blackPlayerName = "${black.firstName} ${black.lastName}", gameType = type, result = result,
        date = LocalDate.parse(date), whitePlayerRating = wr, blackPlayerRating = br
    )

    private fun JsonNode.items(): List<JsonNode> = (0 until size()).map { path(it) }

    private fun getJson(url: String): Pair<HttpStatus, JsonNode> {
        val response = restTemplate.getForEntity(url, String::class.java)
        return HttpStatus.valueOf(response.statusCode.value()) to objectMapper.readTree(assertNotNull(response.body))
    }

    @Test
    fun `returns all of the player's games newest first, from the player's perspective`() {
        // Act
        val (status, body) = getJson("/api/players/${alice.pid}/games")

        // Assert
        assertEquals(HttpStatus.OK, status)
        assertEquals(3, body.path("totalElements").asInt())
        assertEquals(1, body.path("totalPages").asInt())
        assertEquals(0, body.path("page").asInt())
        assertEquals(25, body.path("size").asInt())
        val content = body.path("content")
        assertEquals(listOf("2026-10-03", "2026-10-02", "2026-10-01"), content.items().map { it.path("date").asString() })
        assertEquals(listOf("BLACK", "BLACK", "WHITE"), content.items().map { it.path("color").asString() })
        assertEquals(listOf("WIN", "LOSS", "WIN"), content.items().map { it.path("outcome").asString() })
        assertEquals(listOf(1520, 1510, 1500), content.items().map { it.path("playerRating").asInt() })
        assertEquals(listOf("Carla Castillo", "Bob Brooks", "Bob Brooks"), content.items().map { it.path("opponentName").asString() })
        assertEquals(carla.pid, content.path(0).path("opponentId").asString())
    }

    @Test
    fun `opponentName filters case-insensitively on either colour`() {
        // Act
        val (status, body) = getJson("/api/players/${alice.pid}/games?opponentName= brOOks ")

        // Assert
        assertEquals(HttpStatus.OK, status)
        val content = body.path("content").items()
        assertEquals(2, body.path("totalElements").asInt())
        assertEquals(listOf("Bob Brooks", "Bob Brooks"), content.map { it.path("opponentName").asString() })
        assertEquals(listOf("BLACK", "WHITE"), content.map { it.path("color").asString() })
    }

    @Test
    fun `date range and opponent name sort work end to end`() {
        // Act
        val (status, body) = getJson(
            "/api/players/${alice.pid}/games?from=2026-10-02&to=2026-10-03&sort=opponentName&direction=desc"
        )

        // Assert
        assertEquals(HttpStatus.OK, status)
        val content = body.path("content").items()
        assertEquals(listOf("Carla Castillo", "Bob Brooks"), content.map { it.path("opponentName").asString() })
        assertEquals(listOf("2026-10-03", "2026-10-02"), content.map { it.path("date").asString() })
    }

    @Test
    fun `paging parameters are honoured`() {
        val (_, paged) = getJson("/api/players/${alice.pid}/games?size=2&page=1")
        assertEquals(1, paged.path("content").size())
        assertEquals(2, paged.path("totalPages").asInt())
        assertEquals(3, paged.path("totalElements").asInt())
        assertEquals("2026-10-01", paged.path("content").path(0).path("date").asString())
    }

    @Test
    fun `unknown player returns 404 ProblemDetail`() {
        // Act
        val response = restTemplate.getForEntity("/api/players/000000000000000000000000/games", String::class.java)

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertTrue(response.headers.contentType?.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) == true)
        assertEquals(404, objectMapper.readTree(response.body).path("status").asInt())
    }

    @Test
    fun `invalid parameters return 400 ProblemDetail`() {
        listOf("from=nope", "from=2026-02-02&to=2026-01-01", "size=500", "sort=opponentElo", "opponentName=${"x".repeat(101)}").forEach { query ->
            val response = restTemplate.getForEntity("/api/players/${alice.pid}/games?$query", String::class.java)
            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode, query)
            assertTrue(response.headers.contentType?.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) == true, query)
            assertEquals(400, objectMapper.readTree(response.body).path("status").asInt(), query)
        }
    }

    @Test
    fun `birthDate round-trips through the player API`() {
        // Act
        val created = restTemplate.postForEntity(
            "/api/players", aPlayer(firstName = "Dana", lastName = "Dimitrov", birthDate = LocalDate.of(2001, 2, 3)),
            Player::class.java
        )
        val fetched = restTemplate.getForEntity("/api/players/${created.body?.id}", Player::class.java)

        // Assert
        assertEquals(HttpStatus.CREATED, created.statusCode)
        assertEquals(LocalDate.of(2001, 2, 3), fetched.body?.birthDate)
    }
}
