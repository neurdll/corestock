package com.suckcc.corestock.warehouse;

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
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "storage_locations",
    uniqueConstraints = @UniqueConstraint(name = "uq_location_warehouse_code", columnNames = {"warehouse_id", "code"})
)
@Getter
@NoArgsConstructor
public class StorageLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(nullable = false, length = 32)
    private String zone;

    @Column(nullable = false, length = 32)
    private String aisle;

    @Column(nullable = false, length = 32)
    private String rack;

    @Column(nullable = false, length = 32)
    private String bin;

    @Column(nullable = false, length = 128)
    private String code;

    @Column(nullable = false)
    private boolean active = true;

    public StorageLocation(Warehouse warehouse, String zone, String aisle, String rack, String bin, String code) {
        this.warehouse = warehouse;
        this.zone = zone.trim().toUpperCase();
        this.aisle = aisle.trim().toUpperCase();
        this.rack = rack.trim().toUpperCase();
        this.bin = bin.trim().toUpperCase();
        this.code = (code == null || code.isBlank())
            ? String.join("-", this.zone, this.aisle, this.rack, this.bin)
            : code.trim().toUpperCase();
    }
}
