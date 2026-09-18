package bot.chat

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.io.IOException

@Suppress("unused")
class Chat(
    private val client: HttpClient,
    private val host: String, private val token: String,
    private val model: String, private val role: String
) {
    private var prevMessageId: String? = null
    suspend fun answerMessage(prompt: String): ChatResponse? {
        try {
            val requestBody = ChatRequest(
                model = model,
                input = prompt,
                previousResponseId = prevMessageId
            )

            val response = client.post("$host/chat") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                contentType(ContentType.Application.Json)
                setBody(requestBody)
                timeout {
                    requestTimeoutMillis = 100000
                    socketTimeoutMillis = 100000
                }
            }

            if (response.status.isSuccess()) {
                val ans = response.body<ChatApiResponse>()
                val message = ans.output.filter { it is MessageOutput }
                    .joinToString(separator = "\n") { (it as MessageOutput).content }
                prevMessageId = ans.responseId
                return ChatResponse(message)
            }
        } catch (e: IOException) {
            System.err.println("произошла ошибка $e")
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            System.err.println("произошла неожиданная ошибка $e")
        }
        return null
    }
}
