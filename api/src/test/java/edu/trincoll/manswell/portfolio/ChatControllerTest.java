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
    @MockitoBean ModelClient model;
    @MockitoBean ProfileService profile;

    static final String SYSTEM = "system prompt with <profile>{}</profile>";

    void stubProfile() {
        when(profile.systemPrompt()).thenReturn(SYSTEM);
        when(profile.rawJson()).thenReturn("{\"person\":{\"name\":\"Patrick Manswell\"}}");
    }

    @Test
    void validQuestionReturnsTheAnswerModelAndUsage() throws Exception {
        stubProfile();
        when(model.isConfigured()).thenReturn(true);
        when(model.complete(eq(SYSTEM), eq("Has Patrick shipped Java?")))
                .thenReturn(new ChatResult("Yes. Patrick has shipped Java in FHIR Lite.", "openai/gpt-4o-mini", 1200, 30));

        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Has Patrick shipped Java?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.answer").value("Yes. Patrick has shipped Java in FHIR Lite."))
                .andExpect(jsonPath("$.model").value("openai/gpt-4o-mini"))
                .andExpect(jsonPath("$.usage.promptTokens").value(1200))
                .andExpect(jsonPath("$.usage.completionTokens").value(30));
    }

    @Test
    void blankQuestionIs400AndTheModelIsNeverCalled() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.error").isNotEmpty());
        verify(model, never()).complete(anyString(), anyString());
    }

    @Test
    void questionOver500CharactersIs400() throws Exception {
        String longQ = "a".repeat(501);
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"" + longQ + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false));
        verify(model, never()).complete(anyString(), anyString());
    }

    @Test
    void missingQuestionAndNonJsonAre400() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.ok").value(false));
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.ok").value(false));
        verify(model, never()).complete(anyString(), anyString());
    }

    @Test
    void missingKeyIs503WithAFriendlyMessageAndNoKeyName() throws Exception {
        when(model.isConfigured()).thenReturn(false);
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"hi\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("The chatbot is not configured right now."))
                .andExpect(content().string(not(containsString("OPENROUTER"))));
        verify(model, never()).complete(anyString(), anyString());
    }

    @Test
    void upstreamFailureIs502WithoutLeakingTheBody() throws Exception {
        stubProfile();
        when(model.isConfigured()).thenReturn(true);
        when(model.complete(anyString(), anyString())).thenThrow(new UpstreamException("HTTP 429 secret body sk-or-xyz"));
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"hi\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("The chatbot couldn't answer right now. Please try again."))
                .andExpect(content().string(not(containsString("sk-or"))));
    }

    @Test
    void emptyModelAnswerBecomesTheFallbackSentence() throws Exception {
        stubProfile();
        when(model.isConfigured()).thenReturn(true);
        when(model.complete(anyString(), anyString())).thenReturn(new ChatResult("  ", "openai/gpt-4o-mini", 10, 0));
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
