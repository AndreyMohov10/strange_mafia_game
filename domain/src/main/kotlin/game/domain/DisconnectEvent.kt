package game.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("disconnect_event")
@Suppress("unused")
data class DisconnectEvent(
    val index: Int,
    override val eventIndex: Int = 0
) : Event()