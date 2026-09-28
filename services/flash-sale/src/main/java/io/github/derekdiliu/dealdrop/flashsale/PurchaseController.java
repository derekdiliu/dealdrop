package io.github.derekdiliu.dealdrop.flashsale;

import io.github.derekdiliu.dealdrop.flashsale.Purchases.Purchase;
import io.github.derekdiliu.dealdrop.flashsale.Purchases.Status;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/flash-sale")
class PurchaseController {

    private final PurchaseService purchases;

    PurchaseController(PurchaseService purchases) {
        this.purchases = purchases;
    }

    /**
     * 202 Accepted: stock is held and the order is being created. Poll the Location for the outcome.
     */
    @PostMapping("/deals/{dealId}/purchases")
    ResponseEntity<PurchaseResponse> purchase(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID dealId,
                                              @Valid @RequestBody PurchaseRequest request) {
        UUID purchaseId = purchases.purchase(userId(jwt), dealId, request.quantity(), request.shippingAddress());
        return ResponseEntity.accepted()
                .location(URI.create("/api/flash-sale/purchases/" + purchaseId))
                .body(new PurchaseResponse(purchaseId, Status.PENDING, null, null));
    }

    @GetMapping("/purchases/{purchaseId}")
    PurchaseResponse status(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID purchaseId) {
        return PurchaseResponse.from(purchases.find(userId(jwt), purchaseId));
    }

    @GetMapping("/deals/{dealId}/stock")
    StockResponse stock(@PathVariable UUID dealId) {
        return new StockResponse(dealId, purchases.remaining(dealId));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record PurchaseRequest(
            // Upper bound guards against absurd input; the deal's own limit is checked separately.
            @NotNull @Min(1) @Max(100) Integer quantity,
            @NotNull @Valid ShippingAddress shippingAddress) {
    }

    /** orderId is set once the order exists (CONFIRMED); reason is set when REJECTED. */
    record PurchaseResponse(UUID purchaseId, Status status, String reason, UUID orderId) {

        static PurchaseResponse from(Purchase purchase) {
            UUID orderId = purchase.status() == Status.CONFIRMED ? purchase.id() : null;
            return new PurchaseResponse(purchase.id(), purchase.status(), purchase.reason(), orderId);
        }
    }

    record StockResponse(UUID dealId, int remaining) {
    }
}
