package io.github.derekdiliu.dealdrop.flashsale;

import io.github.derekdiliu.dealdrop.flashsale.messaging.Messages.DealWithdrawn;
import io.github.derekdiliu.dealdrop.flashsale.messaging.Messaging;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
class CatalogEventListener {

    private final StockGate stock;

    CatalogEventListener(StockGate stock) {
        this.stock = stock;
    }

    /** Safe to repeat: it only sets the marker again. */
    @RabbitListener(queues = Messaging.DEAL_WITHDRAWN)
    void onWithdrawn(DealWithdrawn event) {
        stock.markWithdrawn(event.dealId());
    }
}
