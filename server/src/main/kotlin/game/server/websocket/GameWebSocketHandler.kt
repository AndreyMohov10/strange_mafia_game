package game.server.websocket

import game.domain.GameId
import game.server.GameLobby
import game.server.GameStateRepository
import game.server.Games
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

@Component
class GameWebSocketHandler(
    private val games: Games,
    private val lobby: GameLobby,
    private val database: GameStateRepository,
    private val scope: CoroutineScope
) : TextWebSocketHandler() {

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        encodeDefaults = true
        prettyPrint = false
    }

    private val sessionJobs = ConcurrentHashMap<String, Job>()
    private val sessionDecorators = ConcurrentHashMap<String, WebSocketSession>()

    private fun extractGameId(session: WebSocketSession): GameId? {
        val path = session.uri?.path ?: return null
        val idStr = path.substringAfterLast("/").trim()
        return if (idStr.isNotEmpty()) GameId(idStr) else null
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val gameId = extractGameId(session) ?: run {
            session.close(CloseStatus.BAD_DATA)
            return
        }

        val safeSession = ConcurrentWebSocketSessionDecorator(session, 10000, 64 * 1024)
        sessionDecorators[session.id] = safeSession

        val job = scope.launch {
            try {
                lobby.awaitStart(gameId)
                val game = games.games[gameId] ?: run {
                    val savedState = database.loadGame(gameId)
                    if (savedState != null) {
                        games.Game(savedState)
                    } else {
                        null
                    }
                }

                if (game == null) {
                    safeSession.close(CloseStatus.NOT_ACCEPTABLE)
                    return@launch
                }

                val stateSenderJob = CompletableDeferred<Unit>()
                val subscriberJob = launch {
                    game.createSubscriber({ state ->
                        launch {
                            try {
                                val text = json.encodeToString(state)
                                if (safeSession.isOpen) {
                                    safeSession.sendMessage(TextMessage(text))
                                }
                                stateSenderJob.complete(Unit)
                            } catch (_: Exception) {
                                this.cancel()
                            }
                        }
                    }) { event ->
                        try {
                            stateSenderJob.await()
                            val text = json.encodeToString(event)
                            if (safeSession.isOpen) {
                                safeSession.sendMessage(TextMessage(text))
                            }
                        } catch (_: Exception) {
                            this.cancel()
                        }
                    }
                }

                stateSenderJob.await()

                try {
                    while (game.running && safeSession.isOpen && isActive) {
                        kotlinx.coroutines.delay(1000)
                    }
                } finally {
                    subscriberJob.cancelAndJoin()
                }
            } catch (_: Exception) {
            } finally {
                if (safeSession.isOpen) {
                    try {
                        safeSession.close()
                    } catch (_: Exception) {}
                }
            }
        }

        sessionJobs[session.id] = job
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val gameId = extractGameId(session) ?: return
        val game = games.games[gameId] ?: return
        try {
            val payload = message.payload
            val (userId, answer) = json.decodeFromString<Pair<String, String>>(payload)
            scope.launch {
                game.userAnswer(userId, answer)
            }
        } catch (_: Exception) {
        }
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        cleanUpSession(session.id)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        cleanUpSession(session.id)
    }

    private fun cleanUpSession(sessionId: String) {
        sessionJobs.remove(sessionId)?.cancel()
        sessionDecorators.remove(sessionId)
    }
}
