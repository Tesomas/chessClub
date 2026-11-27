package org.tesomas.chessclub.controller

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.tesomas.chessclub.model.InvalidPlayerGameFilterException
import org.tesomas.chessclub.service.InvalidGameException
import org.tesomas.chessclub.service.PlayerNotFoundException
import kotlin.test.assertEquals

class ApiExceptionHandlerTest {

    private val handler = ApiExceptionHandler()

    @Test
    fun `PlayerNotFoundException maps to a 404 ProblemDetail carrying the message`() {
        // Arrange
        val ex = PlayerNotFoundException("abc123")

        // Act
        val problem = handler.handlePlayerNotFound(ex)

        // Assert
        assertEquals(HttpStatus.NOT_FOUND.value(), problem.status)
        assertEquals("Player abc123 not found", problem.detail)
    }

    @Test
    fun `unexpected exceptions map to a generic 500 ProblemDetail without internal details`() {
        // Arrange
        val ex = IllegalStateException("db password=secret leaked in message")

        // Act
        val problem = handler.handleUnexpected(ex)

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), problem.status)
        assertEquals(ApiExceptionHandler.GENERIC_ERROR_DETAIL, problem.detail)
    }

    @Test
    fun `InvalidPlayerGameFilterException maps to a 400 ProblemDetail listing the violations`() {
        // Arrange
        val ex = InvalidPlayerGameFilterException(listOf("page must be >= 0", "size must be between 1 and 100"))

        // Act
        val problem = handler.handleInvalidPlayerGameFilter(ex)

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.status)
        assertEquals("Invalid query parameters: page must be >= 0; size must be between 1 and 100", problem.detail)
    }

    @Test
    fun `InvalidGameException maps to a 400 ProblemDetail carrying the message`() {
        // Act
        val problem = handler.handleInvalidGame(InvalidGameException("pgn must be at most 65536 characters"))

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.status)
        assertEquals("pgn must be at most 65536 characters", problem.detail)
    }
}
