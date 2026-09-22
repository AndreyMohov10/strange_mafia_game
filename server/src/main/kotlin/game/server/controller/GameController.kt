package game.server.controller

import game.domain.ActionRequest
import game.domain.GameConfigWithoutGameId
import game.domain.GameId
import game.helpers.convertConfig
import game.server.GameLobby
import game.server.db.GameStateRepository
import game.server.Games
import game.server.auth.AuthService
import game.server.db.GameEventRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class GameController(
    private val lobby: GameLobby,
    private val games: Games,
    private val database: GameStateRepository,
    private val eventRepository: GameEventRepository,
    private val authService: AuthService
) {
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        encodeDefaults = true
        isLenient = true
    }

    @PostMapping("/create", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun createGame(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authHeader: String?,
        @RequestParam(required = false) token: String?,
        @RequestParam(required = false) userId: String?,
        @RequestBody(required = false) body: String?
    ): ResponseEntity<String> {
        val rawToken = authHeader ?: token
        val principal = authService.resolveUser(rawToken, userId)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Неверный токен авторизации")

        val config = if (!body.isNullOrBlank()) {
            try {
                json.decodeFromString<GameConfigWithoutGameId>(body)
            } catch (_: Exception) {
                GameConfigWithoutGameId()
            }
        } else {
            GameConfigWithoutGameId()
        }

        val gameId = lobby.createGame(principal.userId) { id ->
            convertConfig(config, id)
        }

        return ResponseEntity.ok(gameId)
    }

    @PostMapping("/join", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun joinGame(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authHeader: String?,
        @RequestParam(required = false) token: String?,
        @RequestParam(required = false) userId: String?,
        @RequestParam(required = false) gameId: String?
    ): ResponseEntity<String> = kotlinx.coroutines.runBlocking {
        val rawToken = authHeader ?: token
        val principal = authService.resolveUser(rawToken, userId)
            ?: return@runBlocking ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Неверный токен авторизации")

        return@runBlocking if (!gameId.isNullOrBlank()) {
            val success = lobby.joinGame(principal.userId, GameId(gameId))
            if (success) {
                ResponseEntity.ok(gameId)
            } else {
                ResponseEntity.status(HttpStatus.NOT_FOUND).body("Игра не найдена или заполнена")
            }
        } else {
            val joinedGameId = lobby.joinGame(principal.userId)
            if (joinedGameId != null) {
                ResponseEntity.ok(joinedGameId.id)
            } else {
                ResponseEntity.status(HttpStatus.NOT_FOUND).body("Нет доступных игр для присоединения")
            }
        }
    }

    @PostMapping(
        "/game/{gameId}/action",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun makeAction(
        @PathVariable gameId: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authHeader: String?,
        @RequestParam(required = false) token: String?,
        @RequestBody body: String
    ): ResponseEntity<String> {
        val actionRequest = try {
            json.decodeFromString<ActionRequest>(body)
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("""{"error": "Неверный формат запроса: ${e.message}"}""")
        }

        val rawToken = authHeader ?: token
        val principal = try {
            authService.resolveUser(rawToken, actionRequest.userId)
        } catch (e: SecurityException) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("""{"error": "${e.message}"}""")
        } ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("""{"error": "Неверный токен авторизации"}""")

        val gid = GameId(gameId)
        val game = games.games[gid]
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body("""{"error": "Игра не найдена или завершена"}""")

        val success = game.makeAction(principal.userId, actionRequest.answer)
        return if (success) {
            ResponseEntity.ok("""{"status": "ok"}""")
        } else {
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("""{"error": "Игрок ${principal.userId} не найден или сейчас не его ход"}""")
        }
    }

    @GetMapping("/game/{gameId}/state", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getState(
        @PathVariable gameId: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authHeader: String?,
        @RequestParam(required = false) token: String?,
        @RequestParam(required = false) userId: String?
    ): ResponseEntity<String> {
        val rawToken = authHeader ?: token
        val principal = try {
            authService.resolveUser(rawToken, userId)
        } catch (e: SecurityException) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("""{"error": "${e.message}"}""")
        } ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("""{"error": "Неверный токен авторизации"}""")

        principal.userId

        val gid = GameId(gameId)
        val game = games.games[gid]
        val state = game?.getState() ?: database.loadGame(gid)
        if (state != null) {
            return if (principal.isRoot || principal.userId in state.playersId) {
                ResponseEntity.ok(json.encodeToString(state))
            } else {
                ResponseEntity.status(HttpStatus.FORBIDDEN).body("""{"error": "player not in game"}""")
            }
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("""{"error": "Игра не найдена"}""")
    }

    @GetMapping("/game/{gameId}/events", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getEvents(
        @PathVariable gameId: String,
        @RequestParam(defaultValue = "0") from: Int,
        @RequestParam(required = false) to: Int?,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authHeader: String?,
        @RequestParam(required = false) token: String?,
        @RequestParam(required = false) userId: String?
    ): ResponseEntity<String> {
        val rawToken = authHeader ?: token
        val principal = try {
            authService.resolveUser(rawToken, userId)
        } catch (e: SecurityException) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("""{"error": "${e.message}"}""")
        } ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("""{"error": "Неверный токен авторизации"}""")

        val gid = GameId(gameId)
        val game = games.games[gid]
        val events = game?.getEvents(from, to, principal.userId, principal.isRoot)
            ?: eventRepository.getEvents(gid, from, to)

        return ResponseEntity.ok(json.encodeToString(events))
    }
}
