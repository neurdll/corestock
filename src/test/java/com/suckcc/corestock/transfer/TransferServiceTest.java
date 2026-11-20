package com.suckcc.corestock.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.suckcc.corestock.catalog.Product;
import com.suckcc.corestock.catalog.ProductRepository;
import com.suckcc.corestock.catalog.TrackingType;
import com.suckcc.corestock.inventory.InventoryBalanceRepository;
import com.suckcc.corestock.inventory.InventoryMovementRepository;
import com.suckcc.corestock.inventory.ReceiveCommand;
import com.suckcc.corestock.inventory.ReceivingService;
import com.suckcc.corestock.inventory.SerialNumberRepository;
import com.suckcc.corestock.inventory.SerialStatus;
import com.suckcc.corestock.warehouse.StorageLocation;
import com.suckcc.corestock.warehouse.StorageLocationRepository;
import com.suckcc.corestock.warehouse.Warehouse;
import com.suckcc.corestock.warehouse.WarehouseRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TransferServiceTest {

    @Autowired private ReceivingService receivingService;
    @Autowired private TransferService transferService;
    @Autowired private TransferOrderRepository transfers;
    @Autowired private ProductRepository products;
    @Autowired private WarehouseRepository warehouses;
    @Autowired private StorageLocationRepository locations;
    @Autowired private InventoryBalanceRepository balances;
    @Autowired private SerialNumberRepository serials;
    @Autowired private InventoryMovementRepository movements;

    private StorageLocation srcBin;
    private StorageLocation dstBin;

    @BeforeEach
    void setUp() {
        cleanAll();

        Warehouse wh1 = warehouses.save(new Warehouse("WH-SRC", "Origin"));
        Warehouse wh2 = warehouses.save(new Warehouse("WH-DST", "Dest"));
        srcBin = locations.save(new StorageLocation(wh1, "A", "01", "R1", "B1", null));
        dstBin = locations.save(new StorageLocation(wh2, "B", "01", "R1", "B1", null));
    }

    @AfterEach
    void tearDown() {
        cleanAll();
    }

    private void cleanAll() {
        transfers.deleteAll();
        movements.deleteAll();
        serials.deleteAll();
        balances.deleteAll();
        locations.deleteAll();
        warehouses.deleteAll();
        products.deleteAll();
    }

    @Test
    void twoPhaseTransferTransitionsThroughInTransitToCompleted() {
        Product ser = products.save(new Product("TRF-SER", "Switch", "Net", "EA", TrackingType.SERIAL));
        receivingService.receive(new ReceiveCommand(
            "TRF-SER", srcBin.getId(), BigDecimal.ONE, new BigDecimal("200"),
            null, null, List.of("SW-1"), "PO-1", "operator"
        ));

        TransferOrder shipped = transferService.ship(new TransferService.ShipCommand(
            "TR-001", "TRF-SER", srcBin.getId(), dstBin.getId(), BigDecimal.ONE, null, List.of("SW-1"), "operator"
        ));
        assertThat(shipped.getStatus()).isEqualTo(TransferStatus.IN_TRANSIT);
        assertThat(balances.sumPhysicalQtyByProductId(ser.getId())).isEqualByComparingTo("0.0000");
        assertThat(serials.findBySerialCode("SW-1").orElseThrow().getStatus()).isEqualTo(SerialStatus.IN_TRANSIT);

        TransferOrder completed = transferService.receive(shipped.getId(), "operator");
        assertThat(completed.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(balances.sumPhysicalQtyByProductId(ser.getId())).isEqualByComparingTo("1.0000");
        var receivedSerial = serials.findBySerialCode("SW-1").orElseThrow();
        assertThat(receivedSerial.getStatus()).isEqualTo(SerialStatus.AVAILABLE);
        assertThat(receivedSerial.getLocation().getId()).isEqualTo(dstBin.getId());
    }

    @Test
    void concurrentPhaseTwoCannotDoubleReceiveTransferOrder() throws Exception {
        Product std = products.save(new Product("TRF-STD", "Bolt", "HW", "EA", TrackingType.STANDARD));
        receivingService.receive(new ReceiveCommand(
            "TRF-STD", srcBin.getId(), new BigDecimal("10"), new BigDecimal("2"),
            null, null, null, "PO-1", "operator"
        ));

        TransferOrder shipped = transferService.ship(new TransferService.ShipCommand(
            "TR-RACE", "TRF-STD", srcBin.getId(), dstBin.getId(), new BigDecimal("4"), null, null, "operator"
        ));

        int threads = 3;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejections = new AtomicInteger();

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            var futures = java.util.stream.IntStream.range(0, threads)
                .mapToObj(i -> pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        transferService.receive(shipped.getId(), "operator");
                        successes.incrementAndGet();
                    } catch (IllegalStateException ex) {
                        rejections.incrementAndGet();
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
        assertThat(rejections.get()).isEqualTo(2);
        assertThat(balances.sumPhysicalQtyByProductId(std.getId())).isEqualByComparingTo("10.0000");
    }
}
