package edu.trincoll.manswell.portfolio;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** POST /api/chat and GET /api/profile (spec component 4). */
@RestController
@RequestMapping("/api")
public class ChatController {

    static final int MAX_QUESTION = 500;
    static final String NO_ANSWER = "I don't have an answer to that from Patrick's profile.";

    private final ModelClient model;
    private final ProfileService profile;

    public ChatController(ModelClient model, ProfileService profile) {
        this.model = model;
        this.profile = profile;
    }

    public record ChatRequest(@NotBlank @Size(max = MAX_QUESTION) String question) {
    }

    public record Usage(int promptTokens, int completionTokens) {
    }

    public record ChatResponse(boolean ok, String answer, String model, Usage usage) {
    }

    @PostMapping(path = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        if (!model.isConfigured()) {
            throw new NotConfiguredException();
        }
        ChatResult r = model.complete(profile.systemPrompt(), request.question().strip());
        String answer = r.answer() == null || r.answer().isBlank() ? NO_ANSWER : r.answer().strip();
        return new ChatResponse(true, answer, r.model(), new Usage(r.promptTokens(), r.completionTokens()));
    }

    @GetMapping("/profile")
    public ResponseEntity<String> profile() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(profile.rawJson());
    }
}
