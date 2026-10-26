package io.github.derekdiliu.dealdrop.order;

public enum CancelReason {
    CUSTOMER,
    PAYMENT_TIMEOUT,
    /** The catalog withdrew the deal; paid orders are refunded. */
    DEAL_WITHDRAWN
}
