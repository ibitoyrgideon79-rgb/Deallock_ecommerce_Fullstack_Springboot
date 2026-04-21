package com.deallock.backend.services;

import com.deallock.backend.repositories.MarketplaceItemRepository;
import com.deallock.backend.repositories.DealRepository;
import com.deallock.backend.repositories.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class DealReadService {

    private final DealRepository dealRepository;
    private final UserRepository userRepository;
    private final MarketplaceItemRepository marketplaceItemRepository;

    @Value("${app.deals.payment-timeout:24h}")
    private Duration paymentTimeout;

    public DealReadService(DealRepository dealRepository,
                           UserRepository userRepository,
                           MarketplaceItemRepository marketplaceItemRepository) {
        this.dealRepository = dealRepository;
        this.userRepository = userRepository;
        this.marketplaceItemRepository = marketplaceItemRepository;
    }

    @Cacheable(cacheNames = "userDeals", key = "#email")
    public List<Map<String, Object>> listDealsForUserEmail(String email) {
        var userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return List.of();
        }

        return dealRepository.findByUserOrderByCreatedAtDesc(userOpt.get()).stream()
                .map(d -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", d.getId());
                    row.put("title", d.getTitle() == null ? "Untitled Deal" : d.getTitle());
                    row.put("status", d.getStatus() == null ? "Pending Approval" : d.getStatus());
                    row.put("value", d.getValue() == null ? 0 : d.getValue());
                    row.put("paymentStatus", d.getPaymentStatus() == null ? "NOT_PAID" : d.getPaymentStatus());
                    row.put("rejectionReason", d.getRejectionReason());
                    row.put("secured", d.isSecured());
                    row.put("balancePaymentStatus", d.getBalancePaymentStatus() == null ? "NOT_PAID" : d.getBalancePaymentStatus());
                    row.put("deliveryInitiatedAt", d.getDeliveryInitiatedAt());
                    row.put("deliveryConfirmedByUser", d.isDeliveryConfirmedByUser());
                    row.put("deliveryConfirmedAt", d.getDeliveryConfirmedAt());
                    row.put("feedback", d.getFeedback());
                    row.put("createdAt", d.getCreatedAt());
                    return row;
                })
                .collect(Collectors.toList());
    }

    @Cacheable(cacheNames = "adminDeals", key = "'all'")
    public List<Map<String, Object>> listAllDealsForAdmin() {
        var deals = dealRepository.findAllByOrderByCreatedAtDesc();

        // Avoid N+1 queries: load all marketplace "sourceDealId" mappings once.
        Set<Long> listedDealIds = marketplaceItemRepository.findAll().stream()
                .map(i -> i.getSourceDealId())
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        Instant now = Instant.now();
        return deals.stream()
                .map(d -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", d.getId());
                    row.put("title", d.getTitle() == null ? "Untitled Deal" : d.getTitle());
                    row.put("status", d.getStatus() == null ? "Pending Approval" : d.getStatus());
                    row.put("value", d.getValue() == null ? 0 : d.getValue());
                    row.put("paymentStatus", d.getPaymentStatus() == null ? "NOT_PAID" : d.getPaymentStatus());
                    row.put("secured", d.isSecured());
                    row.put("balancePaymentStatus", d.getBalancePaymentStatus() == null ? "NOT_PAID" : d.getBalancePaymentStatus());
                    row.put("deliveryInitiatedAt", d.getDeliveryInitiatedAt());
                    row.put("deliveryConfirmedByUser", d.isDeliveryConfirmedByUser());
                    row.put("deliveryConfirmedAt", d.getDeliveryConfirmedAt());
                    row.put("createdAt", d.getCreatedAt());
                    row.put("userEmail", d.getUser() == null ? null : d.getUser().getEmail());
                    row.put("rejectionReason", d.getRejectionReason());

                    boolean allowListing = d.getAllowMarketplaceListing() == null || d.getAllowMarketplaceListing();
                    row.put("allowMarketplaceListing", allowListing);

                    boolean expiredUnpaid = isExpiredUnpaidApprovedDeal(d, now);
                    row.put("expiredUnpaid", expiredUnpaid);
                    row.put("marketplaceListed", d.getId() != null && listedDealIds.contains(d.getId()));
                    return row;
                })
                .collect(Collectors.toList());
    }

    private boolean isExpiredUnpaidApprovedDeal(com.deallock.backend.entities.Deal d, Instant now) {
        if (d == null) return false;
        if (d.getCreatedAt() == null) return false;
        if (d.getStatus() == null || !"Approved".equalsIgnoreCase(d.getStatus())) return false;
        String pay = d.getPaymentStatus() == null ? "NOT_PAID" : d.getPaymentStatus();
        if (!"NOT_PAID".equalsIgnoreCase(pay)) return false;
        if (paymentTimeout == null) return false;
        return d.getCreatedAt().isBefore(now.minus(paymentTimeout));
    }
}
