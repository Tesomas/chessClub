package org.tesomas.chessclub.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.tesomas.chessclub.model.Game
import org.tesomas.chessclub.service.GameService

@RestController
@RequestMapping("/api/games")
@Tag(name = "Games", description = "Record and retrieve chess games played between club members")
class GameController(private val gameService: GameService) {

    @GetMapping
    @Operation(summary = "List all games", description = "Returns every recorded game in insertion order.")
    @ApiResponse(
        responseCode = "200", description = "Success",
        content = [Content(array = ArraySchema(schema = Schema(implementation = Game::class)))]
    )
    fun getAll(): List<Game> = gameService.findAll()

    @GetMapping("/{id}")
    @Operation(summary = "Get a game by ID")
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200", description = "Game found",
            content = [Content(schema = Schema(implementation = Game::class))]
        ),
        ApiResponse(responseCode = "404", description = "Game not found", content = [Content()])
    ])
    fun getById(
        @Parameter(description = "MongoDB ObjectId of the game", required = true, example = "64a1b2c3d4e5f6a7b8c9d0e1")
        @PathVariable id: String
    ): ResponseEntity<Game> =
        gameService.findById(id)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Record a new game",
        description = "Creates a game record. `whitePlayerId` and `blackPlayerId` must reference existing players — the service validates both before saving. Player display names are denormalized from current player records at creation time."
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "201", description = "Game recorded",
            content = [Content(schema = Schema(implementation = Game::class))]
        ),
        ApiResponse(responseCode = "400", description = "Invalid request body", content = [Content()]),
        ApiResponse(responseCode = "404", description = "One or both referenced players do not exist", content = [Content()])
    ])
    fun create(@RequestBody game: Game): Game = gameService.create(game)

    @PutMapping("/{id}")
    @Operation(
        summary = "Update a game record",
        description = "Replaces all fields of the game. Player IDs are re-validated on update."
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200", description = "Game updated",
            content = [Content(schema = Schema(implementation = Game::class))]
        ),
        ApiResponse(responseCode = "404", description = "Game or referenced player not found", content = [Content()])
    ])
    fun update(
        @Parameter(description = "MongoDB ObjectId of the game to update", required = true, example = "64a1b2c3d4e5f6a7b8c9d0e1")
        @PathVariable id: String,
        @RequestBody game: Game
    ): ResponseEntity<Game> =
        gameService.update(id, game)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete a game record",
        description = "Permanently removes the game. Player records are unaffected."
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "204", description = "Game deleted", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Game not found", content = [Content()])
    ])
    fun delete(
        @Parameter(description = "MongoDB ObjectId of the game to delete", required = true, example = "64a1b2c3d4e5f6a7b8c9d0e1")
        @PathVariable id: String
    ): ResponseEntity<Void> =
        if (gameService.delete(id)) ResponseEntity.noContent().build()
        else ResponseEntity.notFound().build()
}
