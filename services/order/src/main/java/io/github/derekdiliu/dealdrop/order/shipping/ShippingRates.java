package io.github.derekdiliu.dealdrop.order.shipping;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Flat shipping per order, in NZD cents including GST. Rural delivery addresses pay a surcharge.
 */
@ConfigurationProperties("dealdrop.shipping")
public record ShippingRates(long northIslandCents, long southIslandCents, long ruralSurchargeCents) {

    public long costFor(ShippingZone zone, boolean ruralDelivery) {
        long base = switch (zone) {
            case NORTH_ISLAND -> northIslandCents;
            case SOUTH_ISLAND -> southIslandCents;
        };
        return ruralDelivery ? base + ruralSurchargeCents : base;
    }
}
