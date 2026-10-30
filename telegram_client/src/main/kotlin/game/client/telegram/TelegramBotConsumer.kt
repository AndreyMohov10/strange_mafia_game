package game.client.telegram

import game.client.telegram.handler.TelegramUpdateRouter
import org.springframework.stereotype.Component
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer
import org.telegram.telegrambots.meta.api.objects.Update

@Component
class TelegramBotConsumer(
    private val updateRouter: TelegramUpdateRouter
) : LongPollingSingleThreadUpdateConsumer {

    override fun consume(update: Update) {
        updateRouter.route(update)
    }
}
