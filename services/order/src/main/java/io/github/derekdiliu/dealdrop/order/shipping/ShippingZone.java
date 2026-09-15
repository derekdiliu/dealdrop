package io.github.derekdiliu.dealdrop.order.shipping;

public enum ShippingZone {
    NORTH_ISLAND,
    SOUTH_ISLAND;

    /**
     * NZ postcodes starting 0-6 are in the North Island and 7-9 in the South Island
     * (Stewart Island and the Chatham Islands fall in the 9 and 8 ranges, so get South Island rates).
     */
    public static ShippingZone forPostcode(String postcode) {
        if (postcode == null || !postcode.matches("\\d{4}")) {
            throw new IllegalArgumentException("Not an NZ postcode: " + postcode);
        }
        return postcode.charAt(0) <= '6' ? NORTH_ISLAND : SOUTH_ISLAND;
    }
}
