package io.github.derekdiliu.dealdrop.catalog.deal;

import java.util.UUID;

/**
 * Published on catalog.events when an admin takes a deal off sale for good: nothing more may be sold,
 * and every order for it is cancelled, with paid ones refunded.
 */
record DealWithdrawn(UUID dealId) {

    static final String ROUTING_KEY = "deal.withdrawn";
}
