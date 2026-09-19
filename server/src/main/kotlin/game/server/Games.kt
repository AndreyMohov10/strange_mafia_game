package game.server

import bot.chat.Chat
import bot.chat.ChatResponse
import game.domain.DisconnectEvent
import game.domain.Event
import game.domain.GameConfig
import game.domain.GameId
import game.domain.GameState
import game.domain.Message
import game.domain.MessageEvent
import game.domain.Phase
import game.domain.Role
import game.helpers.ansValidator
import game.helpers.clone
import game.helpers.description
import game.helpers.getSecret
import game.helpers.getString
import game.helpers.iteratePhase
import game.helpers.outputFormatDescription
import game.helpers.randomChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.random.Random

@Service
class Games(
    @Qualifier("applicationCoroutineScope") parentScope: CoroutineScope,
    private val chatCreator: (Int) -> Chat,
    private val database: GameStateRepository
) : CoroutineScope by parentScope {
    val games = ConcurrentHashMap<GameId, Game>()

    fun createGame(config: GameConfig, players: Set<String>): Game {
        val game = Game(config, players)
        games[config.gameId] = game
        launch {
            game.start()
        }
        return game
    }

    inner class Game {
        private val state: GameState
        val flow = MutableSharedFlow<Event>(
            replay = 0, extraBufferCapacity = 20, onBufferOverflow = BufferOverflow.SUSPEND
        )

        private val bots: AtomicInteger
        private val botsChats: Array<Chat?>
        private val botsIndexRemembrance: Array<Int>
        private val players: MutableMap<String, Player>

        @Volatile
        var running: Boolean = true
        private val stateChangingLock = ReentrantLock(true)


        constructor(state: GameState) {
            this.state = state
            players = buildMap {
                state.playersId.filter { !it.startsWith("bot") }.forEach {
                    put(it, Player())
                }
            }.toMutableMap()
            bots = AtomicInteger(state.playersId.count { it.startsWith("bot") })
            botsChats = Array(state.config.playersNum) { null }
            botsIndexRemembrance = Array(state.config.playersNum) {
                0
            }
        }

        constructor(config: GameConfig, players: Set<String>) {
            this.players = buildMap {
                players.forEach {
                    put(it, Player())
                }
            }.toMutableMap()
            val playersList = players.toMutableList()
            for (i in 0..<config.playersNum - playersList.size) {
                playersList.add("bot_$i")
            }
            playersList.shuffle()
            val roles = mutableListOf<Role>()
            repeat(config.agents - 1) {
                roles.add(Role.AGENT)
            }
            repeat(config.playersNum - config.agents - 1) {
                roles.add(Role.MAFIA)
            }
            roles.add(Role.MAFIA_HEAD)
            roles.add(Role.AGENT_HEAD)
            roles.shuffle()
            state = GameState(
                config, playersList.toTypedArray(), roles.toTypedArray()
            )
            bots = AtomicInteger(config.playersNum - players.size)
            botsChats = Array(state.config.playersNum) { null }
            botsIndexRemembrance = Array(state.config.playersNum) {
                0
            }
        }

        suspend fun userAnswer(userId: String, ans: String) {
            if (players[userId] == null) return
            players[userId]!!.send { ans }
        }

        suspend fun createSubscriber(onState: (GameState) -> Unit, onEvent: suspend (Event) -> Unit) {
            val channel = Channel<Event>(Channel.UNLIMITED)

            val eventsJob = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    flow.takeWhile { isActive }.collect { event ->
                        channel.send(event)
                    }
                } finally {
                    channel.close()
                }
            }

            try {
                stateChangingLock.withLock {
                    onState(state.clone())
                }

                for (event in channel) {
                    if (!isActive) break
                    onEvent(event)
                }
            } finally {
                eventsJob.cancelAndJoin()
            }
        }

        private fun getDescription(): String {
            return "роли: ${Role.entries.joinToString { it.getString() }}\n" +
                    "стадии: ${Phase.entries.joinToString { "${it.getString()}: ${it.description()}" }}\n" +
                    "конфигурация: ${state.config.description()}"
        }

        private fun getAiDescription(role: Role): String {
            return "ты играешь в игру на подобии мафии твоя роль - ${role.getString()}\n" +
                    "описание игры: ${getDescription()}\n" +
                    "вместо системны будет игрок с номером 0"
        }

        private fun mergeMessages(messages: List<Message>, secret: Boolean): String {
            return if (secret) {
                messages.filter { it.userNum >= 0 || it.userNum == -state.player - 1 }
                    .joinToString(separator = "\n") { "user_${it.userNum}: ${it.string}" }
            } else {
                messages
                    .filter { it.userNum >= 0 || it.userNum == -state.player - 1 }
                    .filter { !it.secret }
                    .joinToString(separator = "\n") { "user_${it.userNum}: ${it.string}" }
            }
        }

        private suspend fun askPlayer(id: String, i: Int): String? {
            val ans = players[id]!!.receive(state.config.turnTimeout)
            if (ans == null) {
                stateChangingLock.withLock {
                    state.playersId[i] = "bot_${bots.getAndIncrement()}"
                    players.remove(id)?.close()
                    database.updateGameStatePlayersId(state)
                }
                flow.emit(DisconnectEvent(i))
            }
            return ans
        }

        private fun getRoles(): String {
            return buildString {
                if (state.roles[state.player].getSecret()) {
                    for (i in 0..<state.config.playersNum) {
                        if (state.alive[i]) {
                            appendLine("Игрок ${i + 1}: ${state.roles[i].getString()}")
                        } else {
                            appendLine("Игрок ${i + 1}: исключен")
                        }
                    }
                } else {
                    (0..<state.config.playersNum).filter { state.roles[it] == Role.MAFIA_HEAD }.forEach {
                        appendLine("Игрок ${it + 1}: ${state.roles[it].getString()}")
                    }
                }
            }
        }

        private fun aiPrompt(prompt: String): String {
            return "предыдущие действия: $prompt\n " +
                    "сейчас идет стадия ${state.phase.getString()}.\n" +
                    "Роли игроков:\n" +
                    getRoles() +
                    "вывод должен быть представлен в виде " +
                    "${state.phase.outputFormatDescription()}\n" +
                    "Твой ход.\n" +
                    "Играть нужно строго за себя и пытаться выдавать себя за мафию(если глава мафии говори прямо)\n" +
                    "Если ты агент ни в коем случае не говори свою роль в отличии от обычной мафии " +
                    "здесь агенты знают друг друга, а мафия агентов нет.\n" +
                    "твоя роль ${state.roles[state.player]}.Не притворяйся главой мафии если ты не он.\n" +
                    "ты игрок ${state.player + 1}\n" +
                    "сообщения днем читают все, ночью - только агенты\n" +
                    "тебе не нужно говорить длинно хватит 1-2 предложений\n" +
                    "если формат не свободный то ты обязан вывести ровно одну строку в требуемом формате\n" +
                    "если ты не соответствуешь формату выбор будет сделан случайно"
        }

        suspend fun start() {
            try {
                if (state.history.isEmpty()) {
                    stateChangingLock.withLock {
                        state.history.add(Message("игра начинается", 0, false))
                        database.createGame(state)
                    }
                    flow.emit(MessageEvent(state.history[0], 0, 0))
                }
                while (state.day < state.config.artifacts) {
                    val id = state.playersId[state.player]
                    val seed = Random.nextLong()
                    val str: String
                    if (id.startsWith("bot")) {
                        val index = id.substringAfter("bot_").toInt()
                        val role = state.roles[state.player]
                        if (botsChats[index] == null) {
                            val chat = chatCreator(state.player)
                            chat.answerMessage(getAiDescription(role))
                            botsChats[index] = chat
                        }
                        val prompt = mergeMessages(
                            state.history.drop(botsIndexRemembrance[index]), role.getSecret()
                        )
                        var ans: ChatResponse? = null
                        for (i in 0..2) {
                            ans = botsChats[index]!!.answerMessage(
                                aiPrompt(prompt)
                            )
                            if (ans != null) {
                                if (!state.ansValidator(ans.message)) {
                                    ans = null
                                } else {
                                    break
                                }
                            }
                        }
                        str = ans?.message ?: state.randomChoice()

                        botsIndexRemembrance[index] = state.history.size + if (ans == null) 0 else 1
                    } else {
                        str = askPlayer(id, state.player) ?: if (bots.get() == state.config.playersNum - 1) {
                            return
                        } else {
                            continue
                        }
                    }
                    val oldSize = state.history.size

                    stateChangingLock.withLock {
                        state.iteratePhase(str, seed)
                        database.updateGameState(state)
                    }
                    for (i in oldSize..<state.history.size) {
                        flow.emit(MessageEvent(state.history[i], seed, i))
                    }
                }
                games.remove(state.config.gameId)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                running = false
            }
        }
    }
}