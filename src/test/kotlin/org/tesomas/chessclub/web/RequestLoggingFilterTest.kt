package org.tesomas.chessclub.web

import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import org.apache.logging.log4j.ThreadContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.tesomas.chessclub.web.RequestLoggingFilter.Companion.REQUEST_ID_HEADER
import org.tesomas.chessclub.web.RequestLoggingFilter.Companion.REQUEST_ID_KEY
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RequestLoggingFilterTest {

    private val filter = RequestLoggingFilter()

    @AfterEach
    fun cleanUp() = ThreadContext.clearAll()

    @Test
    fun `sets requestId in ThreadContext during chain and echoes it in response header`() {
        // Arrange
        val request = MockHttpServletRequest("GET", "/api/players")
        val response = MockHttpServletResponse()
        var idSeenInChain: String? = null
        val chain = MockFilterChain(NoopServlet { idSeenInChain = ThreadContext.get(REQUEST_ID_KEY) })

        // Act
        filter.doFilter(request, response, chain)

        // Assert
        val headerId = assertNotNull(response.getHeader(REQUEST_ID_HEADER))
        assertEquals(headerId, idSeenInChain)
        UUID.fromString(headerId) // generated id is a UUID
    }

    @Test
    fun `reuses a valid incoming request id`() {
        // Arrange
        val request = MockHttpServletRequest("GET", "/api/games").apply {
            addHeader(REQUEST_ID_HEADER, "abc-123-DEF")
        }
        val response = MockHttpServletResponse()
        var idSeenInChain: String? = null
        val chain = MockFilterChain(NoopServlet { idSeenInChain = ThreadContext.get(REQUEST_ID_KEY) })

        // Act
        filter.doFilter(request, response, chain)

        // Assert
        assertEquals("abc-123-DEF", response.getHeader(REQUEST_ID_HEADER))
        assertEquals("abc-123-DEF", idSeenInChain)
    }

    @Test
    fun `replaces an incoming request id containing a newline`() {
        // Arrange
        val malicious = "abc\r\nFAKE LOG LINE"
        val request = MockHttpServletRequest("GET", "/api/players").apply {
            addHeader(REQUEST_ID_HEADER, malicious)
        }
        val response = MockHttpServletResponse()

        // Act
        filter.doFilter(request, response, MockFilterChain())

        // Assert
        val headerId = assertNotNull(response.getHeader(REQUEST_ID_HEADER))
        assertNotEquals(malicious, headerId)
        UUID.fromString(headerId)
    }

    @Test
    fun `replaces an incoming request id that is too long`() {
        // Arrange
        val tooLong = "a".repeat(65)
        val request = MockHttpServletRequest("GET", "/api/players").apply {
            addHeader(REQUEST_ID_HEADER, tooLong)
        }
        val response = MockHttpServletResponse()

        // Act
        filter.doFilter(request, response, MockFilterChain())

        // Assert
        val headerId = assertNotNull(response.getHeader(REQUEST_ID_HEADER))
        assertNotEquals(tooLong, headerId)
        UUID.fromString(headerId)
    }

    @Test
    fun `accepts an incoming request id of exactly 64 characters`() {
        val maxLength = "a".repeat(64)
        assertEquals(maxLength, RequestLoggingFilter.resolveRequestId(maxLength))
    }

    @Test
    fun `clears ThreadContext after the chain completes`() {
        // Arrange
        val request = MockHttpServletRequest("GET", "/api/players")

        // Act
        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        // Assert
        assertNull(ThreadContext.get(REQUEST_ID_KEY))
    }

    @Test
    fun `clears ThreadContext even when the chain throws`() {
        // Arrange
        val request = MockHttpServletRequest("GET", "/api/players")
        val chain = MockFilterChain(NoopServlet { throw IllegalStateException("boom") })

        // Act
        assertThrows<IllegalStateException> {
            filter.doFilter(request, MockHttpServletResponse(), chain)
        }

        // Assert
        assertNull(ThreadContext.get(REQUEST_ID_KEY))
    }

    /** Servlet stub that runs [onService] while the filter's ThreadContext is active. */
    private class NoopServlet(private val onService: () -> Unit) : jakarta.servlet.GenericServlet() {
        override fun service(req: ServletRequest, res: ServletResponse) = onService()
    }
}
