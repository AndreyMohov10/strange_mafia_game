package game.client

import game.domain.GameState
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import game.domain.Phase
import game.domain.Role
import game.helpers.description
import game.helpers.getSecret
import game.helpers.getString
import game.helpers.outputFormatDescription
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ForceReplyKeyboard

fun createMessage(state: GameState, id: Long): SendMessage {
    val phase = state.phase
    val day = state.day

    val builder = SendMessage.builder()
        .chatId(id)

    val text = buildString {
        appendLine("=== День ${day + 1} ===")
        appendLine("Фаза: ${phase.getString()}")
        appendLine("ты игрок номер ${state.player + 1}")
        appendLine()
        appendLine(phase.description())
        appendLine()
        appendLine("Формат ответа: ${phase.outputFormatDescription()}")
        appendLine()
        appendLine("твоя роль: ${state.roles[state.player].getString()}")
        appendLine()

        when (phase) {
            Phase.DAY_CONVERSATION -> {
            }

            Phase.TEAM_CREATING -> {
                appendLine("нужно выбрать ${state.config.team} игроков")
                appendLine("Доступные игроки для выбора:")
                for (i in 0..<state.config.playersNum) {
                    if (state.alive[i] && state.roles[i] != Role.MAFIA_HEAD) {
                        appendLine("игрок ${i + 1}")
                    }
                }
            }

            Phase.DAY_CONCLUSION -> {}

            Phase.NIGHT_CONVERSATION -> {
                appendLine("Состояние артефактов:")
                for (i in 0..<state.config.artifacts) {
                    if (!state.stolenArtifacts[i]) {
                        appendLine("Артефакт ${i + 1}: на месте")
                    } else {
                        appendLine("Артефакт ${i + 1}: украден")
                    }
                }
            }

            Phase.NIGHT_CONCLUSION -> {}
        }

        if (state.roles[state.player].getSecret()) {
            for (i in 0..<state.config.playersNum) {
                appendLine("игрок ${i + 1}: ${state.roles[i].getString()}")
            }
        } else {
            (0..<state.config.playersNum).filter { state.roles[it] == Role.MAFIA_HEAD }.forEach {
                appendLine("игрок ${it + 1}: ${state.roles[it].getString()}")
            }
        }

        appendLine("Осталось дней: ${state.config.artifacts - day}")
    }

    builder.text(text)
    builder.replyMarkup(
        ForceReplyKeyboard.builder()
            .inputFieldPlaceholder("Введите ваш ответ...")
            .build()
    )

    return builder.build()
}
