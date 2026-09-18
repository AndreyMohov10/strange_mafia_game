package game.server

import bot.chat.Chat
import game.domain.Event
import game.helpers.convertConfig
import game.domain.GameConfigWithoutGameId
import game.domain.GameId
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.application.*
import io.ktor.server.request.receiveText
import io.ktor.server.websocket.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json


object Server {
    fun runServer(
        port: Int,
        database: GameStateRepository,
        chatCreator: (Int) -> Chat,
    ): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
        return embeddedServer(Netty, port) {
            install(WebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(
                    Json {
                        ignoreUnknownKeys = true
                        classDiscriminator = "type"
                        encodeDefaults = true
                        prettyPrint = false
                    }
                )
            }
            val games = Games(this, chatCreator, database)
            val lobby = GameLobby(this, games)
            routing {
                post("/join") {
                    val parameters = call.queryParameters
                    val userId = parameters["userId"]
                    val gameId = parameters["gameId"]
                    if (userId == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            "должен быть указан userId"
                        )
                        return@post
                    }
                    if (gameId != null) {
                        val res = lobby.joinGame(userId, GameId(gameId))
                        if (res) {
                            call.respond(HttpStatusCode.OK)
                        } else {
                            call.respond(HttpStatusCode.NotFound)
                        }
                    } else {
                        val res = lobby.joinGame(userId)
                        if (res != null) {
                            call.respondText(res.id)
                        } else {
                            call.respond(HttpStatusCode.NotFound)
                        }
                    }
                }

                post("/create") {
                    val parameters = call.queryParameters
                    val userId = parameters["userId"]
                    if (userId == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            "должен быть указан userId"
                        )
                        return@post
                    }
                    val content = this.call.receiveText()
                    val config = if (content.isNotEmpty()) Json.decodeFromString(content)
                        else GameConfigWithoutGameId()
                    val id = lobby.createGame(
                        userId
                    ) { id ->
                        convertConfig(config, id)
                    }
                    call.respond(HttpStatusCode.OK, id)
                }

                webSocket("/game/{gameId}") {
                    val gameId = call.parameters["gameId"] ?: return@webSocket
                    lobby.awaitStart(GameId(gameId))
                    val game = games.games[GameId(gameId)] ?: run {
                        val gameState1 = database.loadGame(GameId(gameId))
                        if (gameState1 != null) {
                            games.Game(gameState1)
                        } else {
                            null
                        }
                    }
                    if (game == null) return@webSocket

                    val socket = this
                    val stateSenderJob = CompletableDeferred<Nothing?>()
                    val job = launch {
                        game.createSubscriber({ state ->
                            launch {
                                try {
                                    socket.sendSerialized(state)
                                    stateSenderJob.complete(
                                        null
                                    )
                                } catch (_: Exception) {
                                    this.cancel()
                                }
                            }
                        }) { event ->
                            try {
                                stateSenderJob.await()
                                socket.sendSerialized(event)
                            } catch (_: Exception) {
                                this.cancel()
                            }
                        }
                    }
                    stateSenderJob.await()

                    while (game.running && isActive) {
                        val resp = withTimeoutOrNull(30000) {
                            socket.receiveDeserialized<Pair<String, String>>()
                        } ?: continue
                        game.userAnswer(resp.first, resp.second)
                    }
                    job.cancelAndJoin()
                }

                get("/") {
                    call.respond("Привет")
                }
            }
        }.start(wait = true)
    }
}
