package com.deallock.backend.controllers;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AppErrorController {

    @GetMapping("/error")
    public String error(HttpServletRequest request, org.springframework.ui.Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        String statusCode = status != null ? status.toString() : "500";
        String errorMessage = message != null ? message.toString() : "Unexpected error";
        model.addAttribute("status", statusCode);
        model.addAttribute("message", errorMessage);
        return "error";
    }
}
