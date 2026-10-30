package game.client.session

import game.client.api.GameApiClient
import game.client.api.GameWebSocketClient
import game.domain.GameId
import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

@Service
class GameSessionManager(
    private val apiClient: GameApiClient,
    private val wsClient: GameWebSocketClient,
    private val clientScope: CoroutineScope
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val activeGames: ConcurrentMap<GameId, GameSession> = ConcurrentHashMap()
    private val userToGame: ConcurrentMap<Long, GameId> = ConcurrentHashMap()

    fun createGame(userId: Long, listener: GameSessionListener): Result<GameId> {
        val result = apiClient.createGame(userId)
        return result.map { gameId ->
            val session = GameSession(
                gameId = gameId,
                initialUsers = listOf(userId),
                apiClient = apiClient,
                wsClient = wsClient,
                listener = listener,
                parentScope = clientScope
            )
            activeGames[gameId] = session
            userToGame[userId] = gameId
            session.start()
            logger.info("Создана новая игровая сессия $gameId для пользователя $userId")
            gameId
        }
    }

    fun joinGame(userId: Long, gameId: GameId?, listener: GameSessionListener): Result<Pair<GameId, Boolean>> {
        val result = apiClient.joinGame(userId, gameId)
        return result.map { joinedGameId ->
            val existing = activeGames[joinedGameId]
            val isNewSession: Boolean
            if (existing != null) {
                existing.addUser(userId)
                isNewSession = false
            } else {
                val session = GameSession(
                    gameId = joinedGameId,
                    initialUsers = listOf(userId),
                    apiClient = apiClient,
                    wsClient = wsClient,
                    listener = listener,
                    parentScope = clientScope
                )
                activeGames[joinedGameId] = session
                session.start()
                isNewSession = true
            }
            userToGame[userId] = joinedGameId
            logger.info("Пользователь $userId присоединился к игре $joinedGameId (новая сессия: $isNewSession)")
            joinedGameId to isNewSession
        }
    }

    fun getSession(gameId: GameId): GameSession? = activeGames[gameId]

    fun getGameForUser(userId: Long): GameSession? {
        val gameId = userToGame[userId] ?: return null
        return activeGames[gameId]
    }

    fun getGameIdForUser(userId: Long): GameId? = userToGame[userId]

    fun getLobbyUserCount(gameId: GameId): Int {
        return activeGames[gameId]?.getUsers()?.size ?: 1
    }
}
