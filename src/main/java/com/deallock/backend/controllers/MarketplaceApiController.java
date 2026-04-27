package com.deallock.backend.controllers;

import com.deallock.backend.entities.MarketplaceItem;
import com.deallock.backend.entities.MarketplaceOrder;
import com.deallock.backend.entities.MarketplaceOrderItem;
import com.deallock.backend.repositories.MarketplaceItemRepository;
import com.deallock.backend.repositories.MarketplaceOrderRepository;
import com.deallock.backend.repositories.UserRepository;
import com.deallock.backend.services.CurrentUserService;
import com.deallock.backend.services.FileStorageService;
import com.deallock.backend.services.MarketplaceOrderFlowService;
import com.deallock.backend.services.NotificationDispatchService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.Instant;
import java.util.Set;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/marketplace")
public class MarketplaceApiController {

    private static final BigDecimal DOOR_DELIVERY_FEE = BigDecimal.valueOf(2500);
    private static final long MAX_UPLOAD_BYTES = 2L * 1024L * 1024L;
    private static final Set<String> IMAGE_TYPES = Set.of("image/*");
    private static final Set<String> PROOF_TYPES = Set.of("image/*", "application/pdf");
    private final MarketplaceItemRepository marketplaceItemRepository;
    private final MarketplaceOrderRepository marketplaceOrderRepository;
    private final UserRepository userRepository;
    private final NotificationDispatchService notifier;
    private final MarketplaceOrderFlowService orderFlowService;
    private final CurrentUserService currentUserService;
    private final FileStorageService fileStorageService;

    public MarketplaceApiController(MarketplaceItemRepository marketplaceItemRepository,
                                    MarketplaceOrderRepository marketplaceOrderRepository,
                                    UserRepository userRepository,
                                    NotificationDispatchService notifier,
                                    MarketplaceOrderFlowService orderFlowService,
                                    CurrentUserService currentUserService,
                                    FileStorageService fileStorageService) {
        this.marketplaceItemRepository = marketplaceItemRepository;
        this.marketplaceOrderRepository = marketplaceOrderRepository;
        this.userRepository = userRepository;
        this.notifier = notifier;
        this.orderFlowService = orderFlowService;
        this.currentUserService = currentUserService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/items")
    public ResponseEntity<?> listListedItems() {
        List<MarketplaceItem> items = marketplaceItemRepository.findByListedTrueOrderByCreatedAtDesc();
        return ResponseEntity.ok(items.stream().map(this::toVm).toList());
    }

    @GetMapping("/payment-details")
    public ResponseEntity<?> paymentDetails() {
        return ResponseEntity.ok(Map.of(
                "accountName", "Deallock",
                "bankName", "Fidelity Bank",
                "accountNumber", "5601682913",
                "supportPhone", "+234 703 103 1944",
                "supportEmail", "info@deallock.ng"
        ));
    }

    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(@RequestBody Map<String, Object> body, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }
        var userOpt = currentUserService.resolve(principal);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login required"));
        }

        List<Map<String, Object>> items = extractItems(body);
        if (items.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Cart is empty"));
        }

        String deliveryMethod = orderFlowService.normalizeDeliveryMethod(asString(body.get("deliveryMethod")));
        String paymentMethod = orderFlowService.normalizePaymentMethod(asString(body.get("paymentMethod")));
        String deliveryAddress = asString(body.get("deliveryAddress"));
        if ("door".equals(deliveryMethod) && (deliveryAddress == null || deliveryAddress.isBlank())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Delivery address is required for door delivery"));
        }

        MarketplaceOrder order = new MarketplaceOrder();
        order.setUser(userOpt.get());
        order.setStatus(MarketplaceOrderFlowService.STATUS_PENDING_PAYMENT);
        order.setPaymentMethod(paymentMethod);
        order.setDeliveryMethod(deliveryMethod);
        order.setDeliveryAddress("pickup".equals(deliveryMethod) ? "IN-STORE PICKUP" : deliveryAddress.trim());
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());

        BigDecimal subtotal = BigDecimal.ZERO;
        List<MarketplaceOrderItem> orderItems = new ArrayList<>();
        for (Map<String, Object> itemRow : items) {
            Long itemId = asLong(itemRow.get("id"));
            int quantity = Math.max(1, asInt(itemRow.get("quantity"), 1));
            if (itemId == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid cart item"));
            }

            var itemOpt = marketplaceItemRepository.findById(itemId);
            if (itemOpt.isEmpty() || !itemOpt.get().isListed()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Some items are no longer available"));
            }
            MarketplaceItem item = itemOpt.get();
            BigDecimal unitPrice = item.getPrice() == null ? BigDecimal.ZERO : item.getPrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineTotal);

            MarketplaceOrderItem orderItem = new MarketplaceOrderItem();
            orderItem.setOrder(order);
            orderItem.setMarketplaceItem(item);
            orderItem.setQuantity(quantity);
            orderItem.setUnitPrice(unitPrice);
            orderItem.setLineTotal(lineTotal);
            orderItems.add(orderItem);
        }
        if (orderItems.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Cart is empty"));
        }

        BigDecimal deliveryFee = "door".equals(deliveryMethod) ? DOOR_DELIVERY_FEE : BigDecimal.ZERO;
        BigDecimal total = subtotal.add(deliveryFee).setScale(2, RoundingMode.HALF_UP);
        order.setSubtotalAmount(subtotal.setScale(2, RoundingMode.HALF_UP));
        order.setDeliveryFeeAmount(deliveryFee.setScale(2, RoundingMode.HALF_UP));
        order.setTotalAmount(total);
        order.setItems(orderItems);
        marketplaceOrderRepository.save(order);

        String statusText = "New marketplace order " + order.getId() + " from " + (userOpt.get().getEmail() == null ? "user" : userOpt.get().getEmail());
        notifier.notifyAdmins(
                statusText,
                "New Marketplace Order",
                statusText,
                statusText
        );
        notifier.notifyUser(
                userOpt.get(),
                "Order received. Please make payment and wait for admin confirmation.",
                "Marketplace Order Received",
                "Order MO-" + order.getId() + " received. We will confirm your payment and update your shipment status.",
                "Order MO-" + order.getId() + " received. Awaiting payment."
        );

        return ResponseEntity.ok(Map.of(
                "message", "Checkout successful",
                "order", orderFlowService.toVm(order),
                "paymentDetails", Map.of(
                        "accountName", "Deallock",
                        "bankName", "Fidelity Bank",
                        "accountNumber", "5601682913"
                )
        ));
    }

    @GetMapping("/orders")
    public ResponseEntity<?> myOrders(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var userOpt = currentUserService.resolve(principal);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var rows = marketplaceOrderRepository.findByUserOrderByCreatedAtDesc(userOpt.get()).stream()
                .map(orderFlowService::toVm)
                .toList();
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<?> orderDetails(@PathVariable("id") Long id, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var userOpt = currentUserService.resolve(principal);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var orderOpt = marketplaceOrderRepository.findById(id);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        var order = orderOpt.get();
        if (order.getUser() == null || order.getUser().getId() != userOpt.get().getId()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(orderFlowService.toVm(order));
    }

    @PostMapping(value = "/orders/{id}/payment-proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadPaymentProof(@PathVariable("id") Long id,
                                                @RequestParam("paymentProof") MultipartFile paymentProof,
                                                @RequestParam(value = "note", required = false) String note,
                                                Principal principal) throws Exception {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var userOpt = currentUserService.resolve(principal);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var orderOpt = marketplaceOrderRepository.findById(id);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        var order = orderOpt.get();
        if (order.getUser() == null || order.getUser().getId() != userOpt.get().getId()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (paymentProof == null || paymentProof.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Payment proof file is required"));
        }
        if (paymentProof.getSize() > MAX_UPLOAD_BYTES) {
            return ResponseEntity.badRequest().body(Map.of("message", "Payment proof must be at most 2MB"));
        }

        try {
            FileStorageService.StoredFile stored = fileStorageService.save(
                    "marketplace/order-proofs",
                    paymentProof,
                    MAX_UPLOAD_BYTES,
                    PROOF_TYPES
            );
            order.setPaymentProof(null);
            order.setPaymentProofContentType(stored.contentType());
            order.setPaymentProofKey(stored.key());
        } catch (IOException ex) {
            // Fallback to DB blob if filesystem storage isn't available.
            order.setPaymentProof(paymentProof.getBytes());
            order.setPaymentProofContentType(paymentProof.getContentType());
            order.setPaymentProofKey(null);
        }
        order.setPaymentProofNote(note == null ? null : note.trim());
        order.setPaymentSubmittedAt(Instant.now());
        // Force "payment submitted" as the source of truth when proof is uploaded.
        // This guarantees admins can immediately see and act on submitted payments.
        order.setStatus(MarketplaceOrderFlowService.STATUS_PAYMENT_SUBMITTED);
        order.setUpdatedAt(Instant.now());

        marketplaceOrderRepository.save(order);

        String code = "MO-" + order.getId();
        String userLabel = (userOpt.get().getFullName() != null && !userOpt.get().getFullName().isBlank())
                ? userOpt.get().getFullName()
                : userOpt.get().getEmail();
        try {
            notifier.notifyAdmins(
                    "Payment proof uploaded for " + code + " by " + userLabel,
                    "Marketplace Payment Proof Uploaded",
                    "Payment proof uploaded for " + code + " by " + userLabel + ". Please review in admin dashboard.",
                    "Payment proof uploaded for " + code + ". Please review."
            );
        } catch (Exception ignored) {
        }
        try {
            notifier.notifyUser(
                    userOpt.get(),
                    "Payment proof received for " + code + ". Awaiting admin review.",
                    "Payment Proof Received",
                    "We have received your payment proof for " + code + ". Payment will be confirmed within 60 seconds to 24 hours.",
                    "Payment proof received for " + code + ". Awaiting review."
            );
        } catch (Exception ignored) {
        }

        return ResponseEntity.ok(Map.of(
                "message", "Payment proof uploaded. Payment will be confirmed within 60 seconds to 24 hours.",
                "order", orderFlowService.toVm(order)
        ));
    }

    @GetMapping("/orders/{id}/payment-proof")
    public ResponseEntity<byte[]> myPaymentProof(@PathVariable("id") Long id, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var userOpt = currentUserService.resolve(principal);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var orderOpt = marketplaceOrderRepository.findById(id);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        var order = orderOpt.get();
        if (order.getUser() == null || order.getUser().getId() != userOpt.get().getId()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        if (order.getPaymentProofContentType() != null && !order.getPaymentProofContentType().isBlank()) {
            type = MediaType.parseMediaType(order.getPaymentProofContentType());
        }
        byte[] bytes = resolveBytes(order.getPaymentProof(), order.getPaymentProofKey());
        if (bytes == null || bytes.length == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok().contentType(type).body(bytes);
    }

    @GetMapping("/items/{id}/photo")
    public ResponseEntity<byte[]> itemPhoto(@PathVariable("id") Long id) {
        return itemPhotoSlot(id, 1);
    }

    @GetMapping("/items/{id}/photo/{slot}")
    public ResponseEntity<byte[]> itemPhotoSlot(@PathVariable("id") Long id,
                                                @PathVariable("slot") int slot) {
        var opt = marketplaceItemRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        var item = opt.get();

        byte[] bytes;
        String contentType;
        String key;
        if (slot == 2) {
            bytes = item.getPhoto2();
            contentType = item.getPhoto2ContentType();
            key = item.getPhoto2Key();
        } else if (slot == 3) {
            bytes = item.getPhoto3();
            contentType = item.getPhoto3ContentType();
            key = item.getPhoto3Key();
        } else {
            bytes = item.getPhoto();
            contentType = item.getPhotoContentType();
            key = item.getPhotoKey();
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

    private Map<String, Object> toVm(MarketplaceItem item) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", item.getId());
        row.put("name", item.getName());
        row.put("description", item.getDescription());
        row.put("price", item.getPrice());
        row.put("oldPrice", item.getOldPrice());
        row.put("size", item.getSize());
        row.put("listed", item.isListed());
        row.put("createdAt", item.getCreatedAt());
        String base = "/api/marketplace/items/" + item.getId() + "/photo";
        String img1 = hasMedia(item.getPhoto(), item.getPhotoKey()) ? base : null;
        String img2 = hasMedia(item.getPhoto2(), item.getPhoto2Key()) ? (base + "/2") : null;
        String img3 = hasMedia(item.getPhoto3(), item.getPhoto3Key()) ? (base + "/3") : null;
        row.put("imageUrl", img1);
        List<String> imageUrls = new ArrayList<>();
        if (img1 != null && !img1.isBlank()) imageUrls.add(img1);
        if (img2 != null && !img2.isBlank()) imageUrls.add(img2);
        if (img3 != null && !img3.isBlank()) imageUrls.add(img3);
        row.put("imageUrls", imageUrls);
        return row;
    }

    private boolean hasMedia(byte[] blob, String key) {
        return (blob != null && blob.length > 0) || (key != null && !key.isBlank());
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractItems(Map<String, Object> body) {
        if (body == null) return List.of();
        Object raw = body.get("items");
        if (!(raw instanceof List<?> rawList)) return List.of();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object row : rawList) {
            if (row instanceof Map<?, ?> m) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", m.get("id"));
                item.put("quantity", m.get("quantity"));
                items.add(item);
            }
        }
        return items;
    }

    private Long asLong(Object value) {
        if (value == null) return null;
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private int asInt(Object value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
