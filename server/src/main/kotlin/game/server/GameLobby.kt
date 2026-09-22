package game.server

import game.domain.GameConfig
import game.domain.GameId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.lang.Exception
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.ConcurrentSkipListSet
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
class GameLobby(
    @Qualifier("applicationCoroutineScope") parentScope: CoroutineScope,
    private val games: Games
) : CoroutineScope by parentScope {
    private val game: ConcurrentMap<GameId, GameStart> = ConcurrentHashMap()

    suspend fun joinGame(userId: String, gameId: GameId): Boolean {
        val gameStarter = game[gameId] ?: return false
        return gameStarter.join(userId)
    }

    suspend fun joinGame(userId: String): GameId? {
        val entry = game.entries.firstOrNull() ?: return null
        if (entry.value.join(userId)) {
            return entry.key
        }
        return null
    }

    fun createGame(userId: String, configBuilder: (GameId) -> GameConfig): String {
        val id = GameId(UUID.randomUUID().toString().replace("-", "").uppercase())
        val gameStarter = GameStart(configBuilder(id), userId)
        game[id] = gameStarter
        launch {
            gameStarter.start()
        }
        return id.id
    }

    suspend fun awaitStart(gameId: GameId) {
        val gameStarter = game[gameId] ?: return
        gameStarter.startDeferred.await()
        return
    }

    private inner class GameStart(
        private val config: GameConfig,
        creatorId: String
    ) {
        val startDeferred = CompletableDeferred<Unit>()
        private val players = ConcurrentSkipListSet<String>()
        private val joinChannel = Channel<Pair<String, CompletableDeferred<Boolean>>>(Channel.UNLIMITED)

        init {
            players.add(creatorId)
        }

        suspend fun start() {
            try {
                repeat(config.playersNum - 1) {
                    withTimeout(config.loggingTimeout) {
                        val (userId, deferred) = joinChannel.receive()
                        if (players.size < config.playersNum) {
                            players.add(userId)
                            deferred.complete(true)
                        } else {
                            deferred.complete(false)
                        }
                    }
                }
                games.createGame(config, players)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (_: TimeoutCancellationException) {
                games.createGame(config, players)
            } finally {
                game.remove(config.gameId)
                joinChannel.close()
                startDeferred.complete(Unit)
            }
        }

        suspend fun join(userId: String): Boolean {
            try {
                if (players.contains(userId)) return false
                val deferred = CompletableDeferred<Boolean>()
                joinChannel.send(userId to deferred)
                return deferred.await()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (_: Exception) {
            }
            return false
        }
    }
}
