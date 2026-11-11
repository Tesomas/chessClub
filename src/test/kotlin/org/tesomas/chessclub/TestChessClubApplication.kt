package org.tesomas.chessclub

import org.springframework.boot.fromApplication
import org.springframework.boot.with

/**
 * Runs the application against a Testcontainers MongoDB: `./gradlew bootTestRun`.
 * Used by the Bruno API test runner in `api-tests/`.
 */
fun main(args: Array<String>) {
    fromApplication<ChessClubApplication>().with(TestcontainersConfiguration::class).run(*args)
}
