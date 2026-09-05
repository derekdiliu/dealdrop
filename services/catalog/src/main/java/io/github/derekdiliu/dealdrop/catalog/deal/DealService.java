package io.github.derekdiliu.dealdrop.catalog.deal;

import io.github.derekdiliu.dealdrop.catalog.ApiErrors;
import io.github.derekdiliu.dealdrop.catalog.image.ImageStorage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Writes are read-modify-save without a surrounding transaction, so the image check (a network call)
 * never holds a database connection. Concurrent edits are caught by the entity's @Version.
 */
@Service
class DealService {

    private final DealRepository deals;
    private final ImageStorage images;

    DealService(DealRepository deals, ImageStorage images) {
        this.deals = deals;
        this.images = images;
    }

    List<Deal> storefront(Instant now) {
        return deals.findByStatusAndEndsAtAfterOrderByStartsAtAsc(DealStatus.PUBLISHED, now);
    }

    Deal published(UUID id) {
        return deals.findById(id)
                .filter(deal -> deal.getStatus() == DealStatus.PUBLISHED)
                .orElseThrow(() -> ApiErrors.notFound("Deal not found."));
    }

    Page<Deal> all(Pageable pageable) {
        return deals.findAll(pageable);
    }

    Deal get(UUID id) {
        return deals.findById(id).orElseThrow(() -> ApiErrors.notFound("Deal not found."));
    }

    Deal create(DealDetails details) {
        requireUploadedImage(details.imageKey());
        return deals.save(new Deal(details));
    }

    Deal update(UUID id, DealDetails details) {
        requireUploadedImage(details.imageKey());
        Deal deal = get(id);
        requireDraft(deal, "Published deals cannot be edited.");
        deal.update(details);
        return deals.save(deal);
    }

    Deal publish(UUID id) {
        Deal deal = get(id);
        requireDraft(deal, "Deal is already published.");
        if (deal.getImageKey() == null) {
            throw ApiErrors.badRequest("Add an image before publishing.");
        }
        if (!deal.getEndsAt().isAfter(Instant.now())) {
            throw ApiErrors.badRequest("This deal has already ended.");
        }
        deal.publish();
        return deals.save(deal);
    }

    void delete(UUID id) {
        Deal deal = get(id);
        requireDraft(deal, "Published deals cannot be deleted.");
        deals.delete(deal);
    }

    private void requireUploadedImage(String imageKey) {
        if (imageKey != null && !images.exists(imageKey)) {
            throw ApiErrors.badRequest("Image not found. Upload it before attaching it to a deal.");
        }
    }

    private static void requireDraft(Deal deal, String detail) {
        if (deal.getStatus() != DealStatus.DRAFT) {
            throw ApiErrors.conflict(detail);
        }
    }
}
