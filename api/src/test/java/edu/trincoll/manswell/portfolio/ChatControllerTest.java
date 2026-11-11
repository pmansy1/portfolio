package edu.trincoll.manswell.portfolio;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import edu.trincoll.manswell.portfolio.tools.ClaimCheck;
import edu.trincoll.manswell.portfolio.tools.LoopResult;
import edu.trincoll.manswell.portfolio.tools.ToolCall;
import edu.trincoll.manswell.portfolio.tools.TraceStep;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Behaviors 3-6 and 8 through MockMvc. The model client is a mock: no network, no key, no cost. */
@WebMvcTest(ChatController.class)
@Import({ApiExceptionHandler.class, CorsConfig.class})
@TestPropertySource(properties = "portfolio.allowed-origin=https://patrick.example")
class ChatControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AnswerService answers;
    @MockitoBean ProfileService profile;

    void stubProfile() {
        when(profile.rawJson()).thenReturn("{\"person\":{\"name\":\"Patrick Manswell\"}}");
    }

    static LoopResult answered(String text) {
        return new LoopResult(text, "answer", List.of(TraceStep.answered(1, text)), List.of(), 1200, 30, 0.0002,
                new ClaimCheck(true, List.of()));
    }

    @Test
    void validQuestionReturnsTheAnswerModelAndUsage() throws Exception {
        when(answers.isConfigured()).thenReturn(true);
        when(answers.model()).thenReturn("openai/gpt-4o-mini");
        when(answers.answer(eq("Has Patrick shipped Java?"))).thenReturn(answered("Yes. Patrick has shipped Java in FHIR Lite."));

        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Has Patrick shipped Java?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.answer").value("Yes. Patrick has shipped Java in FHIR Lite."))
                .andExpect(jsonPath("$.model").value("openai/gpt-4o-mini"))
                .andExpect(jsonPath("$.usage.promptTokens").value(1200))
                .andExpect(jsonPath("$.usage.completionTokens").value(30))
                .andExpect(jsonPath("$.stoppedBy").value("answer"))
                .andExpect(jsonPath("$.check.supported").value(true))
                .andExpect(jsonPath("$.trace[0].answer").value("Yes. Patrick has shipped Java in FHIR Lite."));
    }

    @Test
    void theTraceShowsTheToolCallAndItsResult() throws Exception {
        TraceStep call = TraceStep.call(1, new ToolCall("c1", "recent_commits", "{\"repo\":\"portfolio\"}"),
                Map.of("repo", "pmansy1/portfolio", "commits", List.of()));
        LoopResult r = new LoopResult("No commits came back.", "answer",
                List.of(call, TraceStep.answered(2, "No commits came back.")), List.of(), 2000, 40, 0.0004,
                new ClaimCheck(true, List.of()));
        when(answers.isConfigured()).thenReturn(true);
        when(answers.model()).thenReturn("openai/gpt-4o-mini");
        when(answers.answer(anyString())).thenReturn(r);
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"What's new?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trace[0].tool").value("recent_commits"))
                .andExpect(jsonPath("$.trace[0].arguments").value("{\"repo\":\"portfolio\"}"))
                .andExpect(jsonPath("$.trace[0].result.repo").value("pmansy1/portfolio"))
                .andExpect(jsonPath("$.usage.promptTokens").value(2000));
    }

    @Test
    void blankQuestionIs400AndTheModelIsNeverCalled() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error").isNotEmpty());
        verify(answers, never()).answer(anyString());
    }

    @Test
    void questionOver500CharactersIs400() throws Exception {
        String longQ = "a".repeat(501);
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"" + longQ + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false));
        verify(answers, never()).answer(anyString());
    }

    @Test
    void missingQuestionAndNonJsonAre400() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.ok").value(false));
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.ok").value(false));
        verify(answers, never()).answer(anyString());
    }

    @Test
    void missingKeyIs503WithAFriendlyMessageAndNoKeyName() throws Exception {
        when(answers.isConfigured()).thenReturn(false);
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"hi\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("The chatbot is not configured right now."))
                .andExpect(content().string(not(containsString("OPENROUTER"))));
        verify(answers, never()).answer(anyString());
    }

    @Test
    void upstreamFailureIs502WithoutLeakingTheBody() throws Exception {
        when(answers.isConfigured()).thenReturn(true);
        when(answers.answer(anyString())).thenThrow(new UpstreamException("HTTP 429 secret body sk-or-xyz"));
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"hi\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("The chatbot couldn't answer right now. Please try again."))
                .andExpect(content().string(not(containsString("sk-or"))));
    }

    @Test
    void emptyModelAnswerBecomesTheFallbackSentence() throws Exception {
        when(answers.isConfigured()).thenReturn(true);
        when(answers.model()).thenReturn("openai/gpt-4o-mini");
        when(answers.answer(anyString())).thenReturn(answered("  "));
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("I don't have an answer to that from Patrick's profile."));
    }

    @Test
    void profileEndpointReturnsTheSameJsonThePromptWasBuiltFrom() throws Exception {
        stubProfile();
        mvc.perform(get("/api/profile"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"person\":{\"name\":\"Patrick Manswell\"}}"));
    }

    @Test
    void corsAllowsOnlyTheConfiguredOrigin() throws Exception {
        stubProfile();
        mvc.perform(get("/api/profile").header("Origin", "https://patrick.example"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Access-Control-Allow-Origin", "https://patrick.example"));
        mvc.perform(get("/api/profile").header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());
    }
}
