package game.server.auth

import com.example.jooq.generated.Tables.USERS
import game.domain.AuthResponse
import game.domain.UserProfile
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class UserPrincipal(
    val userId: String,
    val username: String,
    val isRoot: Boolean
)

@Service
class AuthService(
    private val dsl: DSLContext,
    @param:Value($$"${auth.root-token:${ROOT_TOKEN:secret_root_token_123}}") val rootToken: String
) {
    private val tokenCache = ConcurrentHashMap<String, UserPrincipal>()

    fun registerUser(username: String, preferredUserId: String? = null): AuthResponse {
        val cleanUsername = username.trim().ifEmpty { "Player" }
        val userId = preferredUserId?.trim()?.takeIf { it.isNotEmpty() } ?: "user_${UUID.randomUUID().toString().take(8)}"
        val token = "tok_${UUID.randomUUID().toString().replace("-", "")}"

        try {
            dsl.insertInto(USERS)
                .set(USERS.ID, userId)
                .set(USERS.USERNAME, cleanUsername)
                .set(USERS.TOKEN, token)
                .execute()
        } catch (_: Exception) {
            val fallbackId = "user_${UUID.randomUUID().toString().take(8)}"
            dsl.insertInto(USERS)
                .set(USERS.ID, fallbackId)
                .set(USERS.USERNAME, cleanUsername)
                .set(USERS.TOKEN, token)
                .execute()
            val principal = UserPrincipal(fallbackId, cleanUsername, isRoot = false)
            tokenCache[token] = principal
            return AuthResponse(fallbackId, cleanUsername, token)
        }

        val principal = UserPrincipal(userId, cleanUsername, isRoot = false)
        tokenCache[token] = principal
        return AuthResponse(userId, cleanUsername, token)
    }

    fun extractToken(headerOrParam: String?): String? {
        if (headerOrParam.isNullOrBlank()) return null
        return if (headerOrParam.startsWith("Bearer ", ignoreCase = true)) {
            headerOrParam.substring(7).trim()
        } else {
            headerOrParam.trim()
        }
    }

    fun resolveUser(rawToken: String?, requestedUserId: String? = null): UserPrincipal? {
        val token = extractToken(rawToken) ?: return null

        if (token == rootToken) {
            val effectiveUserId = requestedUserId?.trim()?.takeIf { it.isNotEmpty() } ?: "root"
            return UserPrincipal(
                userId = effectiveUserId,
                username = "root",
                isRoot = true
            )
        }

        var principal = tokenCache[token]

        if (principal == null) {
            val record = try {
                dsl.selectFrom(USERS)
                    .where(USERS.TOKEN.eq(token))
                    .fetchOne()
            } catch (_: Exception) {
                null
            }

            if (record != null) {
                val dbUserId = record.id ?: return null
                val dbUsername = record.username ?: "Player"
                principal = UserPrincipal(dbUserId, dbUsername, isRoot = false)
                tokenCache[token] = principal
            }
        }

        if (principal == null) {
            return null
        }

        if (!requestedUserId.isNullOrBlank() && requestedUserId.trim() != principal.userId) {
            throw SecurityException("Пользователю запрещено действовать от имени другого игрока ($requestedUserId)")
        }

        return principal
    }

    fun getUserProfile(principal: UserPrincipal): UserProfile {
        return UserProfile(
            userId = principal.userId,
            username = principal.username,
            isRoot = principal.isRoot
        )
    }
}
