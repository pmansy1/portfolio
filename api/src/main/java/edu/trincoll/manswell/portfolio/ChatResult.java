package edu.trincoll.manswell.portfolio;

/** One model answer with what the API response records about it. */
public record ChatResult(String answer, String model, int promptTokens, int completionTokens) {
}
