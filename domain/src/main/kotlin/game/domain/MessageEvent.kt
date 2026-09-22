package game.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("message_event")
@Suppress("unused")
data class MessageEvent(
    val message: Message,
    val seed: Long,
    val messageLength: Int,
    override val eventIndex: Int = 0
) : Event()