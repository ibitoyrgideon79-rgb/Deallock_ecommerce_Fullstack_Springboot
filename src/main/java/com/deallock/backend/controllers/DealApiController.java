package com.deallock.backend.controllers;

import com.deallock.backend.entities.Deal;
import com.deallock.backend.repositories.DealRepository;
import com.deallock.backend.repositories.UserRepository;
import com.deallock.backend.services.DealCacheService;
import com.deallock.backend.services.DealReadService;
import com.deallock.backend.services.NewsletterService;
import com.deallock.backend.services.NotificationDispatchService;
import com.deallock.backend.services.SmsService;
import com.deallock.backend.services.CurrentUserService;
import com.deallock.backend.services.FileStorageService;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/deals")
public class DealApiController {

    private static final long MAX_UPLOAD_BYTES = 2L * 1024L * 1024L;
    private static final Set<String> IMAGE_TYPES = Set.of("image/*");
    private static final Set<String> PROOF_TYPES = Set.of("image/*", "application/pdf");

    private final DealRepository dealRepository;
    private final UserRepository userRepository;
    private final SmsService smsService;
    private final NotificationDispatchService notifier;
    private final DealReadService dealReadService;
    private final DealCacheService dealCacheService;
    private final NewsletterService newsletterService;
    private final CurrentUserService currentUserService;
    private final FileStorageService fileStorageService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;
    @Value("${app.deals.payment-timeout:24h}")
    private Duration paymentTimeout;
    @Value("${app.deals.extension-weekly-service-rate:0.02}")
    private BigDecimal extensionWeeklyServiceRate;

    public DealApiController(DealRepository dealRepository,
                             UserRepository userRepository,
                             SmsService smsService,
                             NotificationDispatchService notifier,
                             DealReadService dealReadService,
                             DealCacheService dealCacheService,
                             NewsletterService newsletterService,
                             CurrentUserService currentUserService,
                             FileStorageService fileStorageService) {
        this.dealRepository = dealRepository;
        this.userRepository = userRepository;
        this.smsService = smsService;
        this.notifier = notifier;
        this.dealReadService = dealReadService;
        this.dealCacheService = dealCacheService;
        this.newsletterService = newsletterService;
        this.currentUserService = currentUserService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping
    public ResponseEntity<?> listDeals(Principal principal) {
        var userOpt = currentUserService.resolve(principal);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(dealReadService.listDealsForUser(userOpt.get()));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createDeal(@RequestParam("deal-title") String title,
                                        @RequestParam(value = "deal-link", required = false) String link,
                                        @RequestParam("client-name") String clientName,
                                        @RequestParam(value = "seller-phone", required = false) String sellerPhone,
                                        @RequestParam("seller-address") String sellerAddress,
                                        @RequestParam("delivery-address") String deliveryAddress,
                                        @RequestParam("item-size") String itemSize,
                                        @RequestParam(value = "listing", required = false) String listing,
                                        @RequestParam(value = "allowMarketplaceListing", required = false) Boolean allowMarketplaceListing,
                                        @RequestParam(value = "courier-partner", required = false) String courierPartner,
                                        @RequestParam("weeks") String weeksSelection,
                                        @RequestParam(value = "customWeeks", required = false) Integer customWeeks,
                                        @RequestParam("deal-value") BigDecimal value,
                                        @RequestParam(value = "subscribeUpdates", required = false) Boolean subscribeUpdates,
                                        @RequestParam(value = "description", required = false) String description,
                                        // Backwards compatible: older UI sends a single `itemPhoto`.
                                        @RequestParam(value = "itemPhoto", required = false) MultipartFile itemPhoto,
                                        // New UI can send up to 3 files under the same field name.
                                        @RequestParam(value = "itemPhotos", required = false) MultipartFile[] itemPhotos,
                                        Principal principal) throws Exception {
        var userOpt = currentUserService.resolve(principal);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        int weeks = resolveWeeks(weeksSelection, customWeeks);
        if (weeks < 1) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid installment weeks."));
        }
        if (value == null || value.compareTo(BigDecimal.valueOf(1000)) < 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid item value."));
        }

        Deal deal = new Deal();
        deal.setUser(userOpt.get());
        deal.setTitle(title);
        deal.setLink(link);
        deal.setClientName(clientName);
        deal.setSellerPhoneNumber(sellerPhone);
        deal.setSellerAddress(sellerAddress);
        deal.setDeliveryAddress(deliveryAddress);
        deal.setItemSize(normalizeSize(itemSize));
        Boolean allow = allowMarketplaceListing;
        if (allow == null) {
            String v = listing == null ? "" : listing.trim().toLowerCase(Locale.ROOT);
            if (v.isBlank()) allow = Boolean.TRUE;
            else if (v.equals("no") || v.equals("false") || v.equals("0")) allow = Boolean.FALSE;
            else allow = Boolean.TRUE;
        }
        deal.setAllowMarketplaceListing(allow);
        deal.setCourierPartner(courierPartner == null || courierPartner.isBlank() ? "Auto-select" : courierPartner);
        deal.setInstallmentWeeks(weeks);
        deal.setValue(value);
        deal.setDescription(description);
        deal.setStatus("Pending Approval");
        deal.setCreatedAt(Instant.now());
        deal.setPaymentDueAt(deal.getCreatedAt().plus(paymentTimeout == null ? Duration.ofHours(24) : paymentTimeout));
        deal.setExtensionWeeksUsed(0);
        deal.setExtensionServiceFeeAmount(BigDecimal.ZERO);
        deal.setLastPaymentReminderAt(null);
        deal.setPaymentStatus("NOT_PAID");
        deal.setBalancePaymentStatus("NOT_PAID");
        deal.setSecured(false);
        deal.setDeliveryConfirmedByUser(false);

        BigDecimal holdingFee = roundMoney(value.multiply(BigDecimal.valueOf(0.05)).multiply(BigDecimal.valueOf(weeks)));
        BigDecimal vatAmount = roundMoney(holdingFee.multiply(BigDecimal.valueOf(0.075)));
        BigDecimal logisticsFee = calculateLogisticsFee(sellerAddress, deliveryAddress, itemSize, deal.getCourierPartner());
        BigDecimal upfront = roundMoney(value.multiply(BigDecimal.valueOf(0.5)).add(logisticsFee));
        BigDecimal total = roundMoney(value.add(holdingFee).add(vatAmount).add(logisticsFee));
        BigDecimal remaining = roundMoney(total.subtract(upfront));
        BigDecimal weekly = weeks > 0
                ? roundMoney(remaining.divide(BigDecimal.valueOf(weeks), 2, RoundingMode.HALF_UP))
                : BigDecimal.ZERO;

        deal.setHoldingFeeAmount(holdingFee);
        deal.setVatAmount(vatAmount);
        deal.setLogisticsFeeAmount(logisticsFee);
        deal.setUpfrontPaymentAmount(upfront);
        deal.setTotalAmount(total);
        deal.setRemainingBalanceAmount(remaining);
        deal.setWeeklyPaymentAmount(weekly);

        // --- Photos (up to 3) ---
        MultipartFile[] incoming = (itemPhotos != null && itemPhotos.length > 0) ? itemPhotos : null;
        if (incoming == null && itemPhoto != null && !itemPhoto.isEmpty()) {
            incoming = new MultipartFile[]{itemPhoto};
        }

        if (incoming != null) {
            int saved = 0;
            for (MultipartFile file : incoming) {
                if (file == null || file.isEmpty()) continue;
                if (file.getSize() > MAX_UPLOAD_BYTES) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Each item photo must be at most 2MB."));
                }

                saved++;
                if (saved == 1) {
                    storeDealItemPhoto(deal, file, 1);
                } else if (saved == 2) {
                    storeDealItemPhoto(deal, file, 2);
                } else if (saved == 3) {
                    storeDealItemPhoto(deal, file, 3);
                    break;
                }
            }
        }

        dealRepository.save(deal);
        dealCacheService.evictUserDealsById(userOpt.get().getId());
        dealCacheService.evictAdminDeals();
        CompletableFuture.runAsync(() -> {
            try {
                if (Boolean.TRUE.equals(subscribeUpdates) && userOpt.get().getEmail() != null) {
                    newsletterService.subscribe(
                            userOpt.get().getEmail(),
                            userOpt.get().getFullName(),
                            "deal-submit"
                    );
                }
            } catch (Exception ignored) {
            }
            try {
                notifyAdminsAndUserOnCreate(deal);
            } catch (Exception ignored) {
            }
            try {
                notifier.notifyUser(userOpt.get(),
                        "Deal sent. We received your deal.",
                        "Your Deal Was Created",
                        "Deal received. We are reviewing: " + safe(deal.getTitle()),
                        "Deal received. We are reviewing: " + safe(deal.getTitle()));
                notifier.notifyAdmins(
                        "New deal submitted: " + safe(deal.getTitle()),
                        "New Deal Created",
                        "New deal submitted: " + safe(deal.getTitle()),
                        "New deal submitted: " + safe(deal.getTitle()));
            } catch (Exception ignored) {
            }
            try {
                if (userOpt.get().getPhone() != null) {
                    smsService.sendToUser(userOpt.get().getPhone(), "Deal received. Awaiting approval.");
                    smsService.sendWhatsAppToUser(userOpt.get().getPhone(), "Deal received. Awaiting approval.");
                }
            } catch (Exception ignored) {
            }
        });
        return ResponseEntity.ok(Map.of(
                "message", "Deal created",
                "id", deal.getId(),
                "upfrontPaymentAmount", deal.getUpfrontPaymentAmount(),
                "logisticsFeeAmount", deal.getLogisticsFeeAmount(),
                "totalAmount", deal.getTotalAmount(),
                "paymentDueAt", deal.getPaymentDueAt()
        ));
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

    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> dealPhoto(@PathVariable("id") Long id,
                                            Principal principal,
                                            Authentication authentication) {
        var userOpt = currentUserService.resolve(principal);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var dealOpt = dealRepository.findById(id);
        if (dealOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        var deal = dealOpt.get();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin && deal.getUser().getId() != userOpt.get().getId()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return dealPhotoSlot(deal, 1);
    }

    @GetMapping("/{id}/photo/{slot}")
    public ResponseEntity<byte[]> dealPhotoSlot(@PathVariable("id") Long id,
                                                @PathVariable("slot") int slot,
                                                Principal principal,
                                                Authentication authentication) {
        var userOpt = currentUserService.resolve(principal);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var dealOpt = dealRepository.findById(id);
        if (dealOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        var deal = dealOpt.get();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin && deal.getUser().getId() != userOpt.get().getId()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return dealPhotoSlot(deal, slot);
    }

    private ResponseEntity<byte[]> dealPhotoSlot(Deal deal, int slot) {
        byte[] bytes;
        String contentType;
        String key;

        if (slot == 2) {
            bytes = deal.getItemPhoto2();
            contentType = deal.getItemPhoto2ContentType();
            key = deal.getItemPhoto2Key();
        } else if (slot == 3) {
            bytes = deal.getItemPhoto3();
            contentType = deal.getItemPhoto3ContentType();
            key = deal.getItemPhoto3Key();
        } else {
            bytes = deal.getItemPhoto();
            contentType = deal.getItemPhotoContentType();
            key = deal.getItemPhotoKey();
        }

        bytes = resolveBytes(bytes, key);
        if (bytes == null || bytes.length == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        if (contentType != null && !contentType.isBlank()) {
            type = MediaType.parseMediaType(contentType);
        }
        return ResponseEntity.ok().contentType(type).body(bytes);
    }

    @GetMapping("/{id}/secured-photo")
    public ResponseEntity<byte[]> securedPhoto(@PathVariable("id") Long id,
                                               Principal principal,
                                               Authentication authentication) {
        var userOpt = currentUserService.resolve(principal);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var dealOpt = dealRepository.findById(id);
        if (dealOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        var deal = dealOpt.get();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin && deal.getUser().getId() != userOpt.get().getId()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        if (deal.getSecuredItemPhotoContentType() != null) {
            type = MediaType.parseMediaType(deal.getSecuredItemPhotoContentType());
        }
        byte[] bytes = resolveBytes(deal.getSecuredItemPhoto(), deal.getSecuredItemPhotoKey());
        if (bytes == null || bytes.length == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok().contentType(type).body(bytes);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteDeal(@PathVariable("id") Long id,
                                        Principal principal,
                                        Authentication authentication) {
        var userOpt = currentUserService.resolve(principal);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var dealOpt = dealRepository.findById(id);
        if (dealOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        var deal = dealOpt.get();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        if (!isAdmin && (deal.getUser() == null || deal.getUser().getId() != userOpt.get().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        dealRepository.deleteById(id);
        dealCacheService.evictUserDealsById(userOpt.get().getId());
        dealCacheService.evictAdminDeals();
        String actor = isAdmin ? "admin" : "user";
        notifier.notifyAdmins(
                "Deal canceled by " + actor + ": " + safe(deal.getTitle()),
                "Deal Canceled",
                "Deal canceled by " + actor + ": " + safe(deal.getTitle()),
                "Deal canceled by " + actor + ": " + safe(deal.getTitle()));
        if (deal.getUser() != null) {
            notifier.notifyUser(deal.getUser(),
                    "Deal canceled: " + safe(deal.getTitle()),
                    "Deal Canceled",
                    "Deal canceled: " + safe(deal.getTitle()),
                    "Deal canceled: " + safe(deal.getTitle()));
        }
        return ResponseEntity.ok(Map.of("message", "Deal deleted"));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelDeal(@PathVariable("id") Long id,
                                        Principal principal,
                                        Authentication authentication) {
        return deleteDeal(id, principal, authentication);
    }

    @PostMapping("/{id}/request-extension")
    public ResponseEntity<?> requestPaymentExtension(@PathVariable("id") Long id,
                                                     @RequestParam(value = "weeks", required = false) Integer weeks,
                                                     Principal principal,
                                                     Authentication authentication) {
        var userOpt = currentUserService.resolve(principal);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var dealOpt = dealRepository.findById(id);
        if (dealOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        var deal = dealOpt.get();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin && (deal.getUser() == null || deal.getUser().getId() != userOpt.get().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (deal.getStatus() == null || !"Approved".equalsIgnoreCase(deal.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Deal must be approved first"));
        }
        if (deal.getPaymentStatus() != null && !"NOT_PAID".equalsIgnoreCase(deal.getPaymentStatus())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Payment already submitted or confirmed"));
        }
        Instant now = Instant.now();
        if (deal.getPaymentDueAt() == null || !deal.getPaymentDueAt().isBefore(now)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Extension is available only after due date"));
        }

        int addWeeks = (weeks == null || weeks < 1) ? 1 : weeks;
        if (addWeeks > 2) addWeeks = 2;
        int used = deal.getExtensionWeeksUsed() == null ? 0 : deal.getExtensionWeeksUsed();
        if (used >= 2) {
            return ResponseEntity.badRequest().body(Map.of("message", "Maximum extension reached (2 weeks)"));
        }
        if (used + addWeeks > 2) {
            addWeeks = 2 - used;
        }
        if (addWeeks <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Maximum extension reached (2 weeks)"));
        }

        deal.setExtensionWeeksUsed(used + addWeeks);
        Instant baseDue = deal.getPaymentDueAt() == null ? now : deal.getPaymentDueAt();
        deal.setPaymentDueAt(baseDue.plus(Duration.ofDays(7L * addWeeks)));

        BigDecimal value = deal.getValue() == null ? BigDecimal.ZERO : deal.getValue();
        BigDecimal rate = extensionWeeklyServiceRate == null ? BigDecimal.valueOf(0.02) : extensionWeeklyServiceRate;
        BigDecimal extraFee = roundMoney(value.multiply(rate).multiply(BigDecimal.valueOf(addWeeks)));
        BigDecimal existingExtFee = deal.getExtensionServiceFeeAmount() == null ? BigDecimal.ZERO : deal.getExtensionServiceFeeAmount();
        deal.setExtensionServiceFeeAmount(roundMoney(existingExtFee.add(extraFee)));
        deal.setTotalAmount(roundMoney((deal.getTotalAmount() == null ? BigDecimal.ZERO : deal.getTotalAmount()).add(extraFee)));
        deal.setRemainingBalanceAmount(roundMoney((deal.getRemainingBalanceAmount() == null ? BigDecimal.ZERO : deal.getRemainingBalanceAmount()).add(extraFee)));

        int installmentWeeks = deal.getInstallmentWeeks() == null || deal.getInstallmentWeeks() <= 0 ? 1 : deal.getInstallmentWeeks();
        deal.setWeeklyPaymentAmount(roundMoney(deal.getRemainingBalanceAmount().divide(BigDecimal.valueOf(installmentWeeks), 2, RoundingMode.HALF_UP)));

        dealRepository.save(deal);
        dealCacheService.evictUserDealsById(userOpt.get().getId());
        dealCacheService.evictAdminDeals();

        notifier.notifyUser(deal.getUser(),
                "Payment period extended by " + addWeeks + " week(s). Extra service fee applied.",
                "Payment Extension Approved",
                "Your payment period was extended by " + addWeeks + " week(s).\nExtra service fee: NGN " + extraFee + "\nNew due date: " + deal.getPaymentDueAt(),
                "Payment extension: +" + addWeeks + " week(s). Extra fee NGN " + extraFee);
        notifier.notifyAdmins(
                "User requested extension for deal " + safe(deal.getTitle()) + " (+" + addWeeks + " week(s)).",
                "Deal Payment Extension",
                "Extension applied for deal: " + safe(deal.getTitle()) + "\nWeeks: " + addWeeks + "\nFee: NGN " + extraFee,
                "Extension applied: " + safe(deal.getTitle()));

        return ResponseEntity.ok(Map.of(
                "message", "Payment period extended",
                "addedWeeks", addWeeks,
                "extensionWeeksUsed", deal.getExtensionWeeksUsed(),
                "extensionFeeAdded", extraFee,
                "paymentDueAt", deal.getPaymentDueAt(),
                "remainingBalanceAmount", deal.getRemainingBalanceAmount(),
                "totalAmount", deal.getTotalAmount()
        ));
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<?> markPaid(@PathVariable("id") Long id,
                                      Principal principal,
                                      Authentication authentication) {
        var userOpt = currentUserService.resolve(principal);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var dealOpt = dealRepository.findById(id);
        if (dealOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        var deal = dealOpt.get();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        if (!isAdmin && (deal.getUser() == null || deal.getUser().getId() != userOpt.get().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (deal.getStatus() == null || !"Approved".equalsIgnoreCase(deal.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Deal not approved"));
        }

        deal.setPaymentStatus("PAID_PENDING_CONFIRMATION");
        dealRepository.save(deal);
        dealCacheService.evictUserDealsById(userOpt.get().getId());
        dealCacheService.evictAdminDeals();
        return ResponseEntity.ok(Map.of("message", "Payment marked as processing"));
    }

    @PostMapping(path = "/{id}/payment-proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadPaymentProof(@PathVariable("id") Long id,
                                                @RequestParam("paymentProof") MultipartFile paymentProof,
                                                Principal principal,
                                                Authentication authentication) throws Exception {
        var userOpt = currentUserService.resolve(principal);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (paymentProof == null || paymentProof.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Payment proof is required"));
        }
        if (paymentProof.getSize() > MAX_UPLOAD_BYTES) {
            return ResponseEntity.badRequest().body(Map.of("message", "Payment proof must be at most 2MB."));
        }

        var dealOpt = dealRepository.findById(id);
        if (dealOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        var deal = dealOpt.get();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        if (!isAdmin && (deal.getUser() == null || deal.getUser().getId() != userOpt.get().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (deal.getStatus() == null || !"Approved".equalsIgnoreCase(deal.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Deal not approved"));
        }

        storeDealPaymentProof(deal, paymentProof, false);
        deal.setPaymentProofUploadedAt(Instant.now());
        if (deal.getValue() != null) {
            if (deal.getUpfrontPaymentAmount() != null) {
                deal.setPaymentProofAmount(deal.getUpfrontPaymentAmount());
            } else {
                deal.setPaymentProofAmount(deal.getValue().multiply(BigDecimal.valueOf(0.5)));
            }
        }
        deal.setPaymentStatus("PAID_PENDING_CONFIRMATION");
        dealRepository.save(deal);
        dealCacheService.evictUserDealsById(userOpt.get().getId());
        dealCacheService.evictAdminDeals();
        notifier.notifyUser(deal.getUser(),
                "Payment proof received. We are verifying your payment.",
                "Payment Proof Received",
                "Payment proof received for: " + safe(deal.getTitle()),
                "Payment proof received. Verifying payment.");
        notifier.notifyAdmins(
                "Payment proof uploaded: " + safe(deal.getTitle()),
                "Payment Proof Uploaded",
                "Payment proof uploaded for: " + safe(deal.getTitle()),
                "Payment proof uploaded: " + safe(deal.getTitle()));

        return ResponseEntity.ok(Map.of("message", "Payment proof uploaded"));
    }

    @PostMapping(path = "/{id}/balance-payment-proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadBalancePaymentProof(@PathVariable("id") Long id,
                                                       @RequestParam("paymentProof") MultipartFile paymentProof,
                                                       Principal principal,
                                                       Authentication authentication) throws Exception {
        var userOpt = currentUserService.resolve(principal);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (paymentProof == null || paymentProof.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Payment proof is required"));
        }
        if (paymentProof.getSize() > MAX_UPLOAD_BYTES) {
            return ResponseEntity.badRequest().body(Map.of("message", "Payment proof must be at most 2MB."));
        }

        var dealOpt = dealRepository.findById(id);
        if (dealOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        var deal = dealOpt.get();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        if (!isAdmin && (deal.getUser() == null || deal.getUser().getId() != userOpt.get().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (!deal.isSecured()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Deal not secured yet"));
        }

        storeDealPaymentProof(deal, paymentProof, true);
        deal.setBalancePaymentUploadedAt(Instant.now());
        if (deal.getRemainingBalanceAmount() != null) {
            deal.setBalancePaymentAmount(deal.getRemainingBalanceAmount());
        }
        deal.setBalancePaymentStatus("PAID_PENDING_CONFIRMATION");
        dealRepository.save(deal);
        dealCacheService.evictUserDealsById(userOpt.get().getId());
        dealCacheService.evictAdminDeals();

        notifier.notifyUser(deal.getUser(),
                "Balance payment proof received. We are verifying your payment.",
                "Balance Payment Proof Received",
                "Balance payment proof received for: " + safe(deal.getTitle()),
                "Balance payment proof received. Verifying payment.");
        notifier.notifyAdmins(
                "Balance payment proof uploaded: " + safe(deal.getTitle()),
                "Balance Payment Proof Uploaded",
                "Balance payment proof uploaded for: " + safe(deal.getTitle()),
                "Balance payment proof uploaded: " + safe(deal.getTitle()));

        return ResponseEntity.ok(Map.of("message", "Balance payment proof uploaded"));
    }

    @GetMapping("/{id}/balance-payment-proof")
    public ResponseEntity<byte[]> balancePaymentProof(@PathVariable("id") Long id,
                                                      Principal principal,
                                                      Authentication authentication) {
        var userOpt = currentUserService.resolve(principal);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var dealOpt = dealRepository.findById(id);
        if (dealOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        var deal = dealOpt.get();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin && (deal.getUser() == null || deal.getUser().getId() != userOpt.get().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        if (deal.getBalancePaymentProofContentType() != null) {
            type = MediaType.parseMediaType(deal.getBalancePaymentProofContentType());
        }
        byte[] bytes = resolveBytes(deal.getBalancePaymentProof(), deal.getBalancePaymentProofKey());
        if (bytes == null || bytes.length == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok().contentType(type).body(bytes);
    }

    @PostMapping("/{id}/confirm-delivery")
    public ResponseEntity<?> confirmDelivery(@PathVariable("id") Long id,
                                             Principal principal,
                                             Authentication authentication) {
        var userOpt = currentUserService.resolve(principal);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var dealOpt = dealRepository.findById(id);
        if (dealOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        var deal = dealOpt.get();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin && (deal.getUser() == null || deal.getUser().getId() != userOpt.get().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (deal.getDeliveryInitiatedAt() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Delivery not initiated yet"));
        }

        deal.setDeliveryConfirmedByUser(true);
        dealRepository.save(deal);
        dealCacheService.evictUserDealsById(userOpt.get().getId());
        dealCacheService.evictAdminDeals();

        notifier.notifyUser(deal.getUser(),
                "Delivery confirmed. Thank you!",
                "Delivery Confirmed",
                "You confirmed delivery for: " + safe(deal.getTitle()),
                "Delivery confirmed for your deal.");
        notifier.notifyAdmins(
                "User confirmed delivery: " + safe(deal.getTitle()),
                "Delivery Confirmed By User",
                "User confirmed delivery for: " + safe(deal.getTitle()),
                "User confirmed delivery: " + safe(deal.getTitle()));

        return ResponseEntity.ok(Map.of("message", "Delivery confirmed"));
    }

    @PostMapping("/{id}/feedback")
    public ResponseEntity<?> submitFeedback(@PathVariable("id") Long id,
                                            @RequestParam("feedback") String feedback,
                                            Principal principal,
                                            Authentication authentication) {
        var userOpt = currentUserService.resolve(principal);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var dealOpt = dealRepository.findById(id);
        if (dealOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        var deal = dealOpt.get();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin && (deal.getUser() == null || deal.getUser().getId() != userOpt.get().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String trimmed = feedback == null ? "" : feedback.trim();
        if (trimmed.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Feedback is required"));
        }
        if (!deal.isDeliveryConfirmedByUser()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Confirm delivery before sending feedback"));
        }

        deal.setFeedback(trimmed);
        deal.setFeedbackSubmittedAt(Instant.now());
        dealRepository.save(deal);
        dealCacheService.evictUserDealsById(userOpt.get().getId());
        dealCacheService.evictAdminDeals();
        notifier.notifyAdmins(
                "New feedback submitted: " + safe(deal.getTitle()),
                "Deal Feedback",
                "New feedback submitted for: " + safe(deal.getTitle()),
                "New feedback submitted: " + safe(deal.getTitle()));

        return ResponseEntity.ok(Map.of("message", "Feedback submitted"));
    }

    @GetMapping("/{id}/payment-proof")
    public ResponseEntity<byte[]> paymentProof(@PathVariable("id") Long id,
                                               Principal principal,
                                               Authentication authentication) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var userOpt = currentUserService.resolve(principal);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var dealOpt = dealRepository.findById(id);
        if (dealOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        var deal = dealOpt.get();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin && (deal.getUser() == null || deal.getUser().getId() != userOpt.get().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        if (deal.getPaymentProofContentType() != null) {
            type = MediaType.parseMediaType(deal.getPaymentProofContentType());
        }
        byte[] bytes = resolveBytes(deal.getPaymentProof(), deal.getPaymentProofKey());
        if (bytes == null || bytes.length == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok().contentType(type).body(bytes);
    }

    private void storeDealItemPhoto(Deal deal, MultipartFile file, int slot) throws Exception {
        try {
            FileStorageService.StoredFile stored = fileStorageService.save("deals/items", file, MAX_UPLOAD_BYTES, IMAGE_TYPES);
            if (slot == 2) {
                deal.setItemPhoto2(null);
                deal.setItemPhoto2ContentType(stored.contentType());
                deal.setItemPhoto2Key(stored.key());
            } else if (slot == 3) {
                deal.setItemPhoto3(null);
                deal.setItemPhoto3ContentType(stored.contentType());
                deal.setItemPhoto3Key(stored.key());
            } else {
                deal.setItemPhoto(null);
                deal.setItemPhotoContentType(stored.contentType());
                deal.setItemPhotoKey(stored.key());
            }
        } catch (IOException ex) {
            // Fallback to DB blob if filesystem storage isn't available.
            if (slot == 2) {
                deal.setItemPhoto2(file.getBytes());
                deal.setItemPhoto2ContentType(file.getContentType());
                deal.setItemPhoto2Key(null);
            } else if (slot == 3) {
                deal.setItemPhoto3(file.getBytes());
                deal.setItemPhoto3ContentType(file.getContentType());
                deal.setItemPhoto3Key(null);
            } else {
                deal.setItemPhoto(file.getBytes());
                deal.setItemPhotoContentType(file.getContentType());
                deal.setItemPhotoKey(null);
            }
        }
    }

    private void storeDealPaymentProof(Deal deal, MultipartFile file, boolean balance) throws Exception {
        try {
            String folder = balance ? "deals/balance-proofs" : "deals/payment-proofs";
            FileStorageService.StoredFile stored = fileStorageService.save(folder, file, MAX_UPLOAD_BYTES, PROOF_TYPES);
            if (balance) {
                deal.setBalancePaymentProof(null);
                deal.setBalancePaymentProofContentType(stored.contentType());
                deal.setBalancePaymentProofKey(stored.key());
            } else {
                deal.setPaymentProof(null);
                deal.setPaymentProofContentType(stored.contentType());
                deal.setPaymentProofKey(stored.key());
            }
        } catch (IOException ex) {
            // Fallback to DB blob if filesystem storage isn't available.
            if (balance) {
                deal.setBalancePaymentProof(file.getBytes());
                deal.setBalancePaymentProofContentType(file.getContentType());
                deal.setBalancePaymentProofKey(null);
            } else {
                deal.setPaymentProof(file.getBytes());
                deal.setPaymentProofContentType(file.getContentType());
                deal.setPaymentProofKey(null);
            }
        }
    }

    private byte[] resolveBytes(byte[] blob, String key) {
        if (blob != null && blob.length > 0) {
            return blob;
        }
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            return fileStorageService.read(key);
        } catch (IOException ignored) {
            return null;
        }
    }

    private void notifyAdminsAndUserOnCreate(Deal deal) {
        String detailsLink = baseUrl + "/dashboard/deal/" + deal.getId();
        String baseText = "Deal created.\n\n"
                + "Title: " + safe(deal.getTitle()) + "\n"
                + "Seller: " + safe(deal.getClientName()) + "\n"
                + "Seller Phone: " + safe(deal.getSellerPhoneNumber()) + "\n"
                + "Seller Address: " + safe(deal.getSellerAddress()) + "\n"
                + "Delivery Address: " + safe(deal.getDeliveryAddress()) + "\n"
                + "Item Size: " + safe(deal.getItemSize()) + "\n"
                + "Courier Partner: Auto-select\n"
                + "Installment Weeks: " + (deal.getInstallmentWeeks() != null ? deal.getInstallmentWeeks() : 0) + "\n"
                + "Value: NGN " + (deal.getValue() != null ? deal.getValue() : "0") + "\n"
                + "Logistics Fee: NGN " + (deal.getLogisticsFeeAmount() != null ? deal.getLogisticsFeeAmount() : "0") + "\n"
                + "Upfront: NGN " + (deal.getUpfrontPaymentAmount() != null ? deal.getUpfrontPaymentAmount() : "0") + "\n"
                + "Status: " + safe(deal.getStatus()) + "\n"
                + "Details: " + detailsLink + "\n";

        notifier.notifyAdmins(
                "New deal created: " + safe(deal.getTitle()),
                "New Deal Created",
                baseText,
                "New deal created: " + safe(deal.getTitle()));

        if (deal.getUser() != null) {
            notifier.notifyUser(deal.getUser(),
                    "Your deal was created: " + safe(deal.getTitle()),
                    "Your Deal Was Created",
                    baseText,
                    "Your deal was created: " + safe(deal.getTitle()));
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private int resolveWeeks(String weeksSelection, Integer customWeeks) {
        if ("custom".equalsIgnoreCase(weeksSelection)) {
            return customWeeks == null ? 0 : customWeeks;
        }
        try {
            return Integer.parseInt(weeksSelection);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private BigDecimal calculateLogisticsFee(String sellerAddress,
                                             String deliveryAddress,
                                             String itemSize,
                                             String courierPartner) {
        BigDecimal baseFee = switch ((itemSize == null ? "" : itemSize).toLowerCase(Locale.ROOT)) {
            case "medium" -> BigDecimal.valueOf(9000);
            case "large" -> BigDecimal.valueOf(15000);
            default -> BigDecimal.valueOf(5000);
        };

        String seller = sellerAddress == null ? "" : sellerAddress.toLowerCase(Locale.ROOT);
        String buyer = deliveryAddress == null ? "" : deliveryAddress.toLowerCase(Locale.ROOT);
        BigDecimal distanceFactor = BigDecimal.valueOf(1.0);
        if (!seller.isBlank() && !buyer.isBlank()) {
            boolean sellerAbuja = seller.contains("abuja") || seller.contains("fct");
            boolean buyerAbuja = buyer.contains("abuja") || buyer.contains("fct");
            if (sellerAbuja && buyerAbuja) {
                distanceFactor = BigDecimal.valueOf(1.0);
            } else if (sellerAbuja || buyerAbuja) {
                distanceFactor = BigDecimal.valueOf(1.45);
            } else {
                distanceFactor = BigDecimal.valueOf(1.65);
            }
        }

        return roundMoney(baseFee.multiply(distanceFactor));
    }

    private BigDecimal roundMoney(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
