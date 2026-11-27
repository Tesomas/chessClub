package org.tesomas.chessclub.repository

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.tesomas.chessclub.model.PlayerGameFilter
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pure unit tests (no MongoDB) for the criteria builder and the regex escaping. */
class PlayerGameCriteriaTest {

    @ParameterizedTest
    @ValueSource(strings = [".", "(", ")", "[", "]", "{", "}", "*", "+", "?", "^", "$", "|", "\\", "\\E", "\\Q", "a.b(c)*"])
    fun `escaped text matches itself literally and nothing else`(text: String) {
        // Act
        val regex = Regex(PlayerGameCriteria.literal(text))

        // Assert
        assertTrue(regex.containsMatchIn("xx${text}yy"), "should find '$text' literally")
        assertFalse(regex.containsMatchIn("plain letters only"), "'$text' must not act as a pattern")
    }

    @Test
    fun `escaping leaves ordinary characters alone`() {
        assertEquals("Bob Brooks-Smith", PlayerGameCriteria.literal("Bob Brooks-Smith"))
        assertEquals("""bob\.b \(Jr\)""", PlayerGameCriteria.literal("bob.b (Jr)"))
    }

    @Test
    fun `criteria is an or of the white and black branches with each side's opponent name`() {
        // Act
        val doc = PlayerGameCriteria.build("p1", PlayerGameFilter(opponentName = "ann")).criteriaObject.toJson()

        // Assert
        assertTrue(doc.contains("\"\$or\""), doc)
        assertTrue(doc.contains("\"whitePlayerId\": \"p1\""), doc)
        assertTrue(doc.contains("\"blackPlayerId\": \"p1\""), doc)
        assertTrue(doc.contains("\"blackPlayerName\""), doc)
        assertTrue(doc.contains("\"whitePlayerName\""), doc)
    }

    @Test
    fun `no date criteria is added when neither bound is given`() {
        val doc = PlayerGameCriteria.build("p1", PlayerGameFilter()).criteriaObject.toJson()
        assertFalse(doc.contains("\"date\""), doc)
    }

    @Test
    fun `to is matched as before the next day`() {
        val doc = PlayerGameCriteria.build("p1", PlayerGameFilter(to = LocalDate.of(2026, 1, 31))).criteriaObject
        val json = doc.toString()
        assertTrue(json.contains("\$lt"), json)
        assertFalse(json.contains("\$lte"), json)
        assertTrue(json.contains("2026-02-01"), json)
    }
}
