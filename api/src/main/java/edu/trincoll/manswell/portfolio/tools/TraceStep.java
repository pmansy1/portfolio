package edu.trincoll.manswell.portfolio.tools;

import java.util.Map;

/** One step of the loop: a tool request and its result, or the model's answer (behavior 32). */
public record TraceStep(int step, String tool, String arguments, Map<String, Object> result, String answer) {

    public static TraceStep call(int step, ToolCall call, Map<String, Object> result) {
        return new TraceStep(step, call.name(), call.arguments(), result, null);
    }

    public static TraceStep answered(int step, String answer) {
        return new TraceStep(step, null, null, null, answer);
    }

    public boolean succeeded() {
        return tool != null && result != null && !result.containsKey("error");
    }
}
