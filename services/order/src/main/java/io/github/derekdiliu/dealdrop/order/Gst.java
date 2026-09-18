package io.github.derekdiliu.dealdrop.order;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * New Zealand GST is 15%, so a GST-inclusive price contains 3/23 of itself as GST.
 */
final class Gst {

    private static final BigDecimal THREE = BigDecimal.valueOf(3);
    private static final BigDecimal TWENTY_THREE = BigDecimal.valueOf(23);

    private Gst() {
    }

    /** GST included in a GST-inclusive amount, rounded half up to the cent. */
    static long includedIn(long inclusiveCents) {
        return BigDecimal.valueOf(inclusiveCents)
                .multiply(THREE)
                .divide(TWENTY_THREE, 0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
