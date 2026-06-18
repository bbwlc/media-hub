package ch.axa.mediaHub;

import ch.axa.mediaHub.jwt.TokenExpiredException;
import ch.axa.mediaHub.jwt.TokenNotFoundException;
import ch.axa.mediaHub.jwt.UsernameAlreadyExistsException;
import ch.axa.mediaHub.media.FileNotFoundException;
import ch.axa.mediaHub.media.InvalidProfileStateException;
import ch.axa.mediaHub.media.ShareAlreadyExistsException;
import ch.axa.mediaHub.model.ApiError;
import ch.axa.mediaHub.model.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidProfileStateException.class)
    public ResponseEntity<ApiError> handleInvalidState (InvalidProfileStateException ex) {
        ApiError error = new ApiError(
                Instant.now(),409, "PROFILE_INVALID_STATE", ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUsernameAlreadyExists(UsernameAlreadyExistsException e) {
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(TokenNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTokenNotFound(TokenNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handleTokenExpired(TokenExpiredException e) {
        return error(HttpStatus.GONE, e.getMessage());
    }

    @ExceptionHandler(FileNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFileNotFound(FileNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ShareAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleShareAlreadyExists(ShareAlreadyExistsException e) {
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    // ResponseStatusException aus ProfileActionController (.orElseThrow) — Status durchreichen
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        return error(status, e.getReason() != null ? e.getReason() : status.getReasonPhrase());
    }

    // AccessDeniedException muss explizit behandelt werden, damit Spring Security
    // nicht durch den generischen Handler umgangen wird.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return error(HttpStatus.FORBIDDEN, "Access denied.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception e) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.value(), status.getReasonPhrase(), message));
    }
}