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
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gateway to the Python GenAI service. Translates the client's camelCase DTOs
 * into the snake_case contract the Python service expects, forwards the call,
 * and records match activity via JPA.
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private static final String DOWN_MESSAGE =
            "The GenAI service is unavailable. Make sure it is running on the configured "
                    + "base URL and that Ollama is up.";

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
        try {
            return ai.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException ex) {
            log.warn("GenAI call to {} failed: {}", uri, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, DOWN_MESSAGE, ex);
        }
    }

    private JsonNode get(String uri) {
        try {
            return ai.get().uri(uri).retrieve().body(JsonNode.class);
        } catch (RestClientException ex) {
            log.warn("GenAI call to {} failed: {}", uri, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, DOWN_MESSAGE, ex);
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
