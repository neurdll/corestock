package com.suckcc.corestock.warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WarehouseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WarehouseRepository warehouses;

    @Autowired
    private StorageLocationRepository locations;

    @Test
    void enforcesUniqueCompositeLocationCodePerWarehouse() throws Exception {
        mockMvc.perform(post("/warehouses")
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .param("code", "WH1")
                .param("name", "Main Warehouse"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/warehouses"));

        mockMvc.perform(post("/warehouses")
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .param("code", "WH2")
                .param("name", "North Branch"))
            .andExpect(status().is3xxRedirection());

        Warehouse wh1 = warehouses.findByCode("WH1").orElseThrow();
        Warehouse wh2 = warehouses.findByCode("WH2").orElseThrow();

        mockMvc.perform(post("/warehouses/{id}/locations", wh1.getId())
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .param("zone", "A").param("aisle", "01").param("rack", "R1").param("bin", "B1"))
            .andExpect(status().is3xxRedirection());

        assertThat(locations.findByWarehouseIdAndCode(wh1.getId(), "A-01-R1-B1")).isPresent();

        // Duplicate within WH1 is rejected
        mockMvc.perform(post("/warehouses/{id}/locations", wh1.getId())
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .param("zone", "A").param("aisle", "01").param("rack", "R1").param("bin", "B1"))
            .andExpect(status().isOk())
            .andExpect(view().name("warehouses/list"))
            .andExpect(model().attributeHasFieldErrors("locationForm", "code"));

        // Same composite code in WH2 succeeds
        mockMvc.perform(post("/warehouses/{id}/locations", wh2.getId())
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .param("zone", "A").param("aisle", "01").param("rack", "R1").param("bin", "B1"))
            .andExpect(status().is3xxRedirection());

        assertThat(locations.findByWarehouseIdAndCode(wh2.getId(), "A-01-R1-B1")).isPresent();
    }
}
