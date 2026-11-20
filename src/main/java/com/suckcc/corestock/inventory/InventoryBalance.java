package com.suckcc.corestock.inventory;

import com.suckcc.corestock.catalog.Product;
import com.suckcc.corestock.warehouse.StorageLocation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "inventory_balances",
    uniqueConstraints = @UniqueConstraint(name = "uq_balance_prod_loc_lot", columnNames = {"product_id", "location_id", "lot_number"})
)
@Getter
@NoArgsConstructor
public class InventoryBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private StorageLocation location;

    @Column(name = "lot_number", nullable = false, length = 64)
    private String lotNumber = "";

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "physical_qty", nullable = false, precision = 14, scale = 4)
    private BigDecimal physicalQty = BigDecimal.ZERO;

    @Column(name = "committed_qty", nullable = false, precision = 14, scale = 4)
    private BigDecimal committedQty = BigDecimal.ZERO;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public InventoryBalance(Product product, StorageLocation location, String lotNumber, LocalDate expiryDate) {
        this.product = product;
        this.location = location;
        this.lotNumber = lotNumber == null ? "" : lotNumber.trim();
        this.expiryDate = expiryDate;
    }

    public BigDecimal availableQty() {
        return StockMath.availableQty(physicalQty, committedQty);
    }

    public void addPhysical(BigDecimal qty) {
        StockMath.requirePositive(qty, "qty");
        this.physicalQty = this.physicalQty.add(qty);
    }

    public void subtractPhysical(BigDecimal qty) {
        StockMath.requirePositive(qty, "qty");
        if (availableQty().compareTo(qty) < 0) {
            throw new IllegalStateException("Insufficient available stock for SKU " + product.getSku());
        }
        this.physicalQty = this.physicalQty.subtract(qty);
    }

    public void commit(BigDecimal qty) {
        StockMath.requirePositive(qty, "qty");
        if (availableQty().compareTo(qty) < 0) {
            throw new IllegalStateException("Insufficient available stock to commit for SKU " + product.getSku());
        }
        this.committedQty = this.committedQty.add(qty);
    }

    public void release(BigDecimal qty) {
        StockMath.requirePositive(qty, "qty");
        if (committedQty.compareTo(qty) < 0) {
            throw new IllegalStateException("Cannot release more than committed quantity");
        }
        this.committedQty = this.committedQty.subtract(qty);
    }
}
