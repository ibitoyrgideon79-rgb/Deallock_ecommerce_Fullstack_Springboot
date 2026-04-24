package com.deallock.backend.repositories;

import com.deallock.backend.entities.Deal;
import com.deallock.backend.entities.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DealRepository extends JpaRepository<Deal, Long> {

    @Modifying
@Query("UPDATE Deal d SET d.status = :status, d.rejectionReason = :reason WHERE d.id = :id")
int updateStatusAndReason(@Param("id") Long id, 
                          @Param("status") String status, 
                          @Param("reason") String reason);

@Query("SELECT d.id, d.title, d.user.id FROM Deal d WHERE d.id = :id")
Optional<Object[]> findLightweightById(@Param("id") Long id);
    List<Deal> findByUserOrderByCreatedAtDesc(User user);
    List<Deal> findAllByOrderByCreatedAtDesc();
    List<Deal> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant start, Instant end);
    List<Deal> findByPaymentProofIsNotNullOrderByPaymentProofUploadedAtDesc();
    List<Deal> findBySecuredTrueAndDeliveryInitiatedAtIsNullAndDeliveryConfirmedAtIsNullOrderBySecuredAtDesc();
    List<Deal> findByStatusIgnoreCaseAndPaymentStatusIgnoreCase(String status, String paymentStatus);
}
