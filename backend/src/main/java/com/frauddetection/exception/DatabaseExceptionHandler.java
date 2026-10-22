package com.frauddetection.exception;

import com.frauddetection.dto.ApiError;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class DatabaseExceptionHandler {

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiError> handleDuplicate(
            DuplicateKeyException exception) {

        return ResponseEntity.status(409).body(
                new ApiError(
                        "Duplicate record",
                        Map.of("request", "A unique value already exists")
                )
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(
            DataIntegrityViolationException exception) {

        return ResponseEntity.badRequest().body(
                new ApiError(
                        "Database constraint violation",
                        Map.of(
                                "request",
                                "Check referenced IDs and allowed field values"
                        )
                )
        );
    }
}
