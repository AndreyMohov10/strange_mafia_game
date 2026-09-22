package game.helpers

import game.domain.GameConfigWithoutGameId
import game.domain.GameId
import game.domain.GameState
import game.domain.Phase
import game.domain.Role
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameStateValidationTest {

    private fun createTestState(): GameState {
        val config = convertConfig(
            GameConfigWithoutGameId(playersNum = 5, agents = 2, team = 2, artifacts = 5),
            GameId("test_val_game")
        )
        val playersId = arrayOf("p1", "p2", "p3", "p4", "p5")
        val roles = arrayOf(Role.MAFIA_HEAD, Role.MAFIA, Role.MAFIA, Role.AGENT_HEAD, Role.AGENT)
        return GameState(config, playersId, roles)
    }

    @Test
    fun `day and night conversation accept any string`() {
        val state = createTestState()
        state.phase = Phase.DAY_CONVERSATION
        assertNull(state.ansValidator("любое сообщение"))
        assertNull(state.ansValidator(""))
        assertNull(state.ansValidator("123"))

        state.phase = Phase.NIGHT_CONVERSATION
        assertNull(state.ansValidator("секретное сообщение агентов"))
    }

    @Test
    fun `team creating validates player indices count and roles`() {
        val state = createTestState()
        state.phase = Phase.TEAM_CREATING

        // Valid combinations
        assertNull(state.ansValidator("2, 4"))
        assertNull(state.ansValidator(" 2 ,  4 "))
        assertNull(state.ansValidator("2, 3"))
        assertNull(state.ansValidator("2, 5"))

        // Cannot pick mafia head (player 1)
        val selfPickError = state.ansValidator("1, 2")
        assertNotNull(selfPickError)
        assertTrue(selfPickError.message!!.contains("Нельзя выбирать Главу Мафии"))

        // Duplicates
        val duplicateError = state.ansValidator("2, 2")
        assertNotNull(duplicateError)
        assertTrue(duplicateError.message!!.contains("не должны повторяться"))

        // Invalid count
        val countError = state.ansValidator("2")
        assertNotNull(countError)
        assertTrue(countError.message!!.contains("Нужно выбрать ровно 2 игроков"))
        assertNotNull(state.ansValidator("2, 3, 4"))

        // Excluded player
        state.alive[1] = false
        val excludedError = state.ansValidator("2, 3")
        assertNotNull(excludedError)
        assertTrue(excludedError.message!!.contains("уже исключен"))

        // Out of bounds
        assertNotNull(state.ansValidator("0, 3"))
        assertNotNull(state.ansValidator("3, 6"))

        // Non-number
        assertNotNull(state.ansValidator("два, три"))
        assertNotNull(state.ansValidator("abc"))
    }

    @Test
    fun `day conclusion validates exclude and check commands`() {
        val state = createTestState()
        state.phase = Phase.DAY_CONCLUSION
        state.day = 2

        // Valid exclude
        assertNull(state.ansValidator("исключить 2"))
        assertNull(state.ansValidator("исключить    3"))
        assertNull(state.ansValidator("исключить 4"))

        // Excluded player
        state.alive[4] = false
        val excludedError = state.ansValidator("исключить 5")
        assertNotNull(excludedError)
        assertTrue(excludedError.message!!.contains("уже исключен"))

        // Out of bounds
        assertNotNull(state.ansValidator("исключить 0"))
        assertNotNull(state.ansValidator("исключить 6"))

        // Valid check artifact
        assertNull(state.ansValidator("проверить 1"))
        assertNull(state.ansValidator("проверить 2"))

        // Invalid artifact bounds
        assertNotNull(state.ansValidator("проверить 0"))
        assertNotNull(state.ansValidator("проверить 3"))

        // Unknown or incomplete command
        assertNotNull(state.ansValidator("исключить"))
        assertNotNull(state.ansValidator("проверить"))
        val unknownCommandError = state.ansValidator("атаковать 2")
        assertNotNull(unknownCommandError)
        assertTrue(unknownCommandError.message!!.contains("Неизвестная команда"))
        assertNotNull(state.ansValidator("исключить два"))
    }

    @Test
    fun `night conclusion validates blackmail and return commands`() {
        val state = createTestState()
        state.phase = Phase.NIGHT_CONCLUSION
        state.day = 1

        // Valid blackmail of regular mafia
        assertNull(state.ansValidator("шантажировать 2"))
        assertNull(state.ansValidator("шантажировать   3"))

        // Cannot blackmail agent
        val agentBlackmailError = state.ansValidator("шантажировать 4")
        assertNotNull(agentBlackmailError)
        assertTrue(agentBlackmailError.message!!.contains("рядовых членов мафии"))

        // Cannot blackmail mafia head
        val headBlackmailError = state.ansValidator("шантажировать 1")
        assertNotNull(headBlackmailError)
        assertTrue(headBlackmailError.message!!.contains("рядовых членов мафии"))

        // Dead player
        state.alive[1] = false
        assertNotNull(state.ansValidator("шантажировать 2"))

        // Stolen artifacts return
        state.stolenArtifacts[0] = true

        assertNull(state.ansValidator("вернуть 1"))

        val notStolenError = state.ansValidator("вернуть 2")
        assertNotNull(notStolenError)
        assertTrue(notStolenError.message!!.contains("не был украден"))

        assertNotNull(state.ansValidator("вернуть 0"))
        assertNotNull(state.ansValidator("вернуть 3"))

        assertNotNull(state.ansValidator("шантажировать"))
        assertNotNull(state.ansValidator("вернуть"))
        assertNotNull(state.ansValidator("убить 2"))
        assertNotNull(state.ansValidator("шантажировать два"))
    }
}
