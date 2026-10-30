package game.client.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.meta.generics.TelegramClient

@Configuration
class ClientConfig {

    @Bean
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        encodeDefaults = true
        prettyPrint = false
    }

    @Bean
    fun restClient(properties: TelegramClientProperties): RestClient {
        return RestClient.builder()
            .baseUrl(properties.serverUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${properties.serverToken}")
            .build()
    }

    @Bean
    fun telegramClient(properties: TelegramClientProperties): TelegramClient? {
        return if (properties.token.isNotBlank()) {
            OkHttpTelegramClient(properties.token)
        } else {
            null
        }
    }

    @Bean
    fun clientCoroutineScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
