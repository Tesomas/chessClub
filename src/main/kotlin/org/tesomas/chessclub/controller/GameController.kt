package org.tesomas.chessclub.controller

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.tesomas.chessclub.model.Game
import org.tesomas.chessclub.model.GameCreateRequest
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
    fun create(@RequestBody request: GameCreateRequest): Game = gameService.create(request)

    @PutMapping("/{id}")
    fun update(@PathVariable id: String, @RequestBody game: Game): ResponseEntity<Game> =
        gameService.update(id, game)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String): ResponseEntity<Void> =
        if (gameService.delete(id)) ResponseEntity.noContent().build()
        else ResponseEntity.notFound().build()
}
