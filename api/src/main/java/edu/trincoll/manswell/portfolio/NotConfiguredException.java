package edu.trincoll.manswell.portfolio;

/** The server has no model key, so no call is attempted (behavior 5). */
public class NotConfiguredException extends RuntimeException {
    public NotConfiguredException() {
        super("model client is not configured");
    }
}
