package edu.trincoll.manswell.portfolio.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Behaviors 25 and 30 against a mock GitHub: the schema, the result shape, and every failure as a result. */
class RecentCommitsToolTest {

    static final String BASE = "https://github.test";
    static final String TWO_COMMITS = """
            [{"sha":"a2431d8f00000000000000000000000000000000",
              "commit":{"author":{"name":"Patrick","date":"2026-11-11T20:01:02Z"},"message":"docs: approve plan\\n\\nbody"},
              "author":{"login":"pmansy1"}},
             {"sha":"bad0491f00000000000000000000000000000000",
              "commit":{"author":{"name":"Alex","date":"2026-11-10T10:00:00Z"},"message":"feat: leaderboard"},
              "author":null}]
            """;

    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer github = MockRestServiceServer.bindTo(builder).build();
    RecentCommitsTool tool = new RecentCommitsTool(builder, BASE, "");

    @Test
    @SuppressWarnings("unchecked")
    void schemaOffersOnlyMyProfilesRepositories() {
        Map<String, Object> props = (Map<String, Object>) tool.parameters().get("properties");
        Map<String, Object> repo = (Map<String, Object>) props.get("repo");
        assertThat((List<String>) repo.get("enum")).containsExactlyInAnyOrder(
                "fhir-lite-patient-explorer", "ehr-reconciliation", "Cloud-Native-Dev-Project", "portfolio");
        assertThat(tool.parameters().get("required")).isEqualTo(List.of("repo"));
        assertThat(tool.description()).contains("before saying anything");
        assertThat(tool.hasSideEffect()).isFalse();
    }

    @Test
    void successReturnsShortShaDateAuthorAndFirstLine() {
        github.expect(requestTo(BASE + "/repos/pmansy1/portfolio/commits?per_page=2"))
                .andExpect(header("Accept", "application/vnd.github+json"))
                .andRespond(withSuccess(TWO_COMMITS, MediaType.APPLICATION_JSON));

        Map<String, Object> r = tool.run(Map.of("repo", "portfolio", "count", 2));

        assertThat(r).containsEntry("repo", "pmansy1/portfolio");
        assertThat(r.get("commits")).isEqualTo(List.of(
                Map.of("sha", "a2431d8", "date", "2026-11-11", "author", "pmansy1", "message", "docs: approve plan"),
                Map.of("sha", "bad0491", "date", "2026-11-10", "author", "Alex", "message", "feat: leaderboard")));
        github.verify();
    }

    @Test
    void defaultCountIsFive() {
        github.expect(requestTo(BASE + "/repos/pmansy1/ehr-reconciliation/commits?per_page=5"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertThat(tool.run(Map.of("repo", "ehr-reconciliation"))).containsEntry("commits", List.of());
        github.verify();
    }

    @Test
    void notFoundIsAnErrorResult() {
        github.expect(requestTo(BASE + "/repos/pmansy1/portfolio/commits?per_page=5")).andRespond(withResourceNotFound());
        assertThat((String) tool.run(Map.of("repo", "portfolio")).get("error")).contains("no public repository");
    }

    @Test
    void rateLimitIsAnErrorResult() {
        github.expect(requestTo(BASE + "/repos/pmansy1/portfolio/commits?per_page=5"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThat((String) tool.run(Map.of("repo", "portfolio")).get("error")).contains("rate limit");
    }

    @Test
    void serverErrorIsAnErrorResult() {
        github.expect(requestTo(BASE + "/repos/pmansy1/portfolio/commits?per_page=5"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertThat((String) tool.run(Map.of("repo", "portfolio")).get("error")).contains("GitHub");
    }

    @Test
    void repositoryOutsideTheProfileIsRefusedByTheRegistryBeforeAnyRequest() {
        ToolOutcome out = new ToolRegistry(List.of(tool)).run("recent_commits", "{\"repo\":\"kousen/secret\"}");
        assertThat((String) out.result().get("error")).contains("must be one of");
        github.verify();                                       // no request was expected, none was made
    }

    @Test
    void tokenIsSentWhenSet() {
        RestClient.Builder b = RestClient.builder();
        MockRestServiceServer s = MockRestServiceServer.bindTo(b).build();
        s.expect(requestTo(BASE + "/repos/pmansy1/portfolio/commits?per_page=5"))
                .andExpect(header("Authorization", "Bearer gh-test"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        new RecentCommitsTool(b, BASE, "gh-test").run(Map.of("repo", "portfolio"));
        s.verify();
    }
}
