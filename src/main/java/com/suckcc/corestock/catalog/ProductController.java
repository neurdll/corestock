package com.suckcc.corestock.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository products;

    public ProductController(ProductRepository products) {
        this.products = products;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("products", products.findAllByOrderBySkuAsc());
        return "products/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new ProductForm());
        model.addAttribute("trackingTypes", TrackingType.values());
        return "products/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Product product = products.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
        ProductForm form = new ProductForm();
        form.setId(product.getId());
        form.setSku(product.getSku());
        form.setName(product.getName());
        form.setCategory(product.getCategory());
        form.setUom(product.getUom());
        form.setTrackingType(product.getTrackingType());
        form.setActive(product.isActive());
        model.addAttribute("form", form);
        model.addAttribute("trackingTypes", TrackingType.values());
        return "products/form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("form") ProductForm form, BindingResult binding, Model model) {
        String normalizedSku = form.getSku() == null ? "" : form.getSku().trim();
        boolean duplicateSku = form.getId() == null
            ? products.existsBySku(normalizedSku)
            : products.existsBySkuAndIdNot(normalizedSku, form.getId());
        if (duplicateSku) {
            binding.rejectValue("sku", "duplicate", "SKU already exists");
        }
        if (binding.hasErrors()) {
            model.addAttribute("trackingTypes", TrackingType.values());
            return "products/form";
        }
        Product product = form.getId() == null
            ? new Product(normalizedSku, form.getName(), form.getCategory(), form.getUom(), form.getTrackingType())
            : products.findById(form.getId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + form.getId()));
        if (form.getId() != null) {
            product.updateDetails(normalizedSku, form.getName(), form.getCategory(), form.getUom(), form.getTrackingType(), form.isActive());
        }
        products.save(product);
        return "redirect:/products";
    }

    @Data
    public static class ProductForm {
        private Long id;
        @NotBlank @Size(max = 64)
        private String sku;
        @NotBlank @Size(max = 255)
        private String name;
        @NotBlank @Size(max = 120)
        private String category;
        @NotBlank @Size(max = 32)
        private String uom;
        @NotNull
        private TrackingType trackingType = TrackingType.STANDARD;
        private boolean active = true;
    }
}
