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
import game.domain.Role
import game.helpers.aiPrompt
import game.helpers.ansValidator
import game.helpers.clone
import game.helpers.getAiDescription
import game.helpers.getSecret
import game.helpers.iteratePhase
import game.helpers.mergeMessagesForPrompt
import game.helpers.randomChoice
import game.server.db.GameEventRepository
import game.server.db.GameStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.random.Random

@Service
class Games(
    @Qualifier("applicationCoroutineScope") parentScope: CoroutineScope,
    private val chatCreator: (Int) -> Chat,
    private val database: GameStateRepository,
    private val eventRepository: GameEventRepository
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
            replay = 0, extraBufferCapacity = 50, onBufferOverflow = BufferOverflow.SUSPEND
        )

        private val eventCounter = AtomicInteger(0)
        private val events = CopyOnWriteArrayList<Event>()

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
            botsIndexRemembrance = Array(state.config.playersNum) { 0 }
            val existingEvents = eventRepository.getEvents(state.config.gameId)
            events.addAll(existingEvents)
            val nextIndex = existingEvents.maxOfOrNull { it.eventIndex }?.plus(1) ?: 0
            eventCounter.set(nextIndex)
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
            botsIndexRemembrance = Array(state.config.playersNum) { 0 }
        }

        fun getLatestEventIndex(): Int = eventCounter.get() - 1

        fun makeAction(userId: String, ans: String): Boolean {
            val player = players[userId] ?: return false
            return player.send(ans)
        }

        fun getState(): GameState {
            return stateChangingLock.withLock {
                state.clone()
            }
        }

        fun getEvents(from: Int = 0, to: Int? = null, userId: String, isRoot: Boolean): List<Event> {
            val allEvents = if (events.isNotEmpty() && events.first().eventIndex <= from) {
                events.toList()
            } else {
                val dbEvents = eventRepository.getEvents(state.config.gameId, from, to)
                dbEvents.ifEmpty { events.toList() }
            }

            val inRange = allEvents.filter { event ->
                event.eventIndex >= from && (to == null || event.eventIndex <= to)
            }

            if (isRoot) {
                return inRange
            }

            val playerIndex = state.playersId.indexOf(userId)
            val canSeeSecret = playerIndex >= 0 && state.roles[playerIndex].getSecret()

            return inRange.filter { event ->
                when (event) {
                    is MessageEvent -> {
                        val msg = event.message
                        if (msg.userNum < 0 && msg.userNum != -playerIndex - 1) {
                            false
                        } else if (msg.secret && !canSeeSecret) {
                            false
                        } else {
                            true
                        }
                    }
                    is DisconnectEvent -> true
                }
            }
        }

        private fun getAllMessages(): List<Message> {
            return events.filterIsInstance<MessageEvent>().map { it.message }
        }

        private suspend fun askPlayer(id: String, i: Int): String? {
            val ans = players[id]!!.receive(state.config.turnTimeout)
            if (ans == null) {
                stateChangingLock.withLock {
                    state.playersId[i] = "bot_${bots.getAndIncrement()}"
                    players.remove(id)?.close()
                    database.updateGameStatePlayersId(state)
                }
                val disconnectEvent = DisconnectEvent(
                    index = i,
                    eventIndex = eventCounter.getAndIncrement()
                )
                eventRepository.saveEvent(state.config.gameId, disconnectEvent)
                events.add(disconnectEvent)
                flow.emit(disconnectEvent)
            }
            return ans
        }

        suspend fun start() {
            try {
                if (events.isEmpty()) {
                    stateChangingLock.withLock {
                        database.createGame(state)
                    }
                    val initialMessage = Message("игра начинается", 0, false)
                    val initialEvent = MessageEvent(
                        message = initialMessage,
                        seed = 0,
                        messageLength = 0,
                        eventIndex = eventCounter.getAndIncrement()
                    )
                    eventRepository.saveEvent(state.config.gameId, initialEvent)
                    events.add(initialEvent)
                    flow.emit(initialEvent)
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
                            chat.answerMessage(state.getAiDescription(role))
                            botsChats[index] = chat
                        }
                        val allMessages = getAllMessages()
                        val prompt = state.mergeMessagesForPrompt(
                            allMessages.drop(botsIndexRemembrance[index]), role.getSecret()
                        )
                        var ans: ChatResponse? = null
                        for (i in 0..2) {
                            ans = botsChats[index]!!.answerMessage(
                                state.aiPrompt(prompt)
                            )
                            if (ans != null) {
                                if (state.ansValidator(ans.message) != null) {
                                    ans = null
                                } else {
                                    break
                                }
                            }
                        }
                        str = ans?.message ?: state.randomChoice()

                        botsIndexRemembrance[index] = allMessages.size + if (ans == null) 0 else 1
                    } else {
                        str = askPlayer(id, state.player) ?: if (bots.get() == state.config.playersNum - 1) {
                            return
                        } else {
                            continue
                        }
                    }

                    val newMessages: List<Message>
                    stateChangingLock.withLock {
                        newMessages = state.iteratePhase(str, seed)
                        database.updateGameState(state)
                    }
                    for (msg in newMessages) {
                        val messageEvent = MessageEvent(
                            message = msg,
                            seed = seed,
                            messageLength = events.size,
                            eventIndex = eventCounter.getAndIncrement()
                        )
                        eventRepository.saveEvent(state.config.gameId, messageEvent)
                        events.add(messageEvent)
                        flow.emit(messageEvent)
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