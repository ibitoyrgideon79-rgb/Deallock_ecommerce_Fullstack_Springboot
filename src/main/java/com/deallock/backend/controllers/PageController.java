package com.deallock.backend.controllers;

import com.deallock.backend.repositories.DealRepository;
import com.deallock.backend.repositories.MarketplaceOrderRepository;
import com.deallock.backend.repositories.UserRepository;
import com.deallock.backend.entities.Deal;
import com.deallock.backend.services.NotificationService;
import com.deallock.backend.services.MarketplaceLockPolicy;
import com.deallock.backend.services.MarketplaceOrderFlowService;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PageController {
    private final UserRepository userRepository;
    private final DealRepository dealRepository;
    private final NotificationService notificationService;
    private final MarketplaceLockPolicy lockPolicy;
    private final MarketplaceOrderRepository marketplaceOrderRepository;
    private final MarketplaceOrderFlowService marketplaceOrderFlowService;

    public PageController(UserRepository userRepository,
                          DealRepository dealRepository,
                          NotificationService notificationService,
                          MarketplaceLockPolicy lockPolicy,
                          MarketplaceOrderRepository marketplaceOrderRepository,
                          MarketplaceOrderFlowService marketplaceOrderFlowService) {
        this.userRepository = userRepository;
        this.dealRepository = dealRepository;
        this.notificationService = notificationService;
        this.lockPolicy = lockPolicy;
        this.marketplaceOrderRepository = marketplaceOrderRepository;
        this.marketplaceOrderFlowService = marketplaceOrderFlowService;
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
        // Some templates still link "Contact" to /ourteam. Make that path safe for logged-out users too.
        return "redirect:/contactus";
    }

    @GetMapping("/contactus")
    public String contactus() {
        return "contactus";
    }

    @GetMapping("/marketplace")
    public String marketplace() {
        return "marketplace";
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

    //@GetMapping("/frontend/pages/marketplace.html")
    //public String marketplaceLegacy() {
        //return "redirect:/marketplace";
    //}

    @GetMapping("/ai-agent")
    public String aiAgent(Model model, Principal principal) {
        if (principal == null) return "redirect:/login";
        var userOpt = userRepository.findByEmail(principal.getName());
        if (userOpt.isEmpty()) return "redirect:/login";
        var user = userOpt.get();
        model.addAttribute("currentUser", user);
        model.addAttribute("isAdmin", "ROLE_ADMIN".equals(user.getRole()));
        model.addAttribute("notificationCount", notificationService.countUnread(user));
        var deals = dealRepository.findByUserOrderByCreatedAtDesc(user);
        model.addAttribute("deals", deals);
        model.addAttribute("dealsVm", toDealsVm(deals));
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
        var deals = dealRepository.findByUserOrderByCreatedAtDesc(user);
        model.addAttribute("deals", deals);
        model.addAttribute("dealsVm", toDealsVm(deals));
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
        if (deal.getStatus() == null || !"Approved".equalsIgnoreCase(deal.getStatus())) {
            return "redirect:/dashboard/deal/" + id;
        }
        String paymentStatus = deal.getPaymentStatus() == null ? "NOT_PAID" : deal.getPaymentStatus();
        if (!"NOT_PAID".equalsIgnoreCase(paymentStatus)) {
            return "redirect:/dashboard/deal/" + id + "/track";
        }

        model.addAttribute("currentUser", ctx.user());
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("notificationCount", notificationService.countUnread(ctx.user()));
        model.addAttribute("deal", deal);

        // deal-pay.html expects `halfPayment` (historical naming).
        // In our domain model, this is the upfront payment: 50% of value + logistics.
        java.math.BigDecimal upfront = deal.getUpfrontPaymentAmount();
        if (upfront == null) {
            java.math.BigDecimal value = deal.getValue() == null ? java.math.BigDecimal.ZERO : deal.getValue();
            java.math.BigDecimal logistics = deal.getLogisticsFeeAmount() == null ? java.math.BigDecimal.ZERO : deal.getLogisticsFeeAmount();
            upfront = value.multiply(java.math.BigDecimal.valueOf(0.5)).add(logistics);
        }
        model.addAttribute("halfPayment", upfront);
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

    @GetMapping("/dashboard/order/{id}")
    public String marketplaceOrderDetails(@PathVariable("id") Long id, Model model, Principal principal) {
        var ctx = requireUser(principal);
        if (ctx == null) return "redirect:/login";

        var orderOpt = marketplaceOrderRepository.findById(id);
        if (orderOpt.isEmpty()) return "redirect:/dashboard?tab=orders&order=not-found";

        var order = orderOpt.get();
        boolean isAdmin = ctx.isAdmin();
        if (!isAdmin) {
            if (order.getUser() == null || order.getUser().getId() != ctx.user().getId()) {
                return "redirect:/dashboard?tab=orders&order=not-found";
            }
        }

        model.addAttribute("currentUser", ctx.user());
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("notificationCount", notificationService.countUnread(ctx.user()));
        model.addAttribute("order", marketplaceOrderFlowService.toVm(order));
        return "marketplace-order-details";
    }

    @GetMapping("/dashboard/order/{id}/track")
    public String marketplaceOrderTrack(@PathVariable("id") Long id, Model model, Principal principal) {
        var ctx = requireUser(principal);
        if (ctx == null) return "redirect:/login";

        var orderOpt = marketplaceOrderRepository.findById(id);
        if (orderOpt.isEmpty()) return "redirect:/dashboard?tab=orders&order=not-found";

        var order = orderOpt.get();
        boolean isAdmin = ctx.isAdmin();
        if (!isAdmin) {
            if (order.getUser() == null || order.getUser().getId() != ctx.user().getId()) {
                return "redirect:/dashboard?tab=orders&order=not-found";
            }
        }

        model.addAttribute("currentUser", ctx.user());
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("notificationCount", notificationService.countUnread(ctx.user()));
        model.addAttribute("order", marketplaceOrderFlowService.toVm(order));
        return "marketplace-order-track";
    }

    @GetMapping("/dashboard/order/{id}/pay")
    public String marketplaceOrderPay(@PathVariable("id") Long id, Model model, Principal principal) {
        var ctx = requireUser(principal);
        if (ctx == null) return "redirect:/login";

        var orderOpt = marketplaceOrderRepository.findById(id);
        if (orderOpt.isEmpty()) return "redirect:/dashboard?tab=orders&order=not-found";

        var order = orderOpt.get();
        boolean isAdmin = ctx.isAdmin();
        if (!isAdmin) {
            if (order.getUser() == null || order.getUser().getId() != ctx.user().getId()) {
                return "redirect:/dashboard?tab=orders&order=not-found";
            }
        }

        model.addAttribute("currentUser", ctx.user());
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("notificationCount", notificationService.countUnread(ctx.user()));
        model.addAttribute("order", marketplaceOrderFlowService.toVm(order));
        return "marketplace-order-pay";
    }

    
    private List<Map<String, Object>> toDealsVm(List<Deal> deals) {
        return deals.stream()
                .map(d -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", d.getId());
                    row.put("title", d.getTitle() == null ? "Untitled Deal" : d.getTitle());
                    row.put("status", d.getStatus() == null ? "Pending Approval" : d.getStatus());
                    row.put("value", d.getValue() == null ? 0 : d.getValue());
                    row.put("deliveryConfirmedAt", d.getDeliveryConfirmedAt());
                    row.put("securedAt", d.getSecuredAt());
                    row.put("lockedUntil", lockPolicy.lockedUntil(d.getSecuredAt()));
                    row.put("allowMarketplaceListing", d.getAllowMarketplaceListing() == null ? Boolean.TRUE : d.getAllowMarketplaceListing());
                    return row;
                })
                .collect(Collectors.toList());
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
