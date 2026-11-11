package edu.trincoll.manswell.portfolio.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Behaviors 25-27 and 29: the program checks every request in code; problems are results, not exceptions. */
class ToolRegistryTest {

    final Fakes.EchoTool echo = new Fakes.EchoTool();
    final Fakes.SendMailTool mail = new Fakes.SendMailTool();
    final ToolRegistry registry = new ToolRegistry(List.of(echo, mail));

    @Test
    void schemasAreInTheShapeTheApiExpects() {
        Map<String, Object> first = registry.schemas().get(0);
        assertThat(first).containsEntry("type", "function");
        @SuppressWarnings("unchecked")
        Map<String, Object> fn = (Map<String, Object>) first.get("function");
        assertThat(fn).containsKeys("name", "description", "parameters").containsEntry("name", "echo");
    }

    @Test
    void wellFormedCallRuns() {
        ToolOutcome out = registry.run("echo", "{\"text\":\"hi\",\"times\":2,\"mode\":\"loud\"}");
        assertThat(out.result()).containsEntry("echo", "hi");
        assertThat(out.pending()).isNull();
        assertThat(echo.runs).hasValue(1);
    }

    @Test
    void argumentsThatAreNotJsonAreAnErrorResult() {
        assertThat(error("echo", "{text: hi")).contains("not valid JSON");
    }

    @Test
    void argumentsThatAreNotAnObjectAreAnErrorResult() {
        assertThat(error("echo", "[\"hi\"]")).contains("JSON object");
    }

    @Test
    void missingRequiredArgument() {
        assertThat(error("echo", "{}")).contains("missing required argument 'text'");
    }

    @Test
    void unexpectedArgumentNamesTheExpectedOnes() {
        assertThat(error("echo", "{\"txt\":\"hi\"}")).contains("unexpected argument 'txt'").contains("text");
    }

    @Test
    void wrongTypes() {
        assertThat(error("echo", "{\"text\":5}")).contains("'text' must be a string");
        assertThat(error("echo", "{\"text\":\"hi\",\"times\":\"2\"}")).contains("'times' must be an integer");
        assertThat(error("echo", "{\"text\":\"hi\",\"times\":2.5}")).contains("'times' must be an integer");
    }

    @Test
    void outsideEnumAndRange() {
        assertThat(error("echo", "{\"text\":\"hi\",\"mode\":\"whisper\"}")).contains("'mode' must be one of");
        assertThat(error("echo", "{\"text\":\"hi\",\"times\":9}")).contains("'times' must be between 1 and 3");
    }

    @Test
    void unknownToolNamesTheToolsThatExist() {
        assertThat(error("delete_everything", "{}")).contains("unknown tool 'delete_everything'").contains("echo");
    }

    @Test
    void nothingRunsWhenTheCallIsBad() {
        error("echo", "{}");
        assertThat(echo.runs).hasValue(0);
    }

    @Test
    void sideEffectIsHeldAsPendingAndNeverRun() {
        ToolOutcome out = registry.run("send_mail", "{\"to\":\"patrick@example.com\",\"body\":\"hi\"}");
        assertThat(out.pending()).containsEntry("tool", "send_mail");
        assertThat((String) out.result().get("error")).contains("needs a person to confirm");
        assertThat(mail.sent).hasValue(0);
    }

    private String error(String tool, String args) {
        Object e = registry.run(tool, args).result().get("error");
        assertThat(e).as("error result for %s(%s)", tool, args).isInstanceOf(String.class);
        return (String) e;
    }
}
