package io.github.derekdiliu.dealdrop.catalog.deal;

/**
 * Editorial status, set by admins. Drafts and withdrawn deals are only visible in the back office.
 */
public enum DealStatus {
    DRAFT,
    PUBLISHED,
    /** Taken off sale for good after publishing; its orders are cancelled and refunded. */
    WITHDRAWN
}
