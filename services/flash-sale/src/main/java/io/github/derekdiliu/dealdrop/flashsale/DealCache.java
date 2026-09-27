package io.github.derekdiliu.dealdrop.flashsale;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.github.derekdiliu.dealdrop.flashsale.catalog.CatalogDeal;
import io.github.derekdiliu.dealdrop.flashsale.catalog.DealCatalog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * When a deal goes live, every request needs its details. Caching them briefly means each instance asks
 * the catalog about once per TTL instead of once per request; concurrent misses share one fetch.
 * Published deals cannot change, so staleness only delays noticing a brand-new deal.
 */
@Component
class DealCache {

    private final LoadingCache<UUID, Optional<CatalogDeal>> deals;

    DealCache(DealCatalog catalog, @Value("${dealdrop.flash-sale.deal-cache-ttl}") Duration ttl) {
        this.deals = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(10_000)
                .build(catalog::find);
    }

    CatalogDeal get(UUID dealId) {
        return deals.get(dealId).orElseThrow(() -> ApiErrors.notFound("Deal not found."));
    }
}
