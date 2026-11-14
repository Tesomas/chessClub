package org.tesomas.chessclub

import org.tesomas.chessclub.model.Game
import org.tesomas.chessclub.model.GameCreateRequest
import org.tesomas.chessclub.model.GameResult
import org.tesomas.chessclub.model.GameType
import org.tesomas.chessclub.model.Player
import java.time.LocalDate

/** Shared test data builders. Override any field with named arguments. */
object TestFixtures {

    fun aPlayer(
        id: String? = null,
        firstName: String = "Magnus",
        lastName: String = "Carlsen",
        rapidElo: Int = 2850,
        blitzElo: Int = 2830,
        bulletElo: Int = 2800,
        classicalElo: Int = 2860
    ): Player = Player(
        id = id,
        firstName = firstName,
        lastName = lastName,
        rapidElo = rapidElo,
        blitzElo = blitzElo,
        bulletElo = bulletElo,
        classicalElo = classicalElo
    )

    fun aGame(
        whitePlayerId: String,
        blackPlayerId: String,
        id: String? = null,
        whitePlayerName: String = "",
        blackPlayerName: String = "",
        gameType: GameType = GameType.RAPID,
        result: GameResult = GameResult.WHITE_WINS,
        time: String = "10+0",
        date: LocalDate = LocalDate.of(2024, 1, 15),
        pgn: String = "1. e4 e5 2. Nf3 Nc6"
    ): Game = Game(
        id = id,
        whitePlayerId = whitePlayerId,
        blackPlayerId = blackPlayerId,
        whitePlayerName = whitePlayerName,
        blackPlayerName = blackPlayerName,
        whitePlayerRating = 2850,
        blackPlayerRating = 2800,
        gameType = gameType,
        result = result,
        time = time,
        date = date,
        pgn = pgn
    )

    /** Overload taking saved players; fails loudly if they have no id instead of using `!!`. */
    fun aGame(white: Player, black: Player): Game = aGame(
        whitePlayerId = requireNotNull(white.id) { "white player must be persisted" },
        blackPlayerId = requireNotNull(black.id) { "black player must be persisted" }
    )

    /** Request body for creating a game; the service fills in names and rating snapshots. */
    fun aGameRequest(
        whitePlayerId: String,
        blackPlayerId: String,
        gameType: GameType = GameType.RAPID,
        result: GameResult = GameResult.WHITE_WINS,
        time: String = "10+0",
        date: LocalDate = LocalDate.of(2024, 1, 15),
        pgn: String? = "1. e4 e5 2. Nf3 Nc6"
    ): GameCreateRequest = GameCreateRequest(
        whitePlayerId = whitePlayerId,
        blackPlayerId = blackPlayerId,
        gameType = gameType,
        result = result,
        time = time,
        date = date,
        pgn = pgn
    )
}
