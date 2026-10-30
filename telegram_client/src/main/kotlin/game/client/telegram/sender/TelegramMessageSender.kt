package game.client.telegram.sender

interface TelegramMessageSender {
    fun sendMessage(chatId: Long, text: String, parseMode: String? = "Markdown")
    fun sendForceReply(chatId: Long, text: String, placeholder: String = "Введите ваш ответ...")
}
