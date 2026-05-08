package org.tesomas.chessclub

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ChessClubApplication

fun main(args: Array<String>) {
    runApplication<ChessClubApplication>(*args)
}