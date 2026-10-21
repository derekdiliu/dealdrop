package io.github.derekdiliu.dealdrop.order;

import io.github.derekdiliu.dealdrop.order.messages.OrderEvents;
import io.github.derekdiliu.dealdrop.order.messages.PlaceOrder;
import io.github.derekdiliu.dealdrop.testsupport.TestJwt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Duration;
import java.util.UUID;

import static io.github.derekdiliu.dealdrop.order.OrderFixtures.outboxRoutingKeys;
import static io.github.derekdiliu.dealdrop.order.OrderFixtures.remainingStock;
import static io.github.derekdiliu.dealdrop.order.OrderFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Runs the real outbox relay and RabbitMQ dead-letter path with a one-second payment window.
 */
@SpringBootTest(properties = {
        "dealdrop.order.payment-timeout=PT1S",
        "dealdrop.outbox.poll-interval=PT0.2S"
})
@Import({TestcontainersConfiguration.class, TestJwt.class})
class PaymentTimeoutTests {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void unpaidOrderIsCancelledAndItsStockReleased() {
        UUID deal = UUID.randomUUID();
        PlaceOrder request = request(deal, 1, 1, 1);
        orderService.place(request);
        UUID orderId = request.orderId();
        assertThat(remainingStock(jdbc, deal)).isZero();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Order order = orders.findById(orderId).orElseThrow();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.getCancelReason()).isEqualTo(CancelReason.PAYMENT_TIMEOUT);
        });
        assertThat(remainingStock(jdbc, deal)).isEqualTo(1);
        assertThat(outboxRoutingKeys(jdbc, orderId)).endsWith(OrderEvents.CANCELLED);
        await().atMost(Duration.ofSeconds(5)).until(() -> unpublishedOutboxMessages() == 0);
    }

    @Test
    void repeatedTimeoutDoesNotReleaseStockTwice() {
        UUID deal = UUID.randomUUID();
        PlaceOrder request = request(deal, 2, 1, 1);
        orderService.place(request);
        UUID orderId = request.orderId();

        orderService.expire(orderId);
        orderService.expire(orderId);
        await().atMost(Duration.ofSeconds(15))
                .until(() -> orders.findById(orderId).orElseThrow().getStatus() == OrderStatus.CANCELLED);
        // The broker-delivered timeout arrives after the manual ones; give it time to (not) double-release.
        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5)).until(() -> true);

        assertThat(remainingStock(jdbc, deal)).isEqualTo(2);
        assertThat(outboxRoutingKeys(jdbc, orderId)).filteredOn(OrderEvents.CANCELLED::equals).hasSize(1);
    }

    private int unpublishedOutboxMessages() {
        return jdbc.sql("SELECT count(*) FROM outbox_messages WHERE published_at IS NULL")
                .query(Integer.class)
                .single();
    }
}
