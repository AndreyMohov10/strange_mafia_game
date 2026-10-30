package game.client.telegram.handler

import game.client.api.GameApiClient
import game.client.api.GameWebSocketClient
import game.client.presentation.GameMessageFormatter
import game.client.session.GameSession
import game.client.session.GameSessionListener
import game.client.telegram.sender.TelegramMessageSender
import game.domain.Event
import game.domain.EventNotification
import game.domain.GameId
import game.domain.GameState
import game.domain.Phase
import game.domain.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InGameActionHandlerTest {

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
        var lastActionAnswer: String? = null

        override fun createGame(userId: Long): Result<GameId> = Result.success(GameId("G1"))
        override fun joinGame(userId: Long, gameId: GameId?): Result<GameId> = Result.success(GameId("G1"))
        override fun getState(gameId: GameId): GameState? = null
        override fun getEvents(gameId: GameId, from: Int, to: Int?): List<Event> = emptyList()
        override suspend fun sendAction(gameId: GameId, userId: Long, answer: String): Result<Unit> {
            lastActionAnswer = answer
            return Result.success(Unit)
        }
    }

    private class FakeWebSocketClient : GameWebSocketClient {
        override fun connect(
            gameId: GameId,
            onNotification: (EventNotification) -> Unit,
            onClose: (closeStatus: String) -> Unit
        ): AutoCloseable = AutoCloseable {}
    }

    private class NoOpListener : GameSessionListener {
        override fun onGameStarted(gameId: GameId, state: GameState, userIds: List<Long>) {}
        override fun onTurnRequired(gameId: GameId, state: GameState, currentUserId: Long) {}
        override fun onEvent(gameId: GameId, state: GameState, event: Event, userIds: List<Long>) {}
        override fun onGameFinished(gameId: GameId, state: GameState, userIds: List<Long>) {}
    }

    @Test
    fun `should reject input when not player turn`() {
        val sender = FakeMessageSender()
        val formatter = GameMessageFormatter()
        val apiClient = FakeGameApiClient()
        val wsClient = FakeWebSocketClient()
        val scope = CoroutineScope(Dispatchers.Unconfined)

        val session = GameSession(GameId("G1"), listOf(101L, 102L), apiClient, wsClient, NoOpListener(), scope)
        val handler = InGameActionHandler(formatter, sender, scope)

        handler.handleInGameMessage(102L, "hello", session)
        assertEquals(1, sender.sentMessages.size)
        assertTrue(sender.sentMessages[0].second.contains("Сейчас не твой ход"))
    }

    @Test
    fun `should validate input and ask again on invalid format`() = runBlocking {
        val sender = FakeMessageSender()
        val formatter = GameMessageFormatter()
        val apiClient = object : GameApiClient {
            var sent: String? = null
            val config = game.helpers.convertConfig(
                game.domain.GameConfigWithoutGameId(playersNum = 4, agents = 2, team = 1, artifacts = 5),
                GameId("G1")
            )
            val st = GameState(config, arrayOf("101", "102", "103", "104"), arrayOf(Role.MAFIA_HEAD, Role.AGENT, Role.MAFIA, Role.AGENT), phase = Phase.TEAM_CREATING, player = 0)

            override fun createGame(userId: Long): Result<GameId> = Result.success(GameId("G1"))
            override fun joinGame(userId: Long, gameId: GameId?): Result<GameId> = Result.success(GameId("G1"))
            override fun getState(gameId: GameId): GameState = st
            override fun getEvents(gameId: GameId, from: Int, to: Int?): List<Event> = emptyList()
            override suspend fun sendAction(gameId: GameId, userId: Long, answer: String): Result<Unit> {
                sent = answer
                return Result.success(Unit)
            }
        }
        val wsClient = FakeWebSocketClient()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val session = GameSession(GameId("G1"), listOf(101L), apiClient, wsClient, NoOpListener(), scope)
        session.syncStateAndEvents(0)

        val handler = InGameActionHandler(formatter, sender, scope)

        handler.handleInGameMessage(101L, "invalid-input", session)

        assertTrue(sender.sentMessages.any { it.second.contains("Ошибка") })
        assertTrue(sender.sentForceReplies.isNotEmpty())
        assertEquals(null, apiClient.sent)

        sender.sentMessages.clear()
        sender.sentForceReplies.clear()
        handler.handleInGameMessage(101L, "2", session)

        assertEquals("2", apiClient.sent)
    }
}
