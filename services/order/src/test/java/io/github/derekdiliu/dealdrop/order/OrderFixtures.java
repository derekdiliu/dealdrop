package io.github.derekdiliu.dealdrop.order;

import io.github.derekdiliu.dealdrop.order.catalog.CatalogDeal;
import io.github.derekdiliu.dealdrop.order.catalog.DealCatalog;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;

final class OrderFixtures {

    private OrderFixtures() {
    }

    /** Registers a deal with the mocked catalog and returns it. Each call gets a fresh id and stock. */
    static CatalogDeal deal(DealCatalog catalog, CatalogDeal.State state, int stock, int limit) {
        CatalogDeal deal = new CatalogDeal(UUID.randomUUID(), "Merino throw", 7999, stock, limit, state);
        when(catalog.find(deal.id())).thenReturn(Optional.of(deal));
        return deal;
    }

    static ShippingAddress address(String postcode, boolean ruralDelivery) {
        return new ShippingAddress("Aroha Smith", "021 123 4567", "12 Queen Street", null, "Auckland Central",
                "Auckland", postcode, ruralDelivery);
    }

    static String orderJson(UUID dealId, int quantity, String postcode, boolean ruralDelivery) {
        return """
                {
                  "dealId": "%s",
                  "quantity": %d,
                  "shippingAddress": {
                    "recipientName": "Aroha Smith",
                    "phone": "021 123 4567",
                    "line1": "12 Queen Street",
                    "suburb": "Auckland Central",
                    "city": "Auckland",
                    "postcode": "%s",
                    "ruralDelivery": %b
                  }
                }
                """.formatted(dealId, quantity, postcode, ruralDelivery);
    }
}
