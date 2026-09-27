package io.github.derekdiliu.dealdrop.flashsale.catalog;

import java.util.Optional;
import java.util.UUID;

public interface DealCatalog {

    /** The published deal with this id, or empty if it does not exist or is not published. */
    Optional<CatalogDeal> find(UUID dealId);
}
