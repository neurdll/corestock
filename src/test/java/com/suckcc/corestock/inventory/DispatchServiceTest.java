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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DispatchServiceTest {

    @Autowired private ReceivingService receivingService;
    @Autowired private DispatchService dispatchService;
    @Autowired private ProductRepository products;
    @Autowired private WarehouseRepository warehouses;
    @Autowired private StorageLocationRepository locations;
    @Autowired private InventoryBalanceRepository balances;
    @Autowired private SerialNumberRepository serials;
    @Autowired private InventoryMovementRepository movements;

    private Warehouse wh;
    private StorageLocation bin;

    @BeforeEach
    void setUp() {
        movements.deleteAll();
        serials.deleteAll();
        balances.deleteAll();
        locations.deleteAll();
        warehouses.deleteAll();
        products.deleteAll();

        wh = warehouses.save(new Warehouse("WH-DSP", "Dispatch WH"));
        bin = locations.save(new StorageLocation(wh, "A", "01", "R1", "B1", null));
    }

    @Test
    void picksLotsInFefoOrderAndUpdatesSerialsToSold() {
        products.save(new Product("MED-1", "Vaccine", "Pharma", "EA", TrackingType.LOT));
        receivingService.receive(new ReceiveCommand(
            "MED-1", bin.getId(), new BigDecimal("5"), new BigDecimal("10"),
            "LOT-LATE", LocalDate.now().plusDays(60), null, "PO-1", "operator"
        ));
        receivingService.receive(new ReceiveCommand(
            "MED-1", bin.getId(), new BigDecimal("4"), new BigDecimal("10"),
            "LOT-SOON", LocalDate.now().plusDays(10), null, "PO-2", "operator"
        ));

        List<InventoryMovement> picked = dispatchService.dispatch(new DispatchCommand(
            "MED-1", wh.getId(), null, new BigDecimal("6"), null, null, "SO-1", "operator"
        ));
        assertThat(picked).hasSize(2);
        assertThat(picked.get(0).getLotNumber()).isEqualTo("LOT-SOON");
        assertThat(picked.get(0).getQuantity()).isEqualByComparingTo("4.0000");
        assertThat(picked.get(1).getLotNumber()).isEqualTo("LOT-LATE");
        assertThat(picked.get(1).getQuantity()).isEqualByComparingTo("2.0000");

        products.save(new Product("SER-D", "Router", "Net", "EA", TrackingType.SERIAL));
        receivingService.receive(new ReceiveCommand(
            "SER-D", bin.getId(), BigDecimal.ONE, new BigDecimal("50"),
            null, null, List.of("SN-R1"), "PO-3", "operator"
        ));
        dispatchService.dispatch(new DispatchCommand(
            "SER-D", wh.getId(), null, BigDecimal.ONE, null, List.of("SN-R1"), "SO-2", "operator"
        ));
        assertThat(serials.findBySerialCode("SN-R1").orElseThrow().getStatus()).isEqualTo(SerialStatus.SOLD);

        assertThatThrownBy(() -> dispatchService.dispatch(new DispatchCommand(
            "MED-1", wh.getId(), null, new BigDecimal("10"), null, null, "SO-OVER", "operator"
        ))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void concurrentDispatchesCannotOversellOrCreateNegativeStock() throws Exception {
        Product std = products.save(new Product("RACE-1", "Valve", "Plumbing", "EA", TrackingType.STANDARD));
        receivingService.receive(new ReceiveCommand(
            "RACE-1", bin.getId(), new BigDecimal("5"), new BigDecimal("12"),
            null, null, null, "PO-INIT", "operator"
        ));

        int threads = 4;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            var futures = java.util.stream.IntStream.range(0, threads)
                .mapToObj(i -> pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        dispatchService.dispatch(new DispatchCommand(
                            "RACE-1", wh.getId(), bin.getId(), new BigDecimal("3"),
                            null, null, "SO-" + i, "operator"
                        ));
                        successes.incrementAndGet();
                    } catch (IllegalStateException ex) {
                        failures.incrementAndGet();
                    }
                    return null;
                }))
                .toList();
            ready.await();
            start.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        }

        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(3);
        assertThat(balances.sumPhysicalQtyByProductId(std.getId())).isEqualByComparingTo("2.0000");
    }
}
