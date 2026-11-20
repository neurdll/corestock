package com.suckcc.corestock.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.suckcc.corestock.catalog.Product;
import com.suckcc.corestock.catalog.ProductRepository;
import com.suckcc.corestock.catalog.TrackingType;
import com.suckcc.corestock.inventory.InventoryBalanceRepository;
import com.suckcc.corestock.inventory.InventoryMovementRepository;
import com.suckcc.corestock.inventory.SerialNumberRepository;
import com.suckcc.corestock.transfer.TransferOrderRepository;
import com.suckcc.corestock.warehouse.StorageLocation;
import com.suckcc.corestock.warehouse.StorageLocationRepository;
import com.suckcc.corestock.warehouse.Warehouse;
import com.suckcc.corestock.warehouse.WarehouseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OperationsControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ProductRepository products;
    @Autowired private WarehouseRepository warehouses;
    @Autowired private StorageLocationRepository locations;
    @Autowired private InventoryBalanceRepository balances;
    @Autowired private SerialNumberRepository serials;
    @Autowired private InventoryMovementRepository movements;
    @Autowired private TransferOrderRepository transfers;

    private Warehouse wh1;
    private StorageLocation bin1;
    private StorageLocation bin2;
    private Product prod;

    @BeforeEach
    void setUp() {
        cleanAll();
        wh1 = warehouses.save(new Warehouse("WH-OP1", "Floor 1"));
        Warehouse wh2 = warehouses.save(new Warehouse("WH-OP2", "Floor 2"));
        bin1 = locations.save(new StorageLocation(wh1, "A", "01", "R1", "B1", null));
        bin2 = locations.save(new StorageLocation(wh2, "A", "01", "R1", "B1", null));
        prod = products.save(new Product("UI-SKU", "Hammer", "Tools", "EA", TrackingType.STANDARD));
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
    void executesReceiveDispatchAdjustAndTwoPhaseTransferViaWebForms() throws Exception {
        mockMvc.perform(get("/").with(user("operator").roles("OPERATOR")))
            .andExpect(status().isOk());

        mockMvc.perform(post("/ops/receive")
                .with(user("operator").roles("OPERATOR")).with(csrf())
                .param("sku", "UI-SKU")
                .param("locationId", bin1.getId().toString())
                .param("quantity", "10")
                .param("unitCost", "12.50")
                .param("referenceDoc", "PO-UI"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/ops/receive"))
            .andExpect(flash().attributeExists("message"));

        mockMvc.perform(post("/ops/dispatch")
                .with(user("operator").roles("OPERATOR")).with(csrf())
                .param("sku", "UI-SKU")
                .param("warehouseId", wh1.getId().toString())
                .param("quantity", "3")
                .param("referenceDoc", "SO-UI"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/ops/dispatch"))
            .andExpect(flash().attributeExists("message"));

        mockMvc.perform(post("/ops/adjust")
                .with(user("operator").roles("OPERATOR")).with(csrf())
                .param("sku", "UI-SKU")
                .param("locationId", bin1.getId().toString())
                .param("deltaQuantity", "-1")
                .param("reason", "Damaged handle"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/ops/adjust"))
            .andExpect(flash().attributeExists("message"));

        mockMvc.perform(post("/ops/transfers/ship")
                .with(user("operator").roles("OPERATOR")).with(csrf())
                .param("transferNumber", "TR-UI-1")
                .param("sku", "UI-SKU")
                .param("sourceLocationId", bin1.getId().toString())
                .param("destLocationId", bin2.getId().toString())
                .param("quantity", "2"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/ops/transfers"))
            .andExpect(flash().attributeExists("message"));

        Long transferId = transfers.findByTransferNumber("TR-UI-1").orElseThrow().getId();
        mockMvc.perform(post("/ops/transfers/{id}/receive", transferId)
                .with(user("operator").roles("OPERATOR")).with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/ops/transfers"))
            .andExpect(flash().attributeExists("message"));

        assertThat(balances.sumPhysicalQtyByProductId(prod.getId())).isEqualByComparingTo("6.0000");

        for (String path : new String[]{"/", "/ops/receive", "/ops/dispatch", "/ops/adjust", "/ops/transfers"}) {
            mockMvc.perform(get(path).with(user("operator").roles("OPERATOR")))
                .andExpect(status().isOk());
        }
    }
}
