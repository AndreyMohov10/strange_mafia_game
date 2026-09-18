package game.domain

import kotlinx.serialization.Serializable

@Serializable
enum class Role {
    AGENT,
    AGENT_HEAD,
    MAFIA,
    MAFIA_HEAD
}
