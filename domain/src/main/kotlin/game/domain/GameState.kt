package game.domain

import game.helpers.getSecret
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GameState(
    val config: GameConfig,
    @SerialName("players_id")
    val playersId: Array<String>,
    val roles: Array<Role>,
    @SerialName("stolen_artifacts")
    val stolenArtifacts: BooleanArray = BooleanArray(config.artifacts) {
        false
    },
    val alive: BooleanArray = BooleanArray(config.playersNum) {
        true
    },
    val corrupted: BooleanArray = BooleanArray(config.playersNum) {
        false
    },
    var phase: Phase = Phase.DAY_CONVERSATION,
    var day: Int = 0,
    var player: Int = 0,
) {
    init {
        require(playersId.size == config.playersNum) {
            "список игроков должен иметь столько игроков сколько требовалось в конфиге"
        }
        require(roles.size == config.playersNum) {
            "ролей должно быть столько сколько требовалось в конфиге"
        }
        require(roles.count { it.getSecret() } == config.agents) {
            "количество агентов должно быть столько сколько указано в конфиге"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GameState) return false

        if (day != other.day) return false
        if (player != other.player) return false
        if (config != other.config) return false
        if (!playersId.contentEquals(other.playersId)) return false
        if (!roles.contentEquals(other.roles)) return false
        if (!stolenArtifacts.contentEquals(other.stolenArtifacts)) return false
        if (!alive.contentEquals(other.alive)) return false
        if (!corrupted.contentEquals(other.corrupted)) return false
        if (phase != other.phase) return false

        return true
    }

    override fun hashCode(): Int {
        var result = day
        result = 31 * result + player
        result = 31 * result + config.hashCode()
        result = 31 * result + playersId.contentHashCode()
        result = 31 * result + roles.contentHashCode()
        result = 31 * result + stolenArtifacts.contentHashCode()
        result = 31 * result + alive.contentHashCode()
        result = 31 * result + corrupted.contentHashCode()
        result = 31 * result + phase.hashCode()
        return result
    }
}