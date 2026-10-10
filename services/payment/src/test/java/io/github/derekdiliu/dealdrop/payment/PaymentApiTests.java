package io.github.derekdiliu.dealdrop.payment;

import com.stripe.net.Webhook;
import io.github.derekdiliu.dealdrop.payment.Messaging.PaymentEvents;
import io.github.derekdiliu.dealdrop.payment.Payment.Status;
import io.github.derekdiliu.dealdrop.payment.messages.Messages.OrderCancelled;
import io.github.derekdiliu.dealdrop.payment.messages.Messages.OrderPaymentRejected;
import io.github.derekdiliu.dealdrop.payment.orders.OrderClient;
import io.github.derekdiliu.dealdrop.payment.orders.OrderClient.OrderSummary;
import io.github.derekdiliu.dealdrop.payment.stripe.PaymentProvider;
import io.github.derekdiliu.dealdrop.payment.stripe.PaymentProvider.Intent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "dealdrop.stripe.secret-key=sk_test_unused",
        "dealdrop.stripe.webhook-secret=" + PaymentApiTests.WEBHOOK_SECRET
})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestJwt.class})
class PaymentApiTests {

    static final String WEBHOOK_SECRET = "whsec_test_secret";
    private static final String CAPTURED_EVENTS = "test.payment-events";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @MockitoBean
    private PaymentProvider provider;

    @MockitoBean
    private OrderClient orders;

    @MockitoSpyBean
    private PaymentEvents paymentEvents;

    @BeforeEach
    void captureOutgoingEvents() {
        Queue queue = new Queue(CAPTURED_EVENTS, false);
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(BindingBuilder.bind(queue).to(new TopicExchange(Messaging.PAYMENT_EVENTS)).with("#"));
        amqpAdmin.purgeQueue(CAPTURED_EVENTS, false);
    }

    @Test
    void startsPaymentOnceAndResumesIt() throws Exception {
        UUID customer = UUID.randomUUID();
        UUID orderId = awaitingPayment(customer, 16697, Duration.ofMinutes(14));
        when(provider.createIntent(orderId, customer, 16697, "nzd")).thenReturn(new Intent("pi_1", "pi_1_secret"));
        when(provider.clientSecret("pi_1")).thenReturn("pi_1_secret");

        start(customer, orderId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentIntentId").value("pi_1"))
                .andExpect(jsonPath("$.clientSecret").value("pi_1_secret"))
                .andExpect(jsonPath("$.amountCents").value(16697))
                .andExpect(jsonPath("$.currency").value("nzd"));
        start(customer, orderId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentIntentId").value("pi_1"));

        verify(provider, times(1)).createIntent(any(), any(), any(Long.class), any());
        assertThat(payments.findById(orderId)).get().extracting(Payment::getStatus).isEqualTo(Status.PENDING);
    }

    @Test
    void looksUpTheOrderAsTheCustomer() throws Exception {
        UUID customer = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(orders.find(eq(orderId), any())).thenReturn(Optional.empty());

        String bearer = TestJwt.bearerFor(customer);
        mvc.perform(post("/api/payment/orders/" + orderId + "/payment-intent").header("Authorization", bearer))
                .andExpect(status().isNotFound());

        verify(orders).find(orderId, bearer.substring("Bearer ".length()));
    }

    @Test
    void refusesOrdersThatCannotBePaid() throws Exception {
        UUID customer = UUID.randomUUID();
        UUID paid = UUID.randomUUID();
        when(orders.find(eq(paid), any())).thenReturn(Optional.of(
                new OrderSummary(paid, "PAID", 1000, "NZD", Instant.now().plusSeconds(600))));
        UUID expiring = awaitingPayment(customer, 1000, Duration.ofSeconds(30));

        start(customer, paid).andExpect(status().isConflict());
        start(customer, expiring)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This order is about to expire and can no longer be paid."));
        mvc.perform(post("/api/payment/orders/" + paid + "/payment-intent")).andExpect(status().isUnauthorized());
    }

    @Test
    void signedSucceededWebhookAnnouncesThePayment() throws Exception {
        UUID orderId = startedPayment("pi_ok");
        String payload = succeededEvent("pi_ok", orderId, 16697);

        webhook(payload, sign(payload)).andExpect(status().isOk());

        Message event = rabbit.receive(CAPTURED_EVENTS, 5_000);
        assertThat(event).isNotNull();
        assertThat(event.getMessageProperties().getReceivedRoutingKey()).isEqualTo("payment.succeeded");
        assertThat(new String(event.getBody(), StandardCharsets.UTF_8))
                .contains("\"orderId\":\"" + orderId + "\"", "\"paymentIntentId\":\"pi_ok\"", "\"amountCents\":16697");
        assertThat(payments.findById(orderId)).get().extracting(Payment::getStatus).isEqualTo(Status.SUCCEEDED);
    }

    @Test
    void unsignedOrForgedWebhooksAreRejected() throws Exception {
        String payload = succeededEvent("pi_forged", UUID.randomUUID(), 1);

        webhook(payload, null).andExpect(status().isBadRequest());
        webhook(payload, "t=" + Instant.now().getEpochSecond() + ",v1=deadbeef").andExpect(status().isBadRequest());
        webhook(payload.replace("\"amount_received\": 1", "\"amount_received\": 2"), sign(payload))
                .andExpect(status().isBadRequest());

        assertThat(rabbit.receive(CAPTURED_EVENTS, 500)).isNull();
    }

    @Test
    void webhookFailsSoStripeRetriesWhenTheEventCannotBeSent() throws Exception {
        UUID orderId = startedPayment("pi_retry");
        doThrow(new AmqpConnectException(new ConnectException("Connection refused"))).when(paymentEvents).succeeded(any());
        String payload = succeededEvent("pi_retry", orderId, 16697);

        webhook(payload, sign(payload)).andExpect(status().isServiceUnavailable());

        assertThat(payments.findById(orderId)).get().extracting(Payment::getStatus).isEqualTo(Status.PENDING);
    }

    @Test
    void otherWebhookEventsAreAcknowledgedAndIgnored() throws Exception {
        String payload = """
                {"id": "evt_2", "object": "event", "type": "payment_intent.created",
                 "data": {"object": {"id": "pi_new", "object": "payment_intent"}}}
                """;

        webhook(payload, sign(payload)).andExpect(status().isOk());

        assertThat(rabbit.receive(CAPTURED_EVENTS, 500)).isNull();
    }

    @Test
    void cancelledOrderCancelsItsPayment() {
        UUID orderId = startedPayment("pi_cancel");
        when(provider.cancel("pi_cancel")).thenReturn(true);

        rabbit.convertAndSend(Messaging.ORDER_EVENTS, "order.cancelled", new OrderCancelled(orderId));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(payments.findById(orderId)).get().extracting(Payment::getStatus).isEqualTo(Status.CANCELLED));
    }

    @Test
    void rejectedPaymentIsRefunded() {
        UUID orderId = startedPayment("pi_late");

        rabbit.convertAndSend(Messaging.ORDER_EVENTS, "order.payment-rejected",
                new OrderPaymentRejected(orderId, "pi_late", "ORDER_NOT_AWAITING_PAYMENT"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            verify(provider).refund("pi_late", orderId);
            assertThat(payments.findById(orderId)).get().extracting(Payment::getStatus).isEqualTo(Status.REFUNDED);
        });
    }

    /** A payment row as if the customer had started paying. */
    private UUID startedPayment(String paymentIntentId) {
        UUID orderId = UUID.randomUUID();
        payments.save(new Payment(orderId, UUID.randomUUID(), paymentIntentId, 16697, "nzd"));
        return orderId;
    }

    private UUID awaitingPayment(UUID customer, long totalCents, Duration timeLeft) {
        UUID orderId = UUID.randomUUID();
        when(orders.find(eq(orderId), any())).thenReturn(Optional.of(
                new OrderSummary(orderId, "PENDING_PAYMENT", totalCents, "NZD", Instant.now().plus(timeLeft))));
        return orderId;
    }

    private ResultActions start(UUID customer, UUID orderId) throws Exception {
        return mvc.perform(post("/api/payment/orders/" + orderId + "/payment-intent")
                .header("Authorization", TestJwt.bearerFor(customer)));
    }

    private ResultActions webhook(String payload, String signature) throws Exception {
        var request = post("/api/payment/webhooks/stripe").contentType(MediaType.APPLICATION_JSON).content(payload);
        if (signature != null) {
            request.header("Stripe-Signature", signature);
        }
        return mvc.perform(request);
    }

    private static String succeededEvent(String paymentIntentId, UUID orderId, long amount) {
        return """
                {"id": "evt_1", "object": "event", "type": "payment_intent.succeeded",
                 "data": {"object": {"id": "%s", "object": "payment_intent", "amount_received": %d,
                                     "currency": "nzd", "metadata": {"order_id": "%s"}}}}
                """.formatted(paymentIntentId, amount, orderId);
    }

    /** Signs a payload the way Stripe does: HMAC-SHA256 of "timestamp.payload" with the endpoint secret. */
    private static String sign(String payload) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        return "t=" + timestamp + ",v1=" + Webhook.Util.computeHmacSha256(WEBHOOK_SECRET, timestamp + "." + payload);
    }
}
