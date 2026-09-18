package io.github.derekdiliu.dealdrop.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

@RestControllerAdvice
class CatalogUnavailableHandler {

    private static final Logger log = LoggerFactory.getLogger(CatalogUnavailableHandler.class);

    @ExceptionHandler(RestClientException.class)
    ProblemDetail catalogUnavailable(RestClientException e) {
        log.warn("Catalog call failed: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Deals can't be checked right now. Please try again shortly.");
    }
}
