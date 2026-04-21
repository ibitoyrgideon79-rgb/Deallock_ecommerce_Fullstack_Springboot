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
        var opt = marketplaceItemRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        var item = opt.get();
        if (item.getPhoto() == null || item.getPhoto().length == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        if (item.getPhotoContentType() != null && !item.getPhotoContentType().isBlank()) {
            type = MediaType.parseMediaType(item.getPhotoContentType());
        }
        return ResponseEntity.ok().contentType(type).body(item.getPhoto());
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

