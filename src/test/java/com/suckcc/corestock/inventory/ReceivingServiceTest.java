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
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ReceivingServiceTest {

    @Autowired private ReceivingService receivingService;
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

        Warehouse wh = warehouses.save(new Warehouse("WH-RCV", "Receiving WH"));
        bin = locations.save(new StorageLocation(wh, "A", "01", "R1", "B1", null));
    }

    @Test
    void receivesStandardLotAndSerialWithWeightedAverageCost() {
        products.save(new Product("STD-1", "Bolt", "Hardware", "EA", TrackingType.STANDARD));
        products.save(new Product("LOT-1", "Glue", "Chem", "EA", TrackingType.LOT));
        products.save(new Product("SER-1", "Drill", "Tools", "EA", TrackingType.SERIAL));

        receivingService.receive(new ReceiveCommand(
            "STD-1", bin.getId(), new BigDecimal("10"), new BigDecimal("10.00"), null, null, null, "PO-1", "operator"
        ));
        receivingService.receive(new ReceiveCommand(
            "STD-1", bin.getId(), new BigDecimal("10"), new BigDecimal("20.00"), null, null, null, "PO-2", "operator"
        ));
        assertThat(products.findBySku("STD-1").orElseThrow().getAverageCost()).isEqualByComparingTo("15.0000");

        InventoryBalance lotBalance = receivingService.receive(new ReceiveCommand(
            "LOT-1", bin.getId(), new BigDecimal("5"), new BigDecimal("4.00"),
            "L-01", LocalDate.now().plusDays(30), null, "PO-3", "operator"
        ));
        assertThat(lotBalance.getLotNumber()).isEqualTo("L-01");
        assertThat(lotBalance.getPhysicalQty()).isEqualByComparingTo("5.0000");

        receivingService.receive(new ReceiveCommand(
            "SER-1", bin.getId(), new BigDecimal("2"), new BigDecimal("100.00"),
            null, null, List.of("SN-1", "SN-2"), "PO-4", "operator"
        ));
        assertThat(serials.findBySerialCode("SN-1").orElseThrow().getStatus()).isEqualTo(SerialStatus.AVAILABLE);
        assertThat(serials.findBySerialCode("SN-2").orElseThrow().getStatus()).isEqualTo(SerialStatus.AVAILABLE);

        assertThatThrownBy(() -> receivingService.receive(new ReceiveCommand(
            "LOT-1", bin.getId(), BigDecimal.ONE, BigDecimal.ONE, null, null, null, "PO-ERR", "operator"
        ))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void concurrentReceivingHasNoLostUpdates() throws Exception {
        Product std = products.save(new Product("CONC-1", "Nut", "Hardware", "EA", TrackingType.STANDARD));
        receivingService.receive(new ReceiveCommand(
            "CONC-1", bin.getId(), new BigDecimal("1"), new BigDecimal("5.00"), null, null, null, "PO-INIT", "operator"
        ));

        int threads = 6;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            var futures = java.util.stream.IntStream.range(0, threads)
                .mapToObj(i -> pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    receivingService.receive(new ReceiveCommand(
                        "CONC-1", bin.getId(), new BigDecimal("5"), new BigDecimal("5.00"),
                        null, null, null, "PO-C" + i, "operator"
                    ));
                    return null;
                }))
                .toList();
            ready.await();
            start.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        }

        assertThat(balances.sumPhysicalQtyByProductId(std.getId())).isEqualByComparingTo("31.0000");
    }
}
