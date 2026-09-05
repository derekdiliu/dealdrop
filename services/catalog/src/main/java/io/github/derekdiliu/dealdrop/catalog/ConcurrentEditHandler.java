package io.github.derekdiliu.dealdrop.catalog;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ConcurrentEditHandler {

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail concurrentEdit() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "This deal was changed by someone else. Reload it and try again.");
    }
}
