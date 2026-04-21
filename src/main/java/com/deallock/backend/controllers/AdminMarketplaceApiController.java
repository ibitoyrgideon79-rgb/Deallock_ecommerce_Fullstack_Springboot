package com.deallock.backend.controllers;

import com.deallock.backend.entities.MarketplaceItem;
import com.deallock.backend.repositories.MarketplaceItemRepository;
import java.math.BigDecimal;
import java.time.Instant;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/marketplace/items")
public class AdminMarketplaceApiController {

    private static final long MAX_UPLOAD_BYTES = 2L * 1024L * 1024L;

    private final MarketplaceItemRepository marketplaceItemRepository;

    public AdminMarketplaceApiController(MarketplaceItemRepository marketplaceItemRepository) {
        this.marketplaceItemRepository = marketplaceItemRepository;
    }

    @GetMapping
    public ResponseEntity<?> listAll(Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<MarketplaceItem> items = marketplaceItemRepository.findAllByOrderByCreatedAtDesc();
        return ResponseEntity.ok(items.stream().map(this::toVm).toList());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> create(@RequestParam("name") String name,
                                    @RequestParam("price") BigDecimal price,
                                    @RequestParam(value = "oldPrice", required = false) BigDecimal oldPrice,
                                    @RequestParam(value = "description", required = false) String description,
                                    @RequestParam(value = "size", required = false) String size,
                                    @RequestParam(value = "listed", required = false) Boolean listed,
                                    @RequestParam(value = "photo", required = false) MultipartFile photo,
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

        if (photo != null && !photo.isEmpty()) {
            if (photo.getSize() > MAX_UPLOAD_BYTES) {
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                        .body(Map.of("message", "Photo too large (max 2MB)"));
            }
            item.setPhoto(photo.getBytes());
            item.setPhotoContentType(photo.getContentType());
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
        row.put("imageUrl", item.getPhoto() == null || item.getPhoto().length == 0 ? null : ("/api/marketplace/items/" + item.getId() + "/photo"));
        return row;
    }
}

