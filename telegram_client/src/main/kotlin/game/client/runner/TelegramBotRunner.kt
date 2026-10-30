package game.client.runner

import game.client.config.TelegramClientProperties
import game.client.telegram.TelegramBotConsumer
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication

@Component
class TelegramBotRunner(
    private val properties: TelegramClientProperties,
    private val botConsumer: TelegramBotConsumer
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(javaClass)
    private var botsApplication: TelegramBotsLongPollingApplication? = null

    override fun run(args: ApplicationArguments?) {
        val botToken = properties.token
        if (botToken.isBlank()) {
            logger.warn("Токен Telegram бота не задан. Укажите 'bot.token' в application.properties или переменную окружения BOT_TOKEN.")
            return
        }

        try {
            val app = TelegramBotsLongPollingApplication()
            botsApplication = app
            app.registerBot(botToken, botConsumer)
            logger.info("Telegram бот успешно запущен и слушает обновления.")
        } catch (e: Exception) {
            logger.error("Ошибка при запуске Telegram бота: ${e.message}", e)
        }
    }

    @PreDestroy
    fun stop() {
        try {
            botsApplication?.close()
            logger.info("Telegram бот успешно остановлен.")
        } catch (e: Exception) {
            logger.error("Ошибка при остановке Telegram бота: ${e.message}", e)
        }
    }
}
