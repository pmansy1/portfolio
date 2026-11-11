package edu.trincoll.manswell.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;

import edu.trincoll.manswell.portfolio.tools.ModelTurn;
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

    static final String TOOL_REPLY = """
            {"model":"openai/gpt-4o-mini",
             "choices":[{"message":{"role":"assistant","content":null,
                "tool_calls":[{"id":"call_abc","type":"function",
                  "function":{"name":"recent_commits","arguments":"{\\"repo\\":\\"portfolio\\"}"}}]},
               "finish_reason":"tool_calls"}],
             "usage":{"prompt_tokens":3000,"completion_tokens":20,"cost":0.00046}}
            """;

    @Test
    void sendIncludesTheToolsAndParsesToolCallsAndCost() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/chat/completions"))
                .andExpect(jsonPath("$.tools[0].function.name").value("recent_commits"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.max_tokens").value(400))
                .andRespond(withSuccess(TOOL_REPLY, MediaType.APPLICATION_JSON));
        OpenRouterClient client = new OpenRouterClient(builder, BASE, "k", "openai/gpt-4o-mini");

        List<Map<String, Object>> tools = List.of(Map.of("type", "function",
                "function", Map.of("name", "recent_commits", "description", "d", "parameters", Map.of("type", "object"))));
        ModelTurn turn = client.send(List.of(Map.of("role", "system", "content", "S"), Map.of("role", "user", "content", "Q")), tools);

        assertThat(turn.asksForTools()).isTrue();
        assertThat(turn.toolCalls().get(0).id()).isEqualTo("call_abc");
        assertThat(turn.toolCalls().get(0).name()).isEqualTo("recent_commits");
        assertThat(turn.toolCalls().get(0).arguments()).isEqualTo("{\"repo\":\"portfolio\"}");
        assertThat(turn.content()).isEmpty();
        assertThat(turn.cost()).isEqualTo(0.00046);
        assertThat(turn.promptTokens()).isEqualTo(3000);
        server.verify();
    }

    @Test
    void reasoningModelGetsALargerTokenBudget() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/chat/completions"))
                .andExpect(jsonPath("$.model").value("minimax/minimax-m3"))
                .andExpect(jsonPath("$.max_tokens").value(1500))
                .andRespond(withSuccess(REPLY, MediaType.APPLICATION_JSON));
        new OpenRouterClient(builder, BASE, "k", "minimax/minimax-m3").send(List.of(Map.of("role", "user", "content", "Q")), List.of());
        server.verify();
    }
}
