package com.frauddetection.exception;

import com.frauddetection.dto.ApiError;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception) {

        Map<String, String> errors = new LinkedHashMap<>();

        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(
                        error.getField(),
                        error.getDefaultMessage()
                )
        );

        return ResponseEntity.badRequest().body(
                new ApiError("Validation failed", errors)
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableRequest(
            HttpMessageNotReadableException exception) {

        return ResponseEntity.badRequest().body(
                new ApiError(
                        "Invalid request body",
                        Map.of(
                                "body",
                                "Check JSON syntax, field types, timestamp format, "
                                + "and authOutcome (APPROVED or DECLINED)"
                        )
                )
        );
    }

    // Return the service's reason as JSON; Spring Boot hides it from the
    // default error body unless server.error.include-message is enabled.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(
            ResponseStatusException exception) {

        return ResponseEntity.status(exception.getStatusCode()).body(
                new ApiError(
                        exception.getReason() == null
                                ? "Request failed"
                                : exception.getReason(),
                        Map.of()
                )
        );
    }
}
