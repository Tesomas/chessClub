package org.tesomas.chessclub.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.options
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.tesomas.chessclub.TestFixtures.aPlayer
import org.tesomas.chessclub.model.Player
import org.tesomas.chessclub.service.PlayerService

@WebMvcTest(PlayerController::class)
class PlayerControllerWebTest {

    @Autowired lateinit var mockMvc: MockMvc

    @MockkBean lateinit var playerService: PlayerService

    private val playerJson = """
        {"firstName":"Magnus","lastName":"Carlsen","rapidElo":2850,"blitzElo":2830,"bulletElo":2800,"classicalElo":2860}
    """.trimIndent()

    @Test
    fun `GET players returns 200 with a JSON array`() {
        // Arrange
        every { playerService.findAll() } returns listOf(aPlayer(id = "p1"), aPlayer(id = "p2", firstName = "Hikaru"))

        // Act + Assert
        mockMvc.get("/api/players").andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].id") { value("p1") }
            jsonPath("$[1].firstName") { value("Hikaru") }
        }
    }

    @Test
    fun `GET player by id returns 200 with all fields`() {
        // Arrange
        every { playerService.findById("p1") } returns aPlayer(id = "p1")

        // Act + Assert
        mockMvc.get("/api/players/p1").andExpect {
            status { isOk() }
            jsonPath("$.id") { value("p1") }
            jsonPath("$.firstName") { value("Magnus") }
            jsonPath("$.lastName") { value("Carlsen") }
            jsonPath("$.rapidElo") { value(2850) }
            jsonPath("$.blitzElo") { value(2830) }
            jsonPath("$.bulletElo") { value(2800) }
            jsonPath("$.classicalElo") { value(2860) }
        }
    }

    @Test
    fun `GET player by unknown id returns 404`() {
        // Arrange
        every { playerService.findById("missing") } returns null

        // Act + Assert
        mockMvc.get("/api/players/missing").andExpect { status { isNotFound() } }
    }

    @Test
    fun `POST player returns 201 with the created player`() {
        // Arrange
        every { playerService.create(any()) } answers { firstArg<Player>().copy(id = "new-id") }

        // Act + Assert
        mockMvc.post("/api/players") {
            contentType = MediaType.APPLICATION_JSON
            content = playerJson
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { value("new-id") }
            jsonPath("$.firstName") { value("Magnus") }
        }
    }

    @Test
    fun `POST player with omitted ELO fields applies the 1200 defaults`() {
        // Arrange
        every { playerService.create(any()) } answers { firstArg() }

        // Act + Assert
        mockMvc.post("/api/players") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"firstName":"New","lastName":"Member"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.rapidElo") { value(1200) }
            jsonPath("$.classicalElo") { value(1200) }
        }
    }

    @Test
    fun `POST player with malformed JSON returns 400 and never reaches the service`() {
        // Act + Assert
        mockMvc.post("/api/players") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"firstName": "Magnus", """
        }.andExpect {
            status { isBadRequest() }
        }
        verify(exactly = 0) { playerService.create(any()) }
    }

    @Test
    fun `POST player missing a required field returns 400`() {
        // Act + Assert
        mockMvc.post("/api/players") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"firstName":"Magnus"}"""
        }.andExpect {
            status { isBadRequest() }
        }
        verify(exactly = 0) { playerService.create(any()) }
    }

    @Test
    fun `PUT player returns 200 with the updated player`() {
        // Arrange
        every { playerService.update("p1", any()) } answers { secondArg<Player>().copy(id = "p1") }

        // Act + Assert
        mockMvc.put("/api/players/p1") {
            contentType = MediaType.APPLICATION_JSON
            content = playerJson
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value("p1") }
            jsonPath("$.rapidElo") { value(2850) }
        }
    }

    @Test
    fun `PUT player with unknown id returns 404`() {
        // Arrange
        every { playerService.update("missing", any()) } returns null

        // Act + Assert
        mockMvc.put("/api/players/missing") {
            contentType = MediaType.APPLICATION_JSON
            content = playerJson
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `DELETE player returns 204 when deleted`() {
        // Arrange
        every { playerService.delete("p1") } returns true

        // Act + Assert
        mockMvc.delete("/api/players/p1").andExpect { status { isNoContent() } }
    }

    @Test
    fun `DELETE player with unknown id returns 404`() {
        // Arrange
        every { playerService.delete("missing") } returns false

        // Act + Assert
        mockMvc.delete("/api/players/missing").andExpect { status { isNotFound() } }
    }

    @Test
    fun `unexpected service failure returns a generic 500 ProblemDetail`() {
        // Arrange
        every { playerService.findAll() } throws IllegalStateException("internal detail")

        // Act + Assert
        mockMvc.get("/api/players").andExpect {
            status { isInternalServerError() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.status") { value(500) }
            jsonPath("$.detail") { value(ApiExceptionHandler.GENERIC_ERROR_DETAIL) }
        }
    }

    @Test
    fun `CORS preflight from the Angular dev server is allowed`() {
        // Act + Assert
        mockMvc.options("/api/players") {
            header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
            header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
        }.andExpect {
            status { isOk() }
            header { string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN) }
        }
    }

    @Test
    fun `CORS preflight from an unknown origin is rejected`() {
        // Act + Assert
        mockMvc.options("/api/players") {
            header(HttpHeaders.ORIGIN, "http://evil.example.com")
            header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
        }.andExpect {
            status { isForbidden() }
            header { doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN) }
        }
    }

    private companion object {
        const val ALLOWED_ORIGIN = "http://localhost:4200"
    }
}
