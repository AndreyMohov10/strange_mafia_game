package bot.chat

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.io.IOException

@Suppress("unused")
class LMStudio(
    private val client: HttpClient,
    private val host: String, private val token: String
) {
    suspend fun getModels(): List<LLModel> {
        try {
            val res = client.get("$host/models") {
                headers {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            }
            val ans = res.body<Map<String, List<Model>>>()
            return ans.getOrDefault("models", emptyList())
                .filter { it is LLModel }
                .map { (it as LLModel) }
        } catch (e: IOException) {
            System.err.println("произошла ошибка $e")
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            System.err.println("произошла неожиданная ошибка $e")
        }
        return listOf()
    }

    suspend fun loadModel(model: LLModel) {
        try {
            if (model.loadedInstances.isNotEmpty()) return
            val requestBody = "{\"model\": \"${model.key}\"}"
            client.post("$host/models/load") {
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                contentType(ContentType.Application.Json)
                setBody(requestBody)
                timeout {
                    requestTimeoutMillis = 100000
                    socketTimeoutMillis = 100000

                }
            }
        } catch (e: IOException) {
            System.err.println("произошла ошибка $e")
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            System.err.println("произошла неожиданная ошибка $e")
        }
    }
}
