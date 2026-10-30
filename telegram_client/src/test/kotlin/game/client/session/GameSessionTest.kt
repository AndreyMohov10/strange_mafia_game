package game.client.session

import game.client.api.GameApiClient
import game.client.api.GameWebSocketClient
import game.domain.Event
import game.domain.EventNotification
import game.domain.GameId
import game.domain.GameState
import game.domain.Message
import game.domain.MessageEvent
import game.domain.Phase
import game.domain.Role
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameSessionTest {

    private class RecordingListener : GameSessionListener {
        var startedState: GameState? = null
        val turns = mutableListOf<Long>()
        val events = mutableListOf<Event>()
        var finished: Boolean = false

        override fun onGameStarted(gameId: GameId, state: GameState, userIds: List<Long>) {
            startedState = state
        }

        override fun onTurnRequired(gameId: GameId, state: GameState, currentUserId: Long) {
            turns.add(currentUserId)
        }

        override fun onEvent(gameId: GameId, state: GameState, event: Event, userIds: List<Long>) {
            events.add(event)
        }

        override fun onGameFinished(gameId: GameId, state: GameState, userIds: List<Long>) {
            finished = true
        }
    }

    private class StubApiClient(
        var currentState: GameState? = null,
        val stubEvents: MutableList<Event> = mutableListOf()
    ) : GameApiClient {
        override fun createGame(userId: Long): Result<GameId> = Result.success(GameId("G1"))
        override fun joinGame(userId: Long, gameId: GameId?): Result<GameId> = Result.success(GameId("G1"))
        override fun getState(gameId: GameId): GameState? = currentState
        override fun getEvents(gameId: GameId, from: Int, to: Int?): List<Event> {
            return stubEvents.filter { it.eventIndex >= from && (to == null || it.eventIndex <= to) }
        }
        override suspend fun sendAction(gameId: GameId, userId: Long, answer: String): Result<Unit> = Result.success(Unit)
    }

    private class NoOpWebSocketClient : GameWebSocketClient {
        override fun connect(
            gameId: GameId,
            onNotification: (EventNotification) -> Unit,
            onClose: (closeStatus: String) -> Unit
        ): AutoCloseable = AutoCloseable {}
    }

    @Test
    fun `syncStateAndEvents should trigger onGameStarted, onEvent, and onTurnRequired`() = runBlocking {
        val config = game.helpers.convertConfig(
            game.domain.GameConfigWithoutGameId(playersNum = 4, agents = 2, team = 1, artifacts = 5),
            GameId("G1")
        )
        val state = GameState(config, arrayOf("101", "102", "103", "104"), arrayOf(Role.MAFIA_HEAD, Role.AGENT, Role.MAFIA, Role.AGENT), player = 0, phase = Phase.DAY_CONVERSATION)

        val api = StubApiClient(currentState = state)
        val listener = RecordingListener()
        val scope = CoroutineScope(Dispatchers.Unconfined)

        val session = GameSession(GameId("G1"), listOf(101L), api, NoOpWebSocketClient(), listener, scope)

        session.syncStateAndEvents(0)

        assertEquals(state, listener.startedState)
        assertEquals(listOf(101L), listener.turns)

        val msgEvent = MessageEvent(Message("Привет всем", userNum = 1, secret = false), eventIndex = 1)
        api.stubEvents.add(msgEvent)

        session.syncStateAndEvents(1)

        assertEquals(1, listener.events.size)
        assertEquals(msgEvent, listener.events[0])
    }

    @Test
    fun `syncStateAndEvents should trigger onGameFinished when artifacts day reached`() = runBlocking {
        val config = game.helpers.convertConfig(
            game.domain.GameConfigWithoutGameId(playersNum = 4, agents = 2, team = 1, artifacts = 5),
            GameId("G1")
        )
        val endState = GameState(config, arrayOf("101", "102", "103", "104"), arrayOf(Role.MAFIA_HEAD, Role.AGENT, Role.MAFIA, Role.AGENT), day = 5)

        val api = StubApiClient(currentState = endState)
        val listener = RecordingListener()
        val scope = CoroutineScope(Dispatchers.Unconfined)

        val session = GameSession(GameId("G1"), listOf(101L), api, NoOpWebSocketClient(), listener, scope)
        session.syncStateAndEvents(0)

        assertTrue(listener.finished)
    }
}
