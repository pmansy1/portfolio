package edu.trincoll.manswell.portfolio;

import edu.trincoll.manswell.portfolio.tools.LoopResult;
import edu.trincoll.manswell.portfolio.tools.ToolLoop;
import org.springframework.stereotype.Service;

/** The chatbot's one answer function: the typed path and the voice path both come through here (behavior 32). */
@Service
public class AnswerService {

    private final OpenRouterClient client;
    private final ProfileService profile;
    private final ToolLoop loop;

    public AnswerService(OpenRouterClient client, ProfileService profile, ToolLoop loop) {
        this.client = client;
        this.profile = profile;
        this.loop = loop;
    }

    public boolean isConfigured() {
        return client.isConfigured();
    }

    public String model() {
        return client.model();
    }

    public LoopResult answer(String question) {
        return loop.run(profile.systemPrompt(), question);
    }
}
