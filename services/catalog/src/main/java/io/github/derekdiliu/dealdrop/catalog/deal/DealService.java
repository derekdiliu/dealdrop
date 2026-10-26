package io.github.derekdiliu.dealdrop.catalog.deal;

import io.github.derekdiliu.dealdrop.catalog.Messaging;
import io.github.derekdiliu.dealdrop.catalog.image.ImageStorage;
import io.github.derekdiliu.dealdrop.common.ApiErrors;
import io.github.derekdiliu.dealdrop.outbox.Outbox;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Writes are read-modify-save without a surrounding transaction, so the image check (a network call)
 * never holds a database connection. Concurrent edits are caught by the entity's @Version. Withdrawing is
 * the exception: its event must be recorded in the same transaction as the change.
 */
@Service
class DealService {

    private final DealRepository deals;
    private final ImageStorage images;
    private final Outbox outbox;
    private final TransactionTemplate transaction;

    DealService(DealRepository deals, ImageStorage images, Outbox outbox, TransactionTemplate transaction) {
        this.deals = deals;
        this.images = images;
        this.outbox = outbox;
        this.transaction = transaction;
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

    /**
     * Takes a published deal off sale for good, live or not. The order service then cancels its orders,
     * refunding any that were paid, and flash-sale stops admitting purchases.
     */
    Deal withdraw(UUID id) {
        return transaction.execute(status -> {
            Deal deal = get(id);
            if (deal.getStatus() != DealStatus.PUBLISHED) {
                throw ApiErrors.conflict(deal.getStatus() == DealStatus.WITHDRAWN
                        ? "Deal is already withdrawn." : "Only published deals can be withdrawn.");
            }
            deal.withdraw();
            outbox.enqueue(Messaging.EVENTS, DealWithdrawn.ROUTING_KEY, new DealWithdrawn(id), null);
            return deals.save(deal);
        });
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
