package game.helpers

import game.domain.GameConfig
import game.domain.GameState
import game.domain.Message
import game.domain.Phase
import game.domain.Role

fun GameConfig.gameDescription(): String {
    return buildString {
        appendLine("Правила игры 'Странная мафия':")
        appendLine("- В игре участвуют $playersNum игроков.")
        appendLine("- Две противоборствующие стороны: Мафия и тайные Агенты ($agents агентов).")
        appendLine("- Агенты знают роли друг друга. Мафия не знает, кто агент, но знает, кто Глава Мафии.")
        appendLine("- Игра длится до $artifacts дней (или пока одна из сторон не победит).")
        appendLine("- Каждый игровой день состоит из следующих фаз:")
        appendLine("  1. Дневное совещание: общее обсуждение (сообщения видят все).")
        appendLine("  2. Выбор команды: Глава Мафии выбирает $team игроков на вылазку за артефактом.")
        appendLine("     Если в команде окажется агент, вылазка будет саботирована (мафия получит фальшивку).")
        appendLine("  3. Заключение дня: Глава Мафии может исключить игрока или проверить артефакт.")
        appendLine("  4. Ночное совещание: тайное обсуждение агентов (мафия не видит эти сообщения).")
        appendLine("  5. Заключение ночи: Глава Агентов может завербовать (шантажировать) игрока мафии или вернуть артефакт.")
    }.trimEnd()
}

fun GameState.gameDescription(): String = config.gameDescription()


fun getAiInitialPrompt(role: Role, config: GameConfig): String {
    return buildString {
        appendLine("Ты играешь в игру 'Странная мафия'. Твоя роль: ${role.getString().uppercase()}.")
        appendLine()
        appendLine("=== ЦЕЛЬ ТВОЕЙ КОМАНДЫ ===")
        when (role) {
            Role.MAFIA_HEAD -> {
                appendLine("Ты - ГЛАВА МАФИИ. Ты ведешь мафию к победе.")
                appendLine("- Набирай надежных игроков в команду на вылазку (${config.team} игроков). Не бери агентов!")
                appendLine("- На фазе заключения дня исключай подозреваемых агентов или проверяй подозрительные артефакты.")
            }
            Role.MAFIA -> {
                appendLine("Ты - член МАФИИ. Твоя задача - помогать Главе Мафии вычислять агентов.")
                appendLine("- Агенты притворяются мафией. Следи за их поведением и предлагай Главе Мафии, кого отправить на вылазку или исключить.")
            }
            Role.AGENT_HEAD -> {
                appendLine("Ты - ГЛАВА АГЕНТОВ. Ты знаешь всех своих союзников-агентов, а мафия вас не знает!")
                appendLine("- Днем притворяйся обычной мафией, чтобы тебя не заподозрили.")
                appendLine("- В первый день не спеши с подозрениями и обвинениями, так как многие игроки еще не успели высказаться - веди себя сдержанно и естественно.")
                appendLine("- Старайся продвигать агентов в команду на вылазку для саботажа.")
                appendLine("- Ночью ты тайно управляешь агентами: можешь завербовать (шантажировать) мафию или вернуть артефакт.")
            }
            Role.AGENT -> {
                appendLine("Ты - тайный АГЕНТ. Ты знаешь всех своих союзников-агентов, а мафия вас не знает!")
                appendLine("- Днем ни в коем случае не признавайся, что ты агент. Притворяйся обычной мафией.")
                appendLine("- В первый день не спеши с подозрениями и обвинениями, так как многие игроки еще не успели высказаться - веди себя сдержанно и естественно.")
                appendLine("- Если тебя выберут в команду на вылазку, ты автоматически саботируешь добычу артефакта.")
            }
        }
        appendLine()
        appendLine("=== ОБОЗНАЧЕНИЯ В ЧАТЕ ===")
        appendLine("- Ведущий: сообщения системы об игровых событиях.")
        appendLine("- Игрок 1, Игрок 2, ...: реплики других игроков.")
        appendLine("- Днем сообщения видны всем, ночью - только агентам.")
        appendLine()
        appendLine("=== ПРАВИЛА ИГРЫ ===")
        append(config.gameDescription())
    }.trimEnd()
}

fun GameState.getAiDescription(role: Role = roles[player]): String = getAiInitialPrompt(role, config)

fun GameState.getRolesDescription(playerIndex: Int = player): String {
    return buildString {
        val isSecret = roles[playerIndex].getSecret()
        for (i in 0..<config.playersNum) {
            val num = i + 1
            val status = if (alive[i]) "жив" else "исключен"
            val isSelf = if (i == playerIndex) " (это ты)" else ""
            if (isSecret) {
                appendLine("- Игрок $num: ${roles[i].getString()}, $status$isSelf")
            } else {
                if (roles[i] == Role.MAFIA_HEAD) {
                    appendLine("- Игрок $num: глава мафии, $status$isSelf")
                } else if (i == playerIndex) {
                    appendLine("- Игрок $num: ${roles[i].getString()}, $status$isSelf")
                } else {
                    appendLine("- Игрок $num: статус $status")
                }
            }
        }
    }.trimEnd()
}

fun formatMessagesForPrompt(
    messages: List<Message>,
    playerIndex: Int,
    isSecret: Boolean
): String {
    val visible = if (isSecret) {
        messages.filter { it.userNum >= 0 || it.userNum == -playerIndex - 1 }
    } else {
        messages.filter { (it.userNum >= 0 || it.userNum == -playerIndex - 1) && !it.secret }
    }
    if (visible.isEmpty()) {
        return "(нет новых событий)"
    }
    return visible.joinToString(separator = "\n") { msg ->
        when {
            msg.userNum == 0 -> "Ведущий: ${msg.string}"
            msg.userNum == -playerIndex - 1 -> "Секретно для тебя: ${msg.string}"
            msg.userNum > 0 -> "Игрок ${msg.userNum}: ${msg.string}"
            else -> "Игрок ${-msg.userNum}: ${msg.string}"
        }
    }
}

fun GameState.mergeMessagesForPrompt(
    messages: List<Message>,
    playerIndex: Int = player,
    isSecret: Boolean = roles[playerIndex].getSecret()
): String = formatMessagesForPrompt(messages, playerIndex, isSecret)

fun GameState.mergeMessagesForPrompt(
    messages: List<Message>,
    isSecret: Boolean
): String = formatMessagesForPrompt(messages, player, isSecret)

fun GameState.getPhaseInstruction(playerIndex: Int = player): String {
    val role = roles[playerIndex]
    return when (phase) {
        Phase.DAY_CONVERSATION -> {
            val firstDayNote = if (day == 0) {
                " В первый день игры не спеши с подозрениями и обвинениями, так как некоторые игроки еще не успели высказаться; лучше поздоровайся, поделись первыми мыслями по игре."
            } else ""
            when (role) {
                Role.MAFIA_HEAD -> "Идет дневное совещание. Выслушай игроков, выскажи свои мысли.$firstDayNote Напиши 1-2 предложения реплики в чат."
                Role.MAFIA -> "Идет дневное совещание. Обсуди вылазку, выскажи мысли или поддержи Главу Мафии.$firstDayNote Напиши 1-2 предложения реплики в чат."
                Role.AGENT, Role.AGENT_HEAD -> "Идет дневное совещание. Притворяйся честным игроком мафии, не выдавай свою тайную роль.$firstDayNote Напиши 1-2 предложения реплики в чат."
            }
        }

        Phase.TEAM_CREATING -> {
            val aliveOthers = (0..<config.playersNum).filter { alive[it] && it != playerIndex }.map { it + 1 }
            val exampleList = aliveOthers.take(config.team).joinToString(", ")
            buildString {
                appendLine("Ты выбираешь команду из ${config.team} игроков на вылазку.")
                appendLine("Доступные живые игроки (кроме тебя): ${aliveOthers.joinToString(", ")}.")
                appendLine("ФОРМАТ ОТВЕТА: строго одна строка с ${config.team} номерами через запятую.")
                append("ПРИМЕР: $exampleList")
            }
        }

        Phase.DAY_CONCLUSION -> {
            val aliveTargets = (0..<config.playersNum).filter { alive[it] && it != playerIndex }.map { it + 1 }
            val checkableArtifacts = (1..day).filter { it <= config.artifacts }
            buildString {
                appendLine("Заключение дня. Выбери ровно одно действие:")
                appendLine("1) Исключить подозрительного игрока: 'Исключить <номер>' (живые игроки: ${aliveTargets.joinToString(", ")})")
                if (checkableArtifacts.isNotEmpty()) {
                    appendLine("2) Проверить подлинность артефакта за прошлые дни: 'Проверить <номер>' (дни от 1 до $day)")
                }
                appendLine("ФОРМАТ ОТВЕТА: строго одна строка.")
                val exampleTarget = aliveTargets.firstOrNull() ?: 2
                append("ПРИМЕР: Исключить $exampleTarget")
            }
        }

        Phase.NIGHT_CONVERSATION -> {
            "Ночное тайное совещание агентов (мафия не видит эти сообщения). Обсудите тактику и действия на ночь. Напиши 1-2 предложения реплики в чат."
        }

        Phase.NIGHT_CONCLUSION -> {
            val mafiaTargets = (0..<config.playersNum).filter { alive[it] && roles[it] == Role.MAFIA }.map { it + 1 }
            val returnableArtifacts = (0..day).filter { it < config.artifacts && stolenArtifacts[it] }.map { it + 1 }
            buildString {
                appendLine("Заключение ночи. Выбери ровно одно действие:")
                if (mafiaTargets.isNotEmpty()) {
                    appendLine("1) Завербовать (шантажировать) рядового члена мафии: 'Шантажировать <номер>' (доступная мафия: ${mafiaTargets.joinToString(", ")})")
                }
                if (returnableArtifacts.isNotEmpty()) {
                    appendLine("2) Вернуть украденный артефакт: 'Вернуть <номер>' (украденные артефакты: ${returnableArtifacts.joinToString(", ")})")
                }
                appendLine("ФОРМАТ ОТВЕТА: строго одна строка.")
                if (mafiaTargets.isNotEmpty()) {
                    append("ПРИМЕР: Шантажировать ${mafiaTargets.first()}")
                } else if (returnableArtifacts.isNotEmpty()) {
                    append("ПРИМЕР: Вернуть ${returnableArtifacts.first()}")
                } else {
                    append("ПРИМЕР: Шантажировать 1")
                }
            }
        }
    }
}

fun GameState.aiPrompt(
    previousActionsPrompt: String,
    playerIndex: Int = player
): String {
    val role = roles[playerIndex]
    return buildString {
        appendLine("=== ТВОЙ СТАТУС ===")
        appendLine("Ты: Игрок ${playerIndex + 1}")
        appendLine("Твоя роль: ${role.getString().uppercase()}")
        appendLine("День игры: ${day + 1}, стадия: ${phase.getString()}")
        appendLine()
        appendLine("=== ИГРОКИ И РОЛИ ===")
        appendLine(getRolesDescription(playerIndex))
        appendLine()
        appendLine("=== ПОСЛЕДНИЕ СОБЫТИЯ ===")
        appendLine(previousActionsPrompt.ifBlank { "(нет новых событий)" })
        appendLine()
        appendLine("=== ТВОЙ ХОД ===")
        appendLine(getPhaseInstruction(playerIndex))
        appendLine()
        appendLine("ВНИМАНИЕ: выведи только требуемый ответ без кавычек, пояснений и лишнего текста.")
    }.trimEnd()
}

fun GameState.aiPrompt(
    messages: List<Message>,
    playerIndex: Int = player
): String {
    val formattedMessages = mergeMessagesForPrompt(messages, playerIndex)
    return aiPrompt(formattedMessages, playerIndex)
}
