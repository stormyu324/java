package com.quant.web;

import com.quant.trading.BrokerNotConfiguredException;
import com.quant.trading.OrderRejectedException;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, String>> badRequest(Exception e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Map<String, String>> notFound(NoSuchElementException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(OrderRejectedException.class)
    ResponseEntity<Map<String, String>> rejected(OrderRejectedException e) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(BrokerNotConfiguredException.class)
    ResponseEntity<Map<String, String>> notConfigured(BrokerNotConfiguredException e) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    /** Errors returned by Alpaca: pass their message through (e.g. "insufficient buying power"). */
    @ExceptionHandler(RestClientResponseException.class)
    ResponseEntity<Map<String, String>> upstream(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        return error(HttpStatus.BAD_GATEWAY, "Alpaca " + e.getStatusCode().value() + ": "
                + (body.isBlank() ? e.getStatusText() : body));
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message == null ? status.getReasonPhrase() : message));
    }
}
