package bot.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@Suppress("unused")
class LMStudio(
    host: String,
    token: String,
    restClient: RestClient? = null
) {
    private val client: RestClient = restClient ?: RestClient.builder()
        .baseUrl(host)
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(60))
            setReadTimeout(Duration.ofSeconds(120))
        })
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun getModels(): List<LLModel> {
        try {
            val text = client.get()
                .uri("/models")
                .retrieve()
                .body(String::class.java) ?: return emptyList()

            val jsonElement = json.parseToJsonElement(text)
            val modelsArray = (jsonElement as? JsonObject)?.get("models") as? JsonArray ?: emptyList()
            return modelsArray.mapNotNull { element ->
                try {
                    if (element is JsonObject) {
                        json.decodeFromJsonElement<Model>(element) as? LLModel
                    } else null
                } catch (_: Exception) {
                    null
                }
            }
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            System.err.println("произошла ошибка при получении моделей LMStudio: $e")
            throw e
        }
    }

    fun loadModel(model: LLModel) {
        try {
            if (model.loadedInstances.isNotEmpty()) return
            val requestBody = "{\"model\": \"${model.key}\"}"
            client.post()
                .uri("/models/load")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .toBodilessEntity()
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            System.err.println("произошла ошибка при загрузке модели в LMStudio: $e")
            throw e
        }
    }
}
