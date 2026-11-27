package org.tesomas.chessclub.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.tesomas.chessclub.model.GameType
import org.tesomas.chessclub.model.PageResponse
import org.tesomas.chessclub.model.PlayerColor
import org.tesomas.chessclub.model.PlayerGameFilter
import org.tesomas.chessclub.model.PlayerGameSort
import org.tesomas.chessclub.model.PlayerGameView
import org.tesomas.chessclub.model.PlayerOutcome
import org.tesomas.chessclub.model.SortDirection
import org.tesomas.chessclub.service.PlayerGameService
import org.tesomas.chessclub.service.PlayerNotFoundException
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

@WebMvcTest(PlayerGameController::class)
class PlayerGameControllerWebTest {

    @Autowired lateinit var mockMvc: MockMvc

    @MockkBean lateinit var playerGameService: PlayerGameService

    private val filter = slot<PlayerGameFilter>()

    private val view = PlayerGameView(
        gameId = "g1", date = LocalDate.of(2026, 10, 1), gameType = GameType.BLITZ, time = "3+2",
        color = PlayerColor.WHITE, outcome = PlayerOutcome.WIN, playerRating = 1500, playerRatingChange = 9,
        opponentId = "p2", opponentName = "Bob Brooks", opponentRating = 1480, opponentRatingChange = -9
    )

    @BeforeEach
    fun stubService() {
        every { playerGameService.findGames("p1", capture(filter)) } answers {
            PageResponse.of(listOf(view), filter.captured.page, filter.captured.size, 140)
        }
    }

    @Test
    fun `returns 200 with the agreed JSON shape`() {
        mockMvc.get("/api/players/p1/games").andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.content.length()") { value(1) }
            jsonPath("$.content[0].gameId") { value("g1") }
            jsonPath("$.content[0].date") { value("2026-10-01") }
            jsonPath("$.content[0].gameType") { value("BLITZ") }
            jsonPath("$.content[0].time") { value("3+2") }
            jsonPath("$.content[0].color") { value("WHITE") }
            jsonPath("$.content[0].outcome") { value("WIN") }
            jsonPath("$.content[0].playerRating") { value(1500) }
            jsonPath("$.content[0].playerRatingChange") { value(9) }
            jsonPath("$.content[0].opponentId") { value("p2") }
            jsonPath("$.content[0].opponentName") { value("Bob Brooks") }
            jsonPath("$.content[0].opponentRating") { value(1480) }
            jsonPath("$.content[0].opponentRatingChange") { value(-9) }
            jsonPath("$.content[0].length()") { value(12) }
            jsonPath("$.page") { value(0) }
            jsonPath("$.size") { value(25) }
            jsonPath("$.totalElements") { value(140) }
            jsonPath("$.totalPages") { value(6) }
            jsonPath("$.length()") { value(5) }
        }
    }

    @Test
    fun `no query params yields the default filter`() {
        mockMvc.get("/api/players/p1/games").andExpect { status { isOk() } }
        assertEquals(PlayerGameFilter(), filter.captured)
    }

    @Test
    fun `binds every query parameter`() {
        // Act
        mockMvc.get("/api/players/p1/games") {
            param("opponentName", "bob")
            param("from", "2026-01-01")
            param("to", "2026-06-30")
            param("page", "2")
            param("size", "10")
            param("sort", "opponentName")
            param("direction", "asc")
        }.andExpect { status { isOk() } }

        // Assert
        assertEquals(
            PlayerGameFilter(
                opponentName = "bob", from = LocalDate.of(2026, 1, 1), to = LocalDate.of(2026, 6, 30),
                page = 2, size = 10, sort = PlayerGameSort.OPPONENT_NAME, direction = SortDirection.ASC
            ),
            filter.captured
        )
    }

    @Test
    fun `sort and direction values are case-insensitive`() {
        mockMvc.get("/api/players/p1/games?sort=OPPONENTNAME&direction=DESC").andExpect { status { isOk() } }
        assertEquals(PlayerGameSort.OPPONENT_NAME, filter.captured.sort)
        assertEquals(SortDirection.DESC, filter.captured.direction)
    }

    @Test
    fun `opponentName is trimmed`() {
        mockMvc.get("/api/players/p1/games") { param("opponentName", "  Bob B  ") }.andExpect { status { isOk() } }
        assertEquals("Bob B", filter.captured.opponentName)
    }

    @Test
    fun `a blank opponentName is treated as no filter`() {
        mockMvc.get("/api/players/p1/games") { param("opponentName", "   ") }.andExpect { status { isOk() } }
        assertNull(filter.captured.opponentName)
    }

    @Test
    fun `opponentName with regex metacharacters is passed through verbatim`() {
        mockMvc.get("/api/players/p1/games") { param("opponentName", "a.b(c)*") }.andExpect { status { isOk() } }
        assertEquals("a.b(c)*", filter.captured.opponentName)
    }

    @Test
    fun `opponentName of 100 characters after trimming is accepted`() {
        mockMvc.get("/api/players/p1/games") { param("opponentName", " " + "x".repeat(100) + " ") }
            .andExpect { status { isOk() } }
        assertEquals(100, filter.captured.opponentName?.length)
    }

    @Test
    fun `parameters that are no longer supported are ignored`() {
        mockMvc.get("/api/players/p1/games?type=BLITZ&color=WHITE&result=WIN&opponentId=x&minPlayerElo=1")
            .andExpect { status { isOk() } }
        assertEquals(PlayerGameFilter(), filter.captured)
    }

    @Test
    fun `unknown player returns 404 ProblemDetail`() {
        // Arrange
        every { playerGameService.findGames("missing", any()) } throws PlayerNotFoundException("missing")

        // Act + Assert
        mockMvc.get("/api/players/missing/games").andExpect {
            status { isNotFound() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.status") { value(404) }
            jsonPath("$.detail") { value("Player missing not found") }
        }
    }

    @ParameterizedTest(name = "{0} -> 400")
    @ValueSource(
        strings = [
            // type mismatches raised by Spring's binder
            "from=2026-13-01",
            "to=yesterday",
            "from=01/02/2026",
            "page=first",
            "page=1.5",
            "size=ten",
            "size=99999999999",
            // validation rules of the filter
            "sort=rating",
            "sort=playerElo",
            "sort=opponentElo",
            "direction=up",
            "from=2026-02-02&to=2026-02-01",
            "page=-1",
            "size=0",
            "size=101"
        ]
    )
    fun `invalid query parameters return 400 ProblemDetail and never reach the service`(query: String) {
        mockMvc.get("/api/players/p1/games?$query").andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.status") { value(400) }
            jsonPath("$.detail") { isNotEmpty() }
        }
        verify(exactly = 0) { playerGameService.findGames(any(), any()) }
    }

    @Test
    fun `opponentName longer than 100 characters returns 400 ProblemDetail`() {
        mockMvc.get("/api/players/p1/games") { param("opponentName", "x".repeat(101)) }.andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.detail") { value("Invalid query parameters: opponentName must be at most 100 characters") }
        }
        verify(exactly = 0) { playerGameService.findGames(any(), any()) }
    }

    @Test
    fun `an extreme to date returns 400 ProblemDetail instead of a 500`() {
        // "+999999999-12-31" parses to LocalDate.MAX; a raw "+" in a URL would decode to a space, so set it directly
        mockMvc.get("/api/players/p1/games") { param("to", "+999999999-12-31") }.andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.detail") { value("Invalid query parameters: to must be between 1900-01-01 and 9999-12-31") }
        }
        verify(exactly = 0) { playerGameService.findGames(any(), any()) }
    }

    @Test
    fun `an extreme from date returns 400 ProblemDetail`() {
        mockMvc.get("/api/players/p1/games?from=1000-01-01").andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { value("Invalid query parameters: from must be between 1900-01-01 and 9999-12-31") }
        }
    }

    @ParameterizedTest(name = "opponentName with U+{0} -> 400")
    @ValueSource(strings = ["000A", "000D", "0009", "0000", "001B"])
    fun `opponentName with an embedded control character returns 400 ProblemDetail`(codePoint: String) {
        val name = "bob" + codePoint.toInt(16).toChar() + "fake log line"
        mockMvc.get("/api/players/p1/games") { param("opponentName", name) }.andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.detail") { value("Invalid query parameters: opponentName must not contain control characters") }
        }
        verify(exactly = 0) { playerGameService.findGames(any(), any()) }
    }

    @Test
    fun `filter validation message names the broken rules`() {
        mockMvc.get("/api/players/p1/games?size=101&page=-1").andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") {
                value("Invalid query parameters: page must be >= 0; size must be between 1 and 100")
            }
        }
    }
}
