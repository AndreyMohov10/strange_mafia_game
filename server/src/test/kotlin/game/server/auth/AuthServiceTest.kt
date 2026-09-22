package game.server.auth

import game.domain.ActionRequest
import game.domain.DisconnectEvent
import game.domain.Event
import game.domain.EventNotification
import game.domain.Message
import game.domain.MessageEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class AuthServiceTest {

    private val dsl: DSLContext = Mockito.mock(DSLContext::class.java)
    private val rootToken = "test_root_token"
    private val authService = AuthService(dsl, rootToken)

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        encodeDefaults = true
        isLenient = true
    }

    @Test
    fun `root token authenticates as root user by default`() {
        val principal = authService.resolveUser("Bearer $rootToken")
        assertNotNull(principal)
        assertEquals("root", principal?.userId)
        assertEquals("root", principal?.username)
        assertTrue(principal!!.isRoot)
    }

    @Test
    fun `root token can impersonate any requested userId`() {
        val principal = authService.resolveUser("Bearer $rootToken", requestedUserId = "player_42")
        assertNotNull(principal)
        assertEquals("player_42", principal?.userId)
        assertTrue(principal!!.isRoot)
    }

    @Test
    fun `invalid token returns null`() {
        val principal = authService.resolveUser("Bearer invalid_token")
        assertNull(principal)
    }

    @Test
    fun `extractToken handles Bearer prefix and raw token`() {
        assertEquals("abc", authService.extractToken("Bearer abc"))
        assertEquals("abc", authService.extractToken("bearer abc"))
        assertEquals("abc", authService.extractToken("abc"))
        assertNull(authService.extractToken(null))
        assertNull(authService.extractToken("   "))
    }

    @Test
    fun `events serialize with eventIndex and type discriminator`() {
        val event: Event = MessageEvent(
            message = Message("привет", 1, false),
            seed = 12345L,
            messageLength = 1,
            eventIndex = 5
        )
        val serialized = json.encodeToString(event)
        assertTrue(serialized.contains(""""type":"message_event""""))
        assertTrue(serialized.contains(""""eventIndex":5"""))

        val deserialized = json.decodeFromString<Event>(serialized)
        assertTrue(deserialized is MessageEvent)
        assertEquals(5, deserialized.eventIndex)
        assertEquals("привет", (deserialized as MessageEvent).message.string)
    }

    @Test
    fun `disconnect event serializes with eventIndex`() {
        val event: Event = DisconnectEvent(index = 2, eventIndex = 7)
        val serialized = json.encodeToString(event)
        assertTrue(serialized.contains(""""type":"disconnect_event""""))
        assertTrue(serialized.contains(""""eventIndex":7"""))

        val deserialized = json.decodeFromString<Event>(serialized)
        assertTrue(deserialized is DisconnectEvent)
        assertEquals(7, deserialized.eventIndex)
        assertEquals(2, (deserialized as DisconnectEvent).index)
    }

    @Test
    fun `event notification and action request serialize correctly`() {
        val notification = EventNotification(gameId = "game_123", eventIndex = 42)
        val notifStr = json.encodeToString(notification)
        assertTrue(notifStr.contains(""""gameId":"game_123""""))
        assertTrue(notifStr.contains(""""eventIndex":42"""))

        val action = ActionRequest(answer = "голосую за 2", userId = "user_99")
        val actionStr = json.encodeToString(action)
        assertTrue(actionStr.contains(""""answer":"голосую за 2""""))
        assertTrue(actionStr.contains(""""userId":"user_99""""))
    }
}
