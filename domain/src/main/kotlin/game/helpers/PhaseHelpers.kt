package game.helpers

import game.domain.Phase

@Suppress("unused")
fun Phase.description(): String {
    return when (this) {
        Phase.DAY_CONVERSATION -> "фаза общения в ней мафия решает что должен сделать глава мафии на фазе принятия " +
                "решения днем и какую команду сформировать. Агенты стараются не выделятся"

        Phase.TEAM_CREATING -> "фаза в которую глава мафии выбирает команду которая пойдет на вылазку.\n" +
                "Если в ней есть агент то артефакт не будет украден(будет получен муляж), иначе артефакт будет получен"

        Phase.DAY_CONCLUSION -> "фаза на которой глава мафии выбирает одно из двух действий: \n" +
                "1) Исключить игрока x\n" +
                "2) Проверить артефакт x(с некоторой достоверностью скажет настоящий ли артефакт)" +
                "(в первый день эта фаза пропускается)"

        Phase.NIGHT_CONVERSATION -> "фаза на которой агенты обсуждают что стоит сделать главе агентов " +
                "на фазе принятия решения ночью."

        Phase.NIGHT_CONCLUSION -> "фаза на которой глава агентов выбирает один из 2 вариантов:\n" +
                "1) вернуть артефакт x\n" +
                "2) шантажировать игрока x(он становится одним из агентов на следующий день, " +
                "а после этого дня узнает что его шантажировали)"
    }
}

@Suppress("unused")
fun Phase.outputFormatDescription(): String {
    return when (this) {
        Phase.TEAM_CREATING -> "k чисел через запятую(где k - число игроков в команде, нельзя называть себя)"
        Phase.DAY_CONCLUSION -> "одно из двух: 1) Исключить x(где x - игрок которого хочешь исключить), " +
                "2) Проверить x(где x - артефакт который хочешь проверить, артефакт за текущий день нельзя проверить)" +
                "в результате должна получится строка и число"

        Phase.NIGHT_CONCLUSION -> "одно из двух: 1) Шантажировать x(где x - игрок которого хочешь шантажировать), " +
                "2) Вернуть x(где x - артефакт который хочешь вернуть)" +
                "в результате должна получится строка и число"

        else -> "свободный формат"
    }
}

@Suppress("unused")
fun Phase.getString(): String {
    return when (this) {
        Phase.DAY_CONVERSATION -> "Дневное совещание"

        Phase.TEAM_CREATING -> "Выбор команды"

        Phase.DAY_CONCLUSION -> "Заключение дня"

        Phase.NIGHT_CONVERSATION -> "Ночное совещание"

        Phase.NIGHT_CONCLUSION -> "Заключение ночи"
    }
}

fun Phase.nextPhase(): Phase {
    return when (this) {
        Phase.DAY_CONVERSATION -> Phase.TEAM_CREATING

        Phase.TEAM_CREATING -> Phase.DAY_CONCLUSION

        Phase.DAY_CONCLUSION -> Phase.NIGHT_CONVERSATION

        Phase.NIGHT_CONVERSATION -> Phase.NIGHT_CONCLUSION

        Phase.NIGHT_CONCLUSION -> Phase.DAY_CONVERSATION
    }
}

fun Phase.getSecret(): Boolean {
    return when (this) {
        Phase.DAY_CONVERSATION -> false

        Phase.TEAM_CREATING -> false

        Phase.DAY_CONCLUSION -> false

        Phase.NIGHT_CONVERSATION -> true

        Phase.NIGHT_CONCLUSION -> true
    }
}
