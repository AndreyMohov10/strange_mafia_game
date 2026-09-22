package game.server.controller

import game.domain.RegisterRequest
import game.server.auth.AuthService
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    @PostMapping("/register", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun register(@RequestBody body: String): ResponseEntity<String> {
        val request = try {
            json.decodeFromString<RegisterRequest>(body)
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("""{"error": "Неверный формат запроса: ${e.message}"}""")
        }

        if (request.username.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("""{"error": "Имя пользователя не может быть пустым"}""")
        }

        val authResponse = authService.registerUser(request.username, request.preferredUserId)
        return ResponseEntity.ok(json.encodeToString(authResponse))
    }

    @GetMapping("/me", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun me(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authHeader: String?,
        @RequestParam(required = false) token: String?
    ): ResponseEntity<String> {
        val rawToken = authHeader ?: token
        val principal = authService.resolveUser(rawToken)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("""{"error": "Неверный или отсутствующий токен авторизации"}""")

        val profile = authService.getUserProfile(principal)
        return ResponseEntity.ok(json.encodeToString(profile))
    }
}
