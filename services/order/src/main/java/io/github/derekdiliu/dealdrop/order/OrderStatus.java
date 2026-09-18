package io.github.derekdiliu.dealdrop.order;

public enum OrderStatus {
    /** Stock is reserved; the customer has until the order expires to pay. */
    PENDING_PAYMENT,
    PAID,
    /** Stock and the customer's purchase allowance have been released. */
    CANCELLED
}
