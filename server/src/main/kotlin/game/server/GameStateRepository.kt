package game.server

import game.domain.GameConfig
import game.domain.GameId
import game.domain.GameState
import game.domain.Phase
import jooq.Tables.GAME_CONFIGS
import jooq.tables.GameStates.GAME_STATES
import kotlinx.serialization.json.Json
import org.jooq.DSLContext
import org.jooq.JSONB


class GameStateRepository(private val dsl: DSLContext) {
    fun createGame(state: GameState) {
        dsl.transaction { trx ->
            trx.dsl().insertInto(GAME_CONFIGS)
                .set(GAME_CONFIGS.GAME_ID, state.config.gameId.id)
                .set(GAME_CONFIGS.AGENTS, state.config.agents)
                .set(GAME_CONFIGS.ARTIFACTS, state.config.artifacts)
                .set(GAME_CONFIGS.CHECK_CHANCE, state.config.checkChance)
                .set(GAME_CONFIGS.LOGGING_TIMEOUT, state.config.loggingTimeout)
                .set(GAME_CONFIGS.PLAYERS_NUM, state.config.playersNum)
                .set(GAME_CONFIGS.REGAIN_CHANCE, state.config.regainChance)
                .set(GAME_CONFIGS.TEAM, state.config.team)
                .set(GAME_CONFIGS.TURN_TIMEOUT, state.config.turnTimeout)
                .execute()
            trx.dsl().insertInto(GAME_STATES)
                .set(GAME_STATES.GAME_ID, state.config.gameId.id)
                .set(GAME_STATES.ALIVE, JSONB.valueOf(Json.encodeToString(state.alive)))
                .set(GAME_STATES.CORRUPTED, JSONB.valueOf(Json.encodeToString(state.corrupted)))
                .set(GAME_STATES.DAY, state.day)
                .set(GAME_STATES.HISTORY, JSONB.valueOf(Json.encodeToString(state.history)))
                .set(GAME_STATES.PHASE, state.phase.toString())
                .set(GAME_STATES.PLAYER, state.player)
                .set(GAME_STATES.PLAYERS_ID, JSONB.valueOf(Json.encodeToString(state.playersId)))
                .set(GAME_STATES.ROLES, JSONB.valueOf(Json.encodeToString(state.roles)))
                .set(GAME_STATES.STOLEN_ARTIFACTS, JSONB.valueOf(Json.encodeToString(state.stolenArtifacts)))
                .execute()
        }
    }

    fun updateGameStatePlayersId(state: GameState) {
        dsl.update(GAME_STATES)
            .set(GAME_STATES.PLAYERS_ID, JSONB.valueOf(Json.encodeToString(state.playersId)))
            .execute()
    }

    fun updateGameState(state: GameState) {
        dsl.update(GAME_STATES)
            .set(GAME_STATES.ALIVE, JSONB.valueOf(Json.encodeToString(state.alive)))
            .set(GAME_STATES.CORRUPTED, JSONB.valueOf(Json.encodeToString(state.corrupted)))
            .set(GAME_STATES.DAY, state.day)
            .set(GAME_STATES.HISTORY, JSONB.valueOf(Json.encodeToString(state.history)))
            .set(GAME_STATES.PHASE, state.phase.toString())
            .set(GAME_STATES.PLAYER, state.player)
            .set(GAME_STATES.PLAYERS_ID, JSONB.valueOf(Json.encodeToString(state.playersId)))
            .set(GAME_STATES.ROLES, JSONB.valueOf(Json.encodeToString(state.roles)))
            .set(GAME_STATES.STOLEN_ARTIFACTS, JSONB.valueOf(Json.encodeToString(state.stolenArtifacts)))
            .where(GAME_STATES.GAME_ID.eq(state.config.gameId.id))
            .execute()
    }

    fun loadGame(gameId: GameId): GameState? {
        val recordGameState = dsl.selectFrom(GAME_STATES)
            .where(GAME_STATES.GAME_ID.eq(gameId.id))
            .fetchOne() ?: return null
        val recordGameConfig = dsl.selectFrom(GAME_CONFIGS)
            .where(GAME_CONFIGS.GAME_ID.eq(gameId.id))
            .fetchOne() ?: return null
        return GameState(
            config = GameConfig(
                gameId = GameId(recordGameConfig.gameId),
                playersNum = recordGameConfig.playersNum,
                loggingTimeout = recordGameConfig.loggingTimeout.toLong(),
                regainChance = recordGameConfig.regainChance,
                turnTimeout = recordGameConfig.turnTimeout,
                team = recordGameConfig.team,
                agents = recordGameConfig.agents,
                artifacts = recordGameConfig.artifacts,
                checkChance = recordGameConfig.checkChance
            ),
            playersId = Json.decodeFromString(recordGameState.playersId.data()),
            roles = Json.decodeFromString(recordGameState.roles.data()),
            stolenArtifacts = Json.decodeFromString(recordGameState.stolenArtifacts.data()),
            alive = Json.decodeFromString(recordGameState.alive.data()),
            corrupted = Json.decodeFromString(recordGameState.corrupted.data()),
            phase = Phase.valueOf(recordGameState.phase),
            day = recordGameState.day,
            player = recordGameState.player,
            history = Json.decodeFromString(recordGameState.history.data())
        )
    }
}