package game.client.telegram.handler

import game.client.presentation.GameMessageFormatter
import game.client.session.GameSessionManager
import game.client.telegram.bridge.TelegramGameBridge
import game.client.telegram.sender.TelegramMessageSender
import game.domain.GameId
import org.springframework.stereotype.Component

@Component
class TelegramCommandHandler(
    private val sessionManager: GameSessionManager,
    private val formatter: GameMessageFormatter,
    private val messageSender: TelegramMessageSender,
    private val gameBridge: TelegramGameBridge
) {

    fun handleStart(chatId: Long) {
        val text = formatter.formatStart()
        messageSender.sendMessage(chatId, text, "Markdown")
    }

    fun handleHelp(chatId: Long) {
        val text = formatter.formatHelp()
        messageSender.sendMessage(chatId, text, "Markdown")
    }

    fun handleCreate(chatId: Long) {
        val currentGame = sessionManager.getGameForUser(chatId)
        if (currentGame != null) {
            messageSender.sendMessage(chatId, "⚠️ Ты уже участвуешь в активной игре!")
            return
        }

        val result = sessionManager.createGame(chatId, gameBridge)
        result.fold(
            onSuccess = { gameId ->
                val count = sessionManager.getLobbyUserCount(gameId)
                val text = formatter.formatGameCreated(gameId, count)
                messageSender.sendMessage(chatId, text, "Markdown")
            },
            onFailure = { e ->
                messageSender.sendMessage(chatId, "❌ Ошибка создания игры: ${e.message ?: "неизвестная ошибка"}")
            }
        )
    }

    fun handleJoin(chatId: Long, gameIdStr: String?) {
        val currentGame = sessionManager.getGameForUser(chatId)
        if (currentGame != null) {
            messageSender.sendMessage(chatId, "⚠️ Ты уже участвуешь в активной игре!")
            return
        }

        val targetGameId = gameIdStr?.takeIf { it.isNotBlank() }?.let { GameId(it) }
        val result = sessionManager.joinGame(chatId, targetGameId, gameBridge)

        result.fold(
            onSuccess = { (joinedId, _) ->
                val count = sessionManager.getLobbyUserCount(joinedId)
                val text = formatter.formatGameJoined(joinedId, count)
                messageSender.sendMessage(chatId, text, "Markdown")

                val session = sessionManager.getSession(joinedId)
                session?.getUsers()?.filter { it != chatId }?.forEach { otherId ->
                    val notifyText = formatter.formatLobbyPlayerJoined(count)
                    messageSender.sendMessage(otherId, notifyText, "Markdown")
                }
            },
            onFailure = { e ->
                val errorMsg = e.message ?: "Игра не найдена или не удалось подключиться"
                messageSender.sendMessage(chatId, "❌ $errorMsg")
            }
        )
    }
}
