package io.github.derekdiliu.dealdrop.catalog.deal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * Times must include an offset (e.g. 2026-11-20T12:00:00+13:00) so NZ daylight saving is never ambiguous.
 * Prices are NZD cents, GST-inclusive.
 */
record DealRequest(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 4000) String description,
        @Size(max = 200) String imageKey,
        @NotNull @Positive Long rrpCents,
        @NotNull @Positive Long priceCents,
        @NotNull @Min(1) @Max(100_000) Integer stock,
        @NotNull @Min(1) Integer perCustomerLimit,
        @NotNull OffsetDateTime startsAt,
        @NotNull OffsetDateTime endsAt) {

    // Cross-field rules. Each passes when a field is missing, leaving that to @NotNull.

    @AssertTrue(message = "priceCents must not exceed rrpCents")
    boolean isPriceWithinRrp() {
        return priceCents == null || rrpCents == null || priceCents <= rrpCents;
    }

    @AssertTrue(message = "perCustomerLimit must not exceed stock")
    boolean isLimitWithinStock() {
        return perCustomerLimit == null || stock == null || perCustomerLimit <= stock;
    }

    @AssertTrue(message = "endsAt must be after startsAt")
    boolean isEndAfterStart() {
        return startsAt == null || endsAt == null || endsAt.isAfter(startsAt);
    }

    DealDetails toDetails() {
        return new DealDetails(title.strip(), description.strip(), imageKey, rrpCents, priceCents, stock,
                perCustomerLimit, startsAt.toInstant(), endsAt.toInstant());
    }
}
