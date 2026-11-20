package com.suckcc.corestock.kardex;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.suckcc.corestock.catalog.Product;
import com.suckcc.corestock.catalog.ProductRepository;
import com.suckcc.corestock.catalog.TrackingType;
import com.suckcc.corestock.inventory.InventoryBalanceRepository;
import com.suckcc.corestock.inventory.InventoryMovementRepository;
import com.suckcc.corestock.inventory.ReceiveCommand;
import com.suckcc.corestock.inventory.ReceivingService;
import com.suckcc.corestock.inventory.SerialNumberRepository;
import com.suckcc.corestock.transfer.TransferOrderRepository;
import com.suckcc.corestock.warehouse.StorageLocation;
import com.suckcc.corestock.warehouse.StorageLocationRepository;
import com.suckcc.corestock.warehouse.Warehouse;
import com.suckcc.corestock.warehouse.WarehouseRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class KardexControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ReceivingService receivingService;
    @Autowired private TransferOrderRepository transfers;
    @Autowired private InventoryMovementRepository movements;
    @Autowired private SerialNumberRepository serials;
    @Autowired private InventoryBalanceRepository balances;
    @Autowired private StorageLocationRepository locations;
    @Autowired private WarehouseRepository warehouses;
    @Autowired private ProductRepository products;

    private Warehouse wh1;
    private Warehouse wh2;

    @BeforeEach
    void setUp() {
        cleanAll();
        wh1 = warehouses.save(new Warehouse("WH-K1", "Wh 1"));
        wh2 = warehouses.save(new Warehouse("WH-K2", "Wh 2"));
        StorageLocation b1 = locations.save(new StorageLocation(wh1, "A", "01", "R1", "B1", null));
        StorageLocation b2 = locations.save(new StorageLocation(wh2, "A", "01", "R1", "B1", null));

        products.save(new Product("KDX-LOT", "Paint", "Coatings", "L", TrackingType.LOT));
        products.save(new Product("KDX-SER", "Meter", "Tools", "EA", TrackingType.SERIAL));

        receivingService.receive(new ReceiveCommand(
            "KDX-LOT", b1.getId(), new BigDecimal("5"), new BigDecimal("10"),
            "LOT-A", LocalDate.now().plusDays(30), null, "PO-K1", "operator"
        ));
        receivingService.receive(new ReceiveCommand(
            "KDX-SER", b2.getId(), BigDecimal.ONE, new BigDecimal("80"),
            null, null, List.of("SN-K1"), "PO-K2", "operator"
        ));
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
    void filtersKardexBySkuWarehouseLotAndSerial() throws Exception {
        mockMvc.perform(get("/kardex").with(user("operator").roles("OPERATOR"))
                .param("sku", "KDX-LOT")
                .param("warehouseId", wh1.getId().toString())
                .param("lotNumber", "LOT-A"))
            .andExpect(status().isOk())
            .andExpect(view().name("kardex/list"))
            .andExpect(model().attribute("movements", hasSize(1)));

        mockMvc.perform(get("/kardex").with(user("operator").roles("OPERATOR"))
                .param("serialCode", "SN-K1"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("movements", hasSize(1)));

        mockMvc.perform(get("/kardex").with(user("operator").roles("OPERATOR"))
                .param("sku", "KDX-LOT")
                .param("warehouseId", wh2.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(model().attribute("movements", hasSize(0)));
    }
}
