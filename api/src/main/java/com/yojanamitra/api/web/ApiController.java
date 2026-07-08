package com.yojanamitra.api.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.yojanamitra.api.dto.ChatRequest;
import com.yojanamitra.api.dto.MatchRequest;
import com.yojanamitra.api.history.MatchHistory;
import com.yojanamitra.api.history.MatchHistoryRepository;
import com.yojanamitra.api.service.AiService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final AiService ai;
    private final MatchHistoryRepository history;

    public ApiController(AiService ai, MatchHistoryRepository history) {
        this.ai = ai;
        this.history = history;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("api", "ok", "ai", ai.aiHealth());
    }

    @PostMapping("/match")
    public JsonNode match(@Valid @RequestBody MatchRequest request) {
        return ai.match(request);
    }

    @PostMapping("/chat")
    public JsonNode chat(@Valid @RequestBody ChatRequest request) {
        return ai.chat(request);
    }

    @GetMapping("/schemes")
    public JsonNode schemes() {
        return ai.schemes();
    }

    @GetMapping("/history")
    public List<MatchHistory> history(Authentication auth) {
        return history.findTop20ByOwnerOrderByCreatedAtDesc(auth.getName());
    }
}
