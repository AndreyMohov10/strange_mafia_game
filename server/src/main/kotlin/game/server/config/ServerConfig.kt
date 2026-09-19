package game.server.config

import bot.chat.Chat
import bot.chat.LMStudio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.datasource.DriverManagerDataSource
import javax.sql.DataSource

@Configuration
class ServerConfig {

    @Bean
    @Primary
    fun applicationCoroutineScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @Bean
    fun dataSource(
        @Value($$"${spring.datasource.url}") url: String,
        @Value($$"${spring.datasource.username}") user: String,
        @Value($$"${spring.datasource.password}") password: String
    ): DataSource {
        val dataSource = DriverManagerDataSource()
        dataSource.setDriverClassName("org.postgresql.Driver")
        dataSource.url = url
        dataSource.username = user
        dataSource.password = password
        return dataSource
    }

    @Bean
    fun chatCreator(
        @Value($$"${lmstudio.host}") host: String,
        @Value($$"${lmstudio.token}") token: String
    ): (Int) -> Chat {
        val lmStudio = LMStudio(host, token)
        val modelKey = runBlocking {
            try {
                val models = lmStudio.getModels()
                if (models.isNotEmpty()) {
                    if (models[0].loadedInstances.isEmpty()) {
                        lmStudio.loadModel(models[0])
                    }
                    models[0].key
                } else {
                    System.err.println("нет доступных моделей в LMStudio")
                    "default-model"
                }
            } catch (e: Exception) {
                System.err.println("Не удалось подключиться к LMStudio: ${e.message}")
                "default-model"
            }
        }

        return { i ->
            Chat(host, token, modelKey, "user$i")
        }
    }
}
