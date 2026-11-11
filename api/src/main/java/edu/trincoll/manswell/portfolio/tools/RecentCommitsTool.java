package edu.trincoll.manswell.portfolio.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * recent_commits (spec component 11): the latest commits of one of my profile's repositories, from GitHub's public
 * REST API. Read-only. Every failure comes back as {"error": ...} the model can read (behavior 30).
 */
public class RecentCommitsTool implements Tool {

    public static final String OWNER = "pmansy1";
    public static final List<String> REPOS =
            List.of("fhir-lite-patient-explorer", "ehr-reconciliation", "Cloud-Native-Dev-Project", "portfolio");
    static final int DEFAULT_COUNT = 5;
    static final int MAX_COUNT = 10;
    private static final int MAX_MESSAGE = 100;
    private static final ParameterizedTypeReference<List<Map<String, Object>>> COMMITS = new ParameterizedTypeReference<>() { };

    private final RestClient http;

    /** @param token a GitHub token, or blank for unauthenticated (60 requests an hour) */
    public RecentCommitsTool(RestClient.Builder builder, String baseUrl, String token) {
        RestClient.Builder b = builder.baseUrl(baseUrl)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("User-Agent", "pmansy1-portfolio-chatbot");
        if (token != null && !token.isBlank()) {
            b = b.defaultHeader("Authorization", "Bearer " + token.strip());
        }
        this.http = b.build();
    }

    @Override
    public String name() {
        return "recent_commits";
    }

    @Override
    public String description() {
        return "The most recent commits (short hash, date, author, message) in one of Patrick's public GitHub "
                + "repositories. Call this before saying anything about what Patrick has worked on recently or "
                + "lately. Commits by other authors are teammates' work, not Patrick's.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("type", "object",
                "properties", Map.of(
                        "repo", Map.of("type", "string", "enum", REPOS,
                                "description", "The repository name, without the owner"),
                        "count", Map.of("type", "integer", "minimum", 1, "maximum", MAX_COUNT,
                                "description", "How many commits, 1 to 10 (default 5)")),
                "required", List.of("repo"));
    }

    @Override
    public boolean hasSideEffect() {
        return false;
    }

    @Override
    public Map<String, Object> run(Map<String, Object> args) {
        String repo = (String) args.get("repo");
        int count = args.get("count") instanceof Number n ? n.intValue() : DEFAULT_COUNT;
        String full = OWNER + "/" + repo;
        try {
            List<Map<String, Object>> raw = http.get()
                    .uri("/repos/{owner}/{repo}/commits?per_page={n}", OWNER, repo, count)
                    .retrieve()
                    .body(COMMITS);
            return Map.of("repo", full, "commits", summarize(raw == null ? List.of() : raw));
        } catch (RestClientResponseException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            if (status == HttpStatus.NOT_FOUND) {
                return Map.of("error", "no public repository named '" + full + "'", "repo", full);
            }
            if (status == HttpStatus.FORBIDDEN || status == HttpStatus.TOO_MANY_REQUESTS) {
                return Map.of("error", "GitHub rate limit reached; try again later");
            }
            return Map.of("error", "GitHub answered " + e.getStatusCode().value());
        } catch (RestClientException e) {
            return Map.of("error", "could not reach GitHub");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> summarize(List<Map<String, Object>> raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> c : raw) {
            Map<String, Object> commit = (Map<String, Object>) c.getOrDefault("commit", Map.of());
            Map<String, Object> gitAuthor = (Map<String, Object>) commit.getOrDefault("author", Map.of());
            Map<String, Object> ghUser = c.get("author") instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
            String author = ghUser != null && ghUser.get("login") != null
                    ? (String) ghUser.get("login") : String.valueOf(gitAuthor.getOrDefault("name", "unknown"));
            String sha = String.valueOf(c.getOrDefault("sha", ""));
            String date = String.valueOf(gitAuthor.getOrDefault("date", ""));
            String message = String.valueOf(commit.getOrDefault("message", "")).lines().findFirst().orElse("");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sha", sha.length() > 7 ? sha.substring(0, 7) : sha);
            row.put("date", date.length() >= 10 ? date.substring(0, 10) : date);
            row.put("author", author);
            row.put("message", message.length() > MAX_MESSAGE ? message.substring(0, MAX_MESSAGE) : message);
            out.add(Map.copyOf(row));
        }
        return List.copyOf(out);
    }
}
