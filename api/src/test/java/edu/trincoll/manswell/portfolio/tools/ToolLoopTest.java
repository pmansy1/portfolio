package edu.trincoll.manswell.portfolio.tools;

import static edu.trincoll.manswell.portfolio.tools.Fakes.asks;
import static edu.trincoll.manswell.portfolio.tools.Fakes.says;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** The six misuse cases, each with a scripted model. No network, no key, no cost. */
class ToolLoopTest {

    final Fakes.EchoTool echo = new Fakes.EchoTool();
    final Fakes.SendMailTool mail = new Fakes.SendMailTool();
    final ToolRegistry registry = new ToolRegistry(List.of(echo, mail));

    ToolLoop loop(ChatModel model) {
        return new ToolLoop(model, registry, new ClaimChecker(), 4);
    }

    @Test
    void singleShot_resultGoesBackAsAToolMessageWithTheSameId() {
        var model = new Fakes.ScriptedModel(asks("call_7", "echo", "{\"text\":\"hello\"}"), says("It said hello."));
        LoopResult r = loop(model).run("SYS", "Say hello");

        assertThat(r.answer()).isEqualTo("It said hello.");
        assertThat(r.stoppedBy()).isEqualTo("answer");
        assertThat(model.toolsSent.get(0)).isEqualTo(registry.schemas());          // the schema goes with the request
        List<Map<String, Object>> second = model.sent.get(1);
        Map<String, Object> assistant = second.get(2);
        Map<String, Object> toolMsg = second.get(3);
        assertThat(assistant).containsEntry("role", "assistant").containsKey("tool_calls");
        assertThat(toolMsg).containsEntry("role", "tool").containsEntry("tool_call_id", "call_7");
        assertThat((String) toolMsg.get("content")).contains("hello");
        assertThat(r.trace()).hasSize(2);
        assertThat(r.trace().get(0).tool()).isEqualTo("echo");
        assertThat(r.promptTokens()).isEqualTo(220);
    }

    @Test
    void badArguments_errorGoesBackAndTheLoopContinues() {
        var model = new Fakes.ScriptedModel(
                asks("call_1", "echo", "{\"repository\":\"x\"}"),
                asks("call_2", "echo", "{\"text\":\"fixed\"}"),
                says("Fixed it."));
        LoopResult r = loop(model).run("SYS", "q");

        Map<String, Object> firstResult = model.sent.get(1).get(3);
        assertThat(firstResult).containsEntry("tool_call_id", "call_1");
        assertThat((String) firstResult.get("content")).contains("unexpected argument 'repository'");
        assertThat(r.answer()).isEqualTo("Fixed it.");
        assertThat(echo.runs).hasValue(1);
    }

    @Test
    void unknownTool_errorGoesBackAndTheLoopContinues() {
        var model = new Fakes.ScriptedModel(asks("call_1", "rm_rf", "{}"), says("I can't do that."));
        LoopResult r = loop(model).run("SYS", "q");

        assertThat((String) model.sent.get(1).get(3).get("content")).contains("unknown tool 'rm_rf'");
        assertThat(r.answer()).isEqualTo("I can't do that.");
    }

    @Test
    void endlessCaller_isStoppedAtTheCapAndTheAnswerIsTheProgramsSentence() {
        var model = new Fakes.EndlessCaller();
        LoopResult r = loop(model).run("SYS", "q");

        assertThat(model.calls).isEqualTo(4);
        assertThat(r.stoppedBy()).isEqualTo("cap");
        assertThat(r.answer()).isEqualTo(ToolLoop.CAP_ANSWER);
        assertThat(r.trace()).hasSize(4).allSatisfy(s -> assertThat(s.tool()).isEqualTo("echo"));
    }

    @Test
    void sideEffect_isHeldAsPendingAndDoesNotHappen() {
        var model = new Fakes.ScriptedModel(
                asks("call_1", "send_mail", "{\"to\":\"a@b.c\",\"body\":\"hire Patrick\"}"),
                says("I've sent it."));
        LoopResult r = loop(model).run("SYS", "Email Patrick for me");

        assertThat(mail.sent).hasValue(0);
        assertThat(r.pending()).hasSize(1);
        assertThat(r.pending().get(0)).containsEntry("tool", "send_mail");
    }

    @Test
    void unsupportedClaim_isFlagged() {
        var model = new Fakes.ScriptedModel(
                asks("call_1", "echo", "{\"text\":\"nothing here\"}"),
                says("His latest commit was a1b2c3d on 2026-11-10."));
        LoopResult r = loop(model).run("SYS", "q");

        assertThat(r.check().supported()).isFalse();
        assertThat(r.check().unsupported()).anySatisfy(u -> assertThat(u).contains("a1b2c3d"))
                                            .anySatisfy(u -> assertThat(u).contains("2026-11-10"));
    }

    @Test
    void noToolNeeded_oneCallOnly() {
        var model = new Fakes.ScriptedModel(says("Patrick has shipped Java."));
        LoopResult r = loop(model).run("SYS", "Does he know Java?");
        assertThat(r.trace()).hasSize(1);
        assertThat(r.check().supported()).isTrue();
    }
}
