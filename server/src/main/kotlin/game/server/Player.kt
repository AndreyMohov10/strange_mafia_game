package game.server

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.withTimeout
import java.lang.AutoCloseable

class Player : AutoCloseable {
    private val outputChannel = Channel<String>(Channel.UNLIMITED)

    suspend fun receive(timeout: Long): String? {
        try {
            return withTimeout(timeout) {
                return@withTimeout outputChannel.receive()
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (_: Exception) {
        }
        return null
    }

    fun send(str: String): Boolean {
        try {
            outputChannel.trySendBlocking(str).onFailure { throw Exception("failed to send event") }
            return true
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return false
    }

    override fun close() {
        outputChannel.close()
    }
}