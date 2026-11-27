package org.tesomas.chessclub.repository

import org.bson.Document
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.tesomas.chessclub.TestFixtures.aGame
import org.tesomas.chessclub.TestcontainersConfiguration
import org.tesomas.chessclub.config.MongoConfig
import org.tesomas.chessclub.model.Game
import org.tesomas.chessclub.model.GameResult
import org.tesomas.chessclub.model.GameType
import org.tesomas.chessclub.model.PlayerGameFilter
import org.tesomas.chessclub.model.PlayerGameSort
import org.tesomas.chessclub.model.SortDirection
import java.time.Instant
import java.time.LocalDate
import java.util.Date
import kotlin.test.assertEquals

/**
 * Fixture, from the perspective of player "p" ("Pat Player"):
 *
 * | id | white | black | date       | p colour | opponent       |
 * |----|-------|-------|------------|----------|----------------|
 * | g1 | p     | z     | 2026-01-10 | WHITE    | Zed Zimmer     |
 * | g2 | z     | p     | 2026-02-10 | BLACK    | Zed Zimmer     |
 * | g3 | p     | b     | 2026-03-10 | WHITE    | bob.b (Jr)     |
 * | g4 | b     | p     | 2026-04-10 | BLACK    | bob.b (Jr)     |
 * | g5 | p     | a     | 2026-05-10 | WHITE    | Anna Alvarez   |
 * | g6 | a     | p     | 2026-06-10 | BLACK    | Anna Alvarez   |
 *
 * plus x1/x2 between other players only (one of them named "Anna Other"), which must never show up for p.
 * Opponent names sort case-insensitively as Anna < bob < Zed, which differs from both the date order and
 * a binary (case-sensitive) order (Anna < Zed < bob). "bob.b (Jr)" carries regex metacharacters.
 */
@DataMongoTest
@Import(TestcontainersConfiguration::class, MongoConfig::class)
class PlayerGameQueryRepositoryTest {

    @Autowired lateinit var mongoTemplate: MongoTemplate
    @Autowired lateinit var gameRepository: GameRepository

    private val repository by lazy { PlayerGameQueryRepository(mongoTemplate) }

    private val names = mapOf(
        "p" to "Pat Player", "a" to "Anna Alvarez", "b" to "bob.b (Jr)", "z" to "Zed Zimmer",
        "o" to "Anna Other", "q" to "Quinn Q"
    )

    @BeforeEach
    fun setUp() {
        gameRepository.deleteAll()
        gameRepository.saveAll(
            listOf(
                game("g1", "p", "z", "2026-01-10", GameResult.WHITE_WINS),
                game("g2", "z", "p", "2026-02-10", GameResult.WHITE_WINS),
                game("g3", "p", "b", "2026-03-10", GameResult.DRAW),
                game("g4", "b", "p", "2026-04-10", GameResult.BLACK_WINS),
                game("g5", "p", "a", "2026-05-10", GameResult.BLACK_WINS),
                game("g6", "a", "p", "2026-06-10", GameResult.DRAW),
                game("x1", "o", "q", "2026-03-15", GameResult.WHITE_WINS),
                game("x2", "a", "b", "2026-03-16", GameResult.DRAW)
            )
        )
    }

    private fun game(id: String, white: String, black: String, date: String, result: GameResult): Game = aGame(
        white, black, id = id,
        whitePlayerName = names.getValue(white), blackPlayerName = names.getValue(black),
        gameType = GameType.BLITZ, result = result, date = LocalDate.parse(date),
        whitePlayerRating = 1500, blackPlayerRating = 1450
    )

    private fun ids(filter: PlayerGameFilter = PlayerGameFilter(), playerId: String = "p"): List<String?> =
        repository.findPlayerGames(playerId, filter).content.map { it.id }

    private fun idSet(filter: PlayerGameFilter): Set<String?> = ids(filter).toSet()

    private fun byName(name: String) = idSet(PlayerGameFilter(opponentName = name))

    // --- scope -------------------------------------------------------------------------------

    @Test
    fun `no filters returns every game of the player as white or black and excludes other games`() {
        val page = repository.findPlayerGames("p", PlayerGameFilter())
        assertEquals(listOf("g6", "g5", "g4", "g3", "g2", "g1"), page.content.map { it.id })
        assertEquals(6, page.totalElements)
        assertEquals(1, page.totalPages)
    }

    @Test
    fun `returned games are full Game documents without the computed sort field`() {
        val content = repository.findPlayerGames("p", PlayerGameFilter(sort = PlayerGameSort.OPPONENT_NAME)).content
        assertEquals(game("g4", "b", "p", "2026-04-10", GameResult.BLACK_WINS), content.single { it.id == "g4" })
    }

    @Test
    fun `a player without games gets an empty page`() {
        val page = repository.findPlayerGames("nobody", PlayerGameFilter())
        assertEquals(emptyList(), page.content)
        assertEquals(0, page.totalElements)
        assertEquals(0, page.totalPages)
    }

    @Test
    fun `a player who played only one colour is still found`() {
        assertEquals(listOf("x1"), ids(playerId = "q"))
        assertEquals(listOf("x1"), ids(playerId = "o"))
    }

    @Test
    fun `a self-play game matches both colour branches but appears and counts exactly once`() {
        // Arrange
        gameRepository.save(game("self", "p", "p", "2026-07-01", GameResult.DRAW))

        // Act
        val page = repository.findPlayerGames("p", PlayerGameFilter())
        val byNameSort = repository.findPlayerGames("p", PlayerGameFilter(sort = PlayerGameSort.OPPONENT_NAME))

        // Assert
        assertEquals(listOf("self", "g6", "g5", "g4", "g3", "g2", "g1"), page.content.map { it.id })
        assertEquals(7, page.totalElements)
        assertEquals(1, byNameSort.content.count { it.id == "self" })
        assertEquals(7, byNameSort.totalElements)
    }

    @Test
    fun `a self-play game is found by the player's own name, since it is also the opponent's`() {
        // Arrange
        gameRepository.save(game("self", "p", "p", "2026-07-01", GameResult.DRAW))

        // Act + Assert
        assertEquals(listOf("self"), ids(PlayerGameFilter(opponentName = "pat")))
        assertEquals(1, repository.findPlayerGames("p", PlayerGameFilter(opponentName = "pat")).totalElements)
    }

    // --- opponentName --------------------------------------------------------------------------

    @Test
    fun `opponent name matches the opponent whichever colour the opponent had`() {
        assertEquals(setOf("g5", "g6"), byName("Anna Alvarez"))
        assertEquals(setOf("g1", "g2"), byName("Zed Zimmer"))
    }

    @Test
    fun `opponent name is a case-insensitive substring match`() {
        assertEquals(setOf("g5", "g6"), byName("anna"))
        assertEquals(setOf("g5", "g6"), byName("NA ALV"))
        assertEquals(setOf("g1", "g2"), byName("zim"))
        assertEquals(setOf("g3", "g4"), byName("BOB"))
        assertEquals(setOf("g1", "g2", "g5", "g6"), byName("E")) // "Zed Zimmer" and "Anna Alvarez"
    }

    @Test
    fun `opponent name never matches the player's own name`() {
        assertEquals(emptySet(), byName("Pat"))
        assertEquals(emptySet(), byName("Player"))
    }

    @Test
    fun `opponent name that matches nobody gives an empty page with zero totals`() {
        val page = repository.findPlayerGames("p", PlayerGameFilter(opponentName = "Nobody"))
        assertEquals(emptyList(), page.content)
        assertEquals(0, page.totalElements)
        assertEquals(0, page.totalPages)
    }

    @Test
    fun `games between other players are excluded even when the name matches`() {
        // "Anna Other" only plays in x1, which p is not part of
        assertEquals(emptySet(), byName("Other"))
    }

    @Test
    fun `regex metacharacters in the opponent name are matched literally`() {
        assertEquals(setOf("g3", "g4"), byName("b.b"))
        assertEquals(setOf("g3", "g4"), byName("("))
        assertEquals(setOf("g3", "g4"), byName("(Jr)"))
        assertEquals(setOf("g3", "g4"), byName(")"))
        assertEquals(emptySet(), byName("o.b")) // as a regex this would match "ob." in "bob.b"
        assertEquals(emptySet(), byName(".*"))
        assertEquals(emptySet(), byName("Anna|Zed"))
        assertEquals(emptySet(), byName("^Anna"))
        assertEquals(emptySet(), byName("[AZ]"))
        assertEquals(emptySet(), byName("\\Q"))
        assertEquals(emptySet(), byName("Zed\\E.*"))
    }

    // --- dates ---------------------------------------------------------------------------------

    @Test
    fun `date range is inclusive on both edges`() {
        val filter = PlayerGameFilter(from = LocalDate.of(2026, 2, 10), to = LocalDate.of(2026, 4, 10))
        assertEquals(setOf("g2", "g3", "g4"), idSet(filter))
    }

    @Test
    fun `date range with only one bound`() {
        assertEquals(setOf("g5", "g6"), idSet(PlayerGameFilter(from = LocalDate.of(2026, 5, 10))))
        assertEquals(setOf("g1"), idSet(PlayerGameFilter(to = LocalDate.of(2026, 1, 10))))
        assertEquals(emptySet(), idSet(PlayerGameFilter(from = LocalDate.of(2026, 6, 11))))
        assertEquals(emptySet(), idSet(PlayerGameFilter(to = LocalDate.of(2026, 1, 9))))
    }

    @Test
    fun `a single-day range returns only that day`() {
        val day = LocalDate.of(2026, 3, 10)
        assertEquals(setOf("g3"), idSet(PlayerGameFilter(from = day, to = day)))
    }

    @Test
    fun `seed-style dates stored at UTC midnight read back as the same LocalDate and match the range`() {
        // Arrange: written the way docker/mongo-seed/seed.js writes games
        insertRaw("seeded", "2026-06-20T00:00:00Z")

        // Act
        val page = repository.findPlayerGames(
            "p", PlayerGameFilter(from = LocalDate.of(2026, 6, 20), to = LocalDate.of(2026, 6, 20))
        )

        // Assert
        assertEquals(listOf("seeded"), page.content.map { it.id })
        assertEquals(LocalDate.of(2026, 6, 20), page.content.single().date)
    }

    @Test
    fun `a stored time of day on the last day is still inside the inclusive range`() {
        // Arrange
        insertRaw("late", "2026-06-20T23:30:00Z")
        insertRaw("next-day", "2026-06-21T00:00:00Z")

        // Act
        val ids = idSet(PlayerGameFilter(from = LocalDate.of(2026, 6, 20), to = LocalDate.of(2026, 6, 20)))

        // Assert
        assertEquals(setOf("late"), ids)
    }

    private fun insertRaw(id: String, instant: String) {
        mongoTemplate.getCollection("games").insertOne(
            Document(
                mapOf(
                    "_id" to id, "whitePlayerId" to "p", "blackPlayerId" to "a",
                    "whitePlayerName" to "Pat Player", "blackPlayerName" to "Anna Alvarez",
                    "whitePlayerRating" to 1500, "blackPlayerRating" to 1500,
                    "gameType" to "BLITZ", "result" to "DRAW", "time" to "3+2",
                    "date" to Date.from(Instant.parse(instant))
                )
            )
        )
    }

    // --- combinations --------------------------------------------------------------------------

    @Test
    fun `opponent name combined with a date range`() {
        assertEquals(setOf("g2"), idSet(PlayerGameFilter(opponentName = "zed", from = LocalDate.of(2026, 2, 1))))
        assertEquals(setOf("g3"), idSet(PlayerGameFilter(opponentName = "bob", to = LocalDate.of(2026, 3, 10))))
        assertEquals(
            emptySet(),
            idSet(PlayerGameFilter(opponentName = "anna", from = LocalDate.of(2026, 1, 1), to = LocalDate.of(2026, 4, 30)))
        )
    }

    // --- pagination ----------------------------------------------------------------------------

    @Test
    fun `pagination reports totals and returns a partial last page`() {
        val first = repository.findPlayerGames("p", PlayerGameFilter(page = 0, size = 4))
        val last = repository.findPlayerGames("p", PlayerGameFilter(page = 1, size = 4))

        assertEquals(listOf("g6", "g5", "g4", "g3"), first.content.map { it.id })
        assertEquals(listOf("g2", "g1"), last.content.map { it.id })
        listOf(first, last).forEach {
            assertEquals(6, it.totalElements)
            assertEquals(2, it.totalPages)
            assertEquals(4, it.size)
        }
        assertEquals(0, first.page)
        assertEquals(1, last.page)
    }

    @Test
    fun `a page beyond the end is empty but keeps the totals`() {
        val page = repository.findPlayerGames("p", PlayerGameFilter(page = 2, size = 4))
        assertEquals(emptyList(), page.content)
        assertEquals(6, page.totalElements)
        assertEquals(2, page.totalPages)
    }

    @Test
    fun `totals reflect the filter, not all of the player's games`() {
        // Of p's opponents only "Anna Alvarez" contains an "a"
        val page = repository.findPlayerGames("p", PlayerGameFilter(opponentName = "a", size = 1))
        assertEquals(2, page.totalElements)
        assertEquals(2, page.totalPages)
        assertEquals(listOf("g6"), page.content.map { it.id })
    }

    // --- sorting -------------------------------------------------------------------------------

    @Test
    fun `sort by date in both directions`() {
        assertEquals(
            listOf("g1", "g2", "g3", "g4", "g5", "g6"),
            ids(PlayerGameFilter(sort = PlayerGameSort.DATE, direction = SortDirection.ASC))
        )
        assertEquals(
            listOf("g6", "g5", "g4", "g3", "g2", "g1"),
            ids(PlayerGameFilter(sort = PlayerGameSort.DATE, direction = SortDirection.DESC))
        )
    }

    @Test
    fun `sort by opponent name is case-insensitive and uses the opponent's side on either colour`() {
        assertEquals(
            listOf("g5", "g6", "g3", "g4", "g1", "g2"),
            ids(PlayerGameFilter(sort = PlayerGameSort.OPPONENT_NAME, direction = SortDirection.ASC))
        )
        assertEquals(
            listOf("g2", "g1", "g4", "g3", "g6", "g5"),
            ids(PlayerGameFilter(sort = PlayerGameSort.OPPONENT_NAME, direction = SortDirection.DESC))
        )
    }

    @Test
    fun `opponent name sort is applied before paging`() {
        val second = ids(
            PlayerGameFilter(sort = PlayerGameSort.OPPONENT_NAME, direction = SortDirection.ASC, page = 1, size = 2)
        )
        assertEquals(listOf("g3", "g4"), second)
    }

    @Test
    fun `opponent name sort combined with a filter`() {
        val filter = PlayerGameFilter(
            from = LocalDate.of(2026, 2, 1), sort = PlayerGameSort.OPPONENT_NAME, direction = SortDirection.DESC
        )
        assertEquals(listOf("g2", "g4", "g3", "g6", "g5"), ids(filter))
    }

    @Test
    fun `ties are broken by id in the sort direction so paging is deterministic`() {
        // Arrange: three games on the same date against the same opponent
        gameRepository.deleteAll()
        gameRepository.saveAll(listOf("t2", "t3", "t1").map { game(it, "p", "a", "2026-07-01", GameResult.DRAW) })

        // Act + Assert
        PlayerGameSort.entries.forEach { sort ->
            assertEquals(listOf("t1", "t2", "t3"), ids(PlayerGameFilter(sort = sort, direction = SortDirection.ASC)))
            assertEquals(listOf("t3", "t2", "t1"), ids(PlayerGameFilter(sort = sort, direction = SortDirection.DESC)))
            val paged = (0..2).flatMap { ids(PlayerGameFilter(sort = sort, page = it, size = 1)) }
            assertEquals(listOf("t3", "t2", "t1"), paged)
        }
    }
}
