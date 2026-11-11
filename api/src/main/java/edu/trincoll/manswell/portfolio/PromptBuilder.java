package edu.trincoll.manswell.portfolio;

/** Puts the profile JSON into the grounding prompt template (spec component 3, behavior 2). */
public final class PromptBuilder {

    static final String SLOT = "{{PROFILE}}";

    private final String systemPrompt;

    public PromptBuilder(String template, String profileJson) {
        if (template == null || !template.contains(SLOT)) {
            throw new IllegalStateException("prompt template has no " + SLOT + " placeholder");
        }
        if (profileJson == null || profileJson.isBlank()) {
            throw new IllegalStateException("profile JSON is empty");
        }
        this.systemPrompt = template.replace(SLOT, profileJson.strip());
    }

    public String systemPrompt() {
        return systemPrompt;
    }
}
