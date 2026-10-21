package io.github.derekdiliu.dealdrop.order;

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
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.github.derekdiliu.dealdrop.order.OrderFixtures.outboxRoutingKeys;
import static io.github.derekdiliu.dealdrop.order.OrderFixtures.remainingStock;
import static io.github.derekdiliu.dealdrop.order.OrderFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestJwt.class})
class OrderApiTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void placementRequestFromQueueCreatesOrderWithNzShippingAndGst() throws Exception {
        UUID customer = UUID.randomUUID();
        PlaceOrder request = request(customer, UUID.randomUUID(), 50, 2, 2, "1010", false);

        rabbit.convertAndSend(Messaging.DEFAULT_EXCHANGE, Messaging.PLACE_REQUESTS, request);
        await().atMost(Duration.ofSeconds(10)).until(() -> orders.existsById(request.orderId()));

        mvc.perform(get("/api/order/orders/" + request.orderId()).header("Authorization", TestJwt.bearerFor(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.unitPriceCents").value(7999))
                .andExpect(jsonPath("$.shippingZone").value("NORTH_ISLAND"))
                .andExpect(jsonPath("$.shippingCents").value(699))
                // 2 × $79.99 + $6.99 = $166.97, which includes $21.78 GST (3/23).
                .andExpect(jsonPath("$.totalCents").value(16697))
                .andExpect(jsonPath("$.gstCents").value(2178));
        assertThat(outboxRoutingKeys(jdbc, request.orderId()))
                .containsExactly(Messaging.PAYMENT_TIMEOUT_WAITING, OrderEvents.PLACED);
    }

    @Test
    void ruralSouthIslandAddressPaysSurcharge() {
        PlaceOrder request = request(UUID.randomUUID(), UUID.randomUUID(), 50, 2, 1, "9016", true);

        orderService.place(request);

        Order order = orders.findById(request.orderId()).orElseThrow();
        assertThat(order.getShippingCents()).isEqualTo(1499);
        assertThat(order.getTotalCents()).isEqualTo(7999 + 1499);
    }

    @Test
    void exceedingTheLimitIsRejectedWithoutHoldingStock() {
        UUID deal = UUID.randomUUID();
        UUID customer = UUID.randomUUID();
        orderService.place(request(customer, deal, 50, 2, 1, "1010", false));

        PlaceOrder overLimit = request(customer, deal, 50, 2, 2, "1010", false);
        orderService.place(overLimit);

        assertThat(orders.existsById(overLimit.orderId())).isFalse();
        assertThat(outboxRoutingKeys(jdbc, overLimit.orderId())).containsExactly(OrderEvents.REJECTED);
        assertThat(remainingStock(jdbc, deal)).isEqualTo(49);
    }

    @Test
    void soldOutIsRejected() {
        UUID deal = UUID.randomUUID();
        orderService.place(request(deal, 3, 2, 2));
        PlaceOrder tooMany = request(deal, 3, 2, 2);

        orderService.place(tooMany);

        assertThat(orders.existsById(tooMany.orderId())).isFalse();
        assertThat(outboxRoutingKeys(jdbc, tooMany.orderId())).containsExactly(OrderEvents.REJECTED);
        assertThat(remainingStock(jdbc, deal)).isEqualTo(1);
    }

    @Test
    void redeliveredRequestCreatesOneOrder() {
        UUID deal = UUID.randomUUID();
        PlaceOrder request = request(deal, 10, 2, 1);

        orderService.place(request);
        orderService.place(request);

        assertThat(remainingStock(jdbc, deal)).isEqualTo(9);
        assertThat(outboxRoutingKeys(jdbc, request.orderId()))
                .containsExactly(Messaging.PAYMENT_TIMEOUT_WAITING, OrderEvents.PLACED);
    }

    @Test
    void databaseNeverOversellsUnderConcurrency() throws Exception {
        UUID deal = UUID.randomUUID();
        List<PlaceOrder> requests = IntStream.range(0, 20).mapToObj(i -> request(deal, 5, 1, 1)).toList();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(requests.size())) {
            for (PlaceOrder request : requests) {
                pool.submit(() -> {
                    start.await();
                    orderService.place(request);
                    return null;
                });
            }
            start.countDown();
        }

        assertThat(requests.stream().filter(r -> orders.existsById(r.orderId())).count()).isEqualTo(5);
        assertThat(remainingStock(jdbc, deal)).isZero();
    }

    @Test
    void cancellingReleasesStockAndAnnouncesIt() throws Exception {
        UUID deal = UUID.randomUUID();
        UUID customer = UUID.randomUUID();
        PlaceOrder request = request(customer, deal, 1, 1, 1, "1010", false);
        orderService.place(request);
        assertThat(remainingStock(jdbc, deal)).isZero();

        mvc.perform(post("/api/order/orders/" + request.orderId() + "/cancel")
                        .header("Authorization", TestJwt.bearerFor(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("CUSTOMER"));
        mvc.perform(post("/api/order/orders/" + request.orderId() + "/cancel")
                        .header("Authorization", TestJwt.bearerFor(customer)))
                .andExpect(status().isConflict());

        assertThat(remainingStock(jdbc, deal)).isEqualTo(1);
        assertThat(outboxRoutingKeys(jdbc, request.orderId()))
                .containsExactly(Messaging.PAYMENT_TIMEOUT_WAITING, OrderEvents.PLACED, OrderEvents.CANCELLED);
        // Allowance came back too: the same customer can buy again.
        PlaceOrder again = request(customer, deal, 1, 1, 1, "1010", false);
        orderService.place(again);
        assertThat(orders.existsById(again.orderId())).isTrue();
    }

    @Test
    void paymentFromQueueMarksOrderPaidOnce() throws Exception {
        UUID customer = UUID.randomUUID();
        PlaceOrder request = request(customer, UUID.randomUUID(), 10, 2, 1, "1010", false);
        orderService.place(request);
        long total = orders.findById(request.orderId()).orElseThrow().getTotalCents();
        PaymentSucceeded payment = new PaymentSucceeded(request.orderId(), "pi_test_1", total, "nzd");

        rabbit.convertAndSend(Messaging.PAYMENT_EVENTS, PaymentSucceeded.ROUTING_KEY, payment);
        await().atMost(Duration.ofSeconds(10))
                .until(() -> orders.findById(request.orderId()).orElseThrow().getStatus() == OrderStatus.PAID);
        orderService.markPaid(payment);

        mvc.perform(get("/api/order/orders/" + request.orderId()).header("Authorization", TestJwt.bearerFor(customer)))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty());
        assertThat(outboxRoutingKeys(jdbc, request.orderId())).doesNotContain(OrderEvents.PAYMENT_REJECTED);
        // Paid orders keep their stock and can no longer be cancelled by the customer.
        mvc.perform(post("/api/order/orders/" + request.orderId() + "/cancel")
                        .header("Authorization", TestJwt.bearerFor(customer)))
                .andExpect(status().isConflict());
    }

    @Test
    void paymentForCancelledOrderIsRejectedForRefund() {
        UUID customer = UUID.randomUUID();
        PlaceOrder request = request(customer, UUID.randomUUID(), 10, 2, 1, "1010", false);
        orderService.place(request);
        long total = orders.findById(request.orderId()).orElseThrow().getTotalCents();
        orderService.expire(request.orderId());

        orderService.markPaid(new PaymentSucceeded(request.orderId(), "pi_late", total, "nzd"));

        assertThat(orders.findById(request.orderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(outboxRoutingKeys(jdbc, request.orderId())).endsWith(OrderEvents.PAYMENT_REJECTED);
    }

    @Test
    void paymentForWrongAmountIsRejected() {
        PlaceOrder request = request(UUID.randomUUID(), UUID.randomUUID(), 10, 2, 1, "1010", false);
        orderService.place(request);

        orderService.markPaid(new PaymentSucceeded(request.orderId(), "pi_short", 100, "nzd"));

        assertThat(orders.findById(request.orderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(outboxRoutingKeys(jdbc, request.orderId())).endsWith(OrderEvents.PAYMENT_REJECTED);
    }

    @Test
    void customersOnlySeeTheirOwnOrders() throws Exception {
        UUID owner = UUID.randomUUID();
        PlaceOrder request = request(owner, UUID.randomUUID(), 10, 2, 1, "1010", false);
        orderService.place(request);
        String orderId = request.orderId().toString();
        String someoneElse = TestJwt.bearerFor(UUID.randomUUID());

        mvc.perform(get("/api/order/orders").header("Authorization", TestJwt.bearerFor(owner)))
                .andExpect(jsonPath("$[*].id", hasItem(orderId)));
        mvc.perform(get("/api/order/orders/" + orderId).header("Authorization", someoneElse))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/order/orders/" + orderId + "/cancel").header("Authorization", someoneElse))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/order/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminsSeeEveryOrderNewestFirstFilteredByStatusAndDeal() throws Exception {
        UUID deal = UUID.randomUUID();
        PlaceOrder pending = request(deal, 10, 2, 1);
        PlaceOrder expired = request(deal, 10, 2, 1);
        PlaceOrder otherDeal = request(UUID.randomUUID(), 10, 2, 1);
        Stream.of(pending, expired, otherDeal).forEach(orderService::place);
        orderService.expire(expired.orderId());
        String admin = TestJwt.adminBearer();

        mvc.perform(get("/api/order/admin/orders").param("dealId", deal.toString()).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(expired.orderId().toString()))
                .andExpect(jsonPath("$.content[1].id").value(pending.orderId().toString()));
        mvc.perform(get("/api/order/admin/orders").param("dealId", deal.toString()).param("status", "CANCELLED")
                        .header("Authorization", admin))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].cancelReason").value("PAYMENT_TIMEOUT"));
        mvc.perform(get("/api/order/admin/orders/" + otherDeal.orderId()).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shippingAddress.recipientName").value("Aroha Smith"));
        mvc.perform(get("/api/order/admin/orders/" + UUID.randomUUID()).header("Authorization", admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void customersCannotUseTheAdminApi() throws Exception {
        mvc.perform(get("/api/order/admin/orders").header("Authorization", TestJwt.bearerFor(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/order/admin/orders")).andExpect(status().isUnauthorized());
    }
}
