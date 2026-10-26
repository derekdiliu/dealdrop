package io.github.derekdiliu.dealdrop.order;

import io.github.derekdiliu.dealdrop.order.messages.DealWithdrawn;
import io.github.derekdiliu.dealdrop.order.messages.OrderEvents;
import io.github.derekdiliu.dealdrop.order.messages.PaymentSucceeded;
import io.github.derekdiliu.dealdrop.order.messages.PlaceOrder;
import io.github.derekdiliu.dealdrop.testsupport.TestJwt;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.github.derekdiliu.dealdrop.order.OrderFixtures.outboxRoutingKeys;
import static io.github.derekdiliu.dealdrop.order.OrderFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestJwt.class})
class WithdrawalTests {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void withdrawingCancelsOpenOrdersAndRefundsPaidOnes() {
        UUID deal = UUID.randomUUID();
        PlaceOrder pending = request(deal, 10, 2, 1);
        PlaceOrder paid = request(deal, 10, 2, 1);
        PlaceOrder cancelledEarlier = request(deal, 10, 2, 1);
        List.of(pending, paid, cancelledEarlier).forEach(orderService::place);
        orderService.markPaid(new PaymentSucceeded(paid.orderId(), "pi_paid", total(paid), "nzd"));
        orderService.expire(cancelledEarlier.orderId());

        orderService.withdrawDeal(deal);

        assertThat(cancelReason(pending)).isEqualTo(CancelReason.DEAL_WITHDRAWN);
        assertThat(cancelReason(paid)).isEqualTo(CancelReason.DEAL_WITHDRAWN);
        assertThat(cancelReason(cancelledEarlier)).isEqualTo(CancelReason.PAYMENT_TIMEOUT);
        assertThat(outboxRoutingKeys(jdbc, pending.orderId())).endsWith(OrderEvents.PLACED, OrderEvents.CANCELLED);
        assertThat(outboxRoutingKeys(jdbc, paid.orderId())).endsWith(OrderEvents.CANCELLED, OrderEvents.PAYMENT_REJECTED);
        assertThat(payload(paid.orderId(), OrderEvents.PAYMENT_REJECTED))
                .contains("\"paymentIntentId\":\"pi_paid\"", "\"reason\":\"DEAL_WITHDRAWN\"");

        // A redelivered event changes nothing.
        orderService.withdrawDeal(deal);
        assertThat(outboxRoutingKeys(jdbc, paid.orderId())).hasSize(4);
        // A payment that lands afterwards is refunded like any payment for a cancelled order.
        orderService.markPaid(new PaymentSucceeded(pending.orderId(), "pi_late", total(pending), "nzd"));
        assertThat(outboxRoutingKeys(jdbc, pending.orderId())).endsWith(OrderEvents.PAYMENT_REJECTED);
    }

    @Test
    void placementsForAWithdrawnDealAreRejected() {
        UUID untouched = UUID.randomUUID();
        orderService.withdrawDeal(untouched);
        PlaceOrder first = request(untouched, 10, 2, 1);
        orderService.place(first);

        UUID selling = UUID.randomUUID();
        orderService.place(request(selling, 10, 2, 1));
        orderService.withdrawDeal(selling);
        PlaceOrder late = request(selling, 10, 2, 1);
        orderService.place(late);

        for (PlaceOrder rejected : List.of(first, late)) {
            assertThat(orders.existsById(rejected.orderId())).isFalse();
            assertThat(payload(rejected.orderId(), OrderEvents.REJECTED)).contains("\"reason\":\"DEAL_WITHDRAWN\"");
        }
    }

    @Test
    void withdrawalFromTheCatalogEventCancelsOrders() {
        UUID deal = UUID.randomUUID();
        PlaceOrder request = request(deal, 10, 2, 1);
        orderService.place(request);

        rabbit.convertAndSend(Messaging.CATALOG_EVENTS, DealWithdrawn.ROUTING_KEY, new DealWithdrawn(deal));

        await().atMost(Duration.ofSeconds(10))
                .until(() -> orders.findById(request.orderId()).orElseThrow().getStatus() == OrderStatus.CANCELLED);
    }

    @Test
    void noOrderSurvivesAWithdrawalDuringPlacements() throws Exception {
        UUID deal = UUID.randomUUID();
        int buyers = 8;
        int ordersEach = 15;
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(buyers + 1)) {
            for (int buyer = 0; buyer < buyers; buyer++) {
                pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < ordersEach; i++) {
                        orderService.place(request(deal, 1000, 1, 1));
                    }
                    return null;
                });
            }
            // Withdraw mid-sale, so placements land both before and after it.
            pool.submit(() -> {
                start.await();
                await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(5)).until(() -> ordersFor(deal) >= 20);
                orderService.withdrawDeal(deal);
                return null;
            });
            start.countDown();
        }

        assertThat(ordersFor(deal)).isLessThan(buyers * ordersEach);
        assertThat(jdbc.sql("SELECT count(*) FROM orders WHERE deal_id = :deal AND status <> 'CANCELLED'")
                .param("deal", deal)
                .query(Integer.class)
                .single()).isZero();
    }

    private int ordersFor(UUID deal) {
        return jdbc.sql("SELECT count(*) FROM orders WHERE deal_id = :deal").param("deal", deal)
                .query(Integer.class).single();
    }

    private long total(PlaceOrder request) {
        return orders.findById(request.orderId()).orElseThrow().getTotalCents();
    }

    private CancelReason cancelReason(PlaceOrder request) {
        Order order = orders.findById(request.orderId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        return order.getCancelReason();
    }

    private String payload(UUID orderId, String routingKey) {
        return jdbc.sql("SELECT payload FROM outbox_messages WHERE payload LIKE :id AND routing_key = :routingKey")
                .param("id", "%" + orderId + "%")
                .param("routingKey", routingKey)
                .query(String.class)
                .single();
    }
}
