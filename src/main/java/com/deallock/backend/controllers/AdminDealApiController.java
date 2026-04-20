package com.deallock.backend.controllers;

import com.deallock.backend.entities.Deal;
import com.deallock.backend.repositories.DealRepository;
import com.deallock.backend.services.NotificationDispatchService;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    private final NotificationDispatchService notifier;

    public AdminDealApiController(DealRepository dealRepository,
                                  NotificationDispatchService notifier) {
        this.dealRepository = dealRepository;
        this.notifier = notifier;
    }

    @GetMapping
    public ResponseEntity<?> list(Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<Map<String, Object>> deals = dealRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(d -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", d.getId());
                    row.put("title", d.getTitle() == null ? "Untitled Deal" : d.getTitle());
                    row.put("status", d.getStatus() == null ? "Pending Approval" : d.getStatus());
                    row.put("value", d.getValue() == null ? 0 : d.getValue());
                    row.put("paymentStatus", d.getPaymentStatus() == null ? "NOT_PAID" : d.getPaymentStatus());
                    row.put("secured", d.isSecured());
                    row.put("balancePaymentStatus", d.getBalancePaymentStatus() == null ? "NOT_PAID" : d.getBalancePaymentStatus());
                    row.put("deliveryInitiatedAt", d.getDeliveryInitiatedAt());
                    row.put("deliveryConfirmedByUser", d.isDeliveryConfirmedByUser());
                    row.put("deliveryConfirmedAt", d.getDeliveryConfirmedAt());
                    row.put("createdAt", d.getCreatedAt());
                    row.put("userEmail", d.getUser() == null ? null : d.getUser().getEmail());
                    row.put("rejectionReason", d.getRejectionReason());
                    return row;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(deals);
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
        return ResponseEntity.ok(Map.of("message", "deleted"));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
