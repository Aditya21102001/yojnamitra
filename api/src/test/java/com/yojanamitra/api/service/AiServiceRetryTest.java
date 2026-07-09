package com.yojanamitra.api.service;

import com.yojanamitra.api.history.MatchHistoryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The AI runs on a free tier that sleeps, so the gateway retries the failures a
 * waking container produces — but must not retry a genuine client error, or it
 * would turn a fast 4xx into a slow one for no reason.
 */
class AiServiceRetryTest {

    private AiService build(RestClient.Builder builder) {
        return new AiService(builder.build(), Mockito.mock(MatchHistoryRepository.class));
    }

    @Test
    void aGatewayErrorIsRetriedAndThenSucceeds() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        // First attempt: the edge 503 a booting container gives. Second: awake.
        server.expect(requestTo("http://ai/schemes"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("http://ai/schemes"))
                .andRespond(withSuccess("[{\"id\":\"pm-kisan\"}]", MediaType.APPLICATION_JSON));

        var result = build(builder).schemes();

        assertEquals("pm-kisan", result.path(0).path("id").asText());
        server.verify();   // both attempts were made
    }

    @Test
    void aClientErrorIsNotRetried() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        // Exactly one expectation: a 400 must fail fast, without a second call.
        server.expect(requestTo("http://ai/schemes"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST));

        AiService svc = build(builder);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, svc::schemes);

        assertTrue(ex.getStatusCode().is5xxServerError());
        server.verify();   // fails if a retry issued an unexpected second request
    }
}
