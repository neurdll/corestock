package com.suckcc.corestock.kardex;

import static org.assertj.core.api.Assertions.assertThat;

import com.suckcc.corestock.catalog.Product;
import com.suckcc.corestock.catalog.TrackingType;
import com.suckcc.corestock.inventory.InventoryMovement;
import com.suckcc.corestock.inventory.MovementType;
import com.suckcc.corestock.warehouse.StorageLocation;
import com.suckcc.corestock.warehouse.Warehouse;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class KardexPdfExporterTest {

    @Test
    void rendersValidNonEmptyPdfDocument() {
        Warehouse wh = new Warehouse("WH-PDF", "PDF Warehouse");
        StorageLocation loc = new StorageLocation(wh, "A", "01", "R1", "B1", null);
        Product prod = new Product("PDF-SKU", "Drill", "Tools", "EA", TrackingType.STANDARD);
        InventoryMovement mov = new InventoryMovement(
            prod, loc, MovementType.INBOUND, new BigDecimal("5"), new BigDecimal("42.50"),
            "", null, "PO-99", null, "operator"
        );

        byte[] pdf = new KardexPdfExporter().export(new KardexFilter("PDF-SKU", null, null, null, null, null), List.of(mov));
        assertThat(pdf.length).isGreaterThan(500);
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
}
