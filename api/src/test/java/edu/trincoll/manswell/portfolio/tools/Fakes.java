package edu.trincoll.manswell.portfolio.tools;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Test doubles: a model whose replies each test scripts, and tools whose effects can be counted. */
final class Fakes {

    private Fakes() {
    }

    static ModelTurn asks(String id, String tool, String arguments) {
        return new ModelTurn("", List.of(new ToolCall(id, tool, arguments)), "tool_calls", 100, 10, 0.0001);
    }

    static ModelTurn says(String text) {
        return new ModelTurn(text, List.of(), "stop", 120, 30, 0.0001);
    }

    /** Replies in order; records a copy of every messages list it was sent. */
    static final class ScriptedModel implements ChatModel {
        final Deque<ModelTurn> replies;
        final List<List<Map<String, Object>>> sent = new ArrayList<>();
        final List<List<Map<String, Object>>> toolsSent = new ArrayList<>();

        ScriptedModel(ModelTurn... replies) {
            this.replies = new ArrayDeque<>(List.of(replies));
        }

        @Override
        public ModelTurn send(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
            sent.add(List.copyOf(messages));
            toolsSent.add(tools);
            if (replies.isEmpty()) {
                throw new AssertionError("the loop asked the model again after the script ran out");
            }
            return replies.removeFirst();
        }
    }

    /** A model that asks for a tool forever. */
    static final class EndlessCaller implements ChatModel {
        int calls;

        @Override
        public ModelTurn send(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
            calls++;
            return asks("call_" + calls, "echo", "{\"text\":\"again\"}");
        }
    }

    /** A read-only tool with one required string argument. */
    static final class EchoTool implements Tool {
        final AtomicInteger runs = new AtomicInteger();

        @Override public String name() { return "echo"; }
        @Override public String description() { return "Echo the text back."; }
        @Override public boolean hasSideEffect() { return false; }

        @Override
        public Map<String, Object> parameters() {
            return Map.of("type", "object",
                    "properties", Map.of("text", Map.of("type", "string"),
                                         "times", Map.of("type", "integer", "minimum", 1, "maximum", 3),
                                         "mode", Map.of("type", "string", "enum", List.of("plain", "loud"))),
                    "required", List.of("text"));
        }

        @Override
        public Map<String, Object> run(Map<String, Object> args) {
            runs.incrementAndGet();
            return Map.of("echo", args.get("text"));
        }
    }

    /** A tool with a side effect: the test proves it never runs on the model's say-so. */
    static final class SendMailTool implements Tool {
        final AtomicInteger sent = new AtomicInteger();

        @Override public String name() { return "send_mail"; }
        @Override public String description() { return "Send Patrick an email."; }
        @Override public boolean hasSideEffect() { return true; }

        @Override
        public Map<String, Object> parameters() {
            return Map.of("type", "object",
                    "properties", Map.of("to", Map.of("type", "string"), "body", Map.of("type", "string")),
                    "required", List.of("to", "body"));
        }

        @Override
        public Map<String, Object> run(Map<String, Object> args) {
            sent.incrementAndGet();
            return Map.of("sent", true);
        }
    }
}
