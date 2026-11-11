package edu.trincoll.manswell.portfolio;

/** The one thing the controller needs from a model. Tests replace it with a mock. */
public interface ModelClient {

    boolean isConfigured();

    ChatResult complete(String systemPrompt, String question);
}
