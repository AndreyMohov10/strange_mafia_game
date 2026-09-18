package game.helpers

import game.domain.GameConfig
import game.domain.GameConfigWithoutGameId
import game.domain.GameId

@Suppress("unused")
fun convertConfig(config: GameConfigWithoutGameId, id: GameId): GameConfig {
    return GameConfig(
        config.playersNum,
        config.loggingTimeout,
        config.turnTimeout,
        config.regainChance,
        config.agents,
        config.team,
        config.checkChance,
        config.artifacts,
        id
    )
}