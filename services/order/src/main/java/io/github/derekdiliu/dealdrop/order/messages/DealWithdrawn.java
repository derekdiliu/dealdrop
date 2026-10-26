package io.github.derekdiliu.dealdrop.order.messages;

import java.util.UUID;

/**
 * Published by the catalog on the catalog.events exchange when an admin takes a deal off sale for good.
 */
public record DealWithdrawn(UUID dealId) {

    public static final String ROUTING_KEY = "deal.withdrawn";
}
