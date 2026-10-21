package io.github.derekdiliu.dealdrop.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

/**
 * A backend that is down or restarting. Left unhandled, the failure would fall through to the servlet
 * container's error page; answering here gives callers, Stripe included, a 502 they can retry.
 */
@RestControllerAdvice
class BackendFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(BackendFailureHandler.class);

    @ExceptionHandler(ResourceAccessException.class)
    ProblemDetail backendUnreachable(ResourceAccessException e) {
        log.warn("Could not forward request: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "The service is unavailable right now. Please try again shortly.");
    }
}
