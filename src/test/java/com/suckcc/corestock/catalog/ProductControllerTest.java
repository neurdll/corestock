package com.suckcc.corestock.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository products;

    @Test
    void adminCanCreateProductAndDuplicateSkuIsRejected() throws Exception {
        mockMvc.perform(post("/products/save")
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .param("sku", "SKU-100")
                .param("name", "Steel Bolt M8")
                .param("category", "Hardware")
                .param("uom", "EA")
                .param("trackingType", "LOT"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/products"));

        assertThat(products.findBySku("SKU-100")).isPresent();

        mockMvc.perform(post("/products/save")
                .with(user("admin").roles("ADMIN"))
                .with(csrf())
                .param("sku", "SKU-100")
                .param("name", "Duplicate Bolt")
                .param("category", "Hardware")
                .param("uom", "EA")
                .param("trackingType", "STANDARD"))
            .andExpect(status().isOk())
            .andExpect(view().name("products/form"))
            .andExpect(model().attributeHasFieldErrors("form", "sku"));
    }

    @Test
    void operatorCanViewCatalogButCannotMutate() throws Exception {
        mockMvc.perform(get("/products").with(user("operator").roles("OPERATOR")))
            .andExpect(status().isOk())
            .andExpect(view().name("products/list"));

        mockMvc.perform(post("/products/save")
                .with(user("operator").roles("OPERATOR"))
                .with(csrf())
                .param("sku", "SKU-200")
                .param("name", "Denied")
                .param("category", "Hardware")
                .param("uom", "EA")
                .param("trackingType", "STANDARD"))
            .andExpect(status().isForbidden());
    }
}
