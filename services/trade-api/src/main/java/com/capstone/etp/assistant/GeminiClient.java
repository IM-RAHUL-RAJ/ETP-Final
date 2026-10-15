package com.capstone.etp.assistant;

import java.util.Optional;

/** The language model behind the assistant. It only phrases an answer: it is never asked for data and never given a tool. */
public interface GeminiClient {

    /** False when no API key is configured: the assistant then answers from the rules alone. */
    boolean isConfigured();

    /** The model's text, or empty when it could not be had (not configured, timed out, refused, malformed). Never throws. */
    Optional<String> generate(String systemInstruction, String userContent);
}
