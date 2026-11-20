package com.suckcc.corestock.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.suckcc.corestock.catalog.Product;
import com.suckcc.corestock.catalog.ProductRepository;
import com.suckcc.corestock.catalog.TrackingType;
import com.suckcc.corestock.warehouse.StorageLocation;
import com.suckcc.corestock.warehouse.StorageLocationRepository;
import com.suckcc.corestock.warehouse.Warehouse;
import com.suckcc.corestock.warehouse.WarehouseRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class StockControlServiceTest {

    @Autowired private ReceivingService receivingService;
    @Autowired private StockControlService stockControlService;
    @Autowired private ProductRepository products;
    @Autowired private WarehouseRepository warehouses;
    @Autowired private StorageLocationRepository locations;
    @Autowired private InventoryBalanceRepository balances;
    @Autowired private SerialNumberRepository serials;
    @Autowired private InventoryMovementRepository movements;

    private StorageLocation bin;

    @BeforeEach
    void setUp() {
        movements.deleteAll();
        serials.deleteAll();
        balances.deleteAll();
        locations.deleteAll();
        warehouses.deleteAll();
        products.deleteAll();

        Warehouse wh = warehouses.save(new Warehouse("WH-CTL", "Control WH"));
        bin = locations.save(new StorageLocation(wh, "A", "01", "R1", "B1", null));
    }

    @Test
    void commitsAndReleasesStockAndSerialsWhileGuardingBounds() {
        products.save(new Product("SER-C", "Scanner", "HW", "EA", TrackingType.SERIAL));
        receivingService.receive(new ReceiveCommand(
            "SER-C", bin.getId(), new BigDecimal("2"), new BigDecimal("50"),
            null, null, List.of("SC-1", "SC-2"), "PO-1", "operator"
        ));

        InventoryBalance committed = stockControlService.commit(new ReservationCommand(
            "SER-C", bin.getId(), BigDecimal.ONE, null, List.of("SC-1"), "SO-10", "operator"
        ));
        assertThat(committed.getCommittedQty()).isEqualByComparingTo("1.0000");
        assertThat(committed.availableQty()).isEqualByComparingTo("1.0000");
        assertThat(serials.findBySerialCode("SC-1").orElseThrow().getStatus()).isEqualTo(SerialStatus.RESERVED);

        assertThatThrownBy(() -> stockControlService.commit(new ReservationCommand(
            "SER-C", bin.getId(), new BigDecimal("2"), null, List.of("SC-1", "SC-2"), "SO-OVER", "operator"
        ))).isInstanceOf(IllegalStateException.class);

        InventoryBalance released = stockControlService.release(new ReservationCommand(
            "SER-C", bin.getId(), BigDecimal.ONE, null, List.of("SC-1"), "SO-10", "operator"
        ));
        assertThat(released.getCommittedQty()).isEqualByComparingTo("0.0000");
        assertThat(serials.findBySerialCode("SC-1").orElseThrow().getStatus()).isEqualTo(SerialStatus.AVAILABLE);
    }

    @Test
    void adjustmentsRequireReasonAndPreventNegativeAvailableStock() {
        products.save(new Product("ADJ-1", "Cable", "Elec", "M", TrackingType.STANDARD));
        receivingService.receive(new ReceiveCommand(
            "ADJ-1", bin.getId(), new BigDecimal("10"), new BigDecimal("2"),
            null, null, null, "PO-1", "operator"
        ));

        assertThatThrownBy(() -> stockControlService.adjust(new AdjustmentCommand(
            "ADJ-1", bin.getId(), new BigDecimal("-2"), null, "   ", "ADJ-DOC", "admin"
        ))).isInstanceOf(IllegalArgumentException.class);

        InventoryBalance shrunk = stockControlService.adjust(new AdjustmentCommand(
            "ADJ-1", bin.getId(), new BigDecimal("-3"), null, "Cycle count shrinkage", "CC-1", "admin"
        ));
        assertThat(shrunk.getPhysicalQty()).isEqualByComparingTo("7.0000");

        InventoryBalance found = stockControlService.adjust(new AdjustmentCommand(
            "ADJ-1", bin.getId(), new BigDecimal("2"), null, "Cycle count overage", "CC-2", "admin"
        ));
        assertThat(found.getPhysicalQty()).isEqualByComparingTo("9.0000");

        assertThatThrownBy(() -> stockControlService.adjust(new AdjustmentCommand(
            "ADJ-1", bin.getId(), new BigDecimal("-15"), null, "Impossible shrinkage", "CC-3", "admin"
        ))).isInstanceOf(IllegalStateException.class);
    }
}
