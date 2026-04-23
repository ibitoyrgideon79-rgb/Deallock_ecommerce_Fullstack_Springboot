package com.deallock.backend.controllers;

import com.deallock.backend.entities.Deal;
import com.deallock.backend.entities.MarketplaceItem;
import com.deallock.backend.repositories.DealRepository;
import com.deallock.backend.repositories.MarketplaceItemRepository;
import com.deallock.backend.services.DealCacheService;
import com.deallock.backend.services.DealReadService;
import com.deallock.backend.services.NotificationDispatchService;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/deals")
public class AdminDealApiController {

    private final DealRepository dealRepository;
    private final MarketplaceItemRepository marketplaceItemRepository;
    private final NotificationDispatchService notifier;
    private final DealReadService dealReadService;
    private final DealCacheService dealCacheService;

    @Value("${app.deals.payment-timeout:24h}")
    private Duration paymentTimeout;

    public AdminDealApiController(DealRepository dealRepository,
                                  MarketplaceItemRepository marketplaceItemRepository,
                                  NotificationDispatchService notifier,
                                  DealReadService dealReadService,
                                  DealCacheService dealCacheService) {
        this.dealRepository = dealRepository;
        this.marketplaceItemRepository = marketplaceItemRepository;
        this.notifier = notifier;
        this.dealReadService = dealReadService;
        this.dealCacheService = dealCacheService;
    }

    @GetMapping
    public ResponseEntity<?> list(Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(dealReadService.listAllDealsForAdmin());
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable("id") Long id, Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Deal deal = dealRepository.findById(id).orElse(null);
        if (deal == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        deal.setStatus("Approved");
        deal.setRejectionReason(null);
        dealRepository.save(deal);
        dealCacheService.evictAdminDeals();
        if (deal.getUser() != null) {
            dealCacheService.evictUserDeals(deal.getUser().getEmail());
        }

        notifier.notifyUser(deal.getUser(),
                "Your deal was approved. Please proceed to payment.",
                "Your Deal Was Approved",
                "Your deal was approved: " + safe(deal.getTitle()),
                "Your deal was approved: " + safe(deal.getTitle()));

        notifier.notifyAdmins(
                "Deal approved: " + safe(deal.getTitle()),
                "Deal Approved",
                "Deal approved: " + safe(deal.getTitle()),
                "Deal approved: " + safe(deal.getTitle()));

        return ResponseEntity.ok(Map.of("message", "approved"));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable("id") Long id,
                                    @RequestBody(required = false) Map<String, Object> body,
                                    Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Deal deal = dealRepository.findById(id).orElse(null);
        if (deal == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String reason = null;
        if (body != null && body.get("reason") != null) {
            reason = String.valueOf(body.get("reason")).trim();
        }
        if (reason != null && reason.length() > 2000) {
            reason = reason.substring(0, 2000);
        }

        deal.setStatus("Rejected");
        deal.setRejectionReason(reason);
        dealRepository.save(deal);
        dealCacheService.evictAdminDeals();
        if (deal.getUser() != null) {
            dealCacheService.evictUserDeals(deal.getUser().getEmail());
        }

        notifier.notifyUser(deal.getUser(),
                "Your deal was rejected." + (reason == null || reason.isBlank() ? "" : (" Reason: " + reason)),
                "Your Deal Was Rejected",
                "Your deal was rejected: " + safe(deal.getTitle()) + (reason == null || reason.isBlank() ? "" : ("\nReason: " + reason)),
                "Your deal was rejected: " + safe(deal.getTitle()));

        notifier.notifyAdmins(
                "Deal rejected: " + safe(deal.getTitle()),
                "Deal Rejected",
                "Deal rejected: " + safe(deal.getTitle()),
                "Deal rejected: " + safe(deal.getTitle()));

        return ResponseEntity.ok(Map.of("message", "rejected"));
    }

    @PostMapping("/{id}/payment-confirmed")
    public ResponseEntity<?> paymentConfirmed(@PathVariable("id") Long id, Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Deal deal = dealRepository.findById(id).orElse(null);
        if (deal == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (deal.getStatus() == null || !"Approved".equalsIgnoreCase(deal.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Deal must be approved first"));
        }

        deal.setPaymentStatus("PAID_CONFIRMED");
        dealRepository.save(deal);
        dealCacheService.evictAdminDeals();
        if (deal.getUser() != null) {
            dealCacheService.evictUserDeals(deal.getUser().getEmail());
        }

        notifier.notifyUser(deal.getUser(),
                "Payment confirmed for your deal.",
                "Payment Confirmed",
                "Payment confirmed for your deal: " + safe(deal.getTitle()),
                "Payment confirmed for your deal: " + safe(deal.getTitle()));

        notifier.notifyAdmins(
                "Payment confirmed: " + safe(deal.getTitle()),
                "Payment Confirmed",
                "Payment confirmed: " + safe(deal.getTitle()),
                "Payment confirmed: " + safe(deal.getTitle()));

        return ResponseEntity.ok(Map.of("message", "payment-confirmed"));
    }

    @PostMapping("/{id}/payment-not-received")
    public ResponseEntity<?> paymentNotReceived(@PathVariable("id") Long id, Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Deal deal = dealRepository.findById(id).orElse(null);
        if (deal == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (deal.getStatus() == null || !"Approved".equalsIgnoreCase(deal.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Deal must be approved first"));
        }

        deal.setPaymentStatus("NOT_PAID");
        dealRepository.save(deal);
        dealCacheService.evictAdminDeals();
        if (deal.getUser() != null) {
            dealCacheService.evictUserDeals(deal.getUser().getEmail());
        }

        return ResponseEntity.ok(Map.of("message", "payment-not-received"));
    }

    @PostMapping("/{id}/secured")
    public ResponseEntity<?> secured(@PathVariable("id") Long id, Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Deal deal = dealRepository.findById(id).orElse(null);
        if (deal == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (deal.getStatus() == null || !"Approved".equalsIgnoreCase(deal.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Deal must be approved first"));
        }
        if (!"PAID_CONFIRMED".equalsIgnoreCase(deal.getPaymentStatus())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Payment must be confirmed first"));
        }

        deal.setSecured(true);
        deal.setSecuredAt(Instant.now());
        dealRepository.save(deal);
        dealCacheService.evictAdminDeals();
        if (deal.getUser() != null) {
            dealCacheService.evictUserDeals(deal.getUser().getEmail());
        }

        notifier.notifyUser(deal.getUser(),
                "Deal secured: " + safe(deal.getTitle()),
                "Deal Secured",
                "Deal secured: " + safe(deal.getTitle()),
                "Deal secured: " + safe(deal.getTitle()));

        notifier.notifyAdmins(
                "Deal secured: " + safe(deal.getTitle()),
                "Deal Secured",
                "Deal secured: " + safe(deal.getTitle()),
                "Deal secured: " + safe(deal.getTitle()));

        return ResponseEntity.ok(Map.of("message", "secured"));
    }

    @PostMapping("/{id}/balance-confirmed")
    public ResponseEntity<?> balanceConfirmed(@PathVariable("id") Long id, Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Deal deal = dealRepository.findById(id).orElse(null);
        if (deal == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (!deal.isSecured()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Deal must be secured first"));
        }

        deal.setBalancePaymentStatus("PAID_CONFIRMED");
        dealRepository.save(deal);
        dealCacheService.evictAdminDeals();
        if (deal.getUser() != null) {
            dealCacheService.evictUserDeals(deal.getUser().getEmail());
        }

        notifier.notifyUser(deal.getUser(),
                "Balance payment confirmed for your deal.",
                "Balance Payment Confirmed",
                "Balance payment confirmed: " + safe(deal.getTitle()),
                "Balance payment confirmed: " + safe(deal.getTitle()));

        notifier.notifyAdmins(
                "Balance payment confirmed: " + safe(deal.getTitle()),
                "Balance Payment Confirmed",
                "Balance payment confirmed: " + safe(deal.getTitle()),
                "Balance payment confirmed: " + safe(deal.getTitle()));

        return ResponseEntity.ok(Map.of("message", "balance-confirmed"));
    }

    @PostMapping("/{id}/delivery-initiated")
    public ResponseEntity<?> deliveryInitiated(@PathVariable("id") Long id, Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Deal deal = dealRepository.findById(id).orElse(null);
        if (deal == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (!deal.isSecured() || !"PAID_CONFIRMED".equalsIgnoreCase(deal.getBalancePaymentStatus())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Balance must be confirmed first"));
        }

        deal.setDeliveryInitiatedAt(Instant.now());
        dealRepository.save(deal);
        dealCacheService.evictAdminDeals();
        if (deal.getUser() != null) {
            dealCacheService.evictUserDeals(deal.getUser().getEmail());
        }

        notifier.notifyUser(deal.getUser(),
                "Delivery initiated for your deal.",
                "Delivery Initiated",
                "Delivery initiated: " + safe(deal.getTitle()),
                "Delivery initiated: " + safe(deal.getTitle()));

        notifier.notifyAdmins(
                "Delivery initiated: " + safe(deal.getTitle()),
                "Delivery Initiated",
                "Delivery initiated: " + safe(deal.getTitle()),
                "Delivery initiated: " + safe(deal.getTitle()));

        return ResponseEntity.ok(Map.of("message", "delivery-initiated"));
    }

    @PostMapping("/{id}/delivery-confirmed")
    public ResponseEntity<?> deliveryConfirmed(@PathVariable("id") Long id, Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Deal deal = dealRepository.findById(id).orElse(null);
        if (deal == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (deal.getDeliveryInitiatedAt() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Delivery must be initiated first"));
        }

        deal.setDeliveryConfirmedAt(Instant.now());
        dealRepository.save(deal);
        dealCacheService.evictAdminDeals();
        if (deal.getUser() != null) {
            dealCacheService.evictUserDeals(deal.getUser().getEmail());
        }

        notifier.notifyUser(deal.getUser(),
                "Delivery confirmed by admin.",
                "Delivery Confirmation",
                "Delivery confirmed: " + safe(deal.getTitle()),
                "Delivery confirmed: " + safe(deal.getTitle()));

        notifier.notifyAdmins(
                "Delivery confirmed: " + safe(deal.getTitle()),
                "Delivery Confirmation",
                "Delivery confirmed: " + safe(deal.getTitle()),
                "Delivery confirmed: " + safe(deal.getTitle()));

        return ResponseEntity.ok(Map.of("message", "delivery-confirmed"));
    }

    @PostMapping("/{id}/delete")
    public ResponseEntity<?> delete(@PathVariable("id") Long id, Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Deal deal = dealRepository.findById(id).orElse(null);
        if (deal == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        dealRepository.delete(deal);
        dealCacheService.evictAdminDeals();
        if (deal.getUser() != null) {
            dealCacheService.evictUserDeals(deal.getUser().getEmail());
        }
        return ResponseEntity.ok(Map.of("message", "deleted"));
    }

    @PostMapping("/{id}/list-on-marketplace")
    public ResponseEntity<?> listOnMarketplace(@PathVariable("id") Long id, Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Deal deal = dealRepository.findById(id).orElse(null);
        if (deal == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        boolean allowListing = deal.getAllowMarketplaceListing() == null || deal.getAllowMarketplaceListing();
        if (!allowListing) {
            return ResponseEntity.badRequest().body(Map.of("message", "User did not allow marketplace listing for this item"));
        }

        // "Expired unpaid" policy: approved + NOT_PAID + older than configured timeout.
        String paymentStatus = deal.getPaymentStatus() == null ? "NOT_PAID" : deal.getPaymentStatus();
        if (deal.getCreatedAt() == null
                || deal.getStatus() == null
                || !"Approved".equalsIgnoreCase(deal.getStatus())
                || !"NOT_PAID".equalsIgnoreCase(paymentStatus)
                || paymentTimeout == null
                || !deal.getCreatedAt().isBefore(Instant.now().minus(paymentTimeout))) {
            return ResponseEntity.badRequest().body(Map.of("message", "Deal is not eligible (must be approved, unpaid, and expired)"));
        }

        if (marketplaceItemRepository.existsBySourceDealId(deal.getId())) {
            return ResponseEntity.ok(Map.of("message", "already-listed"));
        }

        MarketplaceItem item = new MarketplaceItem();
        item.setName(deal.getTitle() == null || deal.getTitle().isBlank() ? "Untitled Deal" : deal.getTitle().trim());
        item.setDescription(deal.getDescription());
        item.setPrice(deal.getValue() == null ? java.math.BigDecimal.ZERO : deal.getValue());
        item.setOldPrice(null);
        item.setSize(normalizeSize(deal.getItemSize()));
        item.setListed(true);
        item.setCreatedAt(Instant.now());
        item.setSourceDealId(deal.getId());
        if (deal.getItemPhoto() != null && deal.getItemPhoto().length > 0) {
            item.setPhoto(deal.getItemPhoto());
            item.setPhotoContentType(deal.getItemPhotoContentType());
        }
        if (deal.getItemPhoto2() != null && deal.getItemPhoto2().length > 0) {
            item.setPhoto2(deal.getItemPhoto2());
            item.setPhoto2ContentType(deal.getItemPhoto2ContentType());
        }
        if (deal.getItemPhoto3() != null && deal.getItemPhoto3().length > 0) {
            item.setPhoto3(deal.getItemPhoto3());
            item.setPhoto3ContentType(deal.getItemPhoto3ContentType());
        }

        marketplaceItemRepository.save(item);
        return ResponseEntity.ok(Map.of("message", "listed", "marketplaceItemId", item.getId()));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String normalizeSize(String raw) {
        String v = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "small", "s" -> "small";
            case "medium", "m" -> "medium";
            case "big", "large", "l" -> "big";
            default -> "small";
        };
    }
}
