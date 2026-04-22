package com.deallock.backend.repositories;

import com.deallock.backend.entities.DealWaitlistEntry;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealWaitlistRepository extends JpaRepository<DealWaitlistEntry, Long> {
    boolean existsByDealIdAndUserId(Long dealId, int userId);
    long countByDealId(Long dealId);
    Optional<DealWaitlistEntry> findFirstByDealIdAndUserId(Long dealId, int userId);
}

