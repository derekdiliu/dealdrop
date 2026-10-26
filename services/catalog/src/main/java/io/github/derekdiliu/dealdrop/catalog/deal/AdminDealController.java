package io.github.derekdiliu.dealdrop.catalog.deal;

import io.github.derekdiliu.dealdrop.catalog.image.ImageStorage;
import io.github.derekdiliu.dealdrop.common.Gst;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Back-office API; admin role required (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/catalog/admin/deals")
class AdminDealController {

    private final DealService deals;
    private final ImageStorage images;

    AdminDealController(DealService deals, ImageStorage images) {
        this.deals = deals;
        this.images = images;
    }

    @GetMapping
    Page<AdminDealResponse> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Instant now = Instant.now();
        return deals.all(pageable).map(deal -> AdminDealResponse.from(deal, images, now));
    }

    @GetMapping("/{id}")
    AdminDealResponse get(@PathVariable UUID id) {
        return AdminDealResponse.from(deals.get(id), images, Instant.now());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AdminDealResponse create(@Valid @RequestBody DealRequest request) {
        return AdminDealResponse.from(deals.create(request.toDetails()), images, Instant.now());
    }

    @PutMapping("/{id}")
    AdminDealResponse update(@PathVariable UUID id, @Valid @RequestBody DealRequest request) {
        return AdminDealResponse.from(deals.update(id, request.toDetails()), images, Instant.now());
    }

    @PostMapping("/{id}/publish")
    AdminDealResponse publish(@PathVariable UUID id) {
        return AdminDealResponse.from(deals.publish(id), images, Instant.now());
    }

    @PostMapping("/{id}/withdraw")
    AdminDealResponse withdraw(@PathVariable UUID id) {
        return AdminDealResponse.from(deals.withdraw(id), images, Instant.now());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        deals.delete(id);
    }

    record AdminDealResponse(
            UUID id,
            String title,
            String description,
            String imageKey,
            String imageUrl,
            String currency,
            long priceCents,
            long rrpCents,
            long gstCents,
            int stock,
            int perCustomerLimit,
            Instant startsAt,
            Instant endsAt,
            DealStatus status,
            DealState state,
            Instant createdAt,
            Instant updatedAt) {

        static AdminDealResponse from(Deal deal, ImageStorage images, Instant now) {
            String imageUrl = deal.getImageKey() == null ? null : images.publicUrl(deal.getImageKey());
            return new AdminDealResponse(deal.getId(), deal.getTitle(), deal.getDescription(), deal.getImageKey(),
                    imageUrl, "NZD", deal.getPriceCents(), deal.getRrpCents(), Gst.includedIn(deal.getPriceCents()),
                    deal.getStock(), deal.getPerCustomerLimit(), deal.getStartsAt(), deal.getEndsAt(),
                    deal.getStatus(), deal.stateAt(now), deal.getCreatedAt(), deal.getUpdatedAt());
        }
    }
}
