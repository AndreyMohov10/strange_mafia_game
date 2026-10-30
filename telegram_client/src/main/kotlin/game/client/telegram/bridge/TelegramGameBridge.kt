package game.client.telegram.bridge

import game.client.presentation.GameMessageFormatter
import game.client.session.GameSessionListener
import game.client.telegram.sender.TelegramMessageSender
import game.domain.Event
import game.domain.GameId
import game.domain.GameState
import org.springframework.stereotype.Component

@Component
class TelegramGameBridge(
    private val messageSender: TelegramMessageSender,
    private val formatter: GameMessageFormatter
) : GameSessionListener {

    override fun onGameStarted(gameId: GameId, state: GameState, userIds: List<Long>) {
        for (userId in userIds) {
            val playerIndex = state.playersId.indexOf(userId.toString())
            if (playerIndex >= 0) {
                val intro = formatter.formatGameIntro(state, playerIndex)
                messageSender.sendMessage(userId, intro, "Markdown")
            }
        }
    }

    override fun onTurnRequired(gameId: GameId, state: GameState, currentUserId: Long) {
        val playerIndex = state.playersId.indexOf(currentUserId.toString()).takeIf { it >= 0 } ?: state.player
        val prompt = formatter.formatTurnPrompt(state, playerIndex)
        messageSender.sendForceReply(currentUserId, prompt)
    }

    override fun onEvent(gameId: GameId, state: GameState, event: Event, userIds: List<Long>) {
        for (userId in userIds) {
            val playerIndex = state.playersId.indexOf(userId.toString())
            if (playerIndex >= 0) {
                val formattedMessage = formatter.formatEvent(event, state, playerIndex)
                if (formattedMessage != null) {
                    messageSender.sendMessage(userId, formattedMessage, null)
                }
            }
        }
    }

    override fun onGameFinished(gameId: GameId, state: GameState, userIds: List<Long>) {
        for (userId in userIds) {
            val finishedText = formatter.formatGameFinished()
            messageSender.sendMessage(userId, finishedText, "Markdown")
        }
    }
}
