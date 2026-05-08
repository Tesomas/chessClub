package org.tesomas.chessclub.controller

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.tesomas.chessclub.model.Player
import org.tesomas.chessclub.repository.PlayerRepository
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlayerControllerTest {

    @Autowired lateinit var restTemplate: TestRestTemplate
    @Autowired lateinit var playerRepository: PlayerRepository

    @BeforeEach
    fun setUp() {
        playerRepository.deleteAll()
    }

    @Test
    fun `GET players returns empty list`() {
        val response = restTemplate.getForEntity("/api/players", Array<Player>::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(0, response.body?.size)
    }

    @Test
    fun `POST player creates and returns 201`() {
        val player = Player(firstName = "Magnus", lastName = "Carlsen", rapidElo = 2800)
        val response = restTemplate.postForEntity("/api/players", player, Player::class.java)
        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNotNull(response.body?.id)
        assertEquals("Magnus", response.body?.firstName)
    }

    @Test
    fun `GET player by id returns player`() {
        val saved = playerRepository.save(Player(firstName = "Hikaru", lastName = "Nakamura"))
        val response = restTemplate.getForEntity("/api/players/${saved.id}", Player::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("Hikaru", response.body?.firstName)
    }

    @Test
    fun `GET player by unknown id returns 404`() {
        val response = restTemplate.getForEntity("/api/players/nonexistent", Player::class.java)
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `PUT player updates and returns player`() {
        val saved = playerRepository.save(Player(firstName = "Ian", lastName = "Nepomniachtchi"))
        val updated = saved.copy(rapidElo = 2750)
        val response = restTemplate.exchange(
            "/api/players/${saved.id}",
            HttpMethod.PUT,
            HttpEntity(updated),
            Player::class.java
        )
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(2750, response.body?.rapidElo)
    }

    @Test
    fun `PUT player with unknown id returns 404`() {
        val player = Player(firstName = "X", lastName = "Y")
        val response = restTemplate.exchange(
            "/api/players/nonexistent",
            HttpMethod.PUT,
            HttpEntity(player),
            Player::class.java
        )
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `DELETE player removes and returns 204`() {
        val saved = playerRepository.save(Player(firstName = "Fabiano", lastName = "Caruana"))
        val response = restTemplate.exchange(
            "/api/players/${saved.id}",
            HttpMethod.DELETE,
            null,
            Void::class.java
        )
        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        assertEquals(0, playerRepository.count())
    }

    @Test
    fun `DELETE player with unknown id returns 404`() {
        val response = restTemplate.exchange(
            "/api/players/nonexistent",
            HttpMethod.DELETE,
            null,
            Void::class.java
        )
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }
}
