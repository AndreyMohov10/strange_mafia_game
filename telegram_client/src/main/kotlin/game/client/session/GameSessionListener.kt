package game.client.session

import game.domain.Event
import game.domain.GameId
import game.domain.GameState

interface GameSessionListener {
    fun onGameStarted(gameId: GameId, state: GameState, userIds: List<Long>)
    fun onTurnRequired(gameId: GameId, state: GameState, currentUserId: Long)
    fun onEvent(gameId: GameId, state: GameState, event: Event, userIds: List<Long>)
    fun onGameFinished(gameId: GameId, state: GameState, userIds: List<Long>)
}
