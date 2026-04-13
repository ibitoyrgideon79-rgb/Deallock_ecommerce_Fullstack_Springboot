package com.deallock.backend.controllers;

import com.deallock.backend.services.AiAgentClient;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiAgentController {

    private final AiAgentClient aiAgentClient;

    public AiAgentController(AiAgentClient aiAgentClient) {
        this.aiAgentClient = aiAgentClient;
    }

    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> query(@RequestBody QueryRequest request, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Authentication required"));
        }
        if (request == null || request.getPrompt() == null || request.getPrompt().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Prompt must not be blank"));
        }

        String answer = aiAgentClient.ask(request.getPrompt());
        return ResponseEntity.ok(Map.of("response", answer));
    }

    public static class QueryRequest {
        private String prompt;

        public QueryRequest() {
        }

        public QueryRequest(String prompt) {
            this.prompt = prompt;
        }

        public String getPrompt() {
            return prompt;
        }

        public void setPrompt(String prompt) {
            this.prompt = prompt;
        }
    }
}
