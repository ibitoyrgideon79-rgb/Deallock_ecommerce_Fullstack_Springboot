package com.deallock.backend.controllers;

import com.deallock.backend.repositories.DealRepository;
import com.deallock.backend.repositories.UserRepository;
import com.deallock.backend.services.NotificationService;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/terms")
    public String terms() {
        return "terms";
    }

    @GetMapping("/ourteam")
    public String ourteam() {
        return "/contactus";
    }

    @GetMapping("/contactus")
    public String contactus() {
        return "contactus";
    }

    @GetMapping("/marketplace")
    public String marketplace() {
        return "forward:/frontend/pages/marketplace.html";
    }

    // Legacy static links -> clean routes
    @GetMapping("/frontend/pages/login.html")
    public String loginLegacy() {
        return "redirect:/login";
    }

    @GetMapping("/frontend/pages/register.html")
    public String registerLegacy() {
        return "redirect:/register";
    }

    @GetMapping("/frontend/pages/terms.html")
    public String termsLegacy() {
        return "redirect:/terms";
    }

    @GetMapping("/frontend/pages/ourteam.html")
    public String ourteamLegacy() {
        return "redirect:/ourteam";
    }

    @GetMapping("/frontend/pages/marketplace.html")
    public String marketplaceLegacy() {
        return "redirect:/marketplace";
    }

    @GetMapping("/ai-agent")
    public String aiAgent(Model model, Principal principal) {
        if (principal == null) return "redirect:/login";
        var userOpt = userRepository.findByEmail(principal.getName());
        if (userOpt.isEmpty()) return "redirect:/login";
        var user = userOpt.get();
        model.addAttribute("currentUser", user);
        model.addAttribute("isAdmin", "ROLE_ADMIN".equals(user.getRole()));
        model.addAttribute("notificationCount", notificationService.countUnread(user));
        return "userdashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal) {
        if (principal == null) return "redirect:/login";
        var userOpt = userRepository.findByEmail(principal.getName());
        if (userOpt.isEmpty()) return "redirect:/login";
        var user = userOpt.get();
        model.addAttribute("currentUser", user);
        model.addAttribute("isAdmin", "ROLE_ADMIN".equals(user.getRole()));
        model.addAttribute("notificationCount", notificationService.countUnread(user));
        return "userdashboard";
    }

    @GetMapping("/dashboard/deal/{id}")
    public String dealDetails(@PathVariable("id") Long id, Model model, Principal principal) {
        var ctx = requireUser(principal);
        if (ctx == null) return "redirect:/login";

        var dealOpt = dealRepository.findById(id);
        if (dealOpt.isEmpty()) return "redirect:/dashboard?deal=not-found";

        var deal = dealOpt.get();
        boolean isAdmin = ctx.isAdmin();
        if (!isAdmin) {
            if (deal.getUser() == null || deal.getUser().getId() != ctx.user().getId()) {
                return "redirect:/dashboard?deal=not-found";
            }
        }

        model.addAttribute("currentUser", ctx.user());
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("notificationCount", notificationService.countUnread(ctx.user()));
        model.addAttribute("deal", deal);
        return "deal-details";
    }

    @GetMapping("/dashboard/deal/{id}/pay")
    public String dealPay(@PathVariable("id") Long id, Model model, Principal principal) {
        var ctx = requireUser(principal);
        if (ctx == null) return "redirect:/login";

        var dealOpt = dealRepository.findById(id);
        if (dealOpt.isEmpty()) return "redirect:/dashboard?deal=not-found";

        var deal = dealOpt.get();
        boolean isAdmin = ctx.isAdmin();
        if (!isAdmin && (deal.getUser() == null || deal.getUser().getId() != ctx.user().getId())) {
            return "redirect:/dashboard?deal=not-found";
        }

        model.addAttribute("currentUser", ctx.user());
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("notificationCount", notificationService.countUnread(ctx.user()));
        model.addAttribute("deal", deal);
        return "deal-pay";
    }

    @GetMapping("/dashboard/deal/{id}/track")
    public String dealTrack(@PathVariable("id") Long id, Model model, Principal principal) {
        var ctx = requireUser(principal);
        if (ctx == null) return "redirect:/login";

        var dealOpt = dealRepository.findById(id);
        if (dealOpt.isEmpty()) return "redirect:/dashboard?deal=not-found";

        var deal = dealOpt.get();
        boolean isAdmin = ctx.isAdmin();
        if (!isAdmin && (deal.getUser() == null || deal.getUser().getId() != ctx.user().getId())) {
            return "redirect:/dashboard?deal=not-found";
        }

        model.addAttribute("currentUser", ctx.user());
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("notificationCount", notificationService.countUnread(ctx.user()));
        model.addAttribute("deal", deal);
        return "deal-track";
    }

    @GetMapping("/dashboard/deal/{id}/balance-pay")
    public String balancePay(@PathVariable("id") Long id, Model model, Principal principal) {
        var ctx = requireUser(principal);
        if (ctx == null) return "redirect:/login";

        var dealOpt = dealRepository.findById(id);
        if (dealOpt.isEmpty()) return "redirect:/dashboard?deal=not-found";

        var deal = dealOpt.get();
        boolean isAdmin = ctx.isAdmin();
        if (!isAdmin && (deal.getUser() == null || deal.getUser().getId() != ctx.user().getId())) {
            return "redirect:/dashboard?deal=not-found";
        }

        model.addAttribute("currentUser", ctx.user());
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("notificationCount", notificationService.countUnread(ctx.user()));
        model.addAttribute("deal", deal);
        return "deal-balance-pay";
    }

    private UserCtx requireUser(Principal principal) {
        if (principal == null) return null;
        var userOpt = userRepository.findByEmail(principal.getName());
        if (userOpt.isEmpty()) return null;
        var user = userOpt.get();
        return new UserCtx(user, "ROLE_ADMIN".equals(user.getRole()));
    }

    private record UserCtx(com.deallock.backend.entities.User user, boolean isAdmin) {
    }
}
