package com.deallock.backend.controllers;

import com.deallock.backend.entities.Deal;
import com.deallock.backend.repositories.DealRepository;
import com.deallock.backend.services.NotificationDispatchService;
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
    public ResponseEntity<?> approve(@PathVariable("id") Long id,
                                     Authentication authentication) {
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

        // Notify user + admins: in-app + email + sms/whatsapp.
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

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
