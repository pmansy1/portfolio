package edu.trincoll.manswell.portfolio.tools;

/** One request from the model. `arguments` is the JSON text the model wrote, not a parsed object. */
public record ToolCall(String id, String name, String arguments) {
}
