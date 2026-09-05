package io.github.derekdiliu.dealdrop.catalog.deal;

import java.time.Instant;

/**
 * Where a deal is in its sale window, derived from the current time.
 */
public enum DealState {
    UPCOMING,
    LIVE,
    ENDED;

    static DealState of(Instant startsAt, Instant endsAt, Instant now) {
        if (now.isBefore(startsAt)) {
            return UPCOMING;
        }
        return now.isBefore(endsAt) ? LIVE : ENDED;
    }
}
