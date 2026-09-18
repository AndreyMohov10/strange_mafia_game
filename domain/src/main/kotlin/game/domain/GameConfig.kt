package game.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GameConfig(
    @SerialName("players_num")
    val playersNum: Int,
    @SerialName("logging_timeout")
    val loggingTimeout: Long,
    @SerialName("turn_timeout")
    val turnTimeout: Long,
    @SerialName("regain_chance")
    val regainChance: Double,
    val agents: Int,
    val team: Int,
    @SerialName("check_chance")
    val checkChance: Double,
    val artifacts: Int,
    @SerialName("game_id")
    val gameId: GameId,
)


data class GameConfigWithoutGameId(
    @SerialName("players_num")
    val playersNum: Int = 6,
    @SerialName("logging_timeout")
    val loggingTimeout: Long = 60000,
    @SerialName("turn_timeout")
    val turnTimeout: Long = 120000,
    @SerialName("regain_chance")
    val regainChance: Double = 0.6,
    val agents: Int = 2,
    val team: Int = 2,
    @SerialName("check_chance")
    val checkChance: Double = 0.9,
    val artifacts: Int = 10,
) {
    init {
        require(checkChance > 0.5) {
            "шанс проверки должен быть выше 50%"
        }
        require(regainChance > 0.1) {
            "шанс возврата должен быть выше 10%"
        }
        require(artifacts >= 5) {
            "артефактов должно быть хотя-бы 5"
        }
        require(playersNum >= 4) {
            "должно быть хотя-бы 4 игрока в игре"
        }
        require(playersNum < 100) {
            "игроков должно быть не больше 100"
        }
        require(team > 0) {
            "Команду нужно собирать хотя-бы из 1 человека"
        }
        require(agents > 0) {
            "Должен быть хотя-бы 1 агент"
        }
        require(team < playersNum - agents) {
            "количество игроков для сбора группы должно быть не больше чем общее количество игроков "
        }
    }
}


@JvmInline
@Serializable
value class GameId(val id: String)
