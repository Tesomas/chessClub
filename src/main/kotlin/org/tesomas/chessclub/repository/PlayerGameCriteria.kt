package org.tesomas.chessclub.repository

import org.springframework.data.mongodb.core.query.Criteria
import org.tesomas.chessclub.model.Game
import org.tesomas.chessclub.model.PlayerColor
import org.tesomas.chessclub.model.PlayerGameFilter
import java.time.LocalDate

/** Game document fields as seen from one colour: "player" is the side the requested player had. */
internal enum class GameSide(val playerIdField: String, val opponentNameField: String) {
    WHITE(Game::whitePlayerId.name, Game::blackPlayerName.name),
    BLACK(Game::blackPlayerId.name, Game::whitePlayerName.name);

    companion object {
        fun of(color: PlayerColor): GameSide = when (color) {
            PlayerColor.WHITE -> WHITE
            PlayerColor.BLACK -> BLACK
        }
    }
}

/**
 * Builds the match criteria for a player's games: an `$or` of a "player is white" and a
 * "player is black" branch. The opponent-name filter targets the other side's name in each
 * branch; the date range applies to both.
 */
internal object PlayerGameCriteria {

    fun build(playerId: String, filter: PlayerGameFilter): Criteria {
        val branches = PlayerColor.entries.map { branch(playerId, GameSide.of(it), filter) }
        return Criteria().orOperator(*branches.toTypedArray())
    }

    private fun branch(playerId: String, side: GameSide, filter: PlayerGameFilter): Criteria {
        val parts = buildList {
            add(Criteria.where(side.playerIdField).`is`(playerId))
            filter.opponentName?.let { add(Criteria.where(side.opponentNameField).regex(literal(it), "i")) }
            dateRange(filter.from, filter.to)?.let(::add)
        }
        return Criteria().andOperator(*parts.toTypedArray())
    }

    /** Whole days: `to` is matched as `< to + 1 day` so a stored time of day never drops a game. */
    private fun dateRange(from: LocalDate?, to: LocalDate?): Criteria? {
        if (from == null && to == null) return null
        val criteria = Criteria.where(Game::date.name)
        from?.let { criteria.gte(it) }
        to?.let { criteria.lt(it.plusDays(1)) }
        return criteria
    }

    /**
     * Escapes every regex metacharacter so user text is matched literally. Per-character escaping
     * (rather than `\Q...\E`) cannot be broken out of by input that itself contains `\E`.
     */
    internal fun literal(text: String): String = REGEX_METACHARACTERS.replace(text) { "\\" + it.value }

    private val REGEX_METACHARACTERS = Regex("""[\\^$.|?*+()\[\]{}]""")
}
