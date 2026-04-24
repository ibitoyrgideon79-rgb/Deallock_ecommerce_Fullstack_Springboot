package com.deallock.backend.controllers;

import com.deallock.backend.entities.MarketplaceItem;
import com.deallock.backend.entities.MarketplaceOrder;
import com.deallock.backend.repositories.MarketplaceOrderRepository;
import com.deallock.backend.repositories.MarketplaceItemRepository;
import com.deallock.backend.services.MarketplaceOrderFlowService;
import com.deallock.backend.services.NotificationDispatchService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/marketplace/items")
public class AdminMarketplaceApiController {

    private static final long MAX_UPLOAD_BYTES = 2L * 1024L * 1024L;

    private final MarketplaceItemRepository marketplaceItemRepository;
    private final MarketplaceOrderRepository marketplaceOrderRepository;
    private final MarketplaceOrderFlowService orderFlowService;
    private final NotificationDispatchService notifier;

    public AdminMarketplaceApiController(MarketplaceItemRepository marketplaceItemRepository,
                                         MarketplaceOrderRepository marketplaceOrderRepository,
                                         MarketplaceOrderFlowService orderFlowService,
                                         NotificationDispatchService notifier) {
        this.marketplaceItemRepository = marketplaceItemRepository;
        this.marketplaceOrderRepository = marketplaceOrderRepository;
        this.orderFlowService = orderFlowService;
        this.notifier = notifier;
    }

    @GetMapping
    public ResponseEntity<?> listAll(Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<MarketplaceItem> items = marketplaceItemRepository.findAllByOrderByCreatedAtDesc();
        return ResponseEntity.ok(items.stream().map(this::toVm).toList());
    }

    @GetMapping("/orders")
    public ResponseEntity<?> listOrders(Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        var rows = marketplaceOrderRepository.findAll().stream()
                .sorted((a, b) -> {
                    Instant ai = a.getCreatedAt() == null ? Instant.EPOCH : a.getCreatedAt();
                    Instant bi = b.getCreatedAt() == null ? Instant.EPOCH : b.getCreatedAt();
                    return bi.compareTo(ai);
                })
                .map(orderFlowService::toVm)
                .toList();
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/orders/{id}/status")
    public ResponseEntity<?> updateOrderStatus(@PathVariable("id") Long id,
                                               @RequestBody(required = false) Map<String, Object> body,
                                               Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        MarketplaceOrder order = marketplaceOrderRepository.findById(id).orElse(null);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        String nextStatus = body == null || body.get("status") == null ? "" : String.valueOf(body.get("status"));
        String normalizedNext = orderFlowService.normalizeStatus(nextStatus);
        String current = orderFlowService.normalizeStatus(order.getStatus());
        if (!orderFlowService.canTransition(current, normalizedNext)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Invalid status transition",
                    "currentStatus", current,
                    "requestedStatus", normalizedNext
            ));
        }

        String note = body == null || body.get("note") == null ? null : String.valueOf(body.get("note")).trim();
        if (note != null && note.length() > 2000) {
            note = note.substring(0, 2000);
        }
        if (note != null && !note.isBlank()) {
            order.setAdminNote(note);
        }

        orderFlowService.applyTransition(order, normalizedNext);
        marketplaceOrderRepository.save(order);

        String orderCode = "MO-" + order.getId();
        String statusText = humanStatus(normalizedNext);
        String noteText = order.getAdminNote() == null || order.getAdminNote().isBlank() ? "" : ("\nNote: " + order.getAdminNote());
        notifier.notifyUser(
                order.getUser(),
                "Order " + orderCode + " updated: " + statusText,
                "Marketplace Order Update",
                "Your order " + orderCode + " status is now " + statusText + "." + noteText,
                "Order " + orderCode + ": " + statusText
        );

        return ResponseEntity.ok(Map.of(
                "message", "Order status updated",
                "order", orderFlowService.toVm(order)
        ));
    }

    @GetMapping("/orders/{id}/payment-proof")
    public ResponseEntity<byte[]> paymentProof(@PathVariable("id") Long id, Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        MarketplaceOrder order = marketplaceOrderRepository.findById(id).orElse(null);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (order.getPaymentProof() == null || order.getPaymentProof().length == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        if (order.getPaymentProofContentType() != null && !order.getPaymentProofContentType().isBlank()) {
            type = MediaType.parseMediaType(order.getPaymentProofContentType());
        }

        return ResponseEntity.ok().contentType(type).body(order.getPaymentProof());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> create(@RequestParam("name") String name,
                                    @RequestParam("price") BigDecimal price,
                                    @RequestParam(value = "oldPrice", required = false) BigDecimal oldPrice,
                                    @RequestParam(value = "description", required = false) String description,
                                    @RequestParam(value = "size", required = false) String size,
                                    @RequestParam(value = "listed", required = false) Boolean listed,
                                    // Backwards compatible: older UI sends a single `photo`.
                                    @RequestParam(value = "photo", required = false) MultipartFile photo,
                                    // New UI can send up to 3 files under the same field name.
                                    @RequestParam(value = "photos", required = false) MultipartFile[] photos,
                                    Authentication authentication) throws Exception {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Name is required"));
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Valid price is required"));
        }

        MarketplaceItem item = new MarketplaceItem();
        item.setName(normalizedName);
        item.setPrice(price);
        item.setOldPrice(oldPrice);
        item.setDescription(description == null ? null : description.trim());
        item.setSize(normalizeSize(size));
        item.setListed(listed != null && listed);
        item.setCreatedAt(Instant.now());

        MultipartFile[] incoming = (photos != null && photos.length > 0) ? photos : null;
        if (incoming == null && photo != null && !photo.isEmpty()) {
            incoming = new MultipartFile[]{photo};
        }

        if (incoming != null) {
            int saved = 0;
            for (MultipartFile file : incoming) {
                if (file == null || file.isEmpty()) continue;
                if (file.getSize() > MAX_UPLOAD_BYTES) {
                    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                            .body(Map.of("message", "Each photo must be at most 2MB."));
                }

                saved++;
                if (saved == 1) {
                    item.setPhoto(file.getBytes());
                    item.setPhotoContentType(file.getContentType());
                } else if (saved == 2) {
                    item.setPhoto2(file.getBytes());
                    item.setPhoto2ContentType(file.getContentType());
                } else if (saved == 3) {
                    item.setPhoto3(file.getBytes());
                    item.setPhoto3ContentType(file.getContentType());
                    break;
                }
            }
        }

        marketplaceItemRepository.save(item);
        return ResponseEntity.ok(toVm(item));
    }

    @PostMapping("/{id}/toggle-listed")
    public ResponseEntity<?> toggleListed(@PathVariable("id") Long id, Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        var opt = marketplaceItemRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        var item = opt.get();
        item.setListed(!item.isListed());
        marketplaceItemRepository.save(item);
        return ResponseEntity.ok(Map.of("listed", item.isListed()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") Long id, Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        var opt = marketplaceItemRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        marketplaceItemRepository.delete(opt.get());
        return ResponseEntity.ok(Map.of("message", "deleted"));
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private String normalizeSize(String raw) {
        String v = raw == null ? "" : raw.trim().toLowerCase();
        return switch (v) {
            case "small", "s" -> "small";
            case "medium", "m" -> "medium";
            case "big", "large", "l" -> "big";
            default -> "small";
        };
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
        String img1 = (item.getPhoto() == null || item.getPhoto().length == 0) ? null : base;
        String img2 = (item.getPhoto2() == null || item.getPhoto2().length == 0) ? null : (base + "/2");
        String img3 = (item.getPhoto3() == null || item.getPhoto3().length == 0) ? null : (base + "/3");
        row.put("imageUrl", img1);
        List<String> imageUrls = new ArrayList<>();
        if (img1 != null && !img1.isBlank()) imageUrls.add(img1);
        if (img2 != null && !img2.isBlank()) imageUrls.add(img2);
        if (img3 != null && !img3.isBlank()) imageUrls.add(img3);
        row.put("imageUrls", imageUrls);
        return row;
    }

    private String humanStatus(String status) {
        return switch (status) {
            case MarketplaceOrderFlowService.STATUS_PENDING_PAYMENT -> "Pending Payment";
            case MarketplaceOrderFlowService.STATUS_PAYMENT_SUBMITTED -> "Payment Submitted";
            case MarketplaceOrderFlowService.STATUS_PAYMENT_NOT_RECEIVED -> "Payment Not Received";
            case MarketplaceOrderFlowService.STATUS_PAYMENT_RECEIVED -> "Payment Received";
            case MarketplaceOrderFlowService.STATUS_PROCESSING -> "Processing";
            case MarketplaceOrderFlowService.STATUS_SHIPPED -> "Shipped";
            case MarketplaceOrderFlowService.STATUS_DELIVERED -> "Delivered";
            case MarketplaceOrderFlowService.STATUS_REVIEW -> "Review";
            default -> status;
        };
    }
}
