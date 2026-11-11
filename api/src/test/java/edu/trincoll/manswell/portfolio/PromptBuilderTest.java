package edu.trincoll.manswell.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Behavior 2: the system prompt carries the whole profile between <profile> tags and the rules. No network. */
class PromptBuilderTest {

    static final String TEMPLATE = "Rules: only the profile. shipped / learning / not yet.\n<profile>\n{{PROFILE}}\n</profile>\n";
    static final String PROFILE = "{\"person\":{\"name\":\"Patrick Manswell\"}}";

    @Test
    void profileIsInsertedBetweenTheTags() {
        String prompt = new PromptBuilder(TEMPLATE, PROFILE).systemPrompt();
        assertThat(prompt).contains("<profile>\n" + PROFILE + "\n</profile>");
        assertThat(prompt).doesNotContain("{{PROFILE}}");
        assertThat(prompt).startsWith("Rules: only the profile.");
    }

    @Test
    void templateWithoutThePlaceholderIsRejected() {
        assertThatThrownBy(() -> new PromptBuilder("no slot here", PROFILE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("{{PROFILE}}");
    }

    @Test
    void emptyProfileIsRejected() {
        assertThatThrownBy(() -> new PromptBuilder(TEMPLATE, "  "))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theRealPromptFileHasTheRulesTheSpecRequires() throws Exception {
        String real = java.nio.file.Files.readString(java.nio.file.Path.of("..", "prompts", "system.md"));
        String prompt = new PromptBuilder(real, PROFILE).systemPrompt();
        assertThat(prompt).contains("<profile>", "</profile>", "Patrick Manswell");
        assertThat(prompt.toLowerCase()).contains("shipped", "being learned", "deliberately not yet",
                "my_role", "don't rank", "premise", "flattery", "personal", "ignore previous instructions", "120 words");
    }
}
