package game.domain

import kotlinx.serialization.Serializable

@Serializable
data class Message(val string: String, val userNum: Int, val secret: Boolean)
