package com.deallock.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "marketplace_items")
public class MarketplaceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(precision = 19, scale = 2)
    private BigDecimal oldPrice;

    /**
     * Simple string to match the current frontend patterns.
     * Expected values: small | medium | big
     */
    @Column(length = 20)
    private String size;

    @Column(nullable = false)
    private boolean listed;

    private Instant createdAt;

    /**
     * If this marketplace item was created from an unpaid/expired Deal, we keep the Deal id
     * to prevent listing the same deal twice.
     */
    private Long sourceDealId;

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] photo;

    private String photoContentType;

    @Column(length = 500)
    private String photoKey;

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] photo2;

    private String photo2ContentType;

    @Column(length = 500)
    private String photo2Key;

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] photo3;

    private String photo3ContentType;

    @Column(length = 500)
    private String photo3Key;
}
