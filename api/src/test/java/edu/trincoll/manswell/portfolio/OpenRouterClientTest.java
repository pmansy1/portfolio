package edu.trincoll.manswell.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Behavior 7 and the request shape, against a mock HTTP server. No network, no key. */
class OpenRouterClientTest {

    static final String BASE = "https://openrouter.test/api/v1";
    static final String REPLY = """
            {"model":"openai/gpt-4o-mini",
             "choices":[{"message":{"role":"assistant","content":"Patrick has shipped Java."},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":1200,"completion_tokens":7}}
            """;

    @Test
    void sendsSystemAndUserMessagesWithCappedTokensAndTemperature() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("openai/gpt-4o-mini"))
                .andExpect(jsonPath("$.max_tokens").value(Matchers.lessThanOrEqualTo(400)))
                .andExpect(jsonPath("$.temperature").value(Matchers.lessThanOrEqualTo(0.3)))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("SYS"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("Q"))
                .andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));

        OpenRouterClient client = new OpenRouterClient(builder, BASE, "test-key", "openai/gpt-4o-mini");
        ChatResult r = client.complete("SYS", "Q");

        assertThat(r.answer()).isEqualTo("Patrick has shipped Java.");
        assertThat(r.model()).isEqualTo("openai/gpt-4o-mini");
        assertThat(r.promptTokens()).isEqualTo(1200);
        assertThat(r.completionTokens()).isEqualTo(7);
        server.verify();
    }

    @Test
    void serverErrorBecomesUpstreamException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/chat/completions")).andRespond(withServerError());
        OpenRouterClient client = new OpenRouterClient(builder, BASE, "test-key", "openai/gpt-4o-mini");
        assertThatThrownBy(() -> client.complete("SYS", "Q")).isInstanceOf(UpstreamException.class);
    }

    @Test
    void blankKeyMeansNotConfigured() {
        assertThat(new OpenRouterClient(RestClient.builder(), BASE, "", "m").isConfigured()).isFalse();
        assertThat(new OpenRouterClient(RestClient.builder(), BASE, "k", "m").isConfigured()).isTrue();
    }
}
