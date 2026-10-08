package io.github.derekdiliu.dealdrop.payment.stripe;

public class PaymentProviderException extends RuntimeException {

    PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
