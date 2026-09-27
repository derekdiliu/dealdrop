package io.github.derekdiliu.dealdrop.flashsale.catalog;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads deals from the catalog service's public API.
 */
@Component
class HttpDealCatalog implements DealCatalog {

    private final RestClient client;

    HttpDealCatalog(@Value("${dealdrop.services.catalog-url}") String catalogUrl) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.client = RestClient.builder().baseUrl(catalogUrl).requestFactory(requestFactory).build();
    }

    @Override
    public Optional<CatalogDeal> find(UUID dealId) {
        try {
            return Optional.ofNullable(client.get()
                    .uri("/api/catalog/deals/{id}", dealId)
                    .retrieve()
                    .body(CatalogDeal.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }
}
