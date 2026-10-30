package game.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("message_event")
@Suppress("unused")
data class MessageEvent(
    val message: Message,
    override val eventIndex: Int = 0
) : Event()