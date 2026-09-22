package game.domain

import kotlinx.serialization.Serializable

@Serializable
data class EventNotification(
    val gameId: String,
    val eventIndex: Int,
    val timestamp: Long = System.currentTimeMillis()
)
