package com.capstone.etp.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Calls Gemini (generateContent) from the server, with the key in a request header. The key is never logged or put in a
 * message; nothing the customer typed is logged either. Any failure is a quiet "no answer" and the caller falls back.
 *
 * If the configured model is not found (404: a wrong or retired model id), the fallback models are tried in turn, the
 * one that works is remembered, and the models this key can actually use are listed once in the log so the id can be fixed.
 */
@Slf4j
public class RestGeminiClient implements GeminiClient {

    private final RestClient restClient;
    private final String apiKey;
    private final List<String> models;
    private final int maxOutputTokens;
    private volatile String working;
    private volatile boolean listed;

    public RestGeminiClient(RestClient restClient, String apiKey, String model, List<String> fallbackModels, int maxOutputTokens) {
        this.restClient = restClient;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        Set<String> order = new LinkedHashSet<>();
        order.add(model);
        if (fallbackModels != null) {
            fallbackModels.stream().map(String::trim).filter(name -> !name.isEmpty()).forEach(order::add);
        }
        this.models = List.copyOf(order);
        this.maxOutputTokens = maxOutputTokens;
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    @Override
    public Optional<String> generate(String systemInstruction, String userContent) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userContent)))),
                "generationConfig", Map.of("temperature", 0.2, "maxOutputTokens", maxOutputTokens));

        for (String model : candidates()) {
            try {
                Optional<String> text = callWithRetry(model, body);
                if (text.isPresent()) {
                    if (!model.equals(working)) {
                        log.info("[ASSISTANT] using model {}", model);
                    }
                    working = model;
                }
                return text;
            } catch (RestClientResponseException e) {
                if (e.getStatusCode().value() == 404) {
                    log.warn("[ASSISTANT] model {} was not found (404): {}", model, googleSays(e));
                    listModelsOnce();
                    continue;
                }
                log.warn("[ASSISTANT] the model call failed: status={} model={}: {}", e.getStatusCode().value(), model, googleSays(e));
                return Optional.empty();
            } catch (RuntimeException e) {
                // The key travels in a header, so neither the message nor the cause can carry it.
                log.warn("[ASSISTANT] the model call failed: {} model={} cause={} detail={}", e.getClass().getSimpleName(), model,
                        e.getCause() == null ? "none" : e.getCause().getClass().getSimpleName(), brief(e));
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** The model that last worked first, then the configured order. */
    private List<String> candidates() {
        List<String> order = new ArrayList<>();
        if (working != null) {
            order.add(working);
        }
        models.stream().filter(name -> !name.equals(working)).forEach(order::add);
        return order;
    }

    /**
     * Generating text changes nothing, so a call that broke on the way (a reset connection, a response cut short) is
     * safe to make once more. An answer from Google, good or bad, is never repeated.
     */
    private Optional<String> callWithRetry(String model, Map<String, Object> body) {
        long started = System.nanoTime();
        try {
            return call(model, body);
        } catch (RestClientResponseException e) {
            throw e;
        } catch (RestClientException first) {
            // Only a connection that broke quickly is worth one more go; a slow one would just keep the customer waiting.
            if (System.nanoTime() - started > 3_000_000_000L) {
                throw first;
            }
            log.warn("[ASSISTANT] the connection broke ({}), trying once more", first.getClass().getSimpleName());
            return call(model, body);
        }
    }

    /** What Google said about the failure (its error message; the key is in a header, never in the body). */
    private static String googleSays(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        return body == null ? "" : brief(new RuntimeException(body));
    }

    private static String brief(Throwable e) {
        String message = e.getMessage() == null ? "" : e.getMessage().replaceAll("\s+", " ");
        return message.length() > 200 ? message.substring(0, 200) : message;
    }

    private Optional<String> call(String model, Map<String, Object> body) {
        JsonNode response = restClient.post()
                .uri("/v1beta/models/{model}:generateContent", model)
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        JsonNode text = response == null ? null : response.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        if (text == null || text.isMissingNode() || !text.isTextual() || text.asText().isBlank()) {
            log.warn("[ASSISTANT] the model {} returned no usable text", model);
            return Optional.empty();
        }
        return Optional.of(text.asText().trim());
    }

    /** Once per run: which models can this key call? So a wrong id in the settings can be corrected from the log. */
    private void listModelsOnce() {
        if (listed) {
            return;
        }
        listed = true;
        try {
            JsonNode response = restClient.get()
                    .uri("/v1beta/models?pageSize=200")
                    .header("x-goog-api-key", apiKey)
                    .retrieve()
                    .body(JsonNode.class);
            List<String> names = new ArrayList<>();
            if (response != null) {
                for (JsonNode model : response.path("models")) {
                    boolean generates = false;
                    for (JsonNode method : model.path("supportedGenerationMethods")) {
                        generates |= "generateContent".equals(method.asText());
                    }
                    String name = model.path("name").asText("").replaceFirst("^models/", "");
                    if (generates && name.contains("gemini")) {
                        names.add(name);
                    }
                }
            }
            log.warn("[ASSISTANT] models this key can use (set GEMINI_MODEL to one of these): {}", names);
        } catch (RestClientException e) {
            log.warn("[ASSISTANT] could not list the available models: {}", e.getClass().getSimpleName());
        }
    }
}
