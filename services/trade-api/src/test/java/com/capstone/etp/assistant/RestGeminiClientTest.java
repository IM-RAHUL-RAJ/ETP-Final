package com.capstone.etp.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestGeminiClientTest {

    private static final String URL = "http://gemini.test/v1beta/models/gemini-test:generateContent";

    private MockRestServiceServer server;
    private RestGeminiClient client;

    private void build(String key) {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gemini.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestGeminiClient(builder.build(), key, "gemini-test", java.util.List.of("gemini-backup"), 400);
    }

    @Test
    void callsTheModelWithTheKeyInAHeaderAndNeverInTheBodyOrUrl() {
        build("secret-key");
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "secret-key"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value("be nice"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("hello"))
                .andExpect(jsonPath("$.generationConfig.maxOutputTokens").value(400))
                .andRespond(withSuccess("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\" Hi there. \"}]}}]}", MediaType.APPLICATION_JSON));

        Optional<String> answer = client.generate("be nice", "hello");

        server.verify();
        assertEquals(Optional.of("Hi there."), answer);
    }

    @Test
    void aModelThatIsNotFoundFallsBackToTheNextAndRemembersTheOneThatWorks() {
        build("k");
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("http://gemini.test/v1beta/models?pageSize=200")).andRespond(withSuccess("{\"models\":[{\"name\":\"models/gemini-backup\",\"supportedGenerationMethods\":[\"generateContent\"]}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://gemini.test/v1beta/models/gemini-backup:generateContent"))
                .andRespond(withSuccess("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}]}}]}", MediaType.APPLICATION_JSON));
        // the next question goes straight to the model that worked
        server.expect(requestTo("http://gemini.test/v1beta/models/gemini-backup:generateContent"))
                .andRespond(withSuccess("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"again\"}]}}]}", MediaType.APPLICATION_JSON));

        assertEquals(Optional.of("ok"), client.generate("s", "u"));
        assertEquals(Optional.of("again"), client.generate("s", "u"));
        server.verify();
    }

    @Test
    void ifEveryModelIsNotFoundItIsNoAnswer() {
        build("k");
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("http://gemini.test/v1beta/models?pageSize=200")).andRespond(withStatus(HttpStatus.FORBIDDEN));
        server.expect(requestTo("http://gemini.test/v1beta/models/gemini-backup:generateContent")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertTrue(client.generate("s", "u").isEmpty());
        server.verify();
    }

    @Test
    void aConnectionThatBreaksOnceIsRetriedAndThenAnswers() {
        build("k");
        server.expect(requestTo(URL)).andRespond(withException(new java.io.IOException("connection reset")));
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"fine\"}]}}]}", MediaType.APPLICATION_JSON));

        assertEquals(Optional.of("fine"), client.generate("s", "u"));
        server.verify();
    }

    @Test
    void aRefusalFromGoogleIsNeverRepeated() {
        build("k");
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertTrue(client.generate("s", "u").isEmpty());
        server.verify();
    }

    @Test
    void isNotConfiguredWithoutAKeyAndMakesNoCall() {
        build("  ");

        assertFalse(client.isConfigured());
        assertTrue(client.generate("s", "u").isEmpty());
        server.verify();
    }

    @Test
    void aFailureATimeoutOrAnEmptyAnswerIsQuietlyNoAnswer() {
        build("k");
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertTrue(client.generate("s", "u").isEmpty());

        build("k");
        // a broken connection is tried once more, then given up on
        server.expect(org.springframework.test.web.client.ExpectedCount.twice(), requestTo(URL)).andRespond(withException(new SocketTimeoutException("slow")));
        assertTrue(client.generate("s", "u").isEmpty());
        server.verify();

        build("k");
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));
        assertTrue(client.generate("s", "u").isEmpty());

        build("k");
        server.expect(org.springframework.test.web.client.ExpectedCount.twice(), requestTo(URL)).andRespond(withSuccess("not json at all", MediaType.TEXT_PLAIN));
        assertTrue(client.generate("s", "u").isEmpty());
    }
}
