import game.client.Client
import game.client.TelegramBot
import kotlinx.serialization.json.Json
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication
import java.io.File


fun main() {
    val config = Json.decodeFromString<Config>(File(".secret/client_config.json").readText())
    val botToken = config.token
    try {
        TelegramBotsLongPollingApplication().use { botsApplication ->
            val client = Client(config.host)
            val bot = TelegramBot(botToken, client)

            botsApplication.registerBot(botToken, bot)
            Thread.currentThread().join()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
