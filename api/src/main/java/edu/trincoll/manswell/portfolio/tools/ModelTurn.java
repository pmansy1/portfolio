package edu.trincoll.manswell.portfolio.tools;

import java.util.List;

/** One reply from the model: either text, or one or more tool requests. */
public record ModelTurn(String content, List<ToolCall> toolCalls, String finishReason,
                        int promptTokens, int completionTokens, Double cost) {

    public ModelTurn {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public boolean asksForTools() {
        return !toolCalls.isEmpty();
    }
}
