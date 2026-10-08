package io.github.derekdiliu.dealdrop.payment;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Errors rendered as RFC 9457 problem details.
 */
public final class ApiErrors {

    private ApiErrors() {
    }

    public static ErrorResponseException badRequest(String detail) {
        return of(HttpStatus.BAD_REQUEST, detail);
    }

    public static ErrorResponseException notFound(String detail) {
        return of(HttpStatus.NOT_FOUND, detail);
    }

    public static ErrorResponseException conflict(String detail) {
        return of(HttpStatus.CONFLICT, detail);
    }

    private static ErrorResponseException of(HttpStatus status, String detail) {
        return new ErrorResponseException(status, ProblemDetail.forStatusAndDetail(status, detail), null);
    }
}
