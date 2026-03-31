package com.deallock.backend.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SmsService {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${termii.base-url:https://api.ng.termii.com/api}")
    private String baseUrl;
    @Value("${termii.api-key:}")
    private String apiKey;
    @Value("${termii.sender-id:}")
    private String senderId;
    @Value("${termii.sms-channel:dnd}")
    private String smsChannel;
    @Value("${termii.whatsapp-sender:}")
    private String whatsappSender;
    @Value("${app.admin-phones:}")
    private String adminPhones;

    private boolean isSmsConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && senderId != null && !senderId.isBlank();
    }

    private boolean isWhatsAppConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && whatsappSender != null && !whatsappSender.isBlank();
    }

    public static class SmsResult {
        public final boolean ok;
        public final String message;

        public SmsResult(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }

    public void sendToUser(String phone, String message) {
        if (phone == null || phone.isBlank()) return;
        sendSmsResult(phone, message);
    }

    public void sendWhatsAppToUser(String phone, String message) {
        if (phone == null || phone.isBlank()) return;
        sendWhatsAppResult(phone, message);
    }

    public void sendToAdmins(String message) {
        if (adminPhones == null || adminPhones.isBlank()) return;
        List<String> phones = Arrays.stream(adminPhones.split(","))
                .map(String::trim)
                .filter(p -> !p.isBlank())
                .toList();
        phones.forEach(p -> sendSmsResult(p, message));
    }

    public void sendWhatsAppToAdmins(String message) {
        if (adminPhones == null || adminPhones.isBlank()) return;
        List<String> phones = Arrays.stream(adminPhones.split(","))
                .map(String::trim)
                .filter(p -> !p.isBlank())
                .toList();
        phones.forEach(p -> sendWhatsAppResult(p, message));
    }

    private String extractMessage(String body, String fallback) {
        if (body == null || body.isBlank()) return fallback;
        String trimmed = body.trim();
        if (!trimmed.startsWith("{")) return body;
        try {
            Map<?, ?> parsed = objectMapper.readValue(trimmed, Map.class);
            Object msg = parsed.get("message");
            return msg != null ? String.valueOf(msg) : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public SmsResult sendSmsResult(String to, String message) {
        if (!isSmsConfigured()) {
            String msg = "SMS not configured. Please contact support.";
            System.out.println("[DEV] Termii SMS not configured. To: " + to + " | " + message);
            return new SmsResult(false, msg);
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "to", to,
                    "from", senderId,
                    "sms", message,
                    "type", "plain",
                    "channel", smsChannel,
                    "api_key", apiKey
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/sms/send"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("[TERMII] SMS status=" + response.statusCode() + " body=" + response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String msg = extractMessage(response.body(), "SMS sending failed. Please try again.");
                return new SmsResult(false, msg);
            }
            return new SmsResult(true, "OK");
        } catch (Exception ex) {
            System.out.println("[WARN] Termii SMS failed: " + ex.getMessage());
            return new SmsResult(false, ex.getMessage());
        }
    }

    public SmsResult sendWhatsAppResult(String to, String message) {
        if (!isWhatsAppConfigured()) {
            String msg = "WhatsApp not configured.";
            System.out.println("[DEV] Termii WhatsApp not configured. To: " + to + " | " + message);
            return new SmsResult(false, msg);
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "to", to,
                    "from", whatsappSender,
                    "message", message,
                    "api_key", apiKey
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/whatsapp/send"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("[TERMII] WhatsApp status=" + response.statusCode() + " body=" + response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String msg = extractMessage(response.body(), "WhatsApp sending failed. Please try again.");
                return new SmsResult(false, msg);
            }
            return new SmsResult(true, "OK");
        } catch (Exception ex) {
            System.out.println("[WARN] Termii WhatsApp failed: " + ex.getMessage());
            return new SmsResult(false, ex.getMessage());
        }
    }
}
