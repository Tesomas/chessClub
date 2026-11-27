package org.tesomas.chessclub.repository

import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.AggregationOperation
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators
import org.springframework.data.mongodb.core.aggregation.StringOperators
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository
import org.tesomas.chessclub.model.Game
import org.tesomas.chessclub.model.PageResponse
import org.tesomas.chessclub.model.PlayerGameFilter
import org.tesomas.chessclub.model.PlayerGameSort
import org.tesomas.chessclub.model.SortDirection

/**
 * Filtered, sorted, paged lookup of the games one player took part in (as white or black).
 *
 * The opponent-name sort is relative to the requested player, so it sorts on a field computed per
 * document with `$cond` (black or white name depending on the player's side), lower-cased for a
 * case-insensitive order. A lower-cased field is used instead of a collation because a non-simple
 * collation would stop the `$match` from using the player/date indexes. Every sort ends with `_id`
 * in the same direction so paging is deterministic.
 */
@Repository
class PlayerGameQueryRepository(private val mongoTemplate: MongoTemplate) {

    fun findPlayerGames(playerId: String, filter: PlayerGameFilter): PageResponse<Game> {
        val criteria = PlayerGameCriteria.build(playerId, filter)
        val total = mongoTemplate.count(Query(criteria), Game::class.java)
        val content = if (filter.offset >= total) emptyList() else fetchPage(playerId, criteria, filter)
        return PageResponse.of(content, filter.page, filter.size, total)
    }

    private fun fetchPage(playerId: String, criteria: Criteria, filter: PlayerGameFilter): List<Game> {
        val stages = buildList<AggregationOperation> {
            add(Aggregation.match(criteria))
            if (filter.sort == PlayerGameSort.OPPONENT_NAME) add(opponentNameField(playerId))
            add(Aggregation.sort(sortFor(filter)))
            add(Aggregation.skip(filter.offset))
            add(Aggregation.limit(filter.size.toLong()))
        }
        // The computed sort field is not a Game property, so the converter ignores it when reading.
        return mongoTemplate.aggregate(Aggregation.newAggregation(Game::class.java, stages), Game::class.java)
            .mappedResults
    }

    /** `$addFields` stage computing the opponent's lower-cased name from the player's perspective. */
    private fun opponentNameField(playerId: String): AggregationOperation {
        val playerIsWhite = ComparisonOperators.valueOf(Game::whitePlayerId.name).equalToValue(playerId)
        val value = ConditionalOperators.`when`(playerIsWhite)
            .thenValueOf(StringOperators.valueOf(Game::blackPlayerName.name).toLower())
            .otherwiseValueOf(StringOperators.valueOf(Game::whitePlayerName.name).toLower())
        return Aggregation.addFields().addFieldWithValue(OPPONENT_NAME_SORT_FIELD, value).build()
    }

    private fun sortFor(filter: PlayerGameFilter): Sort {
        val direction = when (filter.direction) {
            SortDirection.ASC -> Sort.Direction.ASC
            SortDirection.DESC -> Sort.Direction.DESC
        }
        val key = when (filter.sort) {
            PlayerGameSort.DATE -> Game::date.name
            PlayerGameSort.OPPONENT_NAME -> OPPONENT_NAME_SORT_FIELD
        }
        return Sort.by(direction, key, ID_FIELD)
    }

    private companion object {
        const val OPPONENT_NAME_SORT_FIELD = "opponentNameSortKey"
        const val ID_FIELD = "_id"
    }
}
