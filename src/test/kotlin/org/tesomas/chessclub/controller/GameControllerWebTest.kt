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
import org.tesomas.chessclub.TestFixtures.aGame
import org.tesomas.chessclub.model.Game
import org.tesomas.chessclub.model.GameCreateRequest
import org.tesomas.chessclub.service.GameService
import org.tesomas.chessclub.service.PlayerNotFoundException

@WebMvcTest(GameController::class)
class GameControllerWebTest {

    @Autowired lateinit var mockMvc: MockMvc

    @MockkBean lateinit var gameService: GameService

    private val gameJson = """
        {"whitePlayerId":"w1","blackPlayerId":"b1","gameType":"BLITZ","result":"DRAW","time":"3+2","date":"2024-11-25","pgn":"1. e4 e5 *"}
    """.trimIndent()

    private fun created(request: GameCreateRequest, id: String) = Game(
        id = id,
        whitePlayerId = request.whitePlayerId,
        blackPlayerId = request.blackPlayerId,
        whitePlayerName = "Magnus Carlsen",
        blackPlayerName = "Hikaru Nakamura",
        whitePlayerRating = 2850,
        blackPlayerRating = 2800,
        gameType = request.gameType,
        result = request.result,
        time = request.time,
        date = request.date,
        pgn = request.pgn
    )

    @Test
    fun `GET games returns 200 with a JSON array`() {
        // Arrange
        every { gameService.findAll() } returns listOf(aGame("w1", "b1", id = "g1"))

        // Act + Assert
        mockMvc.get("/api/games").andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].id") { value("g1") }
        }
    }

    @Test
    fun `GET game by id serializes date as ISO string and gameType as enum name`() {
        // Arrange
        every { gameService.findById("g1") } returns aGame("w1", "b1", id = "g1")

        // Act + Assert
        mockMvc.get("/api/games/g1").andExpect {
            status { isOk() }
            jsonPath("$.id") { value("g1") }
            jsonPath("$.whitePlayerId") { value("w1") }
            jsonPath("$.blackPlayerId") { value("b1") }
            jsonPath("$.gameType") { value("RAPID") }
            jsonPath("$.date") { value("2024-01-15") }
            jsonPath("$.time") { value("10+0") }
            jsonPath("$.pgn") { value("1. e4 e5 2. Nf3 Nc6") }
        }
    }

    @Test
    fun `GET game by unknown id returns 404`() {
        // Arrange
        every { gameService.findById("missing") } returns null

        // Act + Assert
        mockMvc.get("/api/games/missing").andExpect { status { isNotFound() } }
    }

    @Test
    fun `POST game returns 201 with denormalized player names`() {
        // Arrange
        every { gameService.create(any()) } answers { created(firstArg(), "new-id") }

        // Act + Assert
        mockMvc.post("/api/games") {
            contentType = MediaType.APPLICATION_JSON
            content = gameJson
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { value("new-id") }
            jsonPath("$.whitePlayerName") { value("Magnus Carlsen") }
            jsonPath("$.blackPlayerName") { value("Hikaru Nakamura") }
            jsonPath("$.gameType") { value("BLITZ") }
            jsonPath("$.date") { value("2024-11-25") }
        }
    }

    @Test
    fun `POST game with unknown player returns 404 ProblemDetail`() {
        // Arrange
        every { gameService.create(any()) } throws PlayerNotFoundException("w1")

        // Act + Assert
        mockMvc.post("/api/games") {
            contentType = MediaType.APPLICATION_JSON
            content = gameJson
        }.andExpect {
            status { isNotFound() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.status") { value(404) }
            jsonPath("$.detail") { value("Player w1 not found") }
        }
    }

    @Test
    fun `POST game with malformed JSON returns 400 and never reaches the service`() {
        // Act + Assert
        mockMvc.post("/api/games") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"whitePlayerId": """
        }.andExpect {
            status { isBadRequest() }
        }
        verify(exactly = 0) { gameService.create(any()) }
    }

    @Test
    fun `POST game with an invalid gameType returns 400`() {
        // Act + Assert
        mockMvc.post("/api/games") {
            contentType = MediaType.APPLICATION_JSON
            content = gameJson.replace("BLITZ", "HYPERBULLET")
        }.andExpect {
            status { isBadRequest() }
        }
        verify(exactly = 0) { gameService.create(any()) }
    }

    @Test
    fun `PUT game returns 200 with the updated game`() {
        // Arrange
        every { gameService.update("g1", any()) } answers { named(secondArg(), "g1") }

        // Act + Assert
        mockMvc.put("/api/games/g1") {
            contentType = MediaType.APPLICATION_JSON
            content = gameJson
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value("g1") }
            jsonPath("$.pgn") { value("1. e4 e5 *") }
        }
    }

    @Test
    fun `PUT game with unknown id returns 404`() {
        // Arrange
        every { gameService.update("missing", any()) } returns null

        // Act + Assert
        mockMvc.put("/api/games/missing") {
            contentType = MediaType.APPLICATION_JSON
            content = gameJson
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `PUT game with unknown player returns 404 ProblemDetail`() {
        // Arrange
        every { gameService.update("g1", any()) } throws PlayerNotFoundException("b1")

        // Act + Assert
        mockMvc.put("/api/games/g1") {
            contentType = MediaType.APPLICATION_JSON
            content = gameJson
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.detail") { value("Player b1 not found") }
        }
    }

    @Test
    fun `DELETE game returns 204 when deleted`() {
        // Arrange
        every { gameService.delete("g1") } returns true

        // Act + Assert
        mockMvc.delete("/api/games/g1").andExpect { status { isNoContent() } }
    }

    @Test
    fun `DELETE game with unknown id returns 404`() {
        // Arrange
        every { gameService.delete("missing") } returns false

        // Act + Assert
        mockMvc.delete("/api/games/missing").andExpect { status { isNotFound() } }
    }

    @Test
    fun `CORS preflight for PUT from the Angular dev server is allowed`() {
        // Act + Assert
        mockMvc.options("/api/games/g1") {
            header(HttpHeaders.ORIGIN, "http://localhost:4200")
            header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT")
        }.andExpect {
            status { isOk() }
            header { string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200") }
        }
    }
}
