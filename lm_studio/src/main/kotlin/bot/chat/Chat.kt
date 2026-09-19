package bot.chat

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@Suppress("unused")
class Chat(
    host: String,
    token: String,
    private val model: String,
    private val role: String,
    restClient: RestClient? = null
) {
    private var prevMessageId: String? = null

    private val client: RestClient = restClient ?: RestClient.builder()
        .baseUrl(host)
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(60))
            setReadTimeout(Duration.ofSeconds(1000))
        })
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun answerMessage(prompt: String): ChatResponse? {
        try {
            val requestBody = ChatRequest(
                model = model,
                input = prompt,
                previousResponseId = prevMessageId
            )
            val requestJson = json.encodeToString(requestBody)

            val responseBody = client.post()
                .uri("/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestJson)
                .retrieve()
                .body(String::class.java) ?: return null

            val ans = json.decodeFromString<ChatApiResponse>(responseBody)
            val message = ans.output.filterIsInstance<MessageOutput>()
                .joinToString(separator = "\n") { it.content }
            prevMessageId = ans.responseId
            return ChatResponse(message)
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            System.err.println("произошла ошибка при отправке сообщения в LMStudio: $e")
            throw e
        }
    }
}
