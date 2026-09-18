import bot.chat.Chat
import bot.chat.LMStudio
import game.server.DatabaseFactory
import game.server.Server
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.io.File

suspend fun main() {
    val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    val file = File(".secret/server_config.json")
    val config = Json.decodeFromString<Config>(
        file.readText()
    )

    val host = config.host
    val token = config.token

    val lmStudio = LMStudio(client, host, token)
    val models = lmStudio.getModels()
    if (models.isEmpty()) {
        System.err.println("нет доступных моделей")
        return
    }
    if (models[0].loadedInstances.isEmpty()) {
        lmStudio.loadModel(models[0])
    }


    val port = config.port

    val sqlConfig = Json.decodeFromString<Map<String, String>>(
        File(".secret/sql_config.json").readText()
    )
    val factory = DatabaseFactory()
    factory.init(
        sqlConfig["url"]!!, sqlConfig["user"]!!,
        sqlConfig["password"]!!
    )

    val gameStateRepository = factory.getRepository()

    Server.runServer(port, gameStateRepository) { i ->
        Chat(
            client, host, token,
            models[0].key,
            "user$i"
        )
    }
}
