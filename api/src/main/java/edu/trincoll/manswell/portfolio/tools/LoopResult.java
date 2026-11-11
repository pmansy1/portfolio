package edu.trincoll.manswell.portfolio.tools;

import java.util.List;
import java.util.Map;

/** Everything one question produced: the answer and the evidence for it. */
public record LoopResult(String answer, String stoppedBy, List<TraceStep> trace, List<Map<String, Object>> pending,
                         int promptTokens, int completionTokens, double cost, ClaimCheck check) {

    public LoopResult {
        trace = List.copyOf(trace);
        pending = List.copyOf(pending);
    }

    public List<String> toolsCalled() {
        return trace.stream().filter(s -> s.tool() != null).map(TraceStep::tool).toList();
    }
}
