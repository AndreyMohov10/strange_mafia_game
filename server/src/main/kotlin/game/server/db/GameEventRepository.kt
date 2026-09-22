package game.server.db

import com.example.jooq.generated.Tables.GAME_EVENTS
import game.domain.DisconnectEvent
import game.domain.Event
import game.domain.GameId
import game.domain.MessageEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jooq.DSLContext
import org.jooq.JSONB
import org.springframework.stereotype.Repository

@Repository
class GameEventRepository(
    private val dsl: DSLContext
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    fun saveEvent(gameId: GameId, event: Event) {
        try {
            val eventType = when (event) {
                is MessageEvent -> "message_event"
                is DisconnectEvent -> "disconnect_event"
            }
            val payloadJson = json.encodeToString(event)

            dsl.insertInto(GAME_EVENTS)
                .set(GAME_EVENTS.GAME_ID, gameId.id)
                .set(GAME_EVENTS.EVENT_INDEX, event.eventIndex)
                .set(GAME_EVENTS.EVENT_TYPE, eventType)
                .set(GAME_EVENTS.PAYLOAD, JSONB.valueOf(payloadJson))
                .onDuplicateKeyIgnore()
                .execute()
        } catch (e: Exception) {
            System.err.println("Ошибка при сохранении события в БД: ${e.message}")
        }
    }

    fun getEvents(gameId: GameId, from: Int = 0, to: Int? = null): List<Event> {
        return try {
            val condition = if (to != null) {
                GAME_EVENTS.GAME_ID.eq(gameId.id)
                    .and(GAME_EVENTS.EVENT_INDEX.ge(from))
                    .and(GAME_EVENTS.EVENT_INDEX.le(to))
            } else {
                GAME_EVENTS.GAME_ID.eq(gameId.id)
                    .and(GAME_EVENTS.EVENT_INDEX.ge(from))
            }

            val records = dsl.selectFrom(GAME_EVENTS)
                .where(condition)
                .orderBy(GAME_EVENTS.EVENT_INDEX.asc())
                .fetch()

            records.mapNotNull { record ->
                val jsonb = record.payload ?: return@mapNotNull null
                try {
                    json.decodeFromString<Event>(jsonb.data())
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            System.err.println("Ошибка при загрузке событий из БД: ${e.message}")
            emptyList()
        }
    }
}
