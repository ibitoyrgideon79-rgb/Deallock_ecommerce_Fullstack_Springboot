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

    @GetMapping("/login")     public String login()     { return "redirect:/frontend/pages/login.html"; }
    @GetMapping("/terms")     public String terms()     { return "redirect:/frontend/pages/terms.html"; }
    @GetMapping("/ourteam")   public String ourteam()   { return "redirect:/frontend/pages/contactus.html"; }
    @GetMapping("/marketplace") public String marketplace() { return "redirect:/frontend/pages/marketplace.html"; }

    @GetMapping("/ai-agent")
    public String aiAgent(Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        var userOpt = userRepository.findByEmail(principal.getName());
        if (userOpt.isEmpty()) {
            return "redirect:/login";
        }
        return "redirect:/frontend/pages/userdashboard.html";
    }

    @GetMapping("/dashboard")
    public String dashboard(Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        return "redirect:/frontend/pages/userdashboard.html";
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
            if (deal.getUser() == null || deal.getUser().getId() != userOpt.get().getId()) {
                return "redirect:/dashboard?deal=not-found";
            }
        }
        return "redirect:/frontend/pages/userdashboard.html?dealId=" + id;
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
        if (!isAdmin && (deal.getUser() == null || deal.getUser().getId() != userOpt.get().getId())) {
            return "redirect:/dashboard?deal=not-found";
        }
        return "redirect:/frontend/pages/deal-pay.html?dealId=" + id;
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
        if (!isAdmin && (deal.getUser() == null || deal.getUser().getId() != userOpt.get().getId())) {
            return "redirect:/dashboard?deal=not-found";
        }
        return "redirect:/frontend/pages/deal-track.html?dealId=" + id;
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
        if (!isAdmin && (deal.getUser() == null || deal.getUser().getId() != userOpt.get().getId())) {
            return "redirect:/dashboard?deal=not-found";
        }
        return "redirect:/frontend/pages/deal-balance-pay.html?dealId=" + id;
    }

}



