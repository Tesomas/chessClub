package org.tesomas.chessclub.controller

import org.apache.logging.log4j.LogManager
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import org.tesomas.chessclub.service.PlayerNotFoundException

/**
 * Maps exceptions to RFC 9457 ProblemDetail responses.
 *
 * Extends [ResponseEntityExceptionHandler] so Spring MVC's own exceptions (malformed JSON -> 400,
 * unknown resource -> 404, unsupported method -> 405, ...) keep their correct status codes and are
 * not swallowed by the generic [Exception] fallback below.
 */
@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {

    @ExceptionHandler(PlayerNotFoundException::class)
    fun handlePlayerNotFound(ex: PlayerNotFoundException): ProblemDetail {
        log.warn("Request rejected: {}", ex.message)
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Player not found")
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ProblemDetail {
        log.error("Unhandled exception", ex)
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_ERROR_DETAIL)
    }

    companion object {
        const val GENERIC_ERROR_DETAIL = "An unexpected error occurred"
        private val log = LogManager.getLogger(ApiExceptionHandler::class.java)
    }
}
