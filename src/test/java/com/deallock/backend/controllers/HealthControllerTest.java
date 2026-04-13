package com.deallock.backend.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class HealthControllerTest {

    @Test
    void returnsHealthyStatus() {
        HealthController healthController = new HealthController();
        ResponseEntity<Map<String, String>> response = healthController.health();
        assertEquals("ok", response.getBody().get("status"));
    }
}
