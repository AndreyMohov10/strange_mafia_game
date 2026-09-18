package game.domain

import kotlinx.serialization.Serializable

@Serializable
enum class Phase {
    DAY_CONVERSATION,
    TEAM_CREATING,
    DAY_CONCLUSION,
    NIGHT_CONVERSATION,
    NIGHT_CONCLUSION
}
