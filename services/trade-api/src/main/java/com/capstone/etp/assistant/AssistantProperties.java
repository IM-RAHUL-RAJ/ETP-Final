package com.capstone.etp.assistant;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** app.assistant.*. The key comes from the environment (GEMINI_API_KEY) and is never logged, returned or committed. */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.assistant")
public class AssistantProperties {

    private final Gemini gemini = new Gemini();
    /** Questions one account may ask per minute. */
    private int ratePerMinute = 20;

    @Getter
    @Setter
    public static class Gemini {
        private String apiKey = "";
        private String model = "gemini-3.1-flash-lite";
        /** Tried in order when the model above is not found (404). */
        private java.util.List<String> fallbackModels = java.util.List.of("gemini-flash-lite-latest", "gemini-flash-latest");
        /** Set from GEMINI_BASE_URL (app.assistant.gemini.base-url). */
        private String baseUrl;
        private int connectTimeoutMs = 2000;
        private int readTimeoutMs = 8000;
        private int maxOutputTokens = 400;
    }
}
