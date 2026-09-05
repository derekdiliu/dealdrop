package io.github.derekdiliu.dealdrop.catalog.deal;

import java.time.Instant;

/**
 * The admin-editable fields of a deal, already validated.
 */
record DealDetails(
        String title,
        String description,
        String imageKey,
        long rrpCents,
        long priceCents,
        int stock,
        int perCustomerLimit,
        Instant startsAt,
        Instant endsAt) {
}
