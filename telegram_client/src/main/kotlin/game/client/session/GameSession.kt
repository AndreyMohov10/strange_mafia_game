package game.client.session

import game.client.api.GameApiClient
import game.client.api.GameWebSocketClient
import game.domain.GameId
import game.domain.GameState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class GameSession(
    val gameId: GameId,
    initialUsers: Collection<Long>,
    private val apiClient: GameApiClient,
    private val wsClient: GameWebSocketClient,
    private val listener: GameSessionListener,
    parentScope: CoroutineScope
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val userIds: MutableSet<Long> = ConcurrentHashMap.newKeySet<Long>().apply {
        addAll(initialUsers)
    }

    private val sessionJob = SupervisorJob(parentScope.coroutineContext[kotlinx.coroutines.Job])
    private val sessionScope = CoroutineScope(parentScope.coroutineContext + sessionJob)

    @Volatile
    var state: GameState? = null
        private set

    private val lastProcessedEventIndex = AtomicInteger(-1)
    private var wsHandle: AutoCloseable? = null
    private val syncLock = Any()

    fun addUser(userId: Long) {
        val added = userIds.add(userId)
        val currentState = state
        if (added && currentState != null) {
            listener.onGameStarted(gameId, currentState, listOf(userId))
            checkTurn(currentState)
        }
    }

    fun getUsers(): List<Long> = userIds.toList()

    fun isCurrentPlayer(userId: Long): Boolean {
        val s = state ?: return false
        return s.playersId.getOrNull(s.player) == userId.toString()
    }

    fun start() {
        try {
            wsHandle = wsClient.connect(
                gameId = gameId,
                onNotification = { notification ->
                    sessionScope.launch {
                        syncStateAndEvents(notification.eventIndex)
                    }
                },
                onClose = {
                    logger.info("WebSocket соединение для игры ${gameId.id} закрыто")
                }
            )
        } catch (e: Exception) {
            logger.error("Не удалось установить WebSocket соединение для ${gameId.id}: ${e.message}")
        }
    }

    fun syncStateAndEvents(targetEventIndex: Int) {
        synchronized(syncLock) {
            if (state == null) {
                val fetched = apiClient.getState(gameId) ?: return
                state = fetched
                logger.info("Игра ${gameId.id} запущена. Состояние загружено по нотификации.")
                listener.onGameStarted(gameId, fetched, getUsers())
            }

            val from = lastProcessedEventIndex.get() + 1
            if (targetEventIndex >= from) {
                val events = apiClient.getEvents(gameId, from, targetEventIndex)
                for (event in events) {
                    if (event.eventIndex > lastProcessedEventIndex.get()) {
                        lastProcessedEventIndex.set(event.eventIndex)
                        val latestState = apiClient.getState(gameId) ?: state!!
                        state = latestState
                        listener.onEvent(gameId, latestState, event, getUsers())
                    }
                }
            }

            val current = apiClient.getState(gameId) ?: state ?: return
            state = current
            if (current.day >= current.config.artifacts) {
                logger.info("Игра ${gameId.id} завершена!")
                listener.onGameFinished(gameId, current, getUsers())
                close()
            } else {
                checkTurn(current)
            }
        }
    }

    private fun checkTurn(currentState: GameState) {
        val currentTurnUserStr = currentState.playersId.getOrNull(currentState.player)
        val currentLongId = currentTurnUserStr?.toLongOrNull()
        if (currentLongId != null && userIds.contains(currentLongId)) {
            listener.onTurnRequired(gameId, currentState, currentLongId)
        }
    }

    suspend fun submitAction(userId: Long, answer: String): Result<Unit> {
        return apiClient.sendAction(gameId, userId, answer)
    }

    override fun close() {
        sessionJob.cancel()
        try {
            wsHandle?.close()
        } catch (e: Exception) {
            logger.warn("Ошибка при закрытии WebSocket для ${gameId.id}: ${e.message}")
        }
    }
}
