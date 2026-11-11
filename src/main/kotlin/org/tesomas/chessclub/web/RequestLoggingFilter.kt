package org.tesomas.chessclub.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.ThreadContext
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Assigns a request id to every HTTP request, exposes it to log lines via the Log4j2 ThreadContext
 * (`%X{requestId}`), echoes it in the `X-Request-Id` response header, and logs one summary line
 * (method, path, status, duration) per request.
 *
 * An incoming `X-Request-Id` is reused only if it is short and alphanumeric/dash, to prevent log injection.
 * The query string is never logged because it may carry sensitive values.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestLoggingFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER))
        val startNanos = System.nanoTime()
        ThreadContext.put(REQUEST_ID_KEY, requestId)
        response.setHeader(REQUEST_ID_HEADER, requestId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            val durationMs = (System.nanoTime() - startNanos) / NANOS_PER_MILLI
            val path = request.requestURI
            log.log(
                levelFor(path),
                "{} {} -> {} ({} ms)",
                request.method,
                path,
                response.status,
                durationMs
            )
            ThreadContext.remove(REQUEST_ID_KEY)
        }
    }

    private fun levelFor(path: String): Level =
        if (path.startsWith(HEALTH_PATH)) Level.DEBUG else Level.INFO

    companion object {
        const val REQUEST_ID_HEADER = "X-Request-Id"
        const val REQUEST_ID_KEY = "requestId"

        private const val MAX_REQUEST_ID_LENGTH = 64
        private const val HEALTH_PATH = "/actuator/health"
        private const val NANOS_PER_MILLI = 1_000_000L
        private val SAFE_REQUEST_ID = Regex("^[A-Za-z0-9-]{1,$MAX_REQUEST_ID_LENGTH}$")
        private val log = LogManager.getLogger(RequestLoggingFilter::class.java)

        internal fun resolveRequestId(incoming: String?): String =
            incoming?.takeIf { SAFE_REQUEST_ID.matches(it) } ?: UUID.randomUUID().toString()
    }
}
