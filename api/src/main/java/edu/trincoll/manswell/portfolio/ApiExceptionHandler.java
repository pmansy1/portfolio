package edu.trincoll.manswell.portfolio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Every error is {"ok": false, "error": "..."} with a message a visitor can read; details go to the log. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    public record ErrorResponse(boolean ok, String error) {
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(false, message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> invalid(MethodArgumentNotValidException e) {
        return error(HttpStatus.BAD_REQUEST, "Please ask a question between 1 and " + ChatController.MAX_QUESTION + " characters.");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    ResponseEntity<ErrorResponse> unreadable(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "Send JSON like {\"question\": \"...\"}.");
    }

    @ExceptionHandler(NotConfiguredException.class)
    ResponseEntity<ErrorResponse> notConfigured(NotConfiguredException e) {
        log.error("Chat request refused: no model API key is configured on the server.");
        return error(HttpStatus.SERVICE_UNAVAILABLE, "The chatbot is not configured right now.");
    }

    @ExceptionHandler(UpstreamException.class)
    ResponseEntity<ErrorResponse> upstream(UpstreamException e) {
        log.warn("Model call failed: {}", e.getMessage());
        return error(HttpStatus.BAD_GATEWAY, "The chatbot couldn't answer right now. Please try again.");
    }
}
