package edu.trincoll.manswell.portfolio;

import java.time.Duration;
import java.util.List;
import java.util.Map;

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
public class OpenRouterClient implements ModelClient {

    static final int MAX_TOKENS = 400;          // behavior 7
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

    @Override
    public ChatResult complete(String systemPrompt, String question) {
        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", MAX_TOKENS,
                "temperature", TEMPERATURE,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", question)));
        try {
            Completion c = http.post().uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Completion.class);
            return toResult(c);
        } catch (RestClientResponseException e) {
            throw new UpstreamException("model provider returned HTTP " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new UpstreamException("model provider call failed: " + e.getClass().getSimpleName(), e);
        }
    }

    private ChatResult toResult(Completion c) {
        if (c == null) {
            throw new UpstreamException("model provider returned an empty body");
        }
        String answer = c.choices() == null || c.choices().isEmpty() || c.choices().get(0).message() == null
                ? "" : nullToEmpty(c.choices().get(0).message().content());
        int in = c.usage() == null || c.usage().promptTokens() == null ? 0 : c.usage().promptTokens();
        int out = c.usage() == null || c.usage().completionTokens() == null ? 0 : c.usage().completionTokens();
        return new ChatResult(answer, c.model() == null ? model : c.model(), in, out);
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
    record Message(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(@JsonProperty("prompt_tokens") Integer promptTokens,
                 @JsonProperty("completion_tokens") Integer completionTokens) {
    }
}
