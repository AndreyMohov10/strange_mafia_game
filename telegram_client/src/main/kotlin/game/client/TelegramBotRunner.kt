package game.client

import game.client.config.TelegramClientProperties
import jakarta.annotation.PreDestroy
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication

@Component
class TelegramBotRunner(
    private val properties: TelegramClientProperties,
    private val client: Client
) : ApplicationRunner {

    private var botsApplication: TelegramBotsLongPollingApplication? = null

    override fun run(args: ApplicationArguments?) {
        val botToken = properties.token
        if (botToken.isBlank()) {
            System.err.println("Токен Telegram бота не задан. Укажите 'bot.token' в application.properties или переменную окружения BOT_TOKEN.")
            return
        }

        try {
            val bot = TelegramBot(botToken, client)
            val app = TelegramBotsLongPollingApplication()
            botsApplication = app
            app.registerBot(botToken, bot)
            println("Telegram бот успешно запущен и слушает обновления.")
        } catch (e: Exception) {
            System.err.println("Ошибка при запуске Telegram бота: ${e.message}")
            e.printStackTrace()
        }
    }

    @PreDestroy
    fun stop() {
        try {
            botsApplication?.close()
            println("Telegram бот успешно остановлен.")
        } catch (e: Exception) {
            System.err.println("Ошибка при остановке Telegram бота: ${e.message}")
        }
    }
}
