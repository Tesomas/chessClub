package org.tesomas.chessclub.service

/** A game write was rejected because of its content; maps to HTTP 400. The message never echoes the value. */
class InvalidGameException(message: String) : RuntimeException(message)
