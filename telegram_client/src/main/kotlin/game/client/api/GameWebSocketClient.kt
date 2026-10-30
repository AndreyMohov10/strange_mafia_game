package game.client.api

import game.domain.EventNotification
import game.domain.GameId

interface GameWebSocketClient {
    fun connect(
        gameId: GameId,
        onNotification: (EventNotification) -> Unit,
        onClose: (closeStatus: String) -> Unit = {}
    ): AutoCloseable
}
