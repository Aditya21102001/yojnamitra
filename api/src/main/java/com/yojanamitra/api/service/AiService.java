package com.yojanamitra.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yojanamitra.api.dto.ChatRequest;
import com.yojanamitra.api.dto.MatchRequest;
import com.yojanamitra.api.dto.ProfileRequest;
import com.yojanamitra.api.history.MatchHistory;
import com.yojanamitra.api.history.MatchHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Gateway to the Python GenAI service. Translates the client's camelCase DTOs
 * into the snake_case contract the Python service expects, forwards the call,
 * and records match activity via JPA.
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private static final String DOWN_MESSAGE =
            "The AI service is starting up or temporarily unavailable. Please try again in a moment.";

    /**
     * Backoff between attempts. The AI runs on a free tier that sleeps after
     * ~15 min idle; the first call wakes it (~15-30s) and often times out. The
     * pauses give the container time to finish booting, so the retry lands on a
     * now-awake service instead of failing the user. One entry per retry.
     */
    private static final List<Duration> RETRY_BACKOFF =
            List.of(Duration.ofSeconds(3), Duration.ofSeconds(8));

    private final RestClient ai;
    private final MatchHistoryRepository history;

    public AiService(RestClient aiRestClient, MatchHistoryRepository history) {
        this.ai = aiRestClient;
        this.history = history;
    }

    public JsonNode match(MatchRequest request) {
        ProfileRequest p = request.profile();
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("age", p.age());
        profile.put("gender", p.gender());
        profile.put("state", p.state());
        profile.put("occupation", p.occupation());
        profile.put("annual_income", p.annualIncome());
        profile.put("category", p.category());
        profile.put("description", p.description());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("profile", profile);
        body.put("top_k", request.topK() != null ? request.topK() : 5);
        body.put("lang", request.lang() != null ? request.lang() : "en");

        JsonNode response = call("/match", body);
        saveHistory(response);
        return response;
    }

    public JsonNode chat(ChatRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scheme_id", request.schemeId());
        body.put("question", request.question());
        body.put("lang", request.lang() != null ? request.lang() : "en");
        return call("/chat", body);
    }

    public JsonNode schemes() {
        return get("/schemes");
    }

    public JsonNode aiHealth() {
        return get("/health");
    }

    private JsonNode call(String uri, Object body) {
        return withRetry(uri, () -> ai.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class));
    }

    private JsonNode get(String uri) {
        return withRetry(uri, () -> ai.get().uri(uri).retrieve().body(JsonNode.class));
    }

    /**
     * Runs a call, retrying only failures that a sleeping/booting container
     * produces — connection refused, read timeout, or a gateway 502/503/504.
     * A 4xx (or any other error) means retrying is pointless, so it surfaces at
     * once. Retries are safe here: the AI endpoints have no side effects, and
     * match history is written only after a call returns, never on a failure.
     */
    private JsonNode withRetry(String uri, Supplier<JsonNode> attempt) {
        RestClientException last = null;
        for (int i = 0; i <= RETRY_BACKOFF.size(); i++) {
            try {
                return attempt.get();
            } catch (RestClientException ex) {
                if (!isTransient(ex)) {
                    log.warn("GenAI call to {} failed (not retried): {}", uri, ex.getMessage());
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, DOWN_MESSAGE, ex);
                }
                last = ex;
                if (i < RETRY_BACKOFF.size()) {
                    Duration pause = RETRY_BACKOFF.get(i);
                    log.info("GenAI {} unreachable (attempt {}/{}), retrying in {}s — likely a cold start",
                            uri, i + 1, RETRY_BACKOFF.size() + 1, pause.toSeconds());
                    sleep(pause);
                }
            }
        }
        log.warn("GenAI call to {} failed after {} attempts: {}",
                uri, RETRY_BACKOFF.size() + 1, last.getMessage());
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, DOWN_MESSAGE, last);
    }

    /** True for the failure shapes a waking free-tier container produces. */
    private static boolean isTransient(RestClientException ex) {
        if (ex instanceof ResourceAccessException) {
            return true;   // connect timeout, read timeout, connection refused
        }
        if (ex instanceof HttpStatusCodeException http) {
            HttpStatusCode status = http.getStatusCode();
            return status.value() == 502 || status.value() == 503 || status.value() == 504;
        }
        return false;
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, DOWN_MESSAGE, ie);
        }
    }

    private void saveHistory(JsonNode response) {
        if (response == null) {
            return;
        }
        try {
            String query = response.path("query").asText("");
            int count = response.path("count").asInt(0);
            String topSchemeId = response.path("schemes").path(0).path("id").asText(null);
            history.save(new MatchHistory(currentUsername(), truncate(query, 500), count, topSchemeId));
        } catch (RuntimeException ex) {
            // History is best-effort — never fail the request because of it.
            log.warn("Could not persist match history: {}", ex.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }

    /** Current logged-in username, or null for an anonymous request. */
    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return null;
        }
        return auth.getName();
    }
}
