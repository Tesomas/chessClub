package org.tesomas.chessclub.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "Players")
data class Player(
    @Id val id: String? = null,
    val firstName: String,
    val lastName: String,
    val rapidElo: Int = 1200,
    val blitzElo: Int = 1200,
    val bulletElo: Int = 1200,
    val classicalElo: Int = 1200
)