package com.deallock.backend.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class AiAgentClient {

    private final RestTemplate restTemplate;
    private final String agentUrl;
    private final String agentApiKey;

    public AiAgentClient(@Value("${ai.agent.url:http://localhost:8000}") String agentUrl,
                         @Value("${ai.agent.api-key:}") String agentApiKey) {
        this.restTemplate = new RestTemplate();
        this.agentUrl = agentUrl;
        this.agentApiKey = agentApiKey;
    }

    public String ask(String prompt) {
        String url = agentUrl.endsWith("/") ? agentUrl + "api/agent/query" : agentUrl + "/api/agent/query";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (agentApiKey != null && !agentApiKey.isBlank()) {
            headers.set("X-AI-AGENT-KEY", agentApiKey);
        }

        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("prompt", prompt), headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<>() {
                    }
            );
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object result = response.getBody().get("text");
                return result == null ? "" : result.toString();
            }
            return "AI service returned unexpected status: " + response.getStatusCode().value();
        } catch (HttpStatusCodeException ex) {
            return "AI service error: " + ex.getStatusCode() + " " + ex.getResponseBodyAsString();
        } catch (Exception ex) {
            return "AI service unavailable: " + ex.getMessage();
        }
    }
}
