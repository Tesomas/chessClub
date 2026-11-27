package org.tesomas.chessclub.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerGameFilterTest {

    @Test
    fun `defaults are page 0, size 25, sorted by date descending with no filters`() {
        // Act
        val filter = PlayerGameFilter()

        // Assert
        assertEquals(0, filter.page)
        assertEquals(25, filter.size)
        assertEquals(PlayerGameSort.DATE, filter.sort)
        assertEquals(SortDirection.DESC, filter.direction)
        assertNull(filter.opponentName)
        assertNull(filter.from)
        assertNull(filter.to)
    }

    @Test
    fun `offset is page times size`() {
        assertEquals(0, PlayerGameFilter().offset)
        assertEquals(30, PlayerGameFilter(page = 3, size = 10).offset)
    }

    @Test
    fun `negative page is rejected`() {
        val ex = assertThrows<InvalidPlayerGameFilterException> { PlayerGameFilter(page = -1) }
        assertEquals(listOf("page must be >= 0"), ex.violations)
    }

    @Test
    fun `size below 1 is rejected`() {
        val ex = assertThrows<InvalidPlayerGameFilterException> { PlayerGameFilter(size = 0) }
        assertEquals(listOf("size must be between 1 and 100"), ex.violations)
    }

    @Test
    fun `size above 100 is rejected`() {
        val ex = assertThrows<InvalidPlayerGameFilterException> { PlayerGameFilter(size = 101) }
        assertEquals(listOf("size must be between 1 and 100"), ex.violations)
    }

    @Test
    fun `size boundaries 1 and 100 are accepted`() {
        assertDoesNotThrow { PlayerGameFilter(size = 1) }
        assertDoesNotThrow { PlayerGameFilter(size = 100) }
    }

    @Test
    fun `from after to is rejected`() {
        val ex = assertThrows<InvalidPlayerGameFilterException> {
            PlayerGameFilter(from = LocalDate.of(2026, 2, 2), to = LocalDate.of(2026, 2, 1))
        }
        assertEquals(listOf("from must not be after to"), ex.violations)
    }

    @Test
    fun `from equal to to is accepted, and either bound alone is accepted`() {
        val day = LocalDate.of(2026, 2, 1)
        assertDoesNotThrow { PlayerGameFilter(from = day, to = day) }
        assertDoesNotThrow { PlayerGameFilter(from = day) }
        assertDoesNotThrow { PlayerGameFilter(to = day) }
    }

    @Test
    fun `date bounds at the supported limits are accepted`() {
        assertDoesNotThrow { PlayerGameFilter(from = LocalDate.of(1900, 1, 1), to = LocalDate.of(9999, 12, 31)) }
        assertDoesNotThrow { PlayerGameFilter(from = LocalDate.of(9999, 12, 31)) }
        assertDoesNotThrow { PlayerGameFilter(to = LocalDate.of(1900, 1, 1)) }
    }

    @Test
    fun `from before 1900 is rejected`() {
        val ex = assertThrows<InvalidPlayerGameFilterException> { PlayerGameFilter(from = LocalDate.of(1899, 12, 31)) }
        assertEquals(listOf("from must be between 1900-01-01 and 9999-12-31"), ex.violations)
    }

    @Test
    fun `to after 9999 is rejected`() {
        val ex = assertThrows<InvalidPlayerGameFilterException> { PlayerGameFilter(to = LocalDate.of(10000, 1, 1)) }
        assertEquals(listOf("to must be between 1900-01-01 and 9999-12-31"), ex.violations)
    }

    @Test
    fun `LocalDate MIN and MAX are rejected instead of overflowing later`() {
        val ex = assertThrows<InvalidPlayerGameFilterException> {
            PlayerGameFilter(from = LocalDate.MIN, to = LocalDate.MAX)
        }
        assertEquals(
            listOf("from must be between 1900-01-01 and 9999-12-31", "to must be between 1900-01-01 and 9999-12-31"),
            ex.violations
        )
    }

    @Test
    fun `out of range dates are reported without also claiming from is after to`() {
        val ex = assertThrows<InvalidPlayerGameFilterException> {
            PlayerGameFilter(from = LocalDate.MAX, to = LocalDate.of(2026, 1, 1))
        }
        assertEquals(listOf("from must be between 1900-01-01 and 9999-12-31"), ex.violations)
    }

    @Test
    fun `opponentName with a control character is rejected`() {
        listOf("bo\nb", "bo\rb", "bo\tb", "bo\u0000b", "bo\u001Bb", "bo\u007Fb", "bo\u0085b").forEach { name ->
            val ex = assertThrows<InvalidPlayerGameFilterException>(name) { PlayerGameFilter(opponentName = name) }
            assertEquals(listOf("opponentName must not contain control characters"), ex.violations, name)
        }
    }

    @Test
    fun `opponentName with non-ASCII letters is accepted`() {
        assertDoesNotThrow { PlayerGameFilter(opponentName = "Zoë Müller-Łukasz") }
    }

    @Test
    fun `opponentName of exactly 100 characters is accepted`() {
        assertDoesNotThrow { PlayerGameFilter(opponentName = "a".repeat(100)) }
    }

    @Test
    fun `opponentName longer than 100 characters is rejected`() {
        val ex = assertThrows<InvalidPlayerGameFilterException> { PlayerGameFilter(opponentName = "a".repeat(101)) }
        assertEquals(listOf("opponentName must be at most 100 characters"), ex.violations)
    }

    @Test
    fun `blank opponentName is rejected`() {
        val ex = assertThrows<InvalidPlayerGameFilterException> { PlayerGameFilter(opponentName = "  ") }
        assertEquals(listOf("opponentName must not be blank"), ex.violations)
    }

    @Test
    fun `opponentName with surrounding whitespace is rejected so callers trim first`() {
        val ex = assertThrows<InvalidPlayerGameFilterException> { PlayerGameFilter(opponentName = " anna ") }
        assertEquals(listOf("opponentName must be trimmed"), ex.violations)
    }

    @Test
    fun `opponentName with regex metacharacters is accepted as plain text`() {
        assertDoesNotThrow { PlayerGameFilter(opponentName = "a.b(c)*[d]\\E\\Q^$|?+{2}") }
    }

    @Test
    fun `all violations are reported together in the message`() {
        // Act
        val ex = assertThrows<InvalidPlayerGameFilterException> {
            PlayerGameFilter(page = -1, size = 0, from = LocalDate.of(2026, 1, 2), to = LocalDate.of(2026, 1, 1))
        }

        // Assert
        assertEquals(3, ex.violations.size)
        val message = ex.message ?: ""
        assertTrue(message.startsWith("Invalid query parameters: "))
        assertTrue(message.contains("page must be >= 0"))
        assertTrue(message.contains("size must be between 1 and 100"))
        assertTrue(message.contains("from must not be after to"))
    }

    @Test
    fun `sort parses its API names case-insensitively`() {
        assertEquals(PlayerGameSort.DATE, PlayerGameSort.fromParam("date"))
        assertEquals(PlayerGameSort.DATE, PlayerGameSort.fromParam("DATE"))
        assertEquals(PlayerGameSort.OPPONENT_NAME, PlayerGameSort.fromParam("opponentName"))
        assertEquals(PlayerGameSort.OPPONENT_NAME, PlayerGameSort.fromParam("opponentname"))
    }

    @Test
    fun `unknown or removed sort keys are rejected listing the allowed values`() {
        listOf("rating", "playerElo", "opponentElo", "").forEach { value ->
            val ex = assertThrows<InvalidPlayerGameFilterException> { PlayerGameSort.fromParam(value) }
            assertEquals(listOf("sort must be one of date, opponentName"), ex.violations, value)
        }
    }

    @Test
    fun `direction parses asc and desc case-insensitively`() {
        assertEquals(SortDirection.ASC, SortDirection.fromParam("asc"))
        assertEquals(SortDirection.DESC, SortDirection.fromParam("DESC"))
    }

    @Test
    fun `unknown direction is rejected`() {
        val ex = assertThrows<InvalidPlayerGameFilterException> { SortDirection.fromParam("up") }
        assertEquals(listOf("direction must be one of asc, desc"), ex.violations)
    }
}
