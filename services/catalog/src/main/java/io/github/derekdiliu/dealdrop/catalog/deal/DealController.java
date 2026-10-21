package io.github.derekdiliu.dealdrop.catalog.deal;

import io.github.derekdiliu.dealdrop.catalog.image.ImageStorage;
import io.github.derekdiliu.dealdrop.common.Gst;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public storefront API.
 */
@RestController
@RequestMapping("/api/catalog/deals")
class DealController {

    private final DealService deals;
    private final ImageStorage images;

    DealController(DealService deals, ImageStorage images) {
        this.deals = deals;
        this.images = images;
    }

    /** Published deals that are live or upcoming, soonest first. */
    @GetMapping
    List<DealResponse> list() {
        Instant now = Instant.now();
        return deals.storefront(now).stream().map(deal -> DealResponse.from(deal, images, now)).toList();
    }

    @GetMapping("/{id}")
    DealResponse get(@PathVariable UUID id) {
        return DealResponse.from(deals.published(id), images, Instant.now());
    }

    record DealResponse(
            UUID id,
            String title,
            String description,
            String imageUrl,
            String currency,
            long priceCents,
            long rrpCents,
            long gstCents,
            int stock,
            int perCustomerLimit,
            Instant startsAt,
            Instant endsAt,
            DealState state) {

        static DealResponse from(Deal deal, ImageStorage images, Instant now) {
            return new DealResponse(deal.getId(), deal.getTitle(), deal.getDescription(),
                    images.publicUrl(deal.getImageKey()), "NZD", deal.getPriceCents(), deal.getRrpCents(),
                    Gst.includedIn(deal.getPriceCents()), deal.getStock(), deal.getPerCustomerLimit(),
                    deal.getStartsAt(), deal.getEndsAt(), deal.stateAt(now));
        }
    }
}
