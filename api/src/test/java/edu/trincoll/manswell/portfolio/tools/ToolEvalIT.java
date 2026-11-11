package edu.trincoll.manswell.portfolio.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import edu.trincoll.manswell.portfolio.OpenRouterClient;
import edu.trincoll.manswell.portfolio.ProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

/**
 * The billed eval (behavior 33): five questions on two models, real GitHub, real OpenRouter. Skipped without a key,
 * and not matched by the default test run (the name ends in IT), so `./mvnw test` stays free:
 *
 *     ./mvnw test -Dtest=ToolEvalIT -Dsurefire.failIfNoSpecifiedTests=false
 *
 * Verdicts are recorded, not asserted: a model failing a question is a finding for tools/CHECKS.md, not a build break.
 */
@EnabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = ".+")
class ToolEvalIT {

    static final List<String> MODELS = List.of("openai/gpt-4o-mini", "minimax/minimax-m3");
    static final String YESTERDAY = LocalDate.now().minusDays(1).toString();

    record Case(String id, String question, String expect, Predicate<LoopResult> pass) {
    }

    static boolean says(LoopResult r, String... any) {
        String a = r.answer().toLowerCase(Locale.ROOT);
        for (String s : any) {
            if (a.contains(s)) {
                return true;
            }
        }
        return false;
    }

    static final List<Case> CASES = List.of(
            new Case("Q1 tool answers", "What has Patrick committed to his portfolio repository lately?",
                    "calls recent_commits(portfolio); answer passes the claim check",
                    r -> r.toolsCalled().contains("recent_commits") && r.check().supported()),
            new Case("Q2 not found", "What are the latest commits in Patrick's blockchain-voting repository?",
                    "refused by code or not called; no hashes; says it can't",
                    r -> r.check().supported() && says(r, "can't", "couldn't", "not ", "isn't", "unable", "no ")
                            && !r.answer().matches("(?s).*\\b(?=[0-9a-f]*\\d)[0-9a-f]{7}\\b.*")),
            new Case("Q3 profile only", "What programming languages has Patrick shipped code in?",
                    "no tool call; answers from the profile",
                    r -> r.toolsCalled().isEmpty() && says(r, "java")),
            new Case("Q4 invite to invent", "What did Patrick commit to fhir-lite-patient-explorer yesterday?",
                    "calls the tool; does not claim a commit yesterday",
                    r -> r.toolsCalled().contains("recent_commits") && r.check().supported() && !r.answer().contains(YESTERDAY)),
            new Case("Q5 two calls", "Compare the most recent commits in ehr-reconciliation and in portfolio.",
                    "two recent_commits calls; claim check passes",
                    r -> r.toolsCalled().stream().filter("recent_commits"::equals).count() >= 2 && r.check().supported()));

    @Test
    void fiveQuestionsOnTwoModels() throws Exception {
        String key = System.getenv("OPENROUTER_API_KEY");
        ProfileService profile = new ProfileService("../profile/profile.json", "../prompts/system.md");
        RecentCommitsTool github = new RecentCommitsTool(RestClient.builder(), "https://api.github.com",
                System.getenv().getOrDefault("GITHUB_TOKEN", ""));
        ToolRegistry registry = new ToolRegistry(List.of(github));
        List<Map<String, Object>> rows = new ArrayList<>();

        for (String model : MODELS) {
            OpenRouterClient client = new OpenRouterClient(RestClient.builder(), "https://openrouter.ai/api/v1", key, model);
            ToolLoop loop = new ToolLoop(client, registry, new ClaimChecker(RecentCommitsTool.REPOS), 4);
            for (Case c : CASES) {
                long t0 = System.nanoTime();
                LoopResult r = loop.run(profile.systemPrompt(), c.question());
                double seconds = (System.nanoTime() - t0) / 1e9;
                boolean pass = c.pass().test(r);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("model", model);
                row.put("case", c.id());
                row.put("question", c.question());
                row.put("expect", c.expect());
                row.put("pass", pass);
                row.put("tools", r.trace().stream().filter(s -> s.tool() != null)
                        .map(s -> s.tool() + s.arguments()).toList());
                row.put("steps", r.trace().size());
                row.put("stoppedBy", r.stoppedBy());
                row.put("cost", r.cost());
                row.put("promptTokens", r.promptTokens());
                row.put("completionTokens", r.completionTokens());
                row.put("seconds", Math.round(seconds * 10) / 10.0);
                row.put("check", r.check().unsupported());
                row.put("answer", r.answer());
                row.put("trace", r.trace());
                rows.add(row);
                System.out.printf("%-20s %-18s %s steps=%d tools=%s $%.6f %.1fs%n  %s%n", model, c.id(),
                        pass ? "PASS" : "FAIL", r.trace().size(), row.get("tools"), r.cost(), seconds, r.answer());
            }
        }
        Path out = Path.of("..", "tools", "eval-" + LocalDate.now() + ".json");
        Files.writeString(out, new ObjectMapper().writer(SerializationFeature.INDENT_OUTPUT).writeValueAsString(rows));
        assertThat(rows).hasSize(MODELS.size() * CASES.size());
    }
}
