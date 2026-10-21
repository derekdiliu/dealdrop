package io.github.derekdiliu.dealdrop.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** A backend restarts between two requests, closing the connection the gateway has pooled. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BackendRestartTests {

    private static final int BACKEND_PORT = unusedPort();
    private static HttpServer backend = startBackend();

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void serviceUrls(DynamicPropertyRegistry registry) {
        String backendUrl = "http://localhost:" + BACKEND_PORT;
        for (String service : new String[] {"identity", "catalog", "order", "flash-sale", "payment"}) {
            registry.add("dealdrop.services." + service + "-url", () -> backendUrl);
        }
    }

    @AfterAll
    static void stopBackend() {
        backend.stop(0);
    }

    @Test
    void requestAfterARestartGetsAFreshConnection() throws Exception {
        assertThat(postWebhook().statusCode()).isEqualTo(200);

        backend.stop(0);
        backend = startBackend();

        // A POST is not retried, so it fails if it is sent on the closed connection.
        assertThat(postWebhook().statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> postWebhook() throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/payment/webhooks/stripe"))
                .header("Stripe-Signature", "t=1,v1=abc")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpServer startBackend() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", BACKEND_PORT), 0);
            server.createContext("/", exchange -> {
                exchange.getRequestBody().readAllBytes();
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int unusedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
