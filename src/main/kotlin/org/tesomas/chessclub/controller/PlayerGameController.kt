package org.tesomas.chessclub.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.tesomas.chessclub.model.PageResponse
import org.tesomas.chessclub.model.PlayerGameFilter
import org.tesomas.chessclub.model.PlayerGameSort
import org.tesomas.chessclub.model.PlayerGameView
import org.tesomas.chessclub.model.SortDirection
import org.tesomas.chessclub.service.PlayerGameService
import java.time.LocalDate

@RestController
@RequestMapping("/api/players/{id}/games")
@Tag(name = "Players", description = "Manage chess club members and their ELO ratings")
class PlayerGameController(private val playerGameService: PlayerGameService) {

    @GetMapping
    @Operation(
        summary = "List a player's games",
        description = "Games the player took part in, from the player's perspective (colour, outcome, own and " +
            "opponent rating at game time). Filters are optional and combine with AND; the date range is inclusive."
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "One page of the player's games"),
        ApiResponse(
            responseCode = "400", description = "Invalid query parameter",
            content = [Content(schema = Schema(implementation = ProblemDetail::class))]
        ),
        ApiResponse(
            responseCode = "404", description = "Player not found",
            content = [Content(schema = Schema(implementation = ProblemDetail::class))]
        )
    ])
    fun getPlayerGames(
        @Parameter(description = "MongoDB ObjectId of the player", required = true, example = "64a1b2c3d4e5f6a7b8c9d0e1")
        @PathVariable id: String,
        @Parameter(
            description = "Case-insensitive substring of the opponent's name (taken literally, trimmed; blank = no " +
                "filter; at most ${PlayerGameFilter.MAX_OPPONENT_NAME_LENGTH} characters)",
            example = "brooks"
        )
        @RequestParam(required = false) opponentName: String?,
        @Parameter(description = "Earliest game date, inclusive (yyyy-MM-dd)", example = "2026-01-01")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @Parameter(description = "Latest game date, inclusive (yyyy-MM-dd)", example = "2026-12-31")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @Parameter(description = "0-based page index")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size, ${PlayerGameFilter.MIN_SIZE}-${PlayerGameFilter.MAX_SIZE}")
        @RequestParam(defaultValue = "${PlayerGameFilter.DEFAULT_SIZE}") size: Int,
        @Parameter(description = "Sort key", schema = Schema(allowableValues = ["date", "opponentName"]))
        @RequestParam(defaultValue = "date") sort: String,
        @Parameter(description = "Sort direction; ties are broken by game id", schema = Schema(allowableValues = ["asc", "desc"]))
        @RequestParam(defaultValue = "desc") direction: String
    ): PageResponse<PlayerGameView> {
        val filter = PlayerGameFilter(
            opponentName = opponentName?.trim()?.takeIf { it.isNotEmpty() },
            from = from,
            to = to,
            page = page,
            size = size,
            sort = PlayerGameSort.fromParam(sort),
            direction = SortDirection.fromParam(direction)
        )
        return playerGameService.findGames(id, filter)
    }
}
