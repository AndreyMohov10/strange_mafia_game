package game.client

import game.client.config.TelegramClientProperties
import game.domain.ActionRequest
import game.domain.Event
import game.domain.EventNotification
import game.domain.GameId
import game.domain.GameState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import org.springframework.web.socket.handler.TextWebSocketHandler
import org.telegram.telegrambots.meta.api.objects.message.Message
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Service
class Client(
    properties: TelegramClientProperties
) {
    private val serverUrl = properties.serverUrl
    private val serverToken = properties.serverToken
    private val restClient = RestClient.builder()
        .baseUrl(serverUrl)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $serverToken")
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        encodeDefaults = true
        prettyPrint = false
    }

    private val games = ConcurrentHashMap<GameId, GameSession>()
    private val userGames = ConcurrentHashMap<Long, GameId>()

    inner class GameSession(
        val gameId: GameId,
        val userIds: MutableList<Long>,
        private val onStateGet: (GameId, GameState, List<Long>) -> Unit,
        private val onEvent: (GameId, Event) -> Unit,
    ) : TextWebSocketHandler() {
        private var session: WebSocketSession? = null
        private var state: GameState? = null
        private var lastProcessedEventIndex: Int = -1
        private val fetchLock = Any()

        fun addUser(id: Long) {
            if (!userIds.contains(id)) {
                userIds.add(id)
            }
        }

        fun connect(wsUri: String) {
            val wsClient = StandardWebSocketClient()
            val futureSession = wsClient.execute(this, wsUri)
            val rawSession = futureSession.get()
            session = ConcurrentWebSocketSessionDecorator(rawSession, 10000, 64 * 1024)
        }

        override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
            val payload = message.payload
            try {
                val notification = json.decodeFromString<EventNotification>(payload)
                fetchStateAndEvents(notification.eventIndex)
            } catch (e: Exception) {
                System.err.println("Ошибка при обработке EventNotification: $e")
            }
        }

        private fun fetchState(): GameState? {
            return try {
                val response = restClient.get()
                    .uri("/game/{gameId}/state", gameId.id)
                    .retrieve()
                    .toEntity(String::class.java)
                if (response.statusCode.is2xxSuccessful && !response.body.isNullOrBlank()) {
                    json.decodeFromString<GameState>(response.body!!)
                } else {
                    null
                }
            } catch (e: Exception) {
                System.err.println("Ошибка при запросе GameState для ${gameId.id}: $e")
                null
            }
        }

        private fun fetchEvents(from: Int, to: Int): List<Event> {
            return try {
                val response = restClient.get()
                    .uri("/game/{gameId}/events?from={from}&to={to}", gameId.id, from, to)
                    .retrieve()
                    .toEntity(String::class.java)
                if (response.statusCode.is2xxSuccessful && !response.body.isNullOrBlank()) {
                    json.decodeFromString<List<Event>>(response.body!!)
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                System.err.println("Ошибка при запросе событий [${from}..${to}] для ${gameId.id}: $e")
                emptyList()
            }
        }

        private fun fetchStateAndEvents(targetEventIndex: Int) {
            synchronized(fetchLock) {
                if (state == null) {
                    val localState = fetchState() ?: return
                    state = localState
                    onStateGet(gameId, localState, userIds)
                }

                if (targetEventIndex > lastProcessedEventIndex) {
                    val events = fetchEvents(lastProcessedEventIndex + 1, targetEventIndex)
                    for (event in events) {
                        if (event.eventIndex > lastProcessedEventIndex) {
                            lastProcessedEventIndex = event.eventIndex
                            onEvent(gameId, event)
                        }
                    }
                }
            }
        }

        override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
            System.err.println("WebSocket транспортная ошибка: ${exception.message}")
        }

        override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
            games.remove(gameId)
        }
    }

    fun createOrJoin(
        message: Message,
        onStateGet: (GameId, GameState, List<Long>) -> Unit,
        onEvent: (GameId, Event) -> Unit,
    ): Result<GameId> {
        val chatId = message.chatId
        val text = message.text

        return try {
            val gameId = when (text.lowercase()) {
                "/create" -> {
                    val response = restClient.post()
                        .uri("/create?userId={userId}", chatId)
                        .retrieve()
                        .toEntity(String::class.java)

                    if (response.statusCode.is2xxSuccessful && !response.body.isNullOrBlank()) {
                        GameId(response.body!!)
                    } else {
                        throw RuntimeException("Не удалось создать игру")
                    }
                }

                "/join" -> {
                    val response = restClient.post()
                        .uri("/join?userId={userId}", chatId)
                        .retrieve()
                        .toEntity(String::class.java)

                    if (response.statusCode.is2xxSuccessful && !response.body.isNullOrBlank()) {
                        GameId(response.body!!)
                    } else {
                        throw RuntimeException("Не удалось присоединиться к игре")
                    }
                }

                else -> {
                    if (text.startsWith("/join ")) {
                        val gameIdStr = text.substringAfter("/join ").trim()
                        val response = restClient.post()
                            .uri("/join?userId={userId}&gameId={gameId}", chatId, gameIdStr)
                            .retrieve()
                            .toBodilessEntity()

                        if (response.statusCode.is2xxSuccessful) {
                            GameId(gameIdStr)
                        } else {
                            throw RuntimeException("Игра с ID $gameIdStr не найдена")
                        }
                    } else {
                        throw Exception("Неизвестная команда. Используйте '/create' или '/join <gameId>'")
                    }
                }
            }

            val existingSession = games[gameId]
            if (existingSession != null) {
                existingSession.addUser(chatId)
            } else {
                val cleanUrl = serverUrl.removePrefix("http://").removePrefix("https://")
                val protocol = if (serverUrl.startsWith("https://")) "wss" else "ws"
                val wsUri = "$protocol://$cleanUrl/game/${gameId.id}?token=$serverToken"

                val session = GameSession(gameId, CopyOnWriteArrayList(listOf(chatId)), onStateGet, onEvent)
                session.connect(wsUri)
                games[gameId] = session
                userGames[chatId] = gameId
            }

            Result.success(gameId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendAnswer(gameId: GameId, userId: Long, answer: String?) {
        if (answer == null) return
        try {
            val actionRequest = ActionRequest(answer = answer, userId = userId.toString())
            val response = restClient.post()
                .uri("/game/{gameId}/action", gameId.id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json.encodeToString(actionRequest))
                .retrieve()
                .toBodilessEntity()

            if (!response.statusCode.is2xxSuccessful) {
                System.err.println("Ошибка при отправке действия: ${response.statusCode}")
            }
        } catch (e: Exception) {
            System.err.println("Ошибка при отправке действия через REST API: $e")
        }
    }
}