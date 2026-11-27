package org.tesomas.chessclub.controller

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.tesomas.chessclub.TestFixtures.aGame
import org.tesomas.chessclub.TestFixtures.aGameRequest
import org.tesomas.chessclub.TestFixtures.aPlayer
import org.tesomas.chessclub.TestcontainersConfiguration
import org.tesomas.chessclub.model.Game
import org.tesomas.chessclub.model.GameType
import org.tesomas.chessclub.model.Player
import org.tesomas.chessclub.repository.GameRepository
import org.tesomas.chessclub.repository.PlayerRepository
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration::class)
class GameControllerTest {

    @Autowired lateinit var restTemplate: TestRestTemplate
    @Autowired lateinit var gameRepository: GameRepository
    @Autowired lateinit var playerRepository: PlayerRepository

    private lateinit var white: Player
    private lateinit var black: Player

    @BeforeEach
    fun setUp() {
        gameRepository.deleteAll()
        playerRepository.deleteAll()
        white = playerRepository.save(aPlayer(firstName = "Magnus", lastName = "Carlsen"))
        black = playerRepository.save(aPlayer(firstName = "Hikaru", lastName = "Nakamura"))
    }

    private fun testGame() = aGame(white, black)

    private fun testRequest() = aGameRequest(
        requireNotNull(white.id) { "white player must be persisted" },
        requireNotNull(black.id) { "black player must be persisted" }
    )

    @Test
    fun `GET games returns empty list`() {
        val response = restTemplate.getForEntity("/api/games", Array<Game>::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(0, response.body?.size)
    }

    @Test
    fun `POST game creates and returns 201`() {
        val response = restTemplate.postForEntity("/api/games", testRequest(), Game::class.java)
        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNotNull(response.body?.id)
        assertEquals(GameType.RAPID, response.body?.gameType)
    }

    @Test
    fun `POST game snapshots player names from player records`() {
        val response = restTemplate.postForEntity("/api/games", testRequest(), Game::class.java)
        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertEquals("Magnus Carlsen", response.body?.whitePlayerName)
        assertEquals("Hikaru Nakamura", response.body?.blackPlayerName)
    }

    @Test
    fun `POST game with unknown player returns 404`() {
        val response = restTemplate.postForEntity(
            "/api/games",
            testRequest().copy(whitePlayerId = "nonexistent"),
            String::class.java
        )
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(0, gameRepository.count())
    }

    @Test
    fun `GET game by id returns game`() {
        val saved = gameRepository.save(testGame())
        val response = restTemplate.getForEntity("/api/games/${saved.id}", Game::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("10+0", response.body?.time)
    }

    @Test
    fun `GET game by unknown id returns 404`() {
        val response = restTemplate.getForEntity("/api/games/nonexistent", Game::class.java)
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `PUT game updates and returns game`() {
        val saved = gameRepository.save(testGame())
        val updated = saved.copy(pgn = "1. d4 d5 2. c4")
        val response = restTemplate.exchange(
            "/api/games/${saved.id}",
            HttpMethod.PUT,
            HttpEntity(updated),
            Game::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("1. d4 d5 2. c4", response.body?.pgn)
    }

    @Test
    fun `PUT game with unknown id returns 404`() {
        val response = restTemplate.exchange(
            "/api/games/nonexistent",
            HttpMethod.PUT,
            HttpEntity(testGame()),
            Game::class.java
        )
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `PUT game with unknown player returns 404`() {
        val saved = gameRepository.save(testGame())
        val response = restTemplate.exchange(
            "/api/games/${saved.id}",
            HttpMethod.PUT,
            HttpEntity(saved.copy(blackPlayerId = "nonexistent")),
            String::class.java
        )
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `DELETE game removes and returns 204`() {
        val saved = gameRepository.save(testGame())
        val response = restTemplate.exchange(
            "/api/games/${saved.id}",
            HttpMethod.DELETE,
            null,
            Void::class.java
        )
        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        assertEquals(0, gameRepository.count())
    }

    @Test
    fun `DELETE game with unknown id returns 404`() {
        val response = restTemplate.exchange(
            "/api/games/nonexistent",
            HttpMethod.DELETE,
            null,
            Void::class.java
        )
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    // --- PGN size limit ----------------------------------------------------------------------

    @Test
    fun `POST game with a pgn of exactly 65536 characters is stored intact`() {
        // Arrange
        val pgn = "x".repeat(65_536)

        // Act
        val response = restTemplate.postForEntity("/api/games", testRequest().copy(pgn = pgn), Game::class.java)

        // Assert
        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertEquals(pgn, gameRepository.findAll().single().pgn)
    }

    @Test
    fun `POST game with a pgn of 65537 characters returns 400 ProblemDetail and stores nothing`() {
        // Act
        val response = restTemplate.postForEntity(
            "/api/games", testRequest().copy(pgn = "x".repeat(65_537)), String::class.java
        )

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertTrue(response.headers.contentType?.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) == true)
        assertTrue(response.body?.contains("pgn must be at most 65536 characters") == true, response.body)
        assertEquals(0, gameRepository.count())
    }

    @Test
    fun `POST game without a pgn is still accepted`() {
        val response = restTemplate.postForEntity("/api/games", testRequest().copy(pgn = null), Game::class.java)
        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNull(response.body?.pgn)
    }

    @Test
    fun `PUT game with a pgn of 65537 characters returns 400 and leaves the game unchanged`() {
        // Arrange
        val saved = gameRepository.save(testGame())

        // Act
        val response = restTemplate.exchange(
            "/api/games/${saved.id}", HttpMethod.PUT, HttpEntity(saved.copy(pgn = "x".repeat(65_537))), String::class.java
        )

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(saved.pgn, gameRepository.findAll().single().pgn)
    }

    @Test
    fun `PUT game with a pgn of exactly 65536 characters succeeds`() {
        // Arrange
        val saved = gameRepository.save(testGame())
        val pgn = "y".repeat(65_536)

        // Act
        val response = restTemplate.exchange(
            "/api/games/${saved.id}", HttpMethod.PUT, HttpEntity(saved.copy(pgn = pgn)), Game::class.java
        )

        // Assert
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(pgn, gameRepository.findAll().single().pgn)
    }
}
