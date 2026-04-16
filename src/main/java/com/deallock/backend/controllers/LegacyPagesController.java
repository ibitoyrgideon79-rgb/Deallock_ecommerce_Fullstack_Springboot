package com.deallock.backend.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LegacyPagesController {

    @GetMapping("/index.html")
    public String indexHtml() {
        return "index";
    }

    @GetMapping("/pages/login.html")
    public String loginHtml() {
        return "login";
    }

    @GetMapping("/pages/register.html")
    public String registerHtml() {
        return "register";
    }

    @GetMapping("/pages/terms.html")
    public String termsHtml() {
        return "terms";
    }

    @GetMapping("/pages/marketplace.html")
    public String marketplaceHtml() {
        return "forward:/frontend/pages/marketplace.html";
    }

    @GetMapping("/pages/contactus.html")
    public String contactHtml() {
        return "contactus";
    }
}
