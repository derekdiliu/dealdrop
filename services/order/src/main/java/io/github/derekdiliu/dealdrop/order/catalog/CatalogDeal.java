package io.github.derekdiliu.dealdrop.order.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * The parts of a published catalog deal that ordering depends on.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogDeal(UUID id, String title, long priceCents, int stock, int perCustomerLimit, State state) {

    public enum State {
        UPCOMING,
        LIVE,
        ENDED
    }
}
