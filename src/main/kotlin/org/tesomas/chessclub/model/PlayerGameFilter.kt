package org.tesomas.chessclub.model

import java.time.LocalDate

/**
 * Validated filter, paging and sort options for `GET /api/players/{id}/games`.
 *
 * Every option is optional. [opponentName] is a case-insensitive, literal substring of the
 * opponent's name as recorded on the game; callers trim it and pass null for "no filter".
 * The date range is inclusive.
 *
 * Construction fails with [InvalidPlayerGameFilterException] listing every broken rule.
 */
data class PlayerGameFilter(
    val opponentName: String? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val page: Int = 0,
    val size: Int = DEFAULT_SIZE,
    val sort: PlayerGameSort = PlayerGameSort.DATE,
    val direction: SortDirection = SortDirection.DESC
) {
    init {
        val violations = violations()
        if (violations.isNotEmpty()) throw InvalidPlayerGameFilterException(violations)
    }

    /** Number of documents to skip for [page]. */
    val offset: Long get() = page.toLong() * size

    private fun violations(): List<String> = buildList {
        // Qualified: inside buildList a bare `size` is the size of the list being built
        val pageSize = this@PlayerGameFilter.size
        if (page < 0) add("page must be >= 0")
        if (pageSize !in MIN_SIZE..MAX_SIZE) add("size must be between $MIN_SIZE and $MAX_SIZE")
        val isFromInRange = isSupportedDate(from)
        val isToInRange = isSupportedDate(to)
        if (!isFromInRange) add("from $DATE_RANGE_MESSAGE")
        if (!isToInRange) add("to $DATE_RANGE_MESSAGE")
        if (isFromInRange && isToInRange && from != null && to != null && from > to) add("from must not be after to")
        opponentNameViolation(opponentName)?.let(::add)
    }

    companion object {
        const val DEFAULT_SIZE = 25
        const val MIN_SIZE = 1
        const val MAX_SIZE = 100
        const val MAX_OPPONENT_NAME_LENGTH = 100

        /** Supported date bounds; outside them `to + 1 day` can overflow and BSON dates stop being sensible. */
        val MIN_DATE: LocalDate = LocalDate.of(1900, 1, 1)
        val MAX_DATE: LocalDate = LocalDate.of(9999, 12, 31)
        private val DATE_RANGE_MESSAGE = "must be between $MIN_DATE and $MAX_DATE"

        private fun isSupportedDate(date: LocalDate?): Boolean = date == null || date in MIN_DATE..MAX_DATE

        private fun opponentNameViolation(name: String?): String? = when {
            name == null -> null
            name.isBlank() -> "opponentName must not be blank"
            name != name.trim() -> "opponentName must be trimmed"
            // Keeps CR/LF and friends out of log lines and queries (log forging)
            name.any(Char::isISOControl) -> "opponentName must not contain control characters"
            name.length > MAX_OPPONENT_NAME_LENGTH ->
                "opponentName must be at most $MAX_OPPONENT_NAME_LENGTH characters"
            else -> null
        }
    }
}

/** A player-games query was rejected; maps to HTTP 400. Messages name parameters, never values. */
class InvalidPlayerGameFilterException(val violations: List<String>) :
    RuntimeException("Invalid query parameters: " + violations.joinToString("; "))
