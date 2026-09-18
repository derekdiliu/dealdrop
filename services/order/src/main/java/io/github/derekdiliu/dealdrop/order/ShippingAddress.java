package io.github.derekdiliu.dealdrop.order;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * An NZ delivery address, as entered at checkout and stored on the order.
 */
@Embeddable
public record ShippingAddress(
        @Column(name = "recipient_name") @NotBlank @Size(max = 100) String recipientName,
        // Couriers need a contact number; allow the usual NZ formats such as 021 123 4567 or +64 21 123 4567.
        @Column(name = "phone") @NotBlank @Pattern(regexp = "\\+?[0-9 ()-]{7,20}") String phone,
        @Column(name = "address_line1") @NotBlank @Size(max = 200) String line1,
        @Column(name = "address_line2") @Size(max = 200) String line2,
        @Column(name = "suburb") @Size(max = 100) String suburb,
        @Column(name = "city") @NotBlank @Size(max = 100) String city,
        @Column(name = "postcode") @NotBlank @Pattern(regexp = "\\d{4}", message = "must be a 4-digit NZ postcode") String postcode,
        // NZ Post rural delivery (RD) addresses cost more to deliver to.
        @Column(name = "rural_delivery") boolean ruralDelivery) {
}
