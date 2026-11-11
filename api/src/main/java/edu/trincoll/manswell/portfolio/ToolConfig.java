package edu.trincoll.manswell.portfolio;

import java.time.Duration;
import java.util.List;

import edu.trincoll.manswell.portfolio.tools.ClaimChecker;
import edu.trincoll.manswell.portfolio.tools.RecentCommitsTool;
import edu.trincoll.manswell.portfolio.tools.Tool;
import edu.trincoll.manswell.portfolio.tools.ToolLoop;
import edu.trincoll.manswell.portfolio.tools.ToolRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** The chatbot's tools and the loop that runs them (spec components 11 and 12). */
@Configuration
public class ToolConfig {

    static final int MAX_STEPS = 4;                       // behavior 28
    static final Duration GITHUB_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    RecentCommitsTool recentCommitsTool(@Value("${github.base-url}") String baseUrl,
                                        @Value("${github.token:}") String token) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(GITHUB_TIMEOUT);
        f.setReadTimeout(GITHUB_TIMEOUT);
        return new RecentCommitsTool(RestClient.builder().requestFactory(f), baseUrl, token);
    }

    @Bean
    ToolRegistry toolRegistry(List<Tool> tools) {
        return new ToolRegistry(tools);
    }

    @Bean
    ToolLoop toolLoop(OpenRouterClient model, ToolRegistry registry) {
        return new ToolLoop(model, registry, new ClaimChecker(RecentCommitsTool.REPOS), MAX_STEPS);
    }
}
