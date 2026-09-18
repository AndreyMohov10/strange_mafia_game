package game.helpers

import game.domain.Role

@Suppress("unused")
fun Role.getString(): String {
    return when (this) {
        Role.AGENT -> "агент"
        Role.AGENT_HEAD -> "глава агентов"
        Role.MAFIA -> "мафия"
        Role.MAFIA_HEAD -> "глава мафии"
    }
}

fun Role.getSecret(): Boolean {
    return when (this) {
        Role.AGENT -> true
        Role.AGENT_HEAD -> true
        Role.MAFIA -> false
        Role.MAFIA_HEAD -> false
    }
}