package io.github.derekdiliu.dealdrop.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Every service points at a port nothing listens on, as when a backend is down or restarting. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BackendDownTests {

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void serviceUrls(DynamicPropertyRegistry registry) throws IOException {
        String downUrl = "http://localhost:" + unusedPort();
        for (String service : new String[] {"identity", "catalog", "order", "flash-sale", "payment"}) {
            registry.add("dealdrop.services." + service + "-url", () -> downUrl);
        }
    }

    @Test
    void publicRequestsToADownBackendGetBadGateway() throws Exception {
        HttpResponse<String> webhook = send(HttpRequest.newBuilder(gateway("/api/payment/webhooks/stripe"))
                .header("Stripe-Signature", "t=1,v1=abc")
                .POST(HttpRequest.BodyPublishers.ofString("{}")));
        HttpResponse<String> deals = send(HttpRequest.newBuilder(gateway("/api/catalog/deals")).GET());

        for (HttpResponse<String> response : List.of(webhook, deals)) {
            assertThat(response.statusCode()).isEqualTo(502);
            assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        }
    }

    private URI gateway(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static int unusedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
