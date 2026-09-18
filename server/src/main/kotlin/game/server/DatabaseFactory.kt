package game.server

import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import java.sql.DriverManager

class DatabaseFactory() {
    private lateinit var dsl: DSLContext

    fun init(
        url: String = "your_host",
        user: String = "your_username",
        password: String = "your_password"
    ): DSLContext {
        val connection = DriverManager.getConnection(url, user, password)
        dsl = DSL.using(connection, SQLDialect.POSTGRES)
        return dsl
    }

    fun getRepository(): GameStateRepository {
        return GameStateRepository(dsl)
    }
}