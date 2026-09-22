package game.helpers

import game.domain.GameConfigWithoutGameId
import game.domain.GameId
import game.domain.GameState
import game.domain.Phase
import game.domain.Role
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameStateIterationTest {

    private fun createTestState(): GameState {
        val config = convertConfig(
            GameConfigWithoutGameId(playersNum = 5, agents = 2, team = 2, artifacts = 5),
            GameId("test_iter_game")
        )
        val playersId = arrayOf("p1", "p2", "p3", "p4", "p5")
        val roles = arrayOf(Role.MAFIA_HEAD, Role.MAFIA, Role.MAFIA, Role.AGENT_HEAD, Role.AGENT)
        return GameState(config, playersId, roles)
    }

    @Test
    fun `day conversation processes blackmail notification when corrupted`() {
        val state = createTestState()
        state.phase = Phase.DAY_CONVERSATION
        state.player = 1
        state.corrupted[1] = true

        val messages = state.iteratePhase("привет всем", seed = 123L)

        assertFalse(state.corrupted[1])
        assertEquals(2, messages.size)
        assertEquals("привет всем", messages[0].string)
        assertEquals("тебя шантажировали", messages[1].string)
        assertEquals(-2, messages[1].userNum)
    }

    @Test
    fun `team creating updates stolen artifacts correctly`() {
        val state = createTestState()
        state.phase = Phase.TEAM_CREATING
        state.player = 0
        state.day = 0

        val messagesSuccess = state.iteratePhase("2, 3", seed = 123L)
        assertTrue(messagesSuccess.isNotEmpty())
        assertTrue(state.stolenArtifacts[0])

        state.stolenArtifacts[0] = false
        state.phase = Phase.TEAM_CREATING
        state.player = 0
        val messagesFail = state.iteratePhase("2, 4", seed = 123L)
        assertTrue(messagesFail.isNotEmpty())
        assertFalse(state.stolenArtifacts[0])
    }

    @Test
    fun `day conclusion excludes player and handles AGENT_HEAD succession`() {
        val state = createTestState()
        state.phase = Phase.DAY_CONCLUSION
        state.player = 0
        state.day = 1

        val messages = state.iteratePhase("исключить 4", seed = 123L)
        assertTrue(messages.isNotEmpty())
        assertFalse(state.alive[3])

        assertEquals(Role.AGENT_HEAD, state.roles[4])
    }

    @Test
    fun `night conclusion corrupts mafia player when blackmailed`() {
        val state = createTestState()
        state.phase = Phase.NIGHT_CONCLUSION
        state.player = 3
        state.day = 1

        val messages = state.iteratePhase("шантажировать 2", seed = 123L)
        assertTrue(messages.isNotEmpty())
        assertTrue(state.corrupted[1])
    }

    @Test
    fun `full game simulation with random valid choices never crashes iteratePhase`() {
        repeat(20) { runIdx ->
            val random = Random(1000L + runIdx)
            val config = convertConfig(
                GameConfigWithoutGameId(playersNum = 5, agents = 2, team = 2, artifacts = 6),
                GameId("sim_game_$runIdx")
            )
            val playersList = (1..5).map { "player_$it" }.toTypedArray()
            val roles = arrayOf(Role.MAFIA_HEAD, Role.MAFIA, Role.MAFIA, Role.AGENT_HEAD, Role.AGENT)
            roles.shuffle(random)

            val state = GameState(config, playersList, roles)

            var turnsCount = 0
            val maxTurns = 500

            while (state.day < state.config.artifacts && turnsCount < maxTurns) {
                turnsCount++

                val choice = state.randomChoice()
                val isValid = state.ansValidator(choice) == null
                if (isValid) {
                    val messages = state.iteratePhase(choice, seed = random.nextLong())
                    assertTrue(messages.isNotEmpty(), "iteratePhase should return at least the user's message")
                } else {
                    val fallback = when (state.phase) {
                        Phase.TEAM_CREATING -> {
                            (1..state.config.playersNum)
                                .filter { state.alive[it - 1] && state.roles[it - 1] != Role.MAFIA_HEAD }
                                .take(state.config.team)
                                .joinToString(",")
                        }
                        Phase.DAY_CONCLUSION -> {
                            val target = (1..state.config.playersNum).firstOrNull { state.alive[it - 1] } ?: 1
                            "исключить $target"
                        }
                        Phase.NIGHT_CONCLUSION -> {
                            val mafiaTarget = (1..state.config.playersNum)
                                .firstOrNull { state.alive[it - 1] && state.roles[it - 1] == Role.MAFIA }
                            if (mafiaTarget != null) {
                                "шантажировать $mafiaTarget"
                            } else {
                                "шантажировать 1"
                            }
                        }
                        else -> "сообщение"
                    }
                    val messages = state.iteratePhase(fallback, seed = random.nextLong())
                    assertTrue(messages.isNotEmpty())
                }
            }

            assertTrue(turnsCount > 0, "Game should execute at least one turn")
        }
    }
}
