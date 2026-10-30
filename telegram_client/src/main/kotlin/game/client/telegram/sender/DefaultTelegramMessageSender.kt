package game.client.telegram.sender

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ForceReplyKeyboard
import org.telegram.telegrambots.meta.generics.TelegramClient

@Component
class DefaultTelegramMessageSender(
    @param:Autowired(required = false) private val telegramClient: TelegramClient?
) : TelegramMessageSender {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun sendMessage(chatId: Long, text: String, parseMode: String?) {
        if (telegramClient == null) {
            logger.warn("TelegramClient не настроен, пропуск отправки сообщения для $chatId: $text")
            return
        }

        val builder = SendMessage.builder()
            .chatId(chatId)
            .text(text)

        if (parseMode != null) {
            builder.parseMode(parseMode)
        }

        try {
            telegramClient.execute(builder.build())
        } catch (e: Exception) {
            if (parseMode != null) {
                try {
                    val plainMessage = SendMessage.builder()
                        .chatId(chatId)
                        .text(text)
                        .build()
                    telegramClient.execute(plainMessage)
                    return
                } catch (retryEx: Exception) {
                    logger.error("Ошибка при повторной отправке простого сообщения пользователю $chatId: ${retryEx.message}")
                }
            }
            logger.error("Ошибка при отправке сообщения пользователю $chatId: ${e.message}")
        }
    }

    override fun sendForceReply(chatId: Long, text: String, placeholder: String) {
        if (telegramClient == null) {
            logger.warn("TelegramClient не настроен, пропуск отправки ForceReply для $chatId: $text")
            return
        }

        val message = SendMessage.builder()
            .chatId(chatId)
            .text(text)
            .replyMarkup(
                ForceReplyKeyboard.builder()
                    .inputFieldPlaceholder(placeholder)
                    .build()
            )
            .build()

        try {
            telegramClient.execute(message)
        } catch (e: Exception) {
            logger.error("Ошибка при отправке ForceReply пользователю $chatId: ${e.message}")
        }
    }
}
