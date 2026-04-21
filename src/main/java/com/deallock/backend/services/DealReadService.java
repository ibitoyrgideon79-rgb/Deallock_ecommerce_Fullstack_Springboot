package com.deallock.backend.services;

import com.deallock.backend.repositories.DealRepository;
import com.deallock.backend.repositories.UserRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class DealReadService {

    private final DealRepository dealRepository;
    private final UserRepository userRepository;

    public DealReadService(DealRepository dealRepository,
                           UserRepository userRepository) {
        this.dealRepository = dealRepository;
        this.userRepository = userRepository;
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
        return dealRepository.findAllByOrderByCreatedAtDesc().stream()
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
                    return row;
                })
                .collect(Collectors.toList());
    }
}

