package com.suckcc.corestock.transfer;

import com.suckcc.corestock.warehouse.Warehouse;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "transfer_orders")
@Getter
@NoArgsConstructor
public class TransferOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_number", nullable = false, unique = true, length = 64)
    private String transferNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_warehouse_id", nullable = false)
    private Warehouse sourceWarehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dest_warehouse_id", nullable = false)
    private Warehouse destWarehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TransferStatus status = TransferStatus.IN_TRANSIT;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "shipped_at")
    private Instant shippedAt = Instant.now();

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "transferOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TransferOrderLine> lines = new ArrayList<>();

    public TransferOrder(String transferNumber, Warehouse sourceWarehouse, Warehouse destWarehouse, String createdBy) {
        this.transferNumber = transferNumber.trim();
        this.sourceWarehouse = sourceWarehouse;
        this.destWarehouse = destWarehouse;
        this.createdBy = createdBy;
    }

    public void addLine(TransferOrderLine line) {
        line.attach(this);
        this.lines.add(line);
    }

    public void markReceived() {
        if (this.status != TransferStatus.IN_TRANSIT) {
            throw new IllegalStateException("Transfer order is already " + this.status);
        }
        this.status = TransferStatus.COMPLETED;
        this.receivedAt = Instant.now();
    }
}
