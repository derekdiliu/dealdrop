package io.github.derekdiliu.dealdrop.gateway;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTests {

    private static final RSAKey SIGNING_KEY = generateKey();
    private static final AtomicReference<HttpExchange> lastForwarded = new AtomicReference<>();
    private static final HttpServer downstreamStub = startDownstreamStub();

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void serviceUrls(DynamicPropertyRegistry registry) {
        String stubUrl = "http://localhost:" + downstreamStub.getAddress().getPort();
        registry.add("dealdrop.services.identity-url", () -> stubUrl);
        registry.add("dealdrop.services.catalog-url", () -> stubUrl);
        registry.add("dealdrop.services.order-url", () -> stubUrl);
        registry.add("dealdrop.services.flash-sale-url", () -> stubUrl);
    }

    @AfterAll
    static void stopStub() {
        downstreamStub.stop(0);
    }

    @BeforeEach
    void resetStub() {
        lastForwarded.set(null);
    }

    @Test
    void publicEndpointsAreForwardedWithoutToken() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(gateway("/api/identity/login"))
                .POST(HttpRequest.BodyPublishers.ofString("{}")));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(lastForwarded.get().getRequestURI().getPath()).isEqualTo("/api/identity/login");
    }

    @Test
    void protectedEndpointWithoutTokenIsRejectedAtTheEdge() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(gateway("/api/identity/me")).GET());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(lastForwarded.get()).isNull();
    }

    @Test
    void validTokenIsForwardedDownstream() throws Exception {
        String token = token(SIGNING_KEY, "dealdrop-identity", Instant.now().plusSeconds(600));

        HttpResponse<String> response = send(HttpRequest.newBuilder(gateway("/api/identity/me"))
                .header("Authorization", "Bearer " + token).GET());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(lastForwarded.get().getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer " + token);
    }

    @Test
    void invalidTokensAreRejected() throws Exception {
        List<String> invalidTokens = List.of(
                token(SIGNING_KEY, "dealdrop-identity", Instant.now().minusSeconds(600)),
                token(SIGNING_KEY, "someone-else", Instant.now().plusSeconds(600)),
                token(generateKey(), "dealdrop-identity", Instant.now().plusSeconds(600)),
                "not-a-jwt");

        for (String token : invalidTokens) {
            HttpResponse<String> response = send(HttpRequest.newBuilder(gateway("/api/identity/me"))
                    .header("Authorization", "Bearer " + token).GET());
            assertThat(response.statusCode()).as("token %s", token).isEqualTo(401);
        }
        assertThat(lastForwarded.get()).isNull();
    }

    @Test
    void storefrontReadsAreForwardedWithoutToken() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(gateway("/api/catalog/deals")).GET());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(lastForwarded.get().getRequestURI().getPath()).isEqualTo("/api/catalog/deals");
    }

    @Test
    void backOfficeRequiresTokenAtTheEdge() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(gateway("/api/catalog/admin/deals")).GET());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(lastForwarded.get()).isNull();
    }

    @Test
    void ordersRequireTokenAndAreForwardedWithIt() throws Exception {
        assertThat(send(HttpRequest.newBuilder(gateway("/api/order/orders")).GET()).statusCode()).isEqualTo(401);
        assertThat(lastForwarded.get()).isNull();

        String token = token(SIGNING_KEY, "dealdrop-identity", Instant.now().plusSeconds(600));
        HttpResponse<String> response = send(HttpRequest.newBuilder(gateway("/api/order/orders"))
                .header("Authorization", "Bearer " + token).GET());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(lastForwarded.get().getRequestURI().getPath()).isEqualTo("/api/order/orders");
    }

    @Test
    void flashSaleStockIsPublicButPurchasesNeedToken() throws Exception {
        HttpResponse<String> stock = send(HttpRequest.newBuilder(
                gateway("/api/flash-sale/deals/8a6e0804-2bd0-4672-b79d-d97027f9071b/stock")).GET());
        assertThat(stock.statusCode()).isEqualTo(200);
        lastForwarded.set(null);

        HttpResponse<String> purchase = send(HttpRequest.newBuilder(
                        gateway("/api/flash-sale/deals/8a6e0804-2bd0-4672-b79d-d97027f9071b/purchases"))
                .POST(HttpRequest.BodyPublishers.ofString("{}")));
        assertThat(purchase.statusCode()).isEqualTo(401);
        assertThat(lastForwarded.get()).isNull();
    }

    @Test
    void healthIsPublic() throws Exception {
        assertThat(send(HttpRequest.newBuilder(gateway("/actuator/health")).GET()).statusCode()).isEqualTo(200);
    }

    private URI gateway(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String token(RSAKey key, String issuer, Instant expiresAt) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("8a6e0804-2bd0-4672-b79d-d97027f9071b")
                .issuedAt(expiresAt.minusSeconds(3600))
                .expiresAt(expiresAt)
                .claim("roles", List.of("CUSTOMER"))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyIDFromThumbprint(true).generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Stands in for the downstream services: serves identity's JWKS and echoes anything routed to it. */
    private static HttpServer startDownstreamStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            String jwks = new JWKSet(SIGNING_KEY.toPublicJWK()).toString();
            server.createContext("/.well-known/jwks.json", exchange -> respond(exchange, jwks));
            for (String prefix : new String[] {"/api/identity/", "/api/catalog/", "/api/order/", "/api/flash-sale/"}) {
                server.createContext(prefix, exchange -> {
                    lastForwarded.set(exchange);
                    respond(exchange, "{\"ok\":true}");
                });
            }
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void respond(HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (var out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
