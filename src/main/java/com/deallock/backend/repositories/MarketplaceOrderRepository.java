package com.deallock.backend.repositories;

import com.deallock.backend.entities.MarketplaceOrder;
import com.deallock.backend.entities.User;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface MarketplaceOrderRepository extends JpaRepository<MarketplaceOrder, Long> {

    @EntityGraph(attributePaths = {"items", "items.marketplaceItem", "user"})
    List<MarketplaceOrder> findByUserOrderByCreatedAtDesc(User user);

    @Override
    @EntityGraph(attributePaths = {"items", "items.marketplaceItem", "user"})
    List<MarketplaceOrder> findAll();

    @Modifying
    @Query("""
            update MarketplaceOrder o
               set o.status = :status,
                   o.adminNote = :adminNote,
                   o.paymentReceivedAt = :paymentReceivedAt,
                   o.shippedAt = :shippedAt,
                   o.deliveredAt = :deliveredAt,
                   o.updatedAt = :updatedAt
             where o.id = :id
            """)
    int updateWorkflowFields(@Param("id") Long id,
                             @Param("status") String status,
                             @Param("adminNote") String adminNote,
                             @Param("paymentReceivedAt") java.time.Instant paymentReceivedAt,
                             @Param("shippedAt") java.time.Instant shippedAt,
                             @Param("deliveredAt") java.time.Instant deliveredAt,
                             @Param("updatedAt") java.time.Instant updatedAt);
}
