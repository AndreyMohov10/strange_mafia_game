package game.client

import game.domain.DisconnectEvent
import game.domain.Event
import game.domain.GameId
import game.domain.GameState
import game.domain.Message
import game.domain.MessageEvent
import game.domain.Phase
import game.domain.Role
import game.helpers.ansValidator
import game.helpers.description
import game.helpers.getSecret
import game.helpers.getString
import game.helpers.iteratePhase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.generics.TelegramClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class TelegramBot(botToken: String, private val client: Client) :
    LongPollingSingleThreadUpdateConsumer {
    private val telegramClient: TelegramClient = OkHttpTelegramClient(botToken)
    private val games: ConcurrentMap<GameId, GameBot> = ConcurrentHashMap()
    private val gamesId: ConcurrentMap<String, GameId> = ConcurrentHashMap()
    private val scope = CoroutineScope(Dispatchers.IO)

    inner class GameBot(
        private val state: GameState, private val gameId: GameId,
        private val ids: List<Long>
    ) {
        private val channel: Channel<Event> = Channel(10)
        private val cancelChannel: Channel<Unit> = Channel(10)
        private val responseChannel: Channel<Pair<Long, String>> = Channel(10)


        suspend fun updateState(event: Event) {
            channel.send(event)
            cancelChannel.send(Unit)
        }

        fun checkIfValidResponse(message: Pair<Long, String>): Boolean {
            return message.first.toString() == state.playersId[state.player]
        }

        suspend fun sendResponse(message: Pair<Long, String>): Boolean {
            if (checkIfValidResponse(message)) {
                responseChannel.send(message)
                return true
            }
            return false
        }

        private fun sendMessageToUser(message: Message, i: Int): String? {
            if (message.userNum < 0 && state.player != -message.userNum - 1) return null
            if (message.secret && !state.roles[i].getSecret()) return null
            if (message.userNum <= 0) {
                return message.string
            }
            return "игрок${message.userNum}: ${message.string}"
        }


        @OptIn(ExperimentalCoroutinesApi::class)
        suspend fun updater() {
            try {
                while (state.day < state.config.artifacts) {
                    do {
                        if (state.playersId[state.player].toLongOrNull() !in ids) {
                            break
                        }
                        val longId = state.playersId[state.player].toLong()
                        val deferred = CompletableDeferred<String>()
                        val job = CoroutineScope(Dispatchers.IO).launch {
                            val message = createMessage(state, state.playersId[state.player].toLong())
                            telegramClient.execute(message)
                            var response: Pair<Long, String>
                            do {
                                response = responseChannel.receive()
                            } while (!checkIfValidResponse(response))

                            var validationError = state.ansValidator(response.second)
                            while (validationError != null) {
                                val errorMessage = validationError.message ?: "Неправильный формат ввода"
                                telegramClient.execute(
                                    SendMessage
                                        .builder()
                                        .chatId(longId)
                                        .text("Ошибка: $errorMessage\nПопробуй еще раз:")
                                        .build()
                                )
                                telegramClient.execute(message)
                                do {
                                    response = responseChannel.receive()
                                } while (!checkIfValidResponse(response))
                                validationError = state.ansValidator(response.second)
                            }
                            deferred.complete(response.second)
                        }
                        val res = select {
                            deferred.onJoin {
                                deferred.getCompleted()
                            }

                            cancelChannel.onReceive {
                                job.cancelAndJoin()
                                deferred.cancelAndJoin()
                                null
                            }
                        }
                        if (res != null) {
                            client.sendAnswer(gameId, longId, res)
                        }
                    } while (false)
                    val event = channel.receive()
                    cancelChannel.receive()
                    for (i in 0..<state.config.playersNum) {
                        val id = state.playersId[i]
                        if (id.toLongOrNull() !in ids) continue
                        val longId = id.toLong()
                        if (state.playersId[state.player] == id && event is MessageEvent) {
                            continue
                        }
                        val message = when (event) {
                            is MessageEvent -> {
                                if (event.message.secret && !state.roles[i].getSecret()) null
                                else {
                                    val str = sendMessageToUser(event.message, i) ?: continue
                                    SendMessage
                                        .builder()
                                        .chatId(longId)
                                        .text(str)
                                        .build()
                                }
                            }

                            is DisconnectEvent -> {
                                SendMessage
                                    .builder()
                                    .chatId(longId)
                                    .text("игрок ${state.player + 1} отключен")
                                    .build()
                            }
                        }
                        if (message != null) {
                            telegramClient.execute(
                                message
                            )
                        }
                    }

                    when (event) {
                        is DisconnectEvent -> {
                            state.playersId[state.player] =
                                "bot_${state.playersId.filter { it.startsWith("bot") }.size}"
                            gamesId.remove(state.playersId[state.player])
                            continue
                        }

                        is MessageEvent -> {
                            if (event.message.userNum == 0) {
                                continue
                            }
                            state.iteratePhase(event.message.string, event.seed)
                        }
                    }

                }
                games.remove(gameId)
            } finally {
                channel.close()
                cancelChannel.close()
                responseChannel.close()
            }
        }
    }

    private fun getDescription(): String {
        return "роли: ${Role.entries.joinToString { it.getString() }}\n" +
                "стадии: ${Phase.entries.joinToString { "${it.getString()}: ${it.description()}" }}\n"
    }

    override fun consume(update: Update) {
        if (update.hasMessage() && update.message.hasText()) {
            val messageText = update.message.text
            val chatId = update.message.chatId
            val gameId = gamesId[chatId.toString()]

            if (messageText == "/start") {
                telegramClient.execute(
                    SendMessage
                        .builder()
                        .chatId(chatId)
                        .parseMode("Markdown")
                        .text(
                            "Привет! Это игра *Странная мафия*.\n\n" +
                            "Команды:\n" +
                            "• `/create` - создать новую игру и получить ID для друзей\n" +
                            "• `/join <ID>` - присоединиться к игре друзей по ID\n" +
                            "• `/join` - присоединиться к случайной открытой игре\n" +
                            "• `/help` - узнать правила игры"
                        )
                        .build()
                )
                return
            }

            if (messageText == "/help") {
                telegramClient.execute(
                    SendMessage
                        .builder()
                        .chatId(chatId)
                        .text(getDescription())
                        .build()
                )
                return
            }

            if (gameId != null) {
                val gameBot = games[gameId]
                runBlocking {
                    gameBot?.sendResponse(chatId to messageText)
                }
                return
            }

            val onStateGet: (GameId, GameState, List<Long>) -> Unit = { id, state, ids ->
                val game = GameBot(state, id, ids)
                games[id] = game
                gamesId[chatId.toString()] = id
                scope.launch {
                    game.updater()
                }
            }

            val serverIdRes = client.createOrJoin(update.message!!, onStateGet) { id, event ->
                scope.launch { games[id]?.updateState(event) }
            }

            val serverId = serverIdRes.getOrElse { e ->
                telegramClient.execute(
                    SendMessage
                        .builder()
                        .chatId(chatId)
                        .text(e.message ?: "Ошибка подключения к игре")
                        .build()
                )
                return
            }
            gamesId[chatId.toString()] = serverId

            val isCreate = messageText.trim().equals("/create", ignoreCase = true)
            val successMessage = if (isCreate) {
                "*Игра успешно создана!*\n\n" +
                "ID твоей игры: `${serverId.id}`\n\n" +
                "Отправь этот ID друзьям. Они смогут подключиться командой:\n" +
                "`/join ${serverId.id}`\n\n" +
                "Ожидаем подключения остальных игроков..."
            } else {
                "*Успешно подключились к игре!*\nID игры: `${serverId.id}`\nОжидаем старта игры..."
            }

            telegramClient.execute(
                SendMessage
                    .builder()
                    .chatId(chatId)
                    .parseMode("Markdown")
                    .text(successMessage)
                    .build()
            )
        }
    }
}
