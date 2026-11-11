package org.tesomas.chessclub.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.tesomas.chessclub.model.Game
import org.tesomas.chessclub.service.GameService

@RestController
@RequestMapping("/api/games")
class GameController(private val gameService: GameService) {

    @GetMapping
    fun getAll(): List<Game> = gameService.findAll()

    @GetMapping("/{id}")
    fun getById(@PathVariable id: String): ResponseEntity<Game> =
        gameService.findById(id)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody game: Game): Game = gameService.create(game)

    @PutMapping("/{id}")
    @Operation(
        summary = "Update a game record",
        description = "Replaces all fields of the game. Player IDs are re-validated on update."
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200", description = "Game updated",
            content = [@Content(schema = Schema(implementation = Game::class))]
        ),
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
    fun delete(@PathVariable id: String): ResponseEntity<Void> =
        if (gameService.delete(id)) ResponseEntity.noContent().build()
        else ResponseEntity.notFound().build()
}