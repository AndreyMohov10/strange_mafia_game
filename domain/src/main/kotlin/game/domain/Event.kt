package game.domain

import kotlinx.serialization.Serializable

@Serializable
sealed class Event {
    abstract val eventIndex: Int
}