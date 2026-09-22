package game.domain

import kotlinx.serialization.Serializable

@Serializable
data class ActionRequest(
    val answer: String,
    val userId: String? = null
)
