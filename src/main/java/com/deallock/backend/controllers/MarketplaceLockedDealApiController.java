package com.deallock.backend.controllers;

import com.deallock.backend.entities.Deal;
import com.deallock.backend.entities.DealWaitlistEntry;
import com.deallock.backend.repositories.DealRepository;
import com.deallock.backend.repositories.DealWaitlistRepository;
import com.deallock.backend.repositories.UserRepository;
import com.deallock.backend.services.MarketplaceLockPolicy;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/marketplace/locked-deals")
public class MarketplaceLockedDealApiController {

    private final DealRepository dealRepository;
    private final DealWaitlistRepository waitlistRepository;
    private final UserRepository userRepository;
    private final MarketplaceLockPolicy lockPolicy;

    public MarketplaceLockedDealApiController(DealRepository dealRepository,
                                              DealWaitlistRepository waitlistRepository,
                                              UserRepository userRepository,
                                              MarketplaceLockPolicy lockPolicy) {
        this.dealRepository = dealRepository;
        this.waitlistRepository = waitlistRepository;
        this.userRepository = userRepository;
        this.lockPolicy = lockPolicy;
    }

    @GetMapping
    public ResponseEntity<?> listLockedDeals() {
        Instant now = Instant.now();
        List<Deal> secured = dealRepository.findBySecuredTrueAndDeliveryInitiatedAtIsNullAndDeliveryConfirmedAtIsNullOrderBySecuredAtDesc().stream()
                .filter(d -> d.getAllowMarketplaceListing() == null || Boolean.TRUE.equals(d.getAllowMarketplaceListing()))
                .toList();

        List<Map<String, Object>> rows = secured.stream()
                .map(d -> toVm(d, now))
                .toList();
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> lockedDealPhoto(@PathVariable("id") Long id) {
        var dealOpt = dealRepository.findById(id);
        if (dealOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Deal deal = dealOpt.get();
        if (!isEligibleForMarketplace(deal)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        byte[] bytes = deal.getSecuredItemPhoto();
        String contentType = deal.getSecuredItemPhotoContentType();
        if (bytes == null || bytes.length == 0) {
            bytes = deal.getItemPhoto();
            contentType = deal.getItemPhotoContentType();
        }
        if (bytes == null || bytes.length == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        if (contentType != null && !contentType.isBlank()) {
            type = MediaType.parseMediaType(contentType);
        }
        return ResponseEntity.ok().contentType(type).body(bytes);
    }

    @PostMapping("/{id}/waitlist")
    public ResponseEntity<?> joinWaitlist(@PathVariable("id") Long dealId,
                                          Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }

        var userOpt = userRepository.findByEmail(authentication.getName());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }

        var dealOpt = dealRepository.findById(dealId);
        if (dealOpt.isEmpty() || !isEligibleForMarketplace(dealOpt.get())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Locked item not found"));
        }

        Deal deal = dealOpt.get();
        Instant now = Instant.now();
        if (!lockPolicy.isStillLocked(deal.getSecuredAt(), now)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "This item is now available"));
        }

        int userId = userOpt.get().getId();
        if (waitlistRepository.existsByDealIdAndUserId(dealId, userId)) {
            long count = waitlistRepository.countByDealId(dealId);
            return ResponseEntity.ok(Map.of("message", "Already on waitlist", "waitlistCount", count));
        }

        DealWaitlistEntry entry = new DealWaitlistEntry();
        entry.setDeal(deal);
        entry.setUser(userOpt.get());
        entry.setCreatedAt(now);
        waitlistRepository.save(entry);

        long count = waitlistRepository.countByDealId(dealId);
        return ResponseEntity.ok(Map.of("message", "Added to waitlist", "waitlistCount", count));
    }

    private boolean isEligibleForMarketplace(Deal deal) {
        if (deal == null) return false;
        if (!deal.isSecured()) return false;
        if (deal.getDeliveryInitiatedAt() != null || deal.getDeliveryConfirmedAt() != null) return false;
        return deal.getAllowMarketplaceListing() == null || Boolean.TRUE.equals(deal.getAllowMarketplaceListing());
    }

    private Map<String, Object> toVm(Deal deal, Instant now) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", deal.getId());
        row.put("title", deal.getTitle() == null ? "Locked item" : deal.getTitle());
        row.put("value", deal.getValue());
        row.put("size", deal.getItemSize());
        row.put("securedAt", deal.getSecuredAt());
        row.put("lockedUntil", lockPolicy.lockedUntil(deal.getSecuredAt()));
        row.put("isLocked", lockPolicy.isStillLocked(deal.getSecuredAt(), now));
        row.put("waitlistCount", waitlistRepository.countByDealId(deal.getId()));
        row.put("imageUrl", "/api/marketplace/locked-deals/" + deal.getId() + "/photo");
        return row;
    }
}
