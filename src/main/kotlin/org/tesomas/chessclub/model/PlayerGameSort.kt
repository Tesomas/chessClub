package org.tesomas.chessclub.model

/** Sort keys for a player's game list; [param] is the value used in the `sort` query parameter. */
enum class PlayerGameSort(val param: String) {
    DATE("date"),
    OPPONENT_NAME("opponentName");

    companion object {
        /** Parses a `sort` query value (case-insensitive) or throws [InvalidPlayerGameFilterException]. */
        fun fromParam(value: String): PlayerGameSort =
            entries.firstOrNull { it.param.equals(value, ignoreCase = true) }
                ?: throw InvalidPlayerGameFilterException(
                    listOf("sort must be one of ${entries.joinToString { it.param }}")
                )
    }
}

/** Sort direction; [param] is the value used in the `direction` query parameter. */
enum class SortDirection(val param: String) {
    ASC("asc"),
    DESC("desc");

    companion object {
        /** Parses a `direction` query value (case-insensitive) or throws [InvalidPlayerGameFilterException]. */
        fun fromParam(value: String): SortDirection =
            entries.firstOrNull { it.param.equals(value, ignoreCase = true) }
                ?: throw InvalidPlayerGameFilterException(
                    listOf("direction must be one of ${entries.joinToString { it.param }}")
                )
    }
}
