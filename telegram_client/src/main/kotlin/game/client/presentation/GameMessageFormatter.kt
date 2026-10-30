package game.client.presentation

import game.domain.DisconnectEvent
import game.domain.Event
import game.domain.GameId
import game.domain.GameState
import game.domain.MessageEvent
import game.domain.Phase
import game.domain.Role
import game.helpers.description
import game.helpers.getSecret
import game.helpers.getString
import game.helpers.outputFormatDescription
import org.springframework.stereotype.Component

@Component
class GameMessageFormatter {

    fun formatStart(): String {
        return buildString {
            appendLine("Привет! Это игра *Странная мафия*.")
            appendLine()
            appendLine("Команды:")
            appendLine("• `/create` - создать новую игру и получить ID для друзей")
            appendLine("• `/join <ID>` - присоединиться к игре друзей по ID")
            appendLine("• `/join` - присоединиться к случайной открытой игре")
            appendLine("• `/help` - узнать правила игры")
        }.trimEnd()
    }

    fun formatHelp(): String {
        return buildString {
            appendLine("*Роли в игре:*")
            Role.entries.forEach { appendLine("• *${it.getString()}*") }
            appendLine()
            appendLine("*Фазы каждого дня:*")
            Phase.entries.forEach { appendLine("• *${it.getString()}*: ${it.description()}") }
        }.trimEnd()
    }

    fun formatGameCreated(gameId: GameId, lobbyUserCount: Int): String {
        return buildString {
            appendLine("*Игра успешно создана!*")
            appendLine()
            appendLine("ID твоей игры: `${gameId.id}`")
            appendLine()
            appendLine("Отправь этот ID друзьям. Они смогут подключиться командой:")
            appendLine("`/join ${gameId.id}`")
            appendLine()
            appendLine("Ожидаем игроков (сейчас в лобби: $lobbyUserCount).")
        }.trimEnd()
    }

    fun formatGameJoined(gameId: GameId, lobbyUserCount: Int): String {
        return buildString {
            appendLine("*Успешно подключились к игре!*")
            appendLine("ID игры: `${gameId.id}`")
            appendLine()
            appendLine("Ожидаем старта игры (сейчас в лобби: $lobbyUserCount).")
        }.trimEnd()
    }

    fun formatLobbyPlayerJoined(lobbyUserCount: Int): String {
        return "Новый игрок подключился к игре! (Всего игроков: $lobbyUserCount)\n\nОжидаем начала партии."
    }

    fun formatGameIntro(state: GameState, playerIndex: Int): String {
        val role = state.roles[playerIndex]
        return buildString {
            appendLine("*Игра началась!*")
            appendLine("Твой номер: *Игрок ${playerIndex + 1}*")
            appendLine("Твоя роль: *${role.getString()}*")
            appendLine()
            appendLine("*Игроки в партии:*")
            for (j in 0 until state.config.playersNum) {
                val otherRole = when {
                    j == playerIndex -> " — твой персонаж (${role.getString()})"
                    role.getSecret() -> " — ${state.roles[j].getString()}"
                    state.roles[j] == Role.MAFIA_HEAD -> " — Глава Мафии"
                    else -> ""
                }
                val name = if (state.playersId[j].startsWith("bot")) "Бот ${j + 1}" else "Игрок ${j + 1}"
                appendLine("• Игрок ${j + 1} ($name)$otherRole")
            }
            appendLine()
            appendLine("Всего артефактов / раундов: *${state.config.artifacts}*")
            appendLine("Ожидайте своей очереди хода...")
        }.trimEnd()
    }

    fun formatTurnPrompt(state: GameState, playerIndex: Int): String {
        val phase = state.phase
        val day = state.day

        return buildString {
            appendLine("=== День ${day + 1} ===")
            appendLine("Фаза: ${phase.getString()}")
            appendLine("Ты игрок номер ${playerIndex + 1}")
            appendLine()
            appendLine(phase.description())
            appendLine()
            appendLine("Формат ответа: ${phase.outputFormatDescription()}")
            appendLine()
            appendLine("Твоя роль: ${state.roles[playerIndex].getString()}")
            appendLine()

            when (phase) {
                Phase.DAY_CONVERSATION -> {}
                Phase.TEAM_CREATING -> {
                    appendLine("Нужно выбрать ${state.config.team} игроков")
                    appendLine("Доступные игроки для выбора:")
                    for (i in 0 until state.config.playersNum) {
                        if (state.alive[i] && state.roles[i] != Role.MAFIA_HEAD) {
                            appendLine("Игрок ${i + 1}")
                        }
                    }
                }
                Phase.DAY_CONCLUSION -> {}
                Phase.NIGHT_CONVERSATION -> {
                    appendLine("Состояние артефактов:")
                    for (i in 0 until state.config.artifacts) {
                        if (!state.stolenArtifacts[i]) {
                            appendLine("Артефакт ${i + 1}: на месте")
                        } else {
                            appendLine("Артефакт ${i + 1}: украден")
                        }
                    }
                }
                Phase.NIGHT_CONCLUSION -> {}
            }

            if (state.roles[playerIndex].getSecret()) {
                for (i in 0 until state.config.playersNum) {
                    appendLine("Игрок ${i + 1}: ${state.roles[i].getString()}")
                }
            } else {
                (0 until state.config.playersNum)
                    .filter { state.roles[it] == Role.MAFIA_HEAD }
                    .forEach { appendLine("Игрок ${it + 1}: ${state.roles[it].getString()}") }
            }

            appendLine("Осталось дней: ${state.config.artifacts - day}")
        }.trimEnd()
    }

    fun formatEvent(event: Event, state: GameState, recipientIndex: Int): String? {
        return when (event) {
            is MessageEvent -> {
                val message = event.message
                if (message.userNum == recipientIndex + 1) return null
                if (message.secret && !state.roles[recipientIndex].getSecret()) return null
                if (message.userNum < 0 && recipientIndex != -message.userNum - 1) return null
                if (message.userNum <= 0) {
                    message.string
                } else {
                    "Игрок ${message.userNum}: ${message.string}"
                }
            }
            is DisconnectEvent -> {
                "Игрок ${event.index + 1} отключился (заменен на бота)"
            }
        }
    }

    fun formatNotYourTurn(): String {
        return "Сейчас не твой ход. Дождись своей очереди!"
    }

    fun formatValidationError(error: String): String {
        return "Ошибка: $error\n\nПопробуй еще раз:"
    }

    fun formatGameFinished(): String {
        return "*Игра окончена!*\nСпасибо за игру!"
    }
}
