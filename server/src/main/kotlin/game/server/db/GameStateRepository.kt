package game.server.db

import com.example.jooq.generated.Tables.GAME_CONFIGS
import com.example.jooq.generated.Tables.GAME_STATES
import game.domain.GameConfig
import game.domain.GameId
import game.domain.GameState
import game.domain.Phase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jooq.DSLContext
import org.jooq.JSONB
import org.springframework.stereotype.Repository

@Repository
class GameStateRepository(private val dsl: DSLContext) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    fun createGame(state: GameState) {
        val gid = state.config.gameId.id
        dsl.transaction { trx ->
            trx.dsl().insertInto(GAME_CONFIGS)
                .set(GAME_CONFIGS.GAME_ID, gid)
                .set(GAME_CONFIGS.AGENTS, state.config.agents)
                .set(GAME_CONFIGS.ARTIFACTS, state.config.artifacts)
                .set(GAME_CONFIGS.CHECK_CHANCE, state.config.checkChance)
                .set(GAME_CONFIGS.LOGGING_TIMEOUT, state.config.loggingTimeout)
                .set(GAME_CONFIGS.PLAYERS_NUM, state.config.playersNum)
                .set(GAME_CONFIGS.REGAIN_CHANCE, state.config.regainChance)
                .set(GAME_CONFIGS.TEAM, state.config.team)
                .set(GAME_CONFIGS.TURN_TIMEOUT, state.config.turnTimeout)
                .onConflict(GAME_CONFIGS.GAME_ID)
                .doUpdate()
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
                .set(GAME_STATES.GAME_ID, gid)
                .set(GAME_STATES.ALIVE, JSONB.valueOf(json.encodeToString(state.alive)))
                .set(GAME_STATES.CORRUPTED, JSONB.valueOf(json.encodeToString(state.corrupted)))
                .set(GAME_STATES.DAY, state.day)
                .set(GAME_STATES.PHASE, state.phase.toString())
                .set(GAME_STATES.PLAYER, state.player)
                .set(GAME_STATES.PLAYERS_ID, JSONB.valueOf(json.encodeToString(state.playersId)))
                .set(GAME_STATES.ROLES, JSONB.valueOf(json.encodeToString(state.roles)))
                .set(GAME_STATES.STOLEN_ARTIFACTS, JSONB.valueOf(json.encodeToString(state.stolenArtifacts)))
                .onConflict(GAME_STATES.GAME_ID)
                .doUpdate()
                .set(GAME_STATES.ALIVE, JSONB.valueOf(json.encodeToString(state.alive)))
                .set(GAME_STATES.CORRUPTED, JSONB.valueOf(json.encodeToString(state.corrupted)))
                .set(GAME_STATES.DAY, state.day)
                .set(GAME_STATES.PHASE, state.phase.toString())
                .set(GAME_STATES.PLAYER, state.player)
                .set(GAME_STATES.PLAYERS_ID, JSONB.valueOf(json.encodeToString(state.playersId)))
                .set(GAME_STATES.ROLES, JSONB.valueOf(json.encodeToString(state.roles)))
                .set(GAME_STATES.STOLEN_ARTIFACTS, JSONB.valueOf(json.encodeToString(state.stolenArtifacts)))
                .execute()
        }
    }

    fun updateGameStatePlayersId(state: GameState) {
        val gid = state.config.gameId.id
        dsl.update(GAME_STATES)
            .set(GAME_STATES.PLAYERS_ID, JSONB.valueOf(json.encodeToString(state.playersId)))
            .where(GAME_STATES.GAME_ID.eq(gid))
            .execute()
    }

    fun updateGameState(state: GameState) {
        val gid = state.config.gameId.id
        dsl.update(GAME_STATES)
            .set(GAME_STATES.ALIVE, JSONB.valueOf(json.encodeToString(state.alive)))
            .set(GAME_STATES.CORRUPTED, JSONB.valueOf(json.encodeToString(state.corrupted)))
            .set(GAME_STATES.DAY, state.day)
            .set(GAME_STATES.PHASE, state.phase.toString())
            .set(GAME_STATES.PLAYER, state.player)
            .set(GAME_STATES.PLAYERS_ID, JSONB.valueOf(json.encodeToString(state.playersId)))
            .set(GAME_STATES.ROLES, JSONB.valueOf(json.encodeToString(state.roles)))
            .set(GAME_STATES.STOLEN_ARTIFACTS, JSONB.valueOf(json.encodeToString(state.stolenArtifacts)))
            .where(GAME_STATES.GAME_ID.eq(gid))
            .execute()
    }

    fun loadGame(gameId: GameId): GameState? {
        val gid = gameId.id
        val recordGameState = dsl.selectFrom(GAME_STATES)
            .where(GAME_STATES.GAME_ID.eq(gid))
            .fetchOne() ?: return null

        val recordGameConfig = dsl.selectFrom(GAME_CONFIGS)
            .where(GAME_CONFIGS.GAME_ID.eq(gid))
            .fetchOne() ?: return null

        return try {
            val playersNum = recordGameConfig.playersNum ?: return null
            val loggingTimeout = recordGameConfig.loggingTimeout ?: return null
            val turnTimeout = recordGameConfig.turnTimeout ?: return null
            val regainChance = recordGameConfig.regainChance ?: return null
            val agents = recordGameConfig.agents ?: return null
            val team = recordGameConfig.team ?: return null
            val checkChance = recordGameConfig.checkChance ?: return null
            val artifacts = recordGameConfig.artifacts ?: return null

            val playersIdJson = recordGameState.playersId?.data() ?: return null
            val rolesJson = recordGameState.roles?.data() ?: return null
            val stolenArtifactsJson = recordGameState.stolenArtifacts?.data() ?: return null
            val aliveJson = recordGameState.alive?.data() ?: return null
            val corruptedJson = recordGameState.corrupted?.data() ?: return null
            val phaseStr = recordGameState.phase ?: return null
            val day = recordGameState.day ?: return null
            val player = recordGameState.player ?: return null

            GameState(
                config = GameConfig(
                    gameId = GameId(gid),
                    playersNum = playersNum,
                    loggingTimeout = loggingTimeout,
                    turnTimeout = turnTimeout,
                    regainChance = regainChance,
                    agents = agents,
                    team = team,
                    checkChance = checkChance,
                    artifacts = artifacts
                ),
                playersId = json.decodeFromString(playersIdJson),
                roles = json.decodeFromString(rolesJson),
                stolenArtifacts = json.decodeFromString(stolenArtifactsJson),
                alive = json.decodeFromString(aliveJson),
                corrupted = json.decodeFromString(corruptedJson),
                phase = Phase.valueOf(phaseStr),
                day = day,
                player = player
            )
        } catch (e: Exception) {
            System.err.println("Ошибка при загрузке GameState для gameId=$gid: ${e.message}")
            null
        }
    }
}