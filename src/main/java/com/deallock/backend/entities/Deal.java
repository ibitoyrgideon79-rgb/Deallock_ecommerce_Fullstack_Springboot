package com.deallock.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "deals")
public class Deal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String title;
    private String link;
    private String clientName;
    private String sellerPhoneNumber;
    @Column(length = 1000)
    private String sellerAddress;
    @Column(length = 1000)
    private String deliveryAddress;
    private String itemSize;
    private String courierPartner;
    private Integer installmentWeeks;
    private BigDecimal value;
    private BigDecimal holdingFeeAmount;
    private BigDecimal vatAmount;
    private BigDecimal logisticsFeeAmount;
    private BigDecimal upfrontPaymentAmount;
    private BigDecimal remainingBalanceAmount;
    private BigDecimal weeklyPaymentAmount;
    private BigDecimal totalAmount;
    @Column(length = 2000)
    private String description;
    private String status;
    private Instant createdAt;
    private String paymentStatus;
    @Column(name = "rejection_reason", length = 2000)
    private String rejectionReason;
    private boolean secured;
    private Instant securedAt;
    private BigDecimal paymentProofAmount;
    private Instant paymentProofUploadedAt;

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] itemPhoto;
    private String itemPhotoContentType;

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] paymentProof;
    private String paymentProofContentType;

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] securedItemPhoto;
    private String securedItemPhotoContentType;
}
