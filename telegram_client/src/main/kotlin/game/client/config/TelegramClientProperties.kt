package game.client.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class TelegramClientProperties(
    @param:Value($$"${bot.server-url:http://localhost:8080}") val serverUrl: String,
    @param:Value($$"${bot.token:}") val token: String
)
