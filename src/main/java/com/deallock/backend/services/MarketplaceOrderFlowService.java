package com.deallock.backend.services;

import com.deallock.backend.entities.MarketplaceOrder;
import com.deallock.backend.entities.MarketplaceOrderItem;
import com.deallock.backend.entities.User;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MarketplaceOrderFlowService {

    public static final String STATUS_PENDING_PAYMENT = "PENDING_PAYMENT";
    public static final String STATUS_PAYMENT_SUBMITTED = "PAYMENT_SUBMITTED";
    public static final String STATUS_PAYMENT_NOT_RECEIVED = "PAYMENT_NOT_RECEIVED";
    public static final String STATUS_PAYMENT_RECEIVED = "PAYMENT_RECEIVED";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_SHIPPED = "SHIPPED";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_REVIEW = "REVIEW";

    public boolean canTransition(String currentStatus, String nextStatus) {
        String current = normalizeStatus(currentStatus);
        String next = normalizeStatus(nextStatus);
        if (current.equals(next)) {
            return true;
        }
        return switch (current) {
            case STATUS_PENDING_PAYMENT -> STATUS_PAYMENT_SUBMITTED.equals(next);
            case STATUS_PAYMENT_SUBMITTED -> STATUS_PAYMENT_RECEIVED.equals(next) || STATUS_PAYMENT_NOT_RECEIVED.equals(next);
            case STATUS_PAYMENT_NOT_RECEIVED -> STATUS_PAYMENT_SUBMITTED.equals(next) || STATUS_PAYMENT_RECEIVED.equals(next);
            case STATUS_PAYMENT_RECEIVED -> STATUS_PROCESSING.equals(next) || STATUS_PAYMENT_NOT_RECEIVED.equals(next);
            case STATUS_PROCESSING -> STATUS_SHIPPED.equals(next);
            case STATUS_SHIPPED -> STATUS_DELIVERED.equals(next);
            case STATUS_DELIVERED -> STATUS_REVIEW.equals(next);
            default -> false;
        };
    }

    public void applyTransition(MarketplaceOrder order, String nextStatus) {
        String next = normalizeStatus(nextStatus);
        order.setStatus(next);
        order.setUpdatedAt(Instant.now());
        if (STATUS_PAYMENT_RECEIVED.equals(next) && order.getPaymentReceivedAt() == null) {
            order.setPaymentReceivedAt(Instant.now());
        }
        if (STATUS_SHIPPED.equals(next) && order.getShippedAt() == null) {
            order.setShippedAt(Instant.now());
        }
        if (STATUS_DELIVERED.equals(next) && order.getDeliveredAt() == null) {
            order.setDeliveredAt(Instant.now());
        }
    }

    public String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return STATUS_PENDING_PAYMENT;
        }
        return status.trim().toUpperCase();
    }

    public String normalizePaymentMethod(String paymentMethod) {
        String method = paymentMethod == null ? "" : paymentMethod.trim().toUpperCase();
        if (method.isBlank()) {
            return "BANK_TRANSFER";
        }
        if (method.equals("BANK_TRANSFER") || method.equals("USSD_TRANSFER") || method.equals("MOBILE_BANKING")) {
            return method;
        }
        return "BANK_TRANSFER";
    }

    public String normalizeDeliveryMethod(String deliveryMethod) {
        String method = deliveryMethod == null ? "" : deliveryMethod.trim().toLowerCase();
        return "pickup".equals(method) ? "pickup" : "door";
    }

    public Map<String, Object> toVm(MarketplaceOrder order) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", order.getId());
        row.put("orderCode", "MO-" + order.getId());
        row.put("status", normalizeStatus(order.getStatus()));
        row.put("paymentMethod", order.getPaymentMethod());
        row.put("deliveryMethod", order.getDeliveryMethod());
        row.put("deliveryAddress", order.getDeliveryAddress());
        row.put("subtotalAmount", order.getSubtotalAmount());
        row.put("deliveryFeeAmount", order.getDeliveryFeeAmount());
        row.put("totalAmount", order.getTotalAmount());
        row.put("adminNote", order.getAdminNote());
        row.put("paymentReceivedAt", order.getPaymentReceivedAt());
        row.put("paymentSubmittedAt", order.getPaymentSubmittedAt());
        row.put("shippedAt", order.getShippedAt());
        row.put("deliveredAt", order.getDeliveredAt());
        row.put("paymentProofUploaded", order.getPaymentProof() != null && order.getPaymentProof().length > 0);
        row.put("paymentProofNote", order.getPaymentProofNote());
        row.put("createdAt", order.getCreatedAt());
        row.put("updatedAt", order.getUpdatedAt());

        User user = order.getUser();
        row.put("buyerName", user != null && user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : (user != null ? user.getEmail() : ""));
        row.put("buyerEmail", user != null ? user.getEmail() : "");
        row.put("buyerPhone", user != null ? user.getPhone() : "");

        List<Map<String, Object>> itemRows = order.getItems().stream().map(this::toVm).toList();
        row.put("items", itemRows);
        String itemNames = itemRows.stream()
                .map(it -> String.valueOf(it.get("name")))
                .reduce((a, b) -> a + ", " + b)
                .orElse("Order");
        row.put("summaryName", itemNames);
        row.put("paymentProofUrl", "/api/marketplace/orders/" + order.getId() + "/payment-proof");
        return row;
    }

    private Map<String, Object> toVm(MarketplaceOrderItem item) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", item.getId());
        row.put("marketplaceItemId", item.getMarketplaceItem() != null ? item.getMarketplaceItem().getId() : null);
        row.put("name", item.getMarketplaceItem() != null ? item.getMarketplaceItem().getName() : "Item");
        row.put("quantity", item.getQuantity());
        row.put("unitPrice", item.getUnitPrice());
        row.put("lineTotal", item.getLineTotal());
        row.put("imageUrl", item.getMarketplaceItem() != null && item.getMarketplaceItem().getPhoto() != null && item.getMarketplaceItem().getPhoto().length > 0
                ? "/api/marketplace/items/" + item.getMarketplaceItem().getId() + "/photo"
                : null);
        return row;
    }
}
