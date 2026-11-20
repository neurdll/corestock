package com.suckcc.corestock.transfer;

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
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "transfer_order_lines")
@Getter
@NoArgsConstructor
public class TransferOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_order_id", nullable = false)
    private TransferOrder transferOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_location_id", nullable = false)
    private StorageLocation sourceLocation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dest_location_id", nullable = false)
    private StorageLocation destLocation;

    @Column(name = "lot_number", nullable = false, length = 64)
    private String lotNumber = "";

    @Column(name = "serial_code", length = 128)
    private String serialCode;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal quantity;

    public TransferOrderLine(Product product,
                             StorageLocation sourceLocation,
                             StorageLocation destLocation,
                             String lotNumber,
                             String serialCode,
                             BigDecimal quantity) {
        this.product = product;
        this.sourceLocation = sourceLocation;
        this.destLocation = destLocation;
        this.lotNumber = lotNumber == null ? "" : lotNumber.trim();
        this.serialCode = serialCode;
        this.quantity = quantity;
    }

    void attach(TransferOrder order) {
        this.transferOrder = order;
    }
}
