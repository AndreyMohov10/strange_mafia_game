package game.client.telegram.handler

import game.client.api.GameApiClient
import game.client.api.GameWebSocketClient
import game.client.presentation.GameMessageFormatter
import game.client.session.GameSessionManager
import game.client.telegram.bridge.TelegramGameBridge
import game.client.telegram.sender.TelegramMessageSender
import game.domain.Event
import game.domain.EventNotification
import game.domain.GameId
import game.domain.GameState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TelegramCommandHandlerTest {

    private class FakeMessageSender : TelegramMessageSender {
        val sentMessages = mutableListOf<Pair<Long, String>>()
        val sentForceReplies = mutableListOf<Pair<Long, String>>()

        override fun sendMessage(chatId: Long, text: String, parseMode: String?) {
            sentMessages.add(chatId to text)
        }

        override fun sendForceReply(chatId: Long, text: String, placeholder: String) {
            sentForceReplies.add(chatId to text)
        }
    }

    private class FakeGameApiClient : GameApiClient {
        var createResult: Result<GameId> = Result.success(GameId("ROOM42"))
        var joinResult: Result<GameId> = Result.success(GameId("ROOM42"))

        override fun createGame(userId: Long): Result<GameId> = createResult
        override fun joinGame(userId: Long, gameId: GameId?): Result<GameId> = joinResult
        override fun getState(gameId: GameId): GameState? = null
        override fun getEvents(gameId: GameId, from: Int, to: Int?): List<Event> = emptyList()
        override suspend fun sendAction(gameId: GameId, userId: Long, answer: String): Result<Unit> = Result.success(Unit)
    }

    private class FakeWebSocketClient : GameWebSocketClient {
        override fun connect(
            gameId: GameId,
            onNotification: (EventNotification) -> Unit,
            onClose: (closeStatus: String) -> Unit
        ): AutoCloseable = AutoCloseable {}
    }

    @Test
    fun `handleStart should send welcome message`() {
        val sender = FakeMessageSender()
        val formatter = GameMessageFormatter()
        val apiClient = FakeGameApiClient()
        val wsClient = FakeWebSocketClient()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val manager = GameSessionManager(apiClient, wsClient, scope)
        val bridge = TelegramGameBridge(sender, formatter)
        val handler = TelegramCommandHandler(manager, formatter, sender, bridge)

        handler.handleStart(12345L)

        assertEquals(1, sender.sentMessages.size)
        assertEquals(12345L, sender.sentMessages[0].first)
        assertTrue(sender.sentMessages[0].second.contains("Странная мафия"))
    }

    @Test
    fun `handleCreate should create game and send formatted response`() {
        val sender = FakeMessageSender()
        val formatter = GameMessageFormatter()
        val apiClient = FakeGameApiClient()
        val wsClient = FakeWebSocketClient()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val manager = GameSessionManager(apiClient, wsClient, scope)
        val bridge = TelegramGameBridge(sender, formatter)
        val handler = TelegramCommandHandler(manager, formatter, sender, bridge)

        handler.handleCreate(12345L)

        assertEquals(1, sender.sentMessages.size)
        assertTrue(sender.sentMessages[0].second.contains("ROOM42"))
        assertEquals(GameId("ROOM42"), manager.getGameIdForUser(12345L))
    }

    @Test
    fun `handleCreate when user already in game should warn user`() {
        val sender = FakeMessageSender()
        val formatter = GameMessageFormatter()
        val apiClient = FakeGameApiClient()
        val wsClient = FakeWebSocketClient()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val manager = GameSessionManager(apiClient, wsClient, scope)
        val bridge = TelegramGameBridge(sender, formatter)
        val handler = TelegramCommandHandler(manager, formatter, sender, bridge)

        handler.handleCreate(12345L)
        sender.sentMessages.clear()

        handler.handleCreate(12345L)

        assertEquals(1, sender.sentMessages.size)
        assertTrue(sender.sentMessages[0].second.contains("уже участвуешь"))
    }

    @Test
    fun `handleJoin should join game and notify other players`() {
        val sender = FakeMessageSender()
        val formatter = GameMessageFormatter()
        val apiClient = FakeGameApiClient()
        val wsClient = FakeWebSocketClient()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val manager = GameSessionManager(apiClient, wsClient, scope)
        val bridge = TelegramGameBridge(sender, formatter)
        val handler = TelegramCommandHandler(manager, formatter, sender, bridge)

        handler.handleCreate(101L)
        sender.sentMessages.clear()

        handler.handleJoin(102L, "ROOM42")

        val p2Message = sender.sentMessages.find { it.first == 102L }
        val p1Message = sender.sentMessages.find { it.first == 101L }

        assertTrue(p2Message != null && p2Message.second.contains("Успешно подключились"))
        assertTrue(p1Message != null && p1Message.second.contains("Новый игрок подключился"))
    }
}
