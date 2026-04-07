package com.deallock.backend.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    @Value("${gemini.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public String getResponse(String userMessage) {

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=" + apiKey;

        String prompt = """
            
You are the official AI assistant for DealLock, an ecommerce escrow platform.

Your job is to help users understand and use the platform.

You should help with:
- user registration and OTP verification
- login and password reset issues
- creating and managing deals
- uploading payment proof and balance payments
- deal approval and admin processes
- tracking deal status (pending, approved, secured, delivered, closed)
- delivery confirmation and feedback
- general navigation of the dashboard and admin panel

Rules:
- Be clear and simple
- Give step-by-step instructions when needed
- If user is confused, guide them based on DealLock workflow
- Do NOT invent features that do not exist in the system

User question:
""" + userMessage;
        
        // Request body
        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                )
        );

        // Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url,
                    request,
                    Map.class
            );

            return extractText(response.getBody());

        } catch (Exception e) {
            return "Sorry, I couldn't process your request right now.";
        }
    }

    // Extract only the chatbot reply from Gemini response
    private String extractText(Map body) {
        try {
            List candidates = (List) body.get("candidates");
            Map firstCandidate = (Map) candidates.get(0);

            Map content = (Map) firstCandidate.get("content");
            List parts = (List) content.get("parts");

            Map firstPart = (Map) parts.get(0);

            return (String) firstPart.get("text");

        } catch (Exception e) {
            return "Sorry, I couldn't understand the response.";
        }
    }
}
