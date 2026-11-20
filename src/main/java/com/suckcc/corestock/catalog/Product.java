package com.suckcc.corestock.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 120)
    private String category;

    @Column(nullable = false, length = 32)
    private String uom;

    @Enumerated(EnumType.STRING)
    @Column(name = "tracking_type", nullable = false, length = 16)
    private TrackingType trackingType;

    @Column(name = "average_cost", nullable = false, precision = 14, scale = 4)
    private BigDecimal averageCost = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Product(String sku, String name, String category, String uom, TrackingType trackingType) {
        this.sku = sku.trim();
        this.name = name.trim();
        this.category = category.trim();
        this.uom = uom.trim();
        this.trackingType = trackingType;
    }

    public void updateDetails(String sku, String name, String category, String uom, TrackingType trackingType, boolean active) {
        this.sku = sku.trim();
        this.name = name.trim();
        this.category = category.trim();
        this.uom = uom.trim();
        this.trackingType = trackingType;
        this.active = active;
    }

    public void updateAverageCost(BigDecimal newAverageCost) {
        if (newAverageCost == null || newAverageCost.signum() < 0) {
            throw new IllegalArgumentException("Average cost must be non-negative");
        }
        this.averageCost = newAverageCost;
    }
}
