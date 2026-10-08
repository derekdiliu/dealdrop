package io.github.derekdiliu.dealdrop.payment.orders;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
class HttpOrderClient implements OrderClient {

    private final RestClient client;

    HttpOrderClient(@Value("${dealdrop.services.order-url}") String orderUrl) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.client = RestClient.builder().baseUrl(orderUrl).requestFactory(requestFactory).build();
    }

    @Override
    public Optional<OrderSummary> find(UUID orderId, String customerToken) {
        try {
            return Optional.ofNullable(client.get()
                    .uri("/api/order/orders/{id}", orderId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                    .retrieve()
                    .body(OrderSummary.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }
}
