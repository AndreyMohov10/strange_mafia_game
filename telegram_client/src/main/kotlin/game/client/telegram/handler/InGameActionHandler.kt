package game.client.telegram.handler

import game.client.presentation.GameMessageFormatter
import game.client.session.GameSession
import game.client.telegram.sender.TelegramMessageSender
import game.helpers.ansValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.springframework.stereotype.Component

@Component
class InGameActionHandler(
    private val formatter: GameMessageFormatter,
    private val messageSender: TelegramMessageSender,
    private val clientScope: CoroutineScope
) {

    fun handleInGameMessage(chatId: Long, text: String, session: GameSession) {
        if (!session.isCurrentPlayer(chatId)) {
            messageSender.sendMessage(chatId, formatter.formatNotYourTurn())
            return
        }

        val state = session.state
        if (state == null) {
            messageSender.sendMessage(chatId, "⏳ Игра еще готовится, подожди немного...")
            return
        }

        val validationError = state.ansValidator(text)
        if (validationError != null) {
            val errorMsg = validationError.message ?: "Неправильный формат ввода"
            messageSender.sendMessage(chatId, formatter.formatValidationError(errorMsg))

            val playerIndex = state.playersId.indexOf(chatId.toString()).takeIf { it >= 0 } ?: state.player
            val prompt = formatter.formatTurnPrompt(state, playerIndex)
            messageSender.sendForceReply(chatId, prompt)
            return
        }

        clientScope.launch {
            val result = session.submitAction(chatId, text)
            result.onFailure { e ->
                messageSender.sendMessage(chatId, "❌ Ошибка при отправке действия: ${e.message}")
            }
        }
    }
}
