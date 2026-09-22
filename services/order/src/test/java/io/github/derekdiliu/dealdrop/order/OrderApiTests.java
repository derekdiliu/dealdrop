package io.github.derekdiliu.dealdrop.order;

import com.jayway.jsonpath.JsonPath;
import io.github.derekdiliu.dealdrop.order.catalog.CatalogDeal;
import io.github.derekdiliu.dealdrop.order.catalog.DealCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static io.github.derekdiliu.dealdrop.order.OrderFixtures.address;
import static io.github.derekdiliu.dealdrop.order.OrderFixtures.deal;
import static io.github.derekdiliu.dealdrop.order.OrderFixtures.orderJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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
    private JdbcClient jdbc;

    @MockitoBean
    private DealCatalog catalog;

    @Test
    void placesOrderWithNzShippingAndGst() throws Exception {
        CatalogDeal deal = deal(catalog, CatalogDeal.State.LIVE, 50, 2);

        String body = place(UUID.randomUUID(), orderJson(deal.id(), 2, "1010", false))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.dealTitle").value("Merino throw"))
                .andExpect(jsonPath("$.unitPriceCents").value(7999))
                .andExpect(jsonPath("$.shippingZone").value("NORTH_ISLAND"))
                .andExpect(jsonPath("$.shippingCents").value(699))
                // 2 × $79.99 + $6.99 = $166.97, which includes $21.78 GST (3/23).
                .andExpect(jsonPath("$.totalCents").value(16697))
                .andExpect(jsonPath("$.gstCents").value(2178))
                .andExpect(jsonPath("$.shippingAddress.postcode").value("1010"))
                .andReturn().getResponse().getContentAsString();

        Instant created = Instant.parse(JsonPath.read(body, "$.createdAt"));
        Instant expires = Instant.parse(JsonPath.read(body, "$.expiresAt"));
        assertThat(Duration.between(created, expires)).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void ruralSouthIslandAddressPaysSurcharge() throws Exception {
        CatalogDeal deal = deal(catalog, CatalogDeal.State.LIVE, 50, 2);

        place(UUID.randomUUID(), orderJson(deal.id(), 1, "9016", true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shippingZone").value("SOUTH_ISLAND"))
                .andExpect(jsonPath("$.shippingCents").value(1499))
                .andExpect(jsonPath("$.totalCents").value(7999 + 1499));
    }

    @Test
    void onlyLiveDealsCanBeOrdered() throws Exception {
        CatalogDeal upcoming = deal(catalog, CatalogDeal.State.UPCOMING, 50, 2);
        CatalogDeal ended = deal(catalog, CatalogDeal.State.ENDED, 50, 2);

        place(UUID.randomUUID(), orderJson(upcoming.id(), 1, "1010", false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This deal hasn't started yet."));
        place(UUID.randomUUID(), orderJson(ended.id(), 1, "1010", false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This deal has ended."));
        place(UUID.randomUUID(), orderJson(UUID.randomUUID(), 1, "1010", false))
                .andExpect(status().isNotFound());
    }

    @Test
    void perCustomerLimitSpansOrders() throws Exception {
        CatalogDeal deal = deal(catalog, CatalogDeal.State.LIVE, 50, 2);
        UUID customer = UUID.randomUUID();

        place(customer, orderJson(deal.id(), 1, "1010", false)).andExpect(status().isCreated());
        place(customer, orderJson(deal.id(), 2, "1010", false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("You can buy at most 2 of this deal."));
        place(customer, orderJson(deal.id(), 1, "1010", false)).andExpect(status().isCreated());

        place(UUID.randomUUID(), orderJson(deal.id(), 3, "1010", false)).andExpect(status().isConflict());
        place(UUID.randomUUID(), orderJson(deal.id(), 2, "1010", false)).andExpect(status().isCreated());
    }

    @Test
    void neverSellsMoreThanStock() throws Exception {
        CatalogDeal deal = deal(catalog, CatalogDeal.State.LIVE, 3, 2);

        place(UUID.randomUUID(), orderJson(deal.id(), 2, "1010", false)).andExpect(status().isCreated());
        place(UUID.randomUUID(), orderJson(deal.id(), 2, "1010", false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Sorry, there isn't enough stock left for that quantity."));
        place(UUID.randomUUID(), orderJson(deal.id(), 1, "1010", false)).andExpect(status().isCreated());
        place(UUID.randomUUID(), orderJson(deal.id(), 1, "1010", false)).andExpect(status().isConflict());
        assertThat(remainingStock(deal.id())).isZero();
    }

    @Test
    void concurrentBuyersCannotOversell() throws Exception {
        CatalogDeal deal = deal(catalog, CatalogDeal.State.LIVE, 5, 1);
        int buyers = 20;
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Boolean>> attempts = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            attempts.add(() -> {
                start.await();
                try {
                    orderService.place(UUID.randomUUID(), deal.id(), 1, address("1010", false));
                    return true;
                } catch (ErrorResponseException soldOut) {
                    return false;
                }
            });
        }

        List<Future<Boolean>> results;
        try (ExecutorService pool = Executors.newFixedThreadPool(buyers)) {
            results = attempts.stream().map(pool::submit).toList();
            start.countDown();
        }

        long succeeded = results.stream().filter(result -> {
            try {
                return result.get();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }).count();
        assertThat(succeeded).isEqualTo(5);
        assertThat(remainingStock(deal.id())).isZero();
    }

    @Test
    void cancellingReleasesStockAndAllowance() throws Exception {
        CatalogDeal deal = deal(catalog, CatalogDeal.State.LIVE, 1, 1);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        String orderId = JsonPath.read(place(first, orderJson(deal.id(), 1, "1010", false))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        place(second, orderJson(deal.id(), 1, "1010", false)).andExpect(status().isConflict());

        mvc.perform(post("/api/order/orders/" + orderId + "/cancel").header("Authorization", TestJwt.bearerFor(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("CUSTOMER"));
        mvc.perform(post("/api/order/orders/" + orderId + "/cancel").header("Authorization", TestJwt.bearerFor(first)))
                .andExpect(status().isConflict());

        place(second, orderJson(deal.id(), 1, "1010", false)).andExpect(status().isCreated());
        // The first customer's allowance was returned too, but the only unit is now taken.
        place(first, orderJson(deal.id(), 1, "1010", false))
                .andExpect(jsonPath("$.detail").value("Sorry, there isn't enough stock left for that quantity."));
    }

    @Test
    void customersOnlySeeTheirOwnOrders() throws Exception {
        CatalogDeal deal = deal(catalog, CatalogDeal.State.LIVE, 50, 2);
        UUID owner = UUID.randomUUID();
        UUID someoneElse = UUID.randomUUID();
        String orderId = JsonPath.read(place(owner, orderJson(deal.id(), 1, "1010", false))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/order/orders").header("Authorization", TestJwt.bearerFor(owner)))
                .andExpect(jsonPath("$[*].id", hasItem(orderId)));
        mvc.perform(get("/api/order/orders/" + orderId).header("Authorization", TestJwt.bearerFor(someoneElse)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/order/orders/" + orderId + "/cancel").header("Authorization", TestJwt.bearerFor(someoneElse)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/order/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidCheckoutDetails() throws Exception {
        CatalogDeal deal = deal(catalog, CatalogDeal.State.LIVE, 50, 2);
        UUID customer = UUID.randomUUID();

        place(customer, orderJson(deal.id(), 1, "123", false)).andExpect(status().isBadRequest());
        place(customer, orderJson(deal.id(), 0, "1010", false)).andExpect(status().isBadRequest());
        place(customer, orderJson(deal.id(), 1, "1010", false).replace("021 123 4567", "call me"))
                .andExpect(status().isBadRequest());
        place(customer, "{\"dealId\": \"%s\", \"quantity\": 1}".formatted(deal.id())).andExpect(status().isBadRequest());
    }

    @Test
    void catalogOutageIsReportedAsUnavailable() throws Exception {
        when(catalog.find(any())).thenThrow(new ResourceAccessException("Connection refused"));

        place(UUID.randomUUID(), orderJson(UUID.randomUUID(), 1, "1010", false))
                .andExpect(status().isServiceUnavailable());
    }

    private ResultActions place(UUID customer, String json) throws Exception {
        return mvc.perform(post("/api/order/orders")
                .header("Authorization", TestJwt.bearerFor(customer))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private int remainingStock(UUID dealId) {
        return jdbc.sql("SELECT remaining FROM deal_inventory WHERE deal_id = :dealId")
                .param("dealId", dealId)
                .query(Integer.class)
                .single();
    }
}
