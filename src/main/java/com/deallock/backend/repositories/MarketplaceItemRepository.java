package com.deallock.backend.repositories;

import com.deallock.backend.entities.MarketplaceItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceItemRepository extends JpaRepository<MarketplaceItem, Long> {

    List<MarketplaceItem> findByListedTrueOrderByCreatedAtDesc();

    List<MarketplaceItem> findAllByOrderByCreatedAtDesc();

    Optional<MarketplaceItem> findBySourceDealId(Long sourceDealId);

    boolean existsBySourceDealId(Long sourceDealId);
}

