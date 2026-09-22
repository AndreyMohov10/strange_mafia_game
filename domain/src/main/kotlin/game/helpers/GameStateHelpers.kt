package game.helpers

import game.domain.GameState
import game.domain.Message
import game.domain.Phase
import game.domain.Role
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
                            || (0..day).any { it < config.artifacts && stolenArtifacts[it] })
        }

        else -> true
    } && alive[player]
}

fun GameState.ansValidator(str: String): Exception? {
    val s = str.lowercase().trim()
    return when (phase) {
        Phase.TEAM_CREATING -> {
            if (s.isEmpty()) {
                return IllegalArgumentException("Список игроков не может быть пустым. Введи ${config.team} номеров через запятую (например: 2, 3)")
            }
            val parts = s.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size != config.team) {
                return IllegalArgumentException("Нужно выбрать ровно ${config.team} игроков через запятую (выбрано: ${parts.size})")
            }
            val indices = parts.mapNotNull { it.toIntOrNull() }
            if (indices.size != parts.size) {
                return IllegalArgumentException("Номера игроков должны быть числами через запятую")
            }
            if (indices.distinct().size != config.team) {
                return IllegalArgumentException("Номера игроков в команде не должны повторяться")
            }
            for (num in indices) {
                if (num !in 1..config.playersNum) {
                    return IllegalArgumentException("Игрок с номером $num не существует (номера игроков от 1 до ${config.playersNum})")
                }
                if (roles[num - 1] == Role.MAFIA_HEAD) {
                    return IllegalArgumentException("Нельзя выбирать Главу Мафии (самого себя) в команду на вылазку")
                }
                if (!alive[num - 1]) {
                    return IllegalArgumentException("Игрок $num уже исключен из игры")
                }
            }
            null
        }

        Phase.DAY_CONCLUSION -> {
            val parts = s.split("\\s+".toRegex())
            if (parts.size < 2) {
                return IllegalArgumentException("Неверный формат команды. Используй: 'Исключить <номер>' или 'Проверить <номер>'")
            }
            val command = parts[0]
            val target = parts[1].toIntOrNull()
                ?: return IllegalArgumentException("Параметр команды должен быть числом, получено: '${parts[1]}'")

            when (command) {
                "исключить" -> {
                    if (target !in 1..config.playersNum) {
                        return IllegalArgumentException("Игрок с номером $target не существует (номера игроков от 1 до ${config.playersNum})")
                    }
                    if (!alive[target - 1]) {
                        return IllegalArgumentException("Игрок $target уже исключен из игры")
                    }
                    null
                }

                "проверить" -> {
                    if (day == 0) {
                        return IllegalArgumentException("В первый день нельзя проверить артефакт (вылазок еще не было)")
                    }
                    if (target !in 1..day) {
                        return IllegalArgumentException("Нельзя проверить артефакт $target (доступны для проверки дни от 1 до $day)")
                    }
                    null
                }

                else -> IllegalArgumentException("Неизвестная команда '$command'. Доступные команды: 'Исключить <номер>' или 'Проверить <номер>'")
            }
        }

        Phase.NIGHT_CONCLUSION -> {
            val parts = s.split("\\s+".toRegex())
            if (parts.size < 2) {
                return IllegalArgumentException("Неверный формат команды. Используй: 'Шантажировать <номер>' или 'Вернуть <номер>'")
            }
            val command = parts[0]
            val target = parts[1].toIntOrNull()
                ?: return IllegalArgumentException("Параметр команды должен быть числом, получено: '${parts[1]}'")

            when (command) {
                "шантажировать" -> {
                    if (target !in 1..config.playersNum) {
                        return IllegalArgumentException("Игрок с номером $target не существует (номера игроков от 1 до ${config.playersNum})")
                    }
                    if (!alive[target - 1]) {
                        return IllegalArgumentException("Игрок $target уже исключен из игры")
                    }
                    if (roles[target - 1] != Role.MAFIA) {
                        return IllegalArgumentException("Шантажировать можно только рядовых членов мафии (игрок $target не является рядовой мафией)")
                    }
                    null
                }

                "вернуть" -> {
                    if (target !in 1..(day + 1) || target > config.artifacts) {
                        return IllegalArgumentException("Артефакт $target не существует или еще не разыгран")
                    }
                    if (!stolenArtifacts[target - 1]) {
                        return IllegalArgumentException("Артефакт $target не был украден мафией (его нельзя вернуть)")
                    }
                    null
                }

                else -> IllegalArgumentException("Неизвестная команда '$command'. Доступные команды: 'Шантажировать <номер>' или 'Вернуть <номер>'")
            }
        }

        else -> null
    }
}

fun GameState.ansProcessor(str: String, seed: Long): List<Message> {
    val s = str.lowercase().trim()
    val messages = mutableListOf<Message>()
    when (phase) {
        Phase.DAY_CONVERSATION -> {
            if (corrupted[player]) {
                corrupted[player] = false
                messages.add(Message("тебя шантажировали", -player - 1, false))
            }
        }

        Phase.TEAM_CREATING -> {
            val parts = s.split(",").mapNotNull { it.trim().toIntOrNull() }
            if (parts.size == config.team) {
                stolenArtifacts[day] = parts.all { it in 1..config.playersNum && roles[it - 1] == Role.MAFIA }
            }
        }

        Phase.DAY_CONCLUSION -> {
            val parts = s.split("\\s+".toRegex())
            val int = parts.getOrNull(1)?.toIntOrNull()
            if (int != null) {
                if (parts[0] == "исключить" && int in 1..config.playersNum) {
                    alive[int - 1] = false
                    if (roles[int - 1] == Role.AGENT_HEAD) {
                        val lst = (0..<config.playersNum).filter { alive[it] && roles[it] == Role.AGENT }
                        if (lst.isNotEmpty()) {
                            roles[lst.random(Random(seed))] = Role.AGENT_HEAD
                        }
                    }
                } else if (parts[0] == "проверить" && int in 1..day) {
                    val real = if (stolenArtifacts[int - 1]) {
                        val success = Random(seed).nextDouble(0.0, 1.0)
                        success > config.checkChance
                    } else {
                        true
                    }
                    if (real) {
                        messages.add(Message("артефакт был настоящий", 0, false))
                    } else {
                        messages.add(Message("артефакт был поддельный", 0, false))
                    }
                }
            }
        }

        Phase.NIGHT_CONCLUSION -> {
            val parts = s.split("\\s+".toRegex())
            val int = parts.getOrNull(1)?.toIntOrNull()
            if (int != null) {
                if (parts[0] == "шантажировать" && int in 1..config.playersNum) {
                    corrupted[int - 1] = true
                } else if (parts[0] == "вернуть" && int - 1 in 0..day && int - 1 < config.artifacts) {
                    val success = Random(seed).nextDouble(0.0, 1.0)
                    val aliveAgents = (0..<config.playersNum).count {
                        alive[it] && (roles[it] == Role.AGENT || roles[it] == Role.AGENT_HEAD)
                    }
                    val chance = (config.regainChance - MIN_REGAIN_CHANCE) * aliveAgents / config.agents + MIN_REGAIN_CHANCE
                    stolenArtifacts[int - 1] = success < chance
                }
            }
        }

        else -> {}
    }
    return messages
}

fun GameState.iteratePhase(str: String, seed: Long): List<Message> {
    val messages = mutableListOf<Message>()
    messages.add(Message(str, player + 1, phase.getSecret()))
    messages.addAll(ansProcessor(str, seed))

    var loopGuard = 0
    val maxLoops = config.playersNum * Phase.entries.size * 2 + 10
    do {
        player++
        if (player >= config.playersNum) {
            player = 0
            phase = phase.nextPhase()
            if (phase == Phase.DAY_CONVERSATION) {
                day++
            }
        }
        loopGuard++
        if (day >= config.artifacts || loopGuard > maxLoops) {
            break
        }
    } while (!phaseValidator())

    return messages
}

fun GameState.clone(): GameState {
    return GameState(
        config,
        this.playersId.clone(),
        roles.clone(),
        stolenArtifacts.clone(),
        alive.clone(),
        corrupted.clone(),
        phase,
        day,
        player
    )
}

fun GameState.randomChoice(): String {
    return when (phase) {
        Phase.TEAM_CREATING -> {
            (1..config.playersNum)
                .filter { alive[it - 1] && roles[it - 1] != Role.MAFIA_HEAD }
                .shuffled()
                .take(config.team)
                .joinToString(separator = ", ")
        }

        Phase.DAY_CONCLUSION -> {
            val canCheck = day > 0
            val possiblePersons = (0..<config.playersNum).filter { alive[it] && roles[it] != Role.MAFIA_HEAD }.toList()
            val action = if (canCheck && possiblePersons.isNotEmpty()) {
                listOf("проверить", "исключить").random()
            } else if (possiblePersons.isNotEmpty()) {
                "исключить"
            } else {
                "проверить"
            }

            if (action == "исключить" && possiblePersons.isNotEmpty()) {
                "исключить ${possiblePersons.random() + 1}"
            } else if (canCheck) {
                "проверить ${(1..day).random()}"
            } else {
                "пропустить"
            }
        }

        Phase.NIGHT_CONCLUSION -> {
            val possibleToReturn = (0..day).filter { it < config.artifacts && stolenArtifacts[it] }.toList()
            val possibleToCorrupt = (0..<config.playersNum).filter { alive[it] && roles[it] == Role.MAFIA }.toList()

            val action = if (possibleToReturn.isNotEmpty() && possibleToCorrupt.isNotEmpty()) {
                listOf("вернуть", "шантажировать").random()
            } else if (possibleToReturn.isNotEmpty()) {
                "вернуть"
            } else if (possibleToCorrupt.isNotEmpty()) {
                "шантажировать"
            } else {
                "вернуть"
            }

            if (action == "вернуть" && possibleToReturn.isNotEmpty()) {
                "вернуть ${possibleToReturn.random() + 1}"
            } else if (possibleToCorrupt.isNotEmpty()) {
                "шантажировать ${possibleToCorrupt.random() + 1}"
            } else {
                "вернуть 1"
            }
        }

        else -> "слишком долго думал"
    }
}