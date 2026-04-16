package com.deallock.backend.controllers;

import com.deallock.backend.repositories.UserRepository;
import com.deallock.backend.repositories.DealRepository;
import com.deallock.backend.services.NotificationService;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PageController {
    private final UserRepository userRepository;
    private final DealRepository dealRepository;
    private final NotificationService notificationService;

    public PageController(UserRepository userRepository,
                          DealRepository dealRepository,
                          NotificationService notificationService) {
        this.userRepository = userRepository;
        this.dealRepository = dealRepository;
        this.notificationService = notificationService;
    }

    @GetMapping("/marketplace") 
    public String marketplace() { return "marketplace"; }

    // Legacy static URLs -> clean Thymeleaf routes
    @GetMapping("/frontend/pages/login.html") public String loginLegacy() { return "redirect:/login"; }
    @GetMapping("/frontend/pages/register.html") public String registerLegacy() { return "redirect:/register"; }
    @GetMapping("/frontend/pages/terms.html") public String termsLegacy() { return "redirect:/terms"; }
    @GetMapping("/frontend/pages/ourteam.html") public String ourteamLegacy() { return "redirect:/ourteam"; }
    @GetMapping("/frontend/pages/marketplace.html") public String marketplaceLegacy() { return "redirect:/marketplace"; }

    @GetMapping("/ai-agent")
    public String aiAgent(Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        var userOpt = userRepository.findByEmail(principal.getName());
        if (userOpt.isEmpty()) {
            return "redirect:/login";
        }
        return "dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        return "dashboard";
    }

    @GetMapping("/dashboard/deal/{id}")
    public String dealDetails(@PathVariable("id") Long id, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        var userOpt = userRepository.findByEmail(principal.getName());
        if (userOpt.isEmpty()) {
            return "redirect:/login";
        }

        var dealOpt = dealRepository.findById(id);
        if (dealOpt.isEmpty()) {
            return "redirect:/dashboard?deal=not-found";
        }

        var deal = dealOpt.get();
        boolean isAdmin = "ROLE_ADMIN".equals(userOpt.get().getRole());
        if (!isAdmin) {
            if (deal.getUser() == null || !deal.getUser().getId().equals(userOpt.get().getId())) {
                return "redirect:/dashboard?deal=not-found";
            }
        }
        return "deal-details";
    }

    @GetMapping("/dashboard/deal/{id}/pay")
    public String dealPay(@PathVariable("id") Long id, Principal principal) {   
        if (principal == null) return "redirect:/login";

        var userOpt = userRepository.findByEmail(principal.getName());
        if (userOpt.isEmpty()) return "redirect:/login";

        var dealOpt = dealRepository.findById(id);
        if (dealOpt.isEmpty()) return "redirect:/dashboard?deal=not-found";     

        var deal = dealOpt.get();
        boolean isAdmin = "ROLE_ADMIN".equals(userOpt.get().getRole());
        if (!isAdmin && (deal.getUser() == null || !deal.getUser().getId().equals(userOpt.get().getId()))) {
            return "redirect:/dashboard?deal=not-found";
        }
        return "deal-pay";
    }

    @GetMapping("/dashboard/deal/{id}/track")
    public String dealTrack(@PathVariable("id") Long id, Principal principal) {
        if (principal == null) return "redirect:/login";

        var userOpt = userRepository.findByEmail(principal.getName());
        if (userOpt.isEmpty()) return "redirect:/login";

        var dealOpt = dealRepository.findById(id);
        if (dealOpt.isEmpty()) return "redirect:/dashboard?deal=not-found";

        var deal = dealOpt.get();
        boolean isAdmin = "ROLE_ADMIN".equals(userOpt.get().getRole());
        if (!isAdmin && (deal.getUser() == null || !deal.getUser().getId().equals(userOpt.get().getId()))) {
            return "redirect:/dashboard?deal=not-found";
        }
        return "deal-track";
    }

    @GetMapping("/dashboard/deal/{id}/balance-pay")
    public String balancePay(@PathVariable("id") Long id, Principal principal) {
        if (principal == null) return "redirect:/login";

        var userOpt = userRepository.findByEmail(principal.getName());
        if (userOpt.isEmpty()) return "redirect:/login";

        var dealOpt = dealRepository.findById(id);
        if (dealOpt.isEmpty()) return "redirect:/dashboard?deal=not-found";     

        var deal = dealOpt.get();
        boolean isAdmin = "ROLE_ADMIN".equals(userOpt.get().getRole());
        if (!isAdmin && (deal.getUser() == null || !deal.getUser().getId().equals(userOpt.get().getId()))) {
            return "redirect:/dashboard?deal=not-found";
        }
        return "deal-balance-pay";
    }
}
