package edu.trincoll.manswell.portfolio.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.ObjectMapper;

/**
 * The loop (behaviors 28, 32, 33): the model asks, the program decides, the result goes back as a `tool` message,
 * until the model answers or the step cap is reached. At the cap the answer is the program's sentence.
 */
public class ToolLoop {

    public static final String CAP_ANSWER = "I couldn't finish looking that up. Please try a narrower question.";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ChatModel model;
    private final ToolRegistry registry;
    private final ClaimChecker checker;
    private final int maxSteps;

    public ToolLoop(ChatModel model, ToolRegistry registry, ClaimChecker checker, int maxSteps) {
        if (maxSteps < 1) {
            throw new IllegalArgumentException("maxSteps must be at least 1");
        }
        this.model = model;
        this.registry = registry;
        this.checker = checker;
        this.maxSteps = maxSteps;
    }

    public LoopResult run(String systemPrompt, String question) {
        List<Map<String, Object>> messages = new ArrayList<>(List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", question)));
        List<TraceStep> trace = new ArrayList<>();
        List<Map<String, Object>> pending = new ArrayList<>();
        int in = 0;
        int out = 0;
        double cost = 0;
        for (int step = 1; step <= maxSteps; step++) {
            ModelTurn turn = model.send(List.copyOf(messages), registry.schemas());
            in += turn.promptTokens();
            out += turn.completionTokens();
            cost += turn.cost() == null ? 0 : turn.cost();
            if (!turn.asksForTools()) {
                String answer = turn.content() == null ? "" : turn.content().strip();
                trace.add(TraceStep.answered(step, answer));
                return new LoopResult(answer, "answer", trace, pending, in, out, cost, checker.check(answer, trace));
            }
            messages.add(assistantMessage(turn));
            for (ToolCall call : turn.toolCalls()) {
                ToolOutcome outcome = registry.run(call.name(), call.arguments());
                if (outcome.pending() != null) {
                    pending.add(outcome.pending());
                }
                trace.add(TraceStep.call(step, call, outcome.result()));
                messages.add(Map.of("role", "tool", "tool_call_id", call.id(),
                        "content", JSON.writeValueAsString(outcome.result())));
            }
        }
        return new LoopResult(CAP_ANSWER, "cap", trace, pending, in, out, cost, checker.check(CAP_ANSWER, trace));
    }

    /** The model's request, recorded exactly as it was made, so the next call sees its own tool_calls. */
    private static Map<String, Object> assistantMessage(ModelTurn turn) {
        List<Map<String, Object>> calls = turn.toolCalls().stream()
                .map(c -> Map.<String, Object>of("id", c.id(), "type", "function",
                        "function", Map.of("name", c.name(), "arguments", c.arguments() == null ? "{}" : c.arguments())))
                .toList();
        return Map.of("role", "assistant", "content", turn.content() == null ? "" : turn.content(), "tool_calls", calls);
    }
}
