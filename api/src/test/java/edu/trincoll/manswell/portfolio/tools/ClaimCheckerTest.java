package edu.trincoll.manswell.portfolio.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Behavior 31: what plain code can catch about an answer, given the trace. */
class ClaimCheckerTest {

    final ClaimChecker checker = new ClaimChecker(RecentCommitsTool.REPOS);

    static TraceStep fetched(String repo, String sha, String date) {
        return TraceStep.call(1, new ToolCall("c1", "recent_commits", "{}"),
                Map.of("repo", "pmansy1/" + repo, "commits",
                        List.of(Map.of("sha", sha, "date", date, "author", "pmansy1", "message", "docs: x"))));
    }

    static TraceStep failed(String repo) {
        return TraceStep.call(1, new ToolCall("c1", "recent_commits", "{\"repo\":\"" + repo + "\"}"),
                Map.of("error", "no public repository named 'pmansy1/" + repo + "'"));
    }

    @Test
    void answerBuiltFromTheResultIsSupported() {
        var trace = List.of(fetched("portfolio", "a2431d8", "2026-11-11"));
        assertThat(checker.check("The latest commit to portfolio was a2431d8 on 2026-11-11.", trace).supported()).isTrue();
    }

    @Test
    void inventedHashAndDateAreFlagged() {
        var trace = List.of(fetched("portfolio", "a2431d8", "2026-11-11"));
        ClaimCheck c = checker.check("He also pushed 9f8e7d6 on 2026-11-12.", trace);
        assertThat(c.unsupported()).hasSize(2);
    }

    @Test
    void recentWorkForARepositoryThatFailedIsFlagged() {
        ClaimCheck c = checker.check("His recent commits to fhir-lite-patient-explorer added search.", List.of(failed("fhir-lite-patient-explorer")));
        assertThat(c.unsupported()).anySatisfy(u -> assertThat(u).contains("fhir-lite-patient-explorer"));
    }

    @Test
    void sayingTheLookupFailedIsNotAClaim() {
        ClaimCheck c = checker.check("I couldn't look up recent commits for fhir-lite-patient-explorer.", List.of(failed("fhir-lite-patient-explorer")));
        assertThat(c.supported()).isTrue();
    }

    @Test
    void ordinaryWordsAreNotMistakenForHashes() {
        assertThat(checker.check("He defaced nothing and added a feature.", List.of()).supported()).isTrue();
    }
}
