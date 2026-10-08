package io.github.derekdiliu.dealdrop.payment;

import io.github.derekdiliu.dealdrop.payment.stripe.PaymentProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

/**
 * Failures of the services payments depend on. A 5xx on the webhook also tells Stripe to retry later.
 */
@RestControllerAdvice
class DependencyFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(DependencyFailureHandler.class);

    @ExceptionHandler(PaymentProviderException.class)
    ProblemDetail paymentProviderFailed(PaymentProviderException e) {
        log.warn("{}: {}", e.getMessage(), e.getCause() == null ? "" : e.getCause().getMessage());
        return unavailable(HttpStatus.BAD_GATEWAY, "Payments are unavailable right now. Please try again shortly.");
    }

    @ExceptionHandler({RestClientException.class, AmqpException.class})
    ProblemDetail internalServiceFailed(RuntimeException e) {
        log.warn("Dependency call failed: {}", e.getMessage());
        return unavailable(HttpStatus.SERVICE_UNAVAILABLE, "Payments are unavailable right now. Please try again shortly.");
    }

    private static ProblemDetail unavailable(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
