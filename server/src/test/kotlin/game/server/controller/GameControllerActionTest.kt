package game.server.controller

import bot.chat.Chat
import game.domain.ActionRequest
import game.domain.GameConfigWithoutGameId
import game.domain.GameId
import game.helpers.convertConfig
import game.server.GameLobby
import game.server.Games
import game.server.auth.AuthService
import game.server.db.GameEventRepository
import game.server.db.GameStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpStatus

class GameControllerActionTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val chatCreator: (Int) -> Chat = { Mockito.mock(Chat::class.java) }
    private val database: GameStateRepository = Mockito.mock(GameStateRepository::class.java)
    private val eventRepository: GameEventRepository = Mockito.mock(GameEventRepository::class.java)
    private val games = Games(scope, chatCreator, database, eventRepository)
    private val lobby = GameLobby(scope, games)
    private val dsl: DSLContext = Mockito.mock(DSLContext::class.java)
    private val rootToken = "secret_root"
    private val authService = AuthService(dsl, rootToken)

    private val controller = GameController(lobby, games, database, eventRepository, authService)

    @Test
    fun `makeAction successfully delivers action without NPE`() {
        val gameId = GameId("test_action_game")
        val config = convertConfig(GameConfigWithoutGameId(playersNum = 4, agents = 1, team = 1), gameId)
        val game = games.Game(config, setOf("user_1", "user_2"))
        games.games[gameId] = game

        val request = ActionRequest(answer = "голос за 1", userId = "user_1")
        val body = Json.encodeToString(request)

        val response = runBlocking {
            controller.makeAction(
                gameId = gameId.id,
                authHeader = "Bearer $rootToken",
                token = null,
                body = body
            )
        }

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body!!.contains(""""status": "ok""""))
    }
}
