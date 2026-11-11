package org.tesomas.chessclub.controller

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
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
}
