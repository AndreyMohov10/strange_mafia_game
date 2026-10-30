package game.client.api

import game.domain.ActionRequest
import game.domain.Event
import game.domain.GameId
import game.domain.GameState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class HttpGameApiClient(
    private val restClient: RestClient,
    private val json: Json
) : GameApiClient {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun createGame(userId: Long): Result<GameId> {
        return runCatching {
            val response = restClient.post()
                .uri("/create?userId={userId}", userId)
                .retrieve()
                .toEntity(String::class.java)

            val body = response.body
            if (response.statusCode.is2xxSuccessful && !body.isNullOrBlank()) {
                GameId(body)
            } else {
                throw IllegalStateException("Не удалось создать игру: код ${response.statusCode}")
            }
        }.onFailure { e ->
            logger.error("Ошибка при создании игры для пользователя $userId: ${e.message}")
        }
    }

    override fun joinGame(userId: Long, gameId: GameId?): Result<GameId> {
        return runCatching {
            val response = if (gameId != null) {
                restClient.post()
                    .uri("/join?userId={userId}&gameId={gameId}", userId, gameId.id)
                    .retrieve()
                    .toEntity(String::class.java)
            } else {
                restClient.post()
                    .uri("/join?userId={userId}", userId)
                    .retrieve()
                    .toEntity(String::class.java)
            }

            val body = response.body
            if (response.statusCode.is2xxSuccessful && !body.isNullOrBlank()) {
                GameId(body)
            } else if (response.statusCode.is2xxSuccessful && gameId != null) {
                gameId
            } else {
                throw IllegalStateException("Не удалось присоединиться к игре: код ${response.statusCode}")
            }
        }.onFailure { e ->
            logger.error("Ошибка при присоединении к игре (gameId=$gameId, userId=$userId): ${e.message}")
        }
    }

    override fun getState(gameId: GameId): GameState? {
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
            logger.debug("Состояние игры ${gameId.id} еще недоступно или произошла ошибка: ${e.message}")
            null
        }
    }

    override fun getEvents(gameId: GameId, from: Int, to: Int?): List<Event> {
        return try {
            val uri = if (to != null) {
                "/game/${gameId.id}/events?from=$from&to=$to"
            } else {
                "/game/${gameId.id}/events?from=$from"
            }

            val response = restClient.get()
                .uri(uri)
                .retrieve()
                .toEntity(String::class.java)

            if (response.statusCode.is2xxSuccessful && !response.body.isNullOrBlank()) {
                json.decodeFromString<List<Event>>(response.body!!)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            logger.error("Ошибка при запросе событий [$from..$to] для игры ${gameId.id}: ${e.message}")
            emptyList()
        }
    }

    override suspend fun sendAction(gameId: GameId, userId: Long, answer: String): Result<Unit> {
        return runCatching {
            val request = ActionRequest(answer = answer, userId = userId.toString())
            val response = restClient.post()
                .uri("/game/{gameId}/action", gameId.id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json.encodeToString(request))
                .retrieve()
                .toBodilessEntity()

            if (!response.statusCode.is2xxSuccessful) {
                throw IllegalStateException("Ошибка при отправке действия: статус ${response.statusCode}")
            }
        }.onFailure { e ->
            logger.error("Ошибка при отправке действия для игры ${gameId.id}, userId=$userId: ${e.message}")
        }
    }
}
