package org.tesomas.chessclub.controller

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.tesomas.chessclub.model.Game
import org.tesomas.chessclub.model.GameType
import org.tesomas.chessclub.model.Player
import org.tesomas.chessclub.repository.GameRepository
import org.tesomas.chessclub.repository.PlayerRepository
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
        white = playerRepository.save(Player(firstName = "Magnus", lastName = "Carlsen"))
        black = playerRepository.save(Player(firstName = "Hikaru", lastName = "Nakamura"))
    }

    private fun testGame() = Game(
        whitePlayerId = white.id!!,
        blackPlayerId = black.id!!,
        whitePlayerName = "Magnus Carlsen",
        blackPlayerName = "Hikaru Nakamura",
        whitePlayerRating = 2850,
        blackPlayerRating = 2800,
        gameType = GameType.RAPID,
        time = "10+0",
        date = LocalDate.of(2024, 1, 15),
        pgn = "1. e4 e5 2. Nf3 Nc6"
    )

    @Test
    fun `GET games returns empty list`() {
        val response = restTemplate.getForEntity("/api/games", Array<Game>::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(0, response.body?.size)
    }

    @Test
    fun `POST game creates and returns 201`() {
        val response = restTemplate.postForEntity("/api/games", testGame(), Game::class.java)
        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNotNull(response.body?.id)
        assertEquals(GameType.RAPID, response.body?.gameType)
    }

    @Test
    fun `POST game snapshots player names from player records`() {
        val response = restTemplate.postForEntity("/api/games", testGame(), Game::class.java)
        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertEquals("Magnus Carlsen", response.body?.whitePlayerName)
        assertEquals("Hikaru Nakamura", response.body?.blackPlayerName)
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
}
