package io.github.derekdiliu.dealdrop.flashsale.catalog;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HttpDealCatalogTests {

    private static final UUID KNOWN = UUID.fromString("5b0a6f8e-8a4e-4d43-9c6b-1d2f0e7a9c11");

    private HttpServer catalog;
    private HttpDealCatalog client;

    @BeforeEach
    void startCatalogStub() throws IOException {
        catalog = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        catalog.createContext("/api/catalog/deals/", exchange -> {
            boolean known = exchange.getRequestURI().getPath().endsWith(KNOWN.toString());
            // Includes fields the client does not model, as the real catalog response does.
            byte[] body = known ? """
                    {"id": "%s", "title": "Merino throw", "description": "Warm", "imageUrl": "https://x/y.jpg",
                     "currency": "NZD", "priceCents": 7999, "rrpCents": 15900, "gstCents": 1043, "stock": 50,
                     "perCustomerLimit": 2, "startsAt": "2026-11-20T00:00:00Z", "endsAt": "2026-11-22T00:00:00Z",
                     "state": "LIVE"}
                    """.formatted(KNOWN).getBytes(StandardCharsets.UTF_8)
                    : "{\"status\":404}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(known ? 200 : 404, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        catalog.start();
        client = new HttpDealCatalog("http://localhost:" + catalog.getAddress().getPort());
    }

    @AfterEach
    void stopCatalogStub() {
        catalog.stop(0);
    }

    @Test
    void readsPublishedDeal() {
        assertThat(client.find(KNOWN)).contains(
                new CatalogDeal(KNOWN, "Merino throw", 7999, 50, 2,
                        Instant.parse("2026-11-20T00:00:00Z"), Instant.parse("2026-11-22T00:00:00Z")));
    }

    @Test
    void unknownDealIsEmpty() {
        assertThat(client.find(UUID.randomUUID())).isEmpty();
    }
}
