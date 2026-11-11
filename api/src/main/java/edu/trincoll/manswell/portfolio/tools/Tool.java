package edu.trincoll.manswell.portfolio.tools;

import java.util.Map;

/** A tool has two halves: the schema the model sees, and the function our program runs (spec behavior 25). */
public interface Tool {

    String name();

    String description();

    /** JSON Schema of the arguments: type object, properties, required. */
    Map<String, Object> parameters();

    /** A tool with a side effect is never run on the model's request alone (behavior 29). */
    boolean hasSideEffect();

    /** Runs with arguments already validated. Problems come back as {"error": ...}, never as exceptions. */
    Map<String, Object> run(Map<String, Object> args);

    default Map<String, Object> schema() {
        return Map.of("type", "function",
                "function", Map.of("name", name(), "description", description(), "parameters", parameters()));
    }
}
