package io.github.derekdiliu.dealdrop.flashsale;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * An NZ delivery address. Validated here so a bad address is rejected before stock is reserved;
 * the order service stores it with the order.
 */
public record ShippingAddress(
        @NotBlank @Size(max = 100) String recipientName,
        // Couriers need a contact number; allow the usual NZ formats such as 021 123 4567 or +64 21 123 4567.
        @NotBlank @Pattern(regexp = "\\+?[0-9 ()-]{7,20}") String phone,
        @NotBlank @Size(max = 200) String line1,
        @Size(max = 200) String line2,
        @Size(max = 100) String suburb,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Pattern(regexp = "\\d{4}", message = "must be a 4-digit NZ postcode") String postcode,
        // NZ Post rural delivery (RD) addresses cost more to deliver to.
        boolean ruralDelivery) {
}
