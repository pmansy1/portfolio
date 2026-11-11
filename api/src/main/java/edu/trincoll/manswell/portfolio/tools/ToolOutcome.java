package edu.trincoll.manswell.portfolio.tools;

import java.util.Map;

/** What happened to one request: the result the model will read, and a held side effect if there was one. */
public record ToolOutcome(Map<String, Object> result, Map<String, Object> pending) {

    static ToolOutcome error(String message) {
        return new ToolOutcome(Map.of("error", message), null);
    }

    public boolean isError() {
        return result.containsKey("error");
    }
}
