package game.server.websocket

import game.domain.EventNotification
import game.domain.GameId
import game.server.GameLobby
import game.server.db.GameStateRepository
import game.server.Games
import game.server.auth.AuthService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.takeWhile
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
    private val authService: AuthService,
    private val scope: CoroutineScope
) : TextWebSocketHandler() {

    private val json = Json {
        ignoreUnknownKeys = true
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

    private fun extractToken(session: WebSocketSession): String? {
        val query = session.uri?.query
        if (!query.isNullOrBlank()) {
            val tokenParam = query.split("&")
                .firstOrNull { it.startsWith("token=") }
                ?.substringAfter("token=")
            if (!tokenParam.isNullOrBlank()) return tokenParam
        }
        return session.handshakeHeaders.getFirst("Authorization")
            ?: session.handshakeHeaders.getFirst("token")
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val gameId = extractGameId(session) ?: run {
            session.close(CloseStatus.BAD_DATA)
            return
        }

        val rawToken = extractToken(session)
        val principal = authService.resolveUser(rawToken)
        if (principal == null) {
            session.close(CloseStatus.POLICY_VIOLATION)
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

                val latest = game.getLatestEventIndex()
                if (latest >= 0 && safeSession.isOpen) {
                    val initialNotification = EventNotification(
                        gameId = gameId.id,
                        eventIndex = latest
                    )
                    safeSession.sendMessage(TextMessage(json.encodeToString(initialNotification)))
                }

                game.flow.takeWhile { isActive && safeSession.isOpen }.collect { event ->
                    try {
                        val notification = EventNotification(
                            gameId = gameId.id,
                            eventIndex = event.eventIndex
                        )
                        val text = json.encodeToString(notification)
                        if (safeSession.isOpen) {
                            safeSession.sendMessage(TextMessage(text))
                        }
                    } catch (_: Exception) {
                        cancel()
                    }
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
