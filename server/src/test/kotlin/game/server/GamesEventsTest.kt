package game.server

import bot.chat.Chat
import game.domain.GameConfigWithoutGameId
import game.domain.GameId
import game.domain.Message
import game.domain.MessageEvent
import game.domain.Role
import game.helpers.convertConfig
import game.server.db.GameEventRepository
import game.server.db.GameStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class GamesEventsTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val chatCreator: (Int) -> Chat = { Mockito.mock(Chat::class.java) }
    private val database: GameStateRepository = Mockito.mock(GameStateRepository::class.java)
    private val eventRepository: GameEventRepository = Mockito.mock(GameEventRepository::class.java)

    private val games = Games(scope, chatCreator, database, eventRepository)

    @Test
    fun `getEvents filters by from and to range correctly`() {
        val config = convertConfig(GameConfigWithoutGameId(playersNum = 4, agents = 1, team = 1), GameId("test_game"))
        val game = games.Game(config, setOf("player1", "player2"))

        val eventsField = game.javaClass.getDeclaredField("events")
        eventsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val eventsList = eventsField.get(game) as java.util.concurrent.CopyOnWriteArrayList<game.domain.Event>

        for (i in 0..10) {
            eventsList.add(
                MessageEvent(
                    message = Message("message $i", i % 2, false),
                    seed = 100L + i,
                    messageLength = i,
                    eventIndex = i
                )
            )
        }

        val rangeRoot = game.getEvents(from = 3, to = 6, userId = "root", isRoot = true)
        assertEquals(4, rangeRoot.size)
        assertEquals(3, rangeRoot.first().eventIndex)
        assertEquals(6, rangeRoot.last().eventIndex)

        val from8 = game.getEvents(from = 8, to = null, userId = "root", isRoot = true)
        assertEquals(3, from8.size)
        assertEquals(8, from8[0].eventIndex)
        assertEquals(9, from8[1].eventIndex)
        assertEquals(10, from8[2].eventIndex)
    }

    @Test
    fun `getEvents respects secret messages based on role and root`() {
        val config = convertConfig(GameConfigWithoutGameId(playersNum = 4, agents = 1, team = 1), GameId("test_secret_game"))
        val game = games.Game(config, setOf("agent_user", "mafia_user"))

        val stateField = game.javaClass.getDeclaredField("state")
        stateField.isAccessible = true
        val state = stateField.get(game) as game.domain.GameState

        state.playersId[0] = "agent_user"
        state.roles[0] = Role.AGENT

        state.playersId[1] = "mafia_user"
        state.roles[1] = Role.MAFIA

        val eventsField = game.javaClass.getDeclaredField("events")
        eventsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val eventsList = eventsField.get(game) as java.util.concurrent.CopyOnWriteArrayList<game.domain.Event>

        eventsList.add(
            MessageEvent(
                message = Message("общий сбор", 0, secret = false),
                seed = 1L,
                messageLength = 0,
                eventIndex = 0
            )
        )
        eventsList.add(
            MessageEvent(
                message = Message("секрет агентов", 1, secret = true),
                seed = 2L,
                messageLength = 1,
                eventIndex = 1
            )
        )

        val rootEvents = game.getEvents(from = 0, to = 10, userId = "root", isRoot = true)
        assertEquals(2, rootEvents.size)

        val agentEvents = game.getEvents(from = 0, to = 10, userId = "agent_user", isRoot = false)
        assertEquals(2, agentEvents.size)

        val mafiaEvents = game.getEvents(from = 0, to = 10, userId = "mafia_user", isRoot = false)
        assertEquals(1, mafiaEvents.size)
        assertEquals(0, mafiaEvents[0].eventIndex)
        assertFalse((mafiaEvents[0] as MessageEvent).message.secret)
    }
}
