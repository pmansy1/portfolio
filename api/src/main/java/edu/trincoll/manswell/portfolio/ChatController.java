package edu.trincoll.manswell.portfolio;

import java.util.List;
import java.util.Map;

import edu.trincoll.manswell.portfolio.tools.ClaimCheck;
import edu.trincoll.manswell.portfolio.tools.LoopResult;
import edu.trincoll.manswell.portfolio.tools.TraceStep;
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

    private final AnswerService answers;
    private final ProfileService profile;

    public ChatController(AnswerService answers, ProfileService profile) {
        this.answers = answers;
        this.profile = profile;
    }

    public record ChatRequest(@NotBlank @Size(max = MAX_QUESTION) String question) {
    }

    public record Usage(int promptTokens, int completionTokens) {
    }

    public record ChatResponse(boolean ok, String answer, String model, Usage usage, String stoppedBy,
                               List<TraceStep> trace, List<Map<String, Object>> pending, ClaimCheck check) {
    }

    @PostMapping(path = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        if (!answers.isConfigured()) {
            throw new NotConfiguredException();
        }
        LoopResult r = answers.answer(request.question().strip());
        String answer = r.answer() == null || r.answer().isBlank() ? NO_ANSWER : r.answer().strip();
        return new ChatResponse(true, answer, answers.model(), new Usage(r.promptTokens(), r.completionTokens()),
                r.stoppedBy(), r.trace(), r.pending(), r.check());
    }

    @GetMapping("/profile")
    public ResponseEntity<String> profile() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(profile.rawJson());
    }
}
