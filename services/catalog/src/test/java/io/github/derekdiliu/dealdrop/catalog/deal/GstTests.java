package io.github.derekdiliu.dealdrop.catalog.deal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class GstTests {

    @ParameterizedTest
    @CsvSource({
            "2300, 300",   // $23.00 incl. GST = $20.00 + $3.00
            "11500, 1500", // $115.00 = $100.00 + $15.00
            "999, 130",    // 130.30 rounds down
            "1001, 131",   // 130.57 rounds up
            "1, 0",
            "0, 0"
    })
    void extractsGstFromInclusivePrice(long inclusiveCents, long expectedGstCents) {
        assertThat(Gst.includedIn(inclusiveCents)).isEqualTo(expectedGstCents);
    }
}
