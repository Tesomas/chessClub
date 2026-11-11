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
import org.tesomas.chessclub.model.Player
import org.tesomas.chessclub.service.PlayerService

@RestController
@RequestMapping("/api/players")
class PlayerController(private val playerService: PlayerService) {

    @GetMapping
    fun getAll(): List<Player> = playerService.findAll()

    @GetMapping("/{id}")
    fun getById(@PathVariable id: String): ResponseEntity<Player> =
        playerService.findById(id)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody player: Player): Player = playerService.create(player)

    @PutMapping("/{id}")
    @Operation(
        summary = "Update an existing player",
        description = "Replaces all fields of the player. ELO values omitted from the body will revert to the default (1200)."
    )
    @ApiResponses(value = [
        ApiResponse(
            responseCode = "200", description = "Player updated",
            content = [Content(schema = Schema(implementation = Player::class))]
        ),
        ApiResponse(responseCode = "400", description = "Invalid request body", content = [Content()]),
        ApiResponse(responseCode = "404", description = "Player not found", content = [Content()])
    ])
    fun update(
        @Parameter(description = "MongoDB ObjectId of the player to update", required = true, example = "64a1b2c3d4e5f6a7b8c9d0e1")
        @PathVariable id: String,
        @RequestBody player: Player
    ): ResponseEntity<Player> =
        playerService.update(id, player)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String): ResponseEntity<Void> =
        if (playerService.delete(id)) ResponseEntity.noContent().build()
        else ResponseEntity.notFound().build()
}