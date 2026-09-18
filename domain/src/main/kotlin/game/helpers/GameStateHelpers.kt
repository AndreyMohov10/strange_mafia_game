package game.helpers

import game.domain.GameState
import game.domain.Message
import game.domain.Phase
import game.domain.Role
import kotlin.String
import kotlin.random.Random

const val MIN_REGAIN_CHANCE = 0.1

fun GameState.phaseValidator(): Boolean {
    return when (phase) {
        Phase.TEAM_CREATING -> {
            roles[player] == Role.MAFIA_HEAD &&
                    alive.count { it } > config.team
        }

        Phase.DAY_CONCLUSION -> {
            day > 0 && roles[player] == Role.MAFIA_HEAD
        }

        Phase.NIGHT_CONVERSATION -> {
            roles[player] == Role.AGENT || roles[player] == Role.AGENT_HEAD
        }

        Phase.NIGHT_CONCLUSION -> {
            roles[player] == Role.AGENT_HEAD &&
                    ((0..<config.playersNum).any { alive[it] && roles[it] == Role.MAFIA }
                            || (0..day).any { stolenArtifacts[it] })
        }

        else -> true
    } && alive[player]
}

@Suppress("unused")
fun GameState.ansValidator(str: String): Boolean {
    val str = str.lowercase().trim()
    return when (phase) {
        Phase.TEAM_CREATING -> {
            try {
                str.split(",").map { it.trim().toInt() }.all {
                    it in 1..config.playersNum && alive[it - 1]
                            && roles[it - 1] != Role.MAFIA_HEAD
                } && str.split(",").size == config.team
            } catch (_: NumberFormatException) {
                false
            }
        }

        Phase.DAY_CONCLUSION -> {
            try {
                val int = str.split(("\\s+").toRegex())[1].toInt()
                return if (str.startsWith("исключить")) {
                    int in 1..config.playersNum && alive[int - 1]
                } else if (str.startsWith("проверить")) {
                    int in 1..day
                } else {
                    false
                }
            } catch (_: NumberFormatException) {
                false
            }
        }

        Phase.NIGHT_CONCLUSION -> {
            try {
                val int = str.split(" ")[1].toInt()
                return if (str.startsWith("шантажировать")) {
                    int in 1..config.playersNum && alive[int - 1] && (roles[int - 1] == Role.MAFIA)
                } else if (str.startsWith("вернуть")) {
                    int - 1 in 0..day && stolenArtifacts[int - 1]
                } else {
                    false
                }
            } catch (_: NumberFormatException) {
                false
            }
        }

        else -> true
    }
}

fun GameState.ansProcessor(str: String, seed: Long) {
    val str = str.lowercase().trim()
    when (phase) {
        Phase.DAY_CONVERSATION -> {
            if (corrupted[player]) {
                corrupted[player] = false
                history.add(Message("тебя шантажировали", -player - 1, false))
            }
        }

        Phase.TEAM_CREATING -> {
            stolenArtifacts[day] = str.split(",").map { it.trim().toInt() }.all {
                roles[it - 1] == Role.MAFIA
            }
        }

        Phase.DAY_CONCLUSION -> {
            val int = str.split(" ")[1].toInt()
            if (str.startsWith("исключить")) {
                alive[int - 1] = false
                if (roles[int - 1] == Role.AGENT_HEAD) {
                    val lst = (0..<config.playersNum).filter { alive[it] && roles[it] == Role.AGENT }
                    if (lst.isNotEmpty()) {
                        roles[lst.random(Random(seed))] = Role.AGENT_HEAD
                    }
                }
            } else if (str.startsWith("проверить")) {
                val real = if (stolenArtifacts[int - 1]) {
                    val success = Random(seed).nextDouble(0.0, 1.0)
                    success > config.checkChance
                } else {
                    true
                }
                if (real) {
                    history.add(Message("артефакт был настоящий", 0, false))
                } else {
                    history.add(Message("артефакт был поддельный", 0, false))
                }
            }
        }

        Phase.NIGHT_CONCLUSION -> {
            val int = str.split(" ")[1].toInt()
            if (str.startsWith("шантажировать")) {
                corrupted[int - 1] = true
            } else if (str.startsWith("вернуть")) {
                val success = Random(seed).nextDouble(0.0, 1.0)
                val aliveAgents =
                    (0..<config.playersNum).count {
                        alive[it] && (roles[it] == Role.AGENT || roles[it] == Role.AGENT_HEAD)
                    }
                stolenArtifacts[int - 1] =
                    success < (config.regainChance - MIN_REGAIN_CHANCE) * aliveAgents / config.agents +
                            MIN_REGAIN_CHANCE
            }
        }

        else -> {}
    }
}

@Suppress("unused")
fun GameState.iteratePhase(str: String, seed: Long) {
    val message = Message(str, player + 1, phase.getSecret())
    history.add(message)
    ansProcessor(str, seed)
    do {
        player++
        if (player >= config.playersNum) {
            player = 0
            phase = phase.nextPhase()
            if (phase == Phase.DAY_CONVERSATION) {
                day++
            }
        }
    } while (!phaseValidator())
}

@Suppress("unused")
fun GameState.clone(): GameState {
    return GameState(
        config, this.playersId.clone(),
        roles.clone(),
        stolenArtifacts.clone(),
        alive.clone(),
        corrupted.clone(),
        phase,
        day,
        player,
        history.toMutableList()
    )
}

@Suppress("unused")
fun GameState.randomChoice(): String {
    return when (phase) {
        Phase.TEAM_CREATING -> {
            (1..config.playersNum)
                .filter { alive[it - 1] && roles[it - 1] != Role.MAFIA_HEAD }.shuffled().take(config.team)
                .joinToString(separator = ", ")
        }

        Phase.DAY_CONCLUSION -> {
            val action = listOf("проверить", "исключить").random()
            if (action == "исключить") {
                val possiblePersons =
                    (0..<config.playersNum).filter { alive[it] && roles[it] != Role.MAFIA_HEAD }.toList()
                if (possiblePersons.isNotEmpty()) {
                    return "исключить ${possiblePersons.random() + 1}"
                }
            }

            return "проверить ${(0..<day).random() + 1}"
        }

        Phase.NIGHT_CONCLUSION -> {
            val action = listOf("вернуть", "шантажировать").random()
            if (action == "вернуть") {
                val possibleToReturn = (0..day).filter { stolenArtifacts[it] }.toList()
                if (possibleToReturn.isNotEmpty()) {
                    "вернуть ${possibleToReturn.random() + 1}"
                }
            }
            val possibleToCorrupt = (0..<config.playersNum)
                .filter { alive[it] && roles[it] == Role.MAFIA }.toList()
            if (possibleToCorrupt.isNotEmpty()) {
                return "шантажировать ${possibleToCorrupt.random() + 1}"
            }
            return "вернуть ${(0..day).filter { stolenArtifacts[it] }.random() + 1}"
        }

        else -> "слишком долго думал"
    }
}