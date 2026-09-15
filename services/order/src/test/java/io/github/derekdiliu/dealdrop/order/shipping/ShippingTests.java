package io.github.derekdiliu.dealdrop.order.shipping;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ShippingTests {

    @ParameterizedTest
    @CsvSource({
            "0110, NORTH_ISLAND", // Whangārei
            "1010, NORTH_ISLAND", // Auckland CBD
            "3204, NORTH_ISLAND", // Hamilton
            "6011, NORTH_ISLAND", // Wellington
            "7010, SOUTH_ISLAND", // Nelson
            "8011, SOUTH_ISLAND", // Christchurch
            "9016, SOUTH_ISLAND", // Dunedin
            "9818, SOUTH_ISLAND"  // Stewart Island
    })
    void zoneComesFromPostcode(String postcode, ShippingZone expected) {
        assertThat(ShippingZone.forPostcode(postcode)).isEqualTo(expected);
    }

    @Test
    void rejectsMalformedPostcodes() {
        assertThatIllegalArgumentException().isThrownBy(() -> ShippingZone.forPostcode("123"));
        assertThatIllegalArgumentException().isThrownBy(() -> ShippingZone.forPostcode("AB12"));
    }

    @Test
    void ruralDeliveryAddsSurcharge() {
        ShippingRates rates = new ShippingRates(699, 999, 500);

        assertThat(rates.costFor(ShippingZone.NORTH_ISLAND, false)).isEqualTo(699);
        assertThat(rates.costFor(ShippingZone.SOUTH_ISLAND, false)).isEqualTo(999);
        assertThat(rates.costFor(ShippingZone.SOUTH_ISLAND, true)).isEqualTo(1499);
    }
}
