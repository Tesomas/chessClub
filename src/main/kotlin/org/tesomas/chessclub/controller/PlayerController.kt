package org.tesomas.chessclub.controller

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
    fun update(@PathVariable id: String, @RequestBody player: Player): ResponseEntity<Player> =
        playerService.update(id, player)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String): ResponseEntity<Void> =
        if (playerService.delete(id)) ResponseEntity.noContent().build()
        else ResponseEntity.notFound().build()
}
