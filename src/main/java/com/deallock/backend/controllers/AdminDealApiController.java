package com.deallock.backend.controllers;

import com.deallock.backend.entities.Deal;
import com.deallock.backend.entities.MarketplaceItem;
import com.deallock.backend.entities.User;
import com.deallock.backend.repositories.DealRepository;
import com.deallock.backend.repositories.MarketplaceItemRepository;
import com.deallock.backend.repositories.UserRepository;
import com.deallock.backend.services.DealCacheService;
import com.deallock.backend.services.DealReadService;
import com.deallock.backend.services.NotificationDispatchService;
import com.deallock.backend.services.FileStorageService;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/admin/deals")
public class AdminDealApiController {

    private static final long MAX_UPLOAD_BYTES = 2L * 1024L * 1024L;
    private static final Set<String> IMAGE_TYPES = Set.of("image/*");

    private final DealRepository dealRepository;
    private final MarketplaceItemRepository marketplaceItemRepository;
    private final NotificationDispatchService notifier;
    private final DealReadService dealReadService;
    private final DealCacheService dealCacheService;
    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;

    @Value("${app.deals.payment-timeout:24h}")
    private Duration paymentTimeout;

    public AdminDealApiController(DealRepository dealRepository,
                                  MarketplaceItemRepository marketplaceItemRepository,
                                  NotificationDispatchService notifier,
                                  DealReadService dealReadService,
                                  DealCacheService dealCacheService,
                                  FileStorageService fileStorageService,
                                  UserRepository userRepository) {
        this.dealRepository = dealRepository;
        this.marketplaceItemRepository = marketplaceItemRepository;
        this.notifier = notifier;
        this.dealReadService = dealReadService;
        this.dealCacheService = dealCacheService;
        this.fileStorageService = fileStorageService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<?> list(Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(dealReadService.listAllDealsForAdmin());
    }

    @PostMapping("/{id}/approve")
    @Transactional
    public ResponseEntity<?> approve(@PathVariable("id") Long id, Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Optional<Object[]> light = dealRepository.findLightweightById(id);
        if (light.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Object[] data = light.get();
        String title = (String) data[1];
        Long userId = (Long) data[2];

        int updated = dealRepository.updateStatusAndReason(id, "Approved", null);
        if (updated == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        dealCacheService.evictAdminDeals();
        if (userId != null) {
            // userId is Long -> convert to int
            dealCacheService.evictUserDealsById(userId.intValue());
            userRepository.findById(userId).ifPresent(user -> {
                if (user.getEmail() != null) {
                    dealCacheService.evictUserDeals(user.getEmail());
                }
            });
        }

        User user = userId == null ? null : userRepository.findById(userId).orElse(null);
        if (user != null) {
            notifier.notifyUser(user,
                    "Your deal was approved. Please proceed to payment.",
                    "Your Deal Was Approved",
                    "Your deal was approved: " + title,
                    "Your deal was approved: " + title);
        }
        notifier.notifyAdmins(
                "Deal approved: " + title,
                "Deal Approved",
                "Deal approved: " + title,
                "Deal approved: " + title);

        return ResponseEntity.ok(Map.of("message", "approved"));
    }

    @PostMapping("/{id}/reject")
    @Transactional
    public ResponseEntity<?> reject(@PathVariable("id") Long id,
                                    @RequestBody(required = false) Map<String, Object> body,
                                    Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Optional<Object[]> light = dealRepository.findLightweightById(id);
        if (light.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Object[] data = light.get();
        String title = (String) data[1];
        Long userId = (Long) data[2];

        String reason = null;
        if (body != null && body.get("reason") != null) {
            reason = String.valueOf(body.get("reason")).trim();
        }
        if (reason != null && reason.length() > 2000) {
            reason = reason.substring(0, 2000);
        }

        int updated = dealRepository.updateStatusAndReason(id, "Rejected", reason);
        if (updated == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        dealCacheService.evictAdminDeals();
        if (userId != null) {
            dealCacheService.evictUserDealsById(userId.intValue());
            userRepository.findById(userId).ifPresent(user -> {
                if (user.getEmail() != null) {
                    dealCacheService.evictUserDeals(user.getEmail());
                }
            });
        }

        User user = userId == null ? null : userRepository.findById(userId).orElse(null);
        if (user != null) {
            notifier.notifyUser(user,
                    "Your deal was rejected." + (reason == null || reason.isBlank() ? "" : (" Reason: " + reason)),
                    "Your Deal Was Rejected",
                    "Your deal was rejected: " + title + (reason == null || reason.isBlank() ? "" : ("\nReason: " + reason)),
                    "Your deal was rejected: " + title);
        }
        notifier.notifyAdmins(
                "Deal rejected: " + title,
                "Deal Rejected",
                "Deal rejected: " + title,
                "Deal rejected: " + title);

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
        // Clear old BLOBs
        deal.setPaymentProof(null);
        deal.setItemPhoto(null);
        deal.setItemPhoto2(null);
        deal.setItemPhoto3(null);
        deal.setSecuredItemPhoto(null);
        deal.setBalancePaymentProof(null);
        dealRepository.save(deal);
        dealCacheService.evictAdminDeals();
        if (deal.getUser() != null) {
            // getUser().getId() returns int (primitive) -> no .intValue()
            dealCacheService.evictUserDealsById(deal.getUser().getId());
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
        deal.setPaymentProof(null);
        deal.setItemPhoto(null);
        deal.setItemPhoto2(null);
        deal.setItemPhoto3(null);
        deal.setSecuredItemPhoto(null);
        deal.setBalancePaymentProof(null);
        dealRepository.save(deal);
        dealCacheService.evictAdminDeals();
        if (deal.getUser() != null) {
            dealCacheService.evictUserDealsById(deal.getUser().getId());
            dealCacheService.evictUserDeals(deal.getUser().getEmail());
        }

        return ResponseEntity.ok(Map.of("message", "payment-not-received"));
    }

    @PostMapping(path = "/{id}/secured", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> secured(@PathVariable("id") Long id,
                                     @RequestParam(value = "securedPhoto", required = false) MultipartFile securedPhoto,
                                     Authentication authentication) throws Exception {
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
        if (securedPhoto != null && !securedPhoto.isEmpty() && securedPhoto.getSize() > MAX_UPLOAD_BYTES) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(Map.of("message", "Secured photo must be at most 2MB."));
        }

        deal.setSecured(true);
        deal.setSecuredAt(Instant.now());
        if (securedPhoto != null && !securedPhoto.isEmpty()) {
            try {
                FileStorageService.StoredFile stored = fileStorageService.save("deals/secured-items", securedPhoto, MAX_UPLOAD_BYTES, IMAGE_TYPES);
                deal.setSecuredItemPhoto(null);
                deal.setSecuredItemPhotoContentType(stored.contentType());
                deal.setSecuredItemPhotoKey(stored.key());
            } catch (IOException ex) {
                deal.setSecuredItemPhoto(securedPhoto.getBytes());
                deal.setSecuredItemPhotoContentType(securedPhoto.getContentType());
                deal.setSecuredItemPhotoKey(null);
            }
        }
        deal.setPaymentProof(null);
        deal.setItemPhoto(null);
        deal.setItemPhoto2(null);
        deal.setItemPhoto3(null);
        deal.setBalancePaymentProof(null);
        dealRepository.save(deal);
        dealCacheService.evictAdminDeals();
        if (deal.getUser() != null) {
            dealCacheService.evictUserDealsById(deal.getUser().getId());
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
        deal.setPaymentProof(null);
        deal.setItemPhoto(null);
        deal.setItemPhoto2(null);
        deal.setItemPhoto3(null);
        deal.setSecuredItemPhoto(null);
        deal.setBalancePaymentProof(null);
        dealRepository.save(deal);
        dealCacheService.evictAdminDeals();
        if (deal.getUser() != null) {
            dealCacheService.evictUserDealsById(deal.getUser().getId());
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
        deal.setPaymentProof(null);
        deal.setItemPhoto(null);
        deal.setItemPhoto2(null);
        deal.setItemPhoto3(null);
        deal.setSecuredItemPhoto(null);
        deal.setBalancePaymentProof(null);
        dealRepository.save(deal);
        dealCacheService.evictAdminDeals();
        if (deal.getUser() != null) {
            dealCacheService.evictUserDealsById(deal.getUser().getId());
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
        deal.setPaymentProof(null);
        deal.setItemPhoto(null);
        deal.setItemPhoto2(null);
        deal.setItemPhoto3(null);
        deal.setSecuredItemPhoto(null);
        deal.setBalancePaymentProof(null);
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