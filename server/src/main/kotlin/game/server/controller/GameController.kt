package game.server.controller

import game.domain.GameConfigWithoutGameId
import game.domain.GameId
import game.helpers.convertConfig
import game.server.GameLobby
import kotlinx.serialization.json.Json
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class GameController(
    private val lobby: GameLobby
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @PostMapping("/create")
    fun createGame(
        @RequestParam(required = false) userId: String?,
        @RequestBody(required = false) body: String?
    ): ResponseEntity<String> {
        if (userId.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("должен быть указан userId")
        }

        val config = if (!body.isNullOrBlank()) {
            try {
                json.decodeFromString<GameConfigWithoutGameId>(body)
            } catch (_: Exception) {
                GameConfigWithoutGameId()
            }
        } else {
            GameConfigWithoutGameId()
        }

        val gameId = lobby.createGame(userId) { id ->
            convertConfig(config, id)
        }

        return ResponseEntity.ok(gameId)
    }

    @PostMapping("/join")
    suspend fun joinGame(
        @RequestParam(required = false) userId: String?,
        @RequestParam(required = false) gameId: String?
    ): ResponseEntity<String> {
        if (userId.isNullOrBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("должен быть указан userId")
        }

        return if (!gameId.isNullOrBlank()) {
            val success = lobby.joinGame(userId, GameId(gameId))
            if (success) {
                ResponseEntity.ok().build()
            } else {
                ResponseEntity.status(HttpStatus.NOT_FOUND).build()
            }
        } else {
            val joinedGameId = lobby.joinGame(userId)
            if (joinedGameId != null) {
                ResponseEntity.ok(joinedGameId.id)
            } else {
                ResponseEntity.status(HttpStatus.NOT_FOUND).build()
            }
        }
    }
}
