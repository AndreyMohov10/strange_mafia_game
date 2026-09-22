package game.domain

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val username: String,
    val preferredUserId: String? = null
)

@Serializable
data class AuthResponse(
    val userId: String,
    val username: String,
    val token: String
)

@Serializable
data class UserProfile(
    val userId: String,
    val username: String,
    val isRoot: Boolean
)
