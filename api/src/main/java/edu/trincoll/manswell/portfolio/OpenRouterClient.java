package edu.trincoll.manswell.portfolio;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import edu.trincoll.manswell.portfolio.tools.ChatModel;
import edu.trincoll.manswell.portfolio.tools.ModelTurn;
import edu.trincoll.manswell.portfolio.tools.ToolCall;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Calls OpenRouter's OpenAI-compatible chat endpoint directly, so the request is visible (spec component 4). */
@Component
public class OpenRouterClient implements ModelClient, ChatModel {

    static final int MAX_TOKENS = 400;          // behavior 7
    /** Models with hidden reasoning spend tokens thinking; at 400 they can return nothing (Week 8 finding). */
    static final Map<String, Integer> MAX_TOKENS_BY_MODEL = Map.of("minimax/minimax-m3", 1500);
    static final double TEMPERATURE = 0.2;      // behavior 7: at most 0.3
    static final Duration READ_TIMEOUT = Duration.ofSeconds(20);
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private static final Logger log = LoggerFactory.getLogger(OpenRouterClient.class);

    private final RestClient http;
    private final String apiKey;
    private final String model;

    @Autowired
    public OpenRouterClient(@Value("${openrouter.base-url}") String baseUrl,
                            @Value("${openrouter.api-key:}") String apiKey,
                            @Value("${openrouter.model}") String model) {
        this(RestClient.builder().requestFactory(timeouts()), baseUrl, apiKey, model);
    }

    OpenRouterClient(RestClient.Builder builder, String baseUrl, String apiKey, String model) {
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.model = model;
        this.http = builder.baseUrl(baseUrl).build();
        if (this.apiKey.isEmpty()) {
            log.warn("No model API key is set: /api/chat will answer 503 until one is configured.");
        }
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(CONNECT_TIMEOUT);
        f.setReadTimeout(READ_TIMEOUT);
        return f;
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    public String model() {
        return model;
    }

    @Override
    public ChatResult complete(String systemPrompt, String question) {
        ModelTurn turn = send(List.of(Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", question)), List.of());
        return new ChatResult(turn.content(), model, turn.promptTokens(), turn.completionTokens());
    }

    @Override
    public ModelTurn send(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", MAX_TOKENS_BY_MODEL.getOrDefault(model, MAX_TOKENS));
        body.put("temperature", TEMPERATURE);
        body.put("messages", messages);
        if (!tools.isEmpty()) {
            body.put("tools", tools);
        }
        try {
            Completion c = http.post().uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.copyOf(body))
                    .retrieve()
                    .body(Completion.class);
            return toTurn(c);
        } catch (RestClientResponseException e) {
            throw new UpstreamException("model provider returned HTTP " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new UpstreamException("model provider call failed: " + e.getClass().getSimpleName(), e);
        }
    }

    private ModelTurn toTurn(Completion c) {
        if (c == null || c.choices() == null || c.choices().isEmpty()) {
            throw new UpstreamException("model provider returned no choices");
        }
        Choice choice = c.choices().get(0);
        Message m = choice.message();
        String content = m == null ? "" : nullToEmpty(m.content());
        List<ToolCall> calls = m == null || m.toolCalls() == null ? List.of() : m.toolCalls().stream()
                .filter(t -> t.function() != null)
                .map(t -> new ToolCall(t.id(), t.function().name(), t.function().arguments()))
                .toList();
        int in = c.usage() == null || c.usage().promptTokens() == null ? 0 : c.usage().promptTokens();
        int out = c.usage() == null || c.usage().completionTokens() == null ? 0 : c.usage().completionTokens();
        Double cost = c.usage() == null ? null : c.usage().cost();
        return new ModelTurn(content, calls, choice.finishReason(), in, out, cost);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Completion(String model, List<Choice> choices, Usage usage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message, @JsonProperty("finish_reason") String finishReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String role, String content, @JsonProperty("tool_calls") List<RespToolCall> toolCalls) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespToolCall(String id, String type, RespFunction function) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespFunction(String name, String arguments) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(@JsonProperty("prompt_tokens") Integer promptTokens,
                 @JsonProperty("completion_tokens") Integer completionTokens,
                 Double cost) {
    }
}
