package edu.trincoll.manswell.portfolio;

/** The model provider failed or timed out. The message is for logs only, never for the visitor. */
public class UpstreamException extends RuntimeException {
    public UpstreamException(String message) {
        super(message);
    }

    public UpstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
