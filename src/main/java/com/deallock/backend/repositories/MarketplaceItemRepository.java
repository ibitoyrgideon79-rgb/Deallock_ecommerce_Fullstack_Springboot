package com.deallock.backend.repositories;

import com.deallock.backend.entities.MarketplaceItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketplaceItemRepository extends JpaRepository<MarketplaceItem, Long> {

    List<MarketplaceItem> findByListedTrueOrderByCreatedAtDesc();

    List<MarketplaceItem> findAllByOrderByCreatedAtDesc();

    Optional<MarketplaceItem> findBySourceDealId(Long sourceDealId);

    boolean existsBySourceDealId(Long sourceDealId);

    @Modifying
    @Query("update MarketplaceItem m set m.listed = :listed where m.id = :id")
    int updateListed(@Param("id") Long id, @Param("listed") boolean listed);
}
