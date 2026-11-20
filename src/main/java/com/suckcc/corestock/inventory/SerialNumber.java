package com.suckcc.corestock.inventory;

import com.suckcc.corestock.catalog.Product;
import com.suckcc.corestock.warehouse.StorageLocation;
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
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "serial_numbers")
@Getter
@NoArgsConstructor
public class SerialNumber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "serial_code", nullable = false, unique = true, length = 128)
    private String serialCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private StorageLocation location;

    @Column(name = "lot_number", nullable = false, length = 64)
    private String lotNumber = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private SerialStatus status = SerialStatus.AVAILABLE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public SerialNumber(Product product, String serialCode, StorageLocation location, String lotNumber) {
        this.product = product;
        this.serialCode = serialCode.trim();
        this.location = location;
        this.lotNumber = lotNumber == null ? "" : lotNumber.trim();
    }

    public void reserve() {
        if (status != SerialStatus.AVAILABLE) {
            throw new IllegalStateException("Only AVAILABLE serials can be reserved: " + serialCode);
        }
        this.status = SerialStatus.RESERVED;
    }

    public void release() {
        if (status != SerialStatus.RESERVED) {
            throw new IllegalStateException("Only RESERVED serials can be released: " + serialCode);
        }
        this.status = SerialStatus.AVAILABLE;
    }

    public void markSold() {
        if (status != SerialStatus.AVAILABLE && status != SerialStatus.RESERVED) {
            throw new IllegalStateException("Serial is not available for sale: " + serialCode);
        }
        this.status = SerialStatus.SOLD;
        this.location = null;
    }

    public void markInTransit() {
        if (status != SerialStatus.AVAILABLE) {
            throw new IllegalStateException("Only AVAILABLE serials can enter transit: " + serialCode);
        }
        this.status = SerialStatus.IN_TRANSIT;
        this.location = null;
    }

    public void receiveAt(StorageLocation destination) {
        if (status != SerialStatus.IN_TRANSIT) {
            throw new IllegalStateException("Only IN_TRANSIT serials can be received: " + serialCode);
        }
        this.location = destination;
        this.status = SerialStatus.AVAILABLE;
    }
}
