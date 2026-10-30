package game.client.telegram.handler

import game.client.session.GameSessionManager
import game.client.telegram.sender.TelegramMessageSender
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class TelegramUpdateRouter(
    private val commandHandler: TelegramCommandHandler,
    private val inGameActionHandler: InGameActionHandler,
    private val sessionManager: GameSessionManager,
    private val messageSender: TelegramMessageSender
) {

    fun route(update: Update) {
        if (!update.hasMessage() || !update.message.hasText()) return

        val chatId = update.message.chatId
        val text = update.message.text.trim()

        when {
            text.equals("/start", ignoreCase = true) -> {
                commandHandler.handleStart(chatId)
            }

            text.equals("/help", ignoreCase = true) -> {
                commandHandler.handleHelp(chatId)
            }

            text.equals("/create", ignoreCase = true) -> {
                commandHandler.handleCreate(chatId)
            }

            text.equals("/join", ignoreCase = true) -> {
                commandHandler.handleJoin(chatId, null)
            }

            text.startsWith("/join ", ignoreCase = true) -> {
                val gameId = text.substringAfter("/join ").trim()
                commandHandler.handleJoin(chatId, gameId)
            }

            else -> {
                val gameSession = sessionManager.getGameForUser(chatId)
                if (gameSession != null) {
                    inGameActionHandler.handleInGameMessage(chatId, text, gameSession)
                } else {
                    messageSender.sendMessage(
                        chatId,
                        "Ты пока не находишься в игре. Создай новую игру командой `/create` или подключись к существующей через `/join <ID>`."
                    )
                }
            }
        }
    }
}
