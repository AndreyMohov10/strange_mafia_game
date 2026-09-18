package game.client

import game.domain.Event
import game.domain.GameId
import game.domain.GameState
import game.domain.MessageEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.close
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.telegram.telegrambots.meta.api.objects.message.Message
import java.util.concurrent.ConcurrentHashMap

class Client(
    private val serverUrl: String = "http://localhost:8080",
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val httpClient = HttpClient(CIO)
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        encodeDefaults = true
        prettyPrint = false
    }
    private val wsClient = HttpClient(CIO) {
        install(WebSockets) {
            contentConverter = io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter(
                json
            )
        }
    }

    private val games = ConcurrentHashMap<GameId, GameSession>()
    private val userGames = ConcurrentHashMap<Long, GameId>()

    inner class GameSession(
        val gameId: GameId,
        val userIds: MutableList<Long>,
        private val onStateGet: (GameId, GameState, List<Long>) -> Unit,
        private val onEvent: (GameId, Event) -> Unit,
        private val session: DefaultClientWebSocketSession,
    ) {
        private var sessionJob: Job? = null
        private var state: GameState? = null

        fun addUser(id: Long) {
            userIds.add(id)
        }

        fun connect() {
            //TODO сделать возможность переподключаться в случае разрыва соединения или пропуска сообщения
            sessionJob = scope.launch {
                try {
                    while (isActive) {
                        withTimeoutOrNull(30000) {
                            val localState = session.receiveDeserialized<GameState>()
                            state = localState
                            onStateGet(gameId, localState, userIds)
                        } ?: continue
                        break
                    }


                    while (isActive) {
                        withTimeoutOrNull(30000) {
                            val event = session.receiveDeserialized<Event>()
                            if (event is MessageEvent && event.messageLength < state!!.history.size) return@withTimeoutOrNull
                            onEvent(gameId, event)
                        }
                    }
                } catch (e: Exception) {
                    print("произошла ошибка $e")
                } finally {
                    session.close()
                }
            }
        }

        suspend fun sendAnswer(answer: String, userId: Long) {
            session.sendSerialized(userId.toString() to answer)
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
            runBlocking {
                try {
                    val gameId = when (text.lowercase()) {
                        //TODO создать возможность пользователю выбирать параметры игры и выводить id игры
                        "/create" -> {
                            val response = httpClient.post("$serverUrl/create?userId=$chatId")
                            if (response.status == HttpStatusCode.OK) {
                                GameId(response.bodyAsText())
                            } else {
                                throw RuntimeException("Не удалось создать игру")
                            }
                        }

                        "/join" -> {
                            val response = httpClient.post("$serverUrl/join?userId=$chatId")
                            if (response.status == HttpStatusCode.OK) {
                                GameId(response.bodyAsText())
                            } else {
                                throw RuntimeException("Не удалось присоединиться к игре")
                            }
                        }

                        else -> {
                            if (text.startsWith("/join ")) {
                                val gameIdStr = text.substringAfter("/join ").trim()
                                val response = httpClient.post("$serverUrl/join?userId=$chatId&gameId=$gameIdStr")
                                if (response.status == HttpStatusCode.OK) {
                                    GameId(response.bodyAsText())
                                } else {
                                    throw RuntimeException("Игра с ID $gameIdStr не найдена")
                                }
                            } else {
                                throw Exception("Неизвестная команда. Используйте '/create' или '/join <gameId>'")
                            }
                        }
                    }
                    val session = games[gameId]
                    if (session != null) {
                        session.addUser(chatId)
                    } else {
                        val webSocketSession = wsClient.webSocketSession("ws${serverUrl.drop(4)}/game/${gameId.id}")
                        val session = GameSession(gameId, mutableListOf(chatId), onStateGet, onEvent, webSocketSession)
                        games[gameId] = session
                        userGames[chatId] = gameId
                        session.connect()
                    }

                    Result.success(gameId)
                } catch (e: RuntimeException) {
                    Result.failure(e)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendAnswer(gameId: GameId, userId: Long, answer: String?) {
        val session = games[gameId]
        answer?.let { session?.sendAnswer(it, userId) }
    }
}