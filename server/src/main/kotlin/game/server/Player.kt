package game.server

import kotlinx.coroutines.channels.Channel
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

    suspend fun send(func: suspend () -> String) {
        try {
            outputChannel.send(func())
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (_: Exception) {
        }
    }

    override fun close() {
        outputChannel.close()
    }
}