package io.github.derekdiliu.dealdrop.flashsale.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/**
 * The parts of a published catalog deal that a purchase depends on.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogDeal(UUID id, String title, long priceCents, int stock, int perCustomerLimit,
                          Instant startsAt, Instant endsAt) {

    /** Decided here from the sale window, so a cached deal still goes live and ends on time. */
    public boolean isLiveAt(Instant now) {
        return !now.isBefore(startsAt) && now.isBefore(endsAt);
    }
}
