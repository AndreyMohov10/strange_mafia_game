package game.client.api

import game.domain.Event
import game.domain.GameId
import game.domain.GameState

interface GameApiClient {
    fun createGame(userId: Long): Result<GameId>
    fun joinGame(userId: Long, gameId: GameId? = null): Result<GameId>
    fun getState(gameId: GameId): GameState?
    fun getEvents(gameId: GameId, from: Int, to: Int? = null): List<Event>
    suspend fun sendAction(gameId: GameId, userId: Long, answer: String): Result<Unit>
}
