package io.github.derekdiliu.dealdrop.flashsale;

import com.jayway.jsonpath.JsonPath;
import io.github.derekdiliu.dealdrop.flashsale.catalog.CatalogDeal;
import io.github.derekdiliu.dealdrop.flashsale.catalog.DealCatalog;
import io.github.derekdiliu.dealdrop.flashsale.messaging.Messages.OrderCancelled;
import io.github.derekdiliu.dealdrop.flashsale.messaging.Messages.OrderPlaced;
import io.github.derekdiliu.dealdrop.flashsale.messaging.Messages.OrderRejected;
import io.github.derekdiliu.dealdrop.flashsale.messaging.Messages.RejectReason;
import io.github.derekdiliu.dealdrop.flashsale.messaging.OrderRequests;
import io.github.derekdiliu.dealdrop.testsupport.TestJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
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
import org.springframework.web.ErrorResponseException;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestJwt.class})
class PurchaseApiTests {

    private static final String PLACE_REQUESTS = "order.place-requests";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PurchaseService purchaseService;

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @MockitoBean
    private DealCatalog catalog;

    @MockitoSpyBean
    private OrderRequests orderRequests;

    @BeforeEach
    void emptyOrderQueue() {
        amqpAdmin.purgeQueue(PLACE_REQUESTS, false);
    }

    @Test
    void acceptedPurchaseHoldsStockAndRequestsTheOrder() throws Exception {
        CatalogDeal deal = liveDeal(50, 2);
        UUID customer = UUID.randomUUID();

        String body = purchase(customer, deal.id(), 2, "1010")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/flash-sale/purchases/")))
                .andReturn().getResponse().getContentAsString();
        String purchaseId = JsonPath.read(body, "$.purchaseId");

        mvc.perform(get("/api/flash-sale/purchases/" + purchaseId).header("Authorization", TestJwt.bearerFor(customer)))
                .andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(get("/api/flash-sale/deals/" + deal.id() + "/stock")).andExpect(jsonPath("$.remaining").value(48));

        Message request = rabbit.receive(PLACE_REQUESTS, 5_000);
        assertThat(request).isNotNull();
        String json = new String(request.getBody(), StandardCharsets.UTF_8);
        assertThat(JsonPath.<String>read(json, "$.orderId")).isEqualTo(purchaseId);
        assertThat(JsonPath.<String>read(json, "$.userId")).isEqualTo(customer.toString());
        assertThat(JsonPath.<Integer>read(json, "$.unitPriceCents")).isEqualTo(7999);
        assertThat(JsonPath.<Integer>read(json, "$.quantity")).isEqualTo(2);
        assertThat(JsonPath.<String>read(json, "$.shippingAddress.postcode")).isEqualTo("1010");
    }

    @Test
    void gateRejectsWhatCannotBeBought() throws Exception {
        CatalogDeal lastUnit = liveDeal(1, 1);
        purchase(UUID.randomUUID(), lastUnit.id(), 1, "1010").andExpect(status().isAccepted());
        purchase(UUID.randomUUID(), lastUnit.id(), 1, "1010")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Sorry, this deal has sold out."));

        CatalogDeal limited = liveDeal(50, 1);
        UUID customer = UUID.randomUUID();
        purchase(customer, limited.id(), 1, "1010").andExpect(status().isAccepted());
        purchase(customer, limited.id(), 1, "1010")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("You can buy at most 1 of this deal."));
        purchase(UUID.randomUUID(), limited.id(), 2, "1010").andExpect(status().isConflict());

        CatalogDeal upcoming = deal(50, 1, Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200));
        purchase(UUID.randomUUID(), upcoming.id(), 1, "1010")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This deal hasn't started yet."));
        CatalogDeal ended = deal(50, 1, Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600));
        purchase(UUID.randomUUID(), ended.id(), 1, "1010")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This deal has ended."));
        purchase(UUID.randomUUID(), UUID.randomUUID(), 1, "1010").andExpect(status().isNotFound());

        purchase(UUID.randomUUID(), limited.id(), 1, "123").andExpect(status().isBadRequest());
    }

    @Test
    void repeatedAttemptsAreRateLimited() throws Exception {
        CatalogDeal deal = liveDeal(100, 1);
        UUID customer = UUID.randomUUID();

        purchase(customer, deal.id(), 1, "1010").andExpect(status().isAccepted());
        for (int attempt = 2; attempt <= 5; attempt++) {
            purchase(customer, deal.id(), 1, "1010").andExpect(status().isConflict());
        }
        purchase(customer, deal.id(), 1, "1010").andExpect(status().isTooManyRequests());
        // Other customers are unaffected.
        purchase(UUID.randomUUID(), deal.id(), 1, "1010").andExpect(status().isAccepted());
    }

    @Test
    void concurrentBuyersCannotOversell() throws Exception {
        CatalogDeal deal = liveDeal(10, 1);
        int buyers = 200;
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Boolean>> results;
        try (ExecutorService pool = Executors.newFixedThreadPool(buyers)) {
            results = IntStream.range(0, buyers).mapToObj(i -> pool.submit(() -> {
                start.await();
                try {
                    purchaseService.purchase(UUID.randomUUID(), deal.id(), 1, address("1010"));
                    return true;
                } catch (ErrorResponseException rejected) {
                    return false;
                }
            })).toList();
            start.countDown();
        }

        long accepted = results.stream().filter(PurchaseApiTests::succeeded).count();
        assertThat(accepted).isEqualTo(10);
        assertThat(purchaseService.remaining(deal.id())).isZero();
        assertThat(amqpAdmin.getQueueInfo(PLACE_REQUESTS).getMessageCount()).isEqualTo(10);
    }

    @Test
    void orderOutcomesUpdateThePurchaseAndStock() throws Exception {
        CatalogDeal deal = liveDeal(10, 1);
        UUID placed = UUID.randomUUID();
        UUID rejected = UUID.randomUUID();
        UUID placedId = purchaseService.purchase(placed, deal.id(), 1, address("1010"));
        UUID rejectedId = purchaseService.purchase(rejected, deal.id(), 1, address("1010"));
        assertThat(purchaseService.remaining(deal.id())).isEqualTo(8);

        publish("order.placed", new OrderPlaced(placedId));
        publish("order.rejected", new OrderRejected(rejectedId, rejected, deal.id(), 1, RejectReason.LIMIT_EXCEEDED));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            mvc.perform(get("/api/flash-sale/purchases/" + placedId).header("Authorization", TestJwt.bearerFor(placed)))
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.orderId").value(placedId.toString()));
            mvc.perform(get("/api/flash-sale/purchases/" + rejectedId).header("Authorization", TestJwt.bearerFor(rejected)))
                    .andExpect(jsonPath("$.status").value("REJECTED"))
                    .andExpect(jsonPath("$.reason").value("LIMIT_EXCEEDED"));
        });
        assertThat(purchaseService.remaining(deal.id())).isEqualTo(9);
        // The rejected customer's allowance was returned.
        purchaseService.purchase(rejected, deal.id(), 1, address("1010"));

        // A cancellation returns stock once, however often the event is delivered.
        publish("order.cancelled", new OrderCancelled(placedId, placed, deal.id(), 1));
        publish("order.cancelled", new OrderCancelled(placedId, placed, deal.id(), 1));
        await().atMost(Duration.ofSeconds(10)).until(() -> purchaseService.remaining(deal.id()) == 9);
        await().pollDelay(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(2)).until(() -> true);
        assertThat(purchaseService.remaining(deal.id())).isEqualTo(9);
    }

    @Test
    void databaseSoldOutStopsTheGate() throws Exception {
        CatalogDeal deal = liveDeal(10, 1);
        UUID customer = UUID.randomUUID();
        UUID purchaseId = purchaseService.purchase(customer, deal.id(), 1, address("1010"));

        publish("order.rejected", new OrderRejected(purchaseId, customer, deal.id(), 1, RejectReason.SOLD_OUT));

        await().atMost(Duration.ofSeconds(10)).until(() -> purchaseService.remaining(deal.id()) == 0);
        purchase(UUID.randomUUID(), deal.id(), 1, "1010").andExpect(status().isConflict());
    }

    @Test
    void brokerOutageUndoesTheReservation() throws Exception {
        CatalogDeal deal = liveDeal(10, 1);
        doThrow(new AmqpConnectException(new ConnectException("Connection refused"))).when(orderRequests).send(any());

        purchase(UUID.randomUUID(), deal.id(), 1, "1010").andExpect(status().isServiceUnavailable());

        assertThat(purchaseService.remaining(deal.id())).isEqualTo(10);
    }

    @Test
    void purchasesArePrivateButStockIsPublic() throws Exception {
        CatalogDeal deal = liveDeal(10, 1);
        UUID owner = UUID.randomUUID();
        UUID purchaseId = purchaseService.purchase(owner, deal.id(), 1, address("1010"));

        mvc.perform(get("/api/flash-sale/purchases/" + purchaseId).header("Authorization", TestJwt.bearerFor(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/flash-sale/purchases/" + purchaseId)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/flash-sale/deals/" + deal.id() + "/purchases")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/flash-sale/deals/" + deal.id() + "/stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remaining").value(9));
    }

    private CatalogDeal liveDeal(int stock, int limit) {
        return deal(stock, limit, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
    }

    private CatalogDeal deal(int stock, int limit, Instant startsAt, Instant endsAt) {
        CatalogDeal deal = new CatalogDeal(UUID.randomUUID(), "Merino throw", 7999, stock, limit, startsAt, endsAt);
        when(catalog.find(deal.id())).thenReturn(Optional.of(deal));
        return deal;
    }

    private void publish(String routingKey, Object event) {
        rabbit.convertAndSend("order.events", routingKey, event);
    }

    private ResultActions purchase(UUID customer, UUID dealId, int quantity, String postcode) throws Exception {
        return mvc.perform(post("/api/flash-sale/deals/" + dealId + "/purchases")
                .header("Authorization", TestJwt.bearerFor(customer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "quantity": %d,
                          "shippingAddress": {
                            "recipientName": "Aroha Smith", "phone": "021 123 4567", "line1": "12 Queen Street",
                            "city": "Auckland", "postcode": "%s", "ruralDelivery": false
                          }
                        }
                        """.formatted(quantity, postcode)));
    }

    private static ShippingAddress address(String postcode) {
        return new ShippingAddress("Aroha Smith", "021 123 4567", "12 Queen Street", null, null, "Auckland", postcode, false);
    }

    private static boolean succeeded(Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
