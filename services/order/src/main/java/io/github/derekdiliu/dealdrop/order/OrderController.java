package io.github.derekdiliu.dealdrop.order;

import io.github.derekdiliu.dealdrop.order.shipping.ShippingZone;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The signed-in customer's orders. Customers only ever see their own orders.
 */
@RestController
@RequestMapping("/api/order/orders")
class OrderController {

    private final OrderService orders;

    OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    OrderResponse place(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PlaceOrderRequest request) {
        return OrderResponse.from(orders.place(userId(jwt), request.dealId(), request.quantity(), request.shippingAddress()));
    }

    @GetMapping
    List<OrderResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return orders.listFor(userId(jwt)).stream().map(OrderResponse::from).toList();
    }

    @GetMapping("/{id}")
    OrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return OrderResponse.from(orders.getFor(userId(jwt), id));
    }

    @PostMapping("/{id}/cancel")
    OrderResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return OrderResponse.from(orders.cancelByCustomer(userId(jwt), id));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record PlaceOrderRequest(
            @NotNull UUID dealId,
            // Upper bound guards against absurd input; the deal's own limit is checked separately.
            @NotNull @Min(1) @Max(100) Integer quantity,
            @NotNull @Valid ShippingAddress shippingAddress) {
    }

    record OrderResponse(
            UUID id,
            UUID dealId,
            String dealTitle,
            int quantity,
            String currency,
            long unitPriceCents,
            long shippingCents,
            long totalCents,
            long gstCents,
            OrderStatus status,
            CancelReason cancelReason,
            ShippingAddress shippingAddress,
            ShippingZone shippingZone,
            Instant createdAt,
            Instant expiresAt) {

        static OrderResponse from(Order order) {
            return new OrderResponse(order.getId(), order.getDealId(), order.getDealTitle(), order.getQuantity(), "NZD",
                    order.getUnitPriceCents(), order.getShippingCents(), order.getTotalCents(), order.getGstCents(),
                    order.getStatus(), order.getCancelReason(), order.getShippingAddress(), order.getShippingZone(),
                    order.getCreatedAt(), order.getExpiresAt());
        }
    }
}
