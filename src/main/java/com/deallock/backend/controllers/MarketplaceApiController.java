package com.deallock.backend.controllers;

import com.deallock.backend.entities.MarketplaceItem;
import com.deallock.backend.repositories.MarketplaceItemRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/marketplace")
public class MarketplaceApiController {

    private final MarketplaceItemRepository marketplaceItemRepository;

    public MarketplaceApiController(MarketplaceItemRepository marketplaceItemRepository) {
        this.marketplaceItemRepository = marketplaceItemRepository;
    }

    @GetMapping("/items")
    public ResponseEntity<?> listListedItems() {
        List<MarketplaceItem> items = marketplaceItemRepository.findByListedTrueOrderByCreatedAtDesc();
        return ResponseEntity.ok(items.stream().map(this::toVm).toList());
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
        if (slot == 2) {
            bytes = item.getPhoto2();
            contentType = item.getPhoto2ContentType();
        } else if (slot == 3) {
            bytes = item.getPhoto3();
            contentType = item.getPhoto3ContentType();
        } else {
            bytes = item.getPhoto();
            contentType = item.getPhotoContentType();
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
        row.put("imageUrls", List.of(img1, img2, img3).stream().filter(u -> u != null && !u.isBlank()).toList());
        return row;
    }
}
