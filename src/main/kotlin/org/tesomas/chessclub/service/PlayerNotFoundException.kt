package org.tesomas.chessclub.service

class PlayerNotFoundException(playerId: String) : RuntimeException("Player $playerId not found")
