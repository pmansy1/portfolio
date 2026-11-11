package edu.trincoll.manswell.portfolio.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * The decision (behaviors 26, 27, 29): every request the model writes is parsed and checked here, in plain code,
 * against the schema the model was shown. Nothing here throws on a bad request; the problem goes back to the
 * model as the tool's result, worded so it can correct itself.
 */
public class ToolRegistry {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Map<String, Tool> tools;

    public ToolRegistry(List<Tool> tools) {
        this.tools = tools.stream().collect(Collectors.toMap(Tool::name, Function.identity(),
                (a, b) -> { throw new IllegalArgumentException("duplicate tool " + a.name()); }, LinkedHashMap::new));
    }

    public List<Map<String, Object>> schemas() {
        return tools.values().stream().map(Tool::schema).toList();
    }

    public ToolOutcome run(String name, String argumentsJson) {
        Tool tool = tools.get(name);
        if (tool == null) {
            return ToolOutcome.error("unknown tool '" + name + "'; the tools are " + tools.keySet());
        }
        Object parsed;
        try {
            parsed = JSON.readValue(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson, Object.class);
        } catch (JacksonException e) {
            return ToolOutcome.error("arguments were not valid JSON: " + e.getOriginalMessage());
        }
        if (!(parsed instanceof Map<?, ?> raw)) {
            return ToolOutcome.error("arguments must be a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> args = (Map<String, Object>) raw;
        String problem = SchemaCheck.problem(tool.parameters(), args);
        if (problem != null) {
            return ToolOutcome.error(problem);
        }
        if (tool.hasSideEffect()) {
            return new ToolOutcome(
                    Map.of("error", name + " has a side effect and needs a person to confirm it; it was not run"),
                    Map.of("tool", name, "arguments", args));
        }
        return new ToolOutcome(tool.run(args), null);
    }
}
