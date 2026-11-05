package com.railbound.portal.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        String correlationId = UUID.randomUUID().toString();
        log.warn("[{}] Resource not found: {}", correlationId, ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("RESOURCE_NOT_FOUND", ex.getMessage(), correlationId));
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ApiError> handleInsufficientFunds(InsufficientFundsException ex) {
        String correlationId = UUID.randomUUID().toString();
        log.warn("[{}] Insufficient funds: {}", correlationId, ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("INSUFFICIENT_FUNDS", ex.getMessage(), correlationId));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> handleDuplicateResource(DuplicateResourceException ex) {
        String correlationId = UUID.randomUUID().toString();
        log.warn("[{}] Duplicate resource: {}", correlationId, ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("DUPLICATE_RESOURCE", ex.getMessage(), correlationId));
    }

    @ExceptionHandler(InvalidSignatureException.class)
    public ResponseEntity<ApiError> handleInvalidSignature(InvalidSignatureException ex) {
        String correlationId = UUID.randomUUID().toString();
        log.error("[{}] Invalid signature: {}", correlationId, ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("INVALID_SIGNATURE", ex.getMessage(), correlationId));
    }

    @ExceptionHandler(InvalidStateException.class)
    public ResponseEntity<ApiError> handleInvalidState(InvalidStateException ex) {
        String correlationId = UUID.randomUUID().toString();
        log.warn("[{}] Invalid state: {}", correlationId, ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("INVALID_STATE", ex.getMessage(), correlationId));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        String correlationId = UUID.randomUUID().toString();
        log.warn("[{}] Access denied: {}", correlationId, ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("ACCESS_DENIED", "Access is forbidden for current credentials", correlationId));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String correlationId = UUID.randomUUID().toString();
        String message = ex.getBindingResult().getAllErrors().isEmpty()
                ? "Validation failed"
                : ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("VALIDATION_ERROR", message, correlationId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneral(Exception ex) {
        String correlationId = UUID.randomUUID().toString();
        log.error("[{}] Unhandled server exception", correlationId, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "An unexpected error occurred. Please contact support.", correlationId));
    }
}
