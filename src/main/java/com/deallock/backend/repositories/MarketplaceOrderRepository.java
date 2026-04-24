package com.deallock.backend.repositories;

import com.deallock.backend.entities.MarketplaceOrder;
import com.deallock.backend.entities.User;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceOrderRepository extends JpaRepository<MarketplaceOrder, Long> {

    @EntityGraph(attributePaths = {"items", "items.marketplaceItem", "user"})
    List<MarketplaceOrder> findByUserOrderByCreatedAtDesc(User user);

    @Override
    @EntityGraph(attributePaths = {"items", "items.marketplaceItem", "user"})
    List<MarketplaceOrder> findAll();
}
