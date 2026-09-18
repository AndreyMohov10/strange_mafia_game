package game.helpers

import game.domain.GameConfig

@Suppress("unused")
fun GameConfig.description(): String {
    return "количество игроков: $playersNum\n" +
            "время на ожидание подключения очередного игрока: $loggingTimeout мс\n" +
            "время на один ход: $turnTimeout мс\n" +
            "шанс на возврат линейно пропорционален от ${MIN_REGAIN_CHANCE * 100}% до ${regainChance * 100}%\n" +
            "количество агентов: $agents\n" +
            "количество человек необходимо набрать в команду: $team\n" +
            "шанс правильной оценки муляжа: $checkChance(если настоящий всегда правильно)\n" +
            "количество дней/артефактов: $artifacts\n"
}
