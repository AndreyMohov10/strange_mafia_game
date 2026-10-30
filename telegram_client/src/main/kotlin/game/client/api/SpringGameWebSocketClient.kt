package game.client.api

import game.client.config.TelegramClientProperties
import game.domain.EventNotification
import game.domain.GameId
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import org.springframework.web.socket.handler.TextWebSocketHandler

@Component
class SpringGameWebSocketClient(
    private val properties: TelegramClientProperties,
    private val json: Json
) : GameWebSocketClient {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun connect(
        gameId: GameId,
        onNotification: (EventNotification) -> Unit,
        onClose: (closeStatus: String) -> Unit
    ): AutoCloseable {
        val serverUrl = properties.serverUrl
        val serverToken = properties.serverToken

        val cleanUrl = serverUrl.removePrefix("http://").removePrefix("https://")
        val protocol = if (serverUrl.startsWith("https://")) "wss" else "ws"
        val wsUri = "$protocol://$cleanUrl/game/${gameId.id}?token=$serverToken"

        val wsHandler = object : TextWebSocketHandler() {
            override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
                try {
                    val notification = json.decodeFromString<EventNotification>(message.payload)
                    onNotification(notification)
                } catch (e: Exception) {
                    logger.error("Ошибка при разборе EventNotification для игры ${gameId.id}: ${e.message}")
                }
            }

            override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
                logger.error("Транспортная ошибка WebSocket для игры ${gameId.id}: ${exception.message}")
            }

            override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
                logger.info("WebSocket соединение для игры ${gameId.id} закрыто со статусом $status")
                onClose(status.toString())
            }
        }

        val wsClient = StandardWebSocketClient()
        val futureSession = wsClient.execute(wsHandler, wsUri)
        val rawSession = futureSession.get()
        val session = ConcurrentWebSocketSessionDecorator(rawSession, 10000, 64 * 1024)

        return AutoCloseable {
            try {
                if (session.isOpen) {
                    session.close()
                }
            } catch (e: Exception) {
                logger.warn("Ошибка при закрытии WebSocket для игры ${gameId.id}: ${e.message}")
            }
        }
    }
}
