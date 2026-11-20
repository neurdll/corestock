package com.suckcc.corestock.inventory;

import com.suckcc.corestock.catalog.Product;
import com.suckcc.corestock.warehouse.StorageLocation;
import com.suckcc.corestock.warehouse.Warehouse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "inventory_movements")
@Getter
@NoArgsConstructor
public class InventoryMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false, updatable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false, updatable = false)
    private StorageLocation location;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, updatable = false, length = 24)
    private MovementType movementType;

    @Column(nullable = false, updatable = false, precision = 14, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_cost", nullable = false, updatable = false, precision = 14, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "lot_number", nullable = false, updatable = false, length = 64)
    private String lotNumber;

    @Column(name = "serial_code", updatable = false, length = 128)
    private String serialCode;

    @Column(name = "reference_doc", updatable = false, length = 128)
    private String referenceDoc;

    @Column(updatable = false)
    private String reason;

    @Column(name = "operator_username", nullable = false, updatable = false, length = 64)
    private String operatorUsername;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    public InventoryMovement(Product product,
                             StorageLocation location,
                             MovementType movementType,
                             BigDecimal quantity,
                             BigDecimal unitCost,
                             String lotNumber,
                             String serialCode,
                             String referenceDoc,
                             String reason,
                             String operatorUsername) {
        this.product = product;
        this.warehouse = location.getWarehouse();
        this.location = location;
        this.movementType = movementType;
        this.quantity = quantity;
        this.unitCost = unitCost == null ? BigDecimal.ZERO : unitCost;
        this.lotNumber = lotNumber == null ? "" : lotNumber.trim();
        this.serialCode = serialCode;
        this.referenceDoc = referenceDoc;
        this.reason = reason;
        this.operatorUsername = operatorUsername;
    }
}
