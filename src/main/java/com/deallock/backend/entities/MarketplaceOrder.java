package com.deallock.backend.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "marketplace_orders")
public class MarketplaceOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 64)
    private String status;

    @Column(nullable = false, length = 64)
    private String paymentMethod;

    @Column(nullable = false, length = 32)
    private String deliveryMethod;

    @Column(length = 1000)
    private String deliveryAddress;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotalAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal deliveryFeeAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(length = 2000)
    private String adminNote;

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] paymentProof;

    private String paymentProofContentType;

    @Column(length = 500)
    private String paymentProofKey;

    @Column(length = 2000)
    private String paymentProofNote;

    private Instant paymentSubmittedAt;
    private Instant paymentReceivedAt;
    private Instant shippedAt;
    private Instant deliveredAt;
    private Instant createdAt;
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MarketplaceOrderItem> items = new ArrayList<>();
}
