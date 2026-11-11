package edu.trincoll.manswell.portfolio;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Loads the profile and the prompt once at startup and fails fast if either is missing or invalid. */
@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final String rawJson;
    private final String systemPrompt;

    public ProfileService(@Value("${portfolio.profile-path}") String profilePath,
                          @Value("${portfolio.prompt-path}") String promptPath) {
        this.rawJson = read(profilePath, "profile");
        try {
            new ObjectMapper().readTree(rawJson);
        } catch (JacksonException e) {
            throw new IllegalStateException("profile at " + profilePath + " is not valid JSON: " + e.getOriginalMessage(), e);
        }
        this.systemPrompt = new PromptBuilder(read(promptPath, "prompt"), rawJson).systemPrompt();
        log.info("Loaded profile ({} chars) and prompt ({} chars)", rawJson.length(), systemPrompt.length());
    }

    private static String read(String path, String what) {
        try {
            return Files.readString(Path.of(path));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the " + what + " file at " + Path.of(path).toAbsolutePath(), e);
        }
    }

    public String rawJson() {
        return rawJson;
    }

    public String systemPrompt() {
        return systemPrompt;
    }
}
