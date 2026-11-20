package com.suckcc.corestock.warehouse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
@RequestMapping("/warehouses")
public class WarehouseController {

    private final WarehouseRepository warehouses;
    private final StorageLocationRepository locations;

    public WarehouseController(WarehouseRepository warehouses, StorageLocationRepository locations) {
        this.warehouses = warehouses;
        this.locations = locations;
    }

    @GetMapping
    public String list(Model model) {
        populateModel(model, new WarehouseForm(), new LocationForm());
        return "warehouses/list";
    }

    @PostMapping
    public String createWarehouse(@Valid @ModelAttribute("warehouseForm") WarehouseForm form,
                                  BindingResult binding, Model model) {
        String code = form.getCode() == null ? "" : form.getCode().trim().toUpperCase();
        if (warehouses.existsByCode(code)) {
            binding.rejectValue("code", "duplicate", "Warehouse code already exists");
        }
        if (binding.hasErrors()) {
            populateModel(model, form, new LocationForm());
            return "warehouses/list";
        }
        warehouses.save(new Warehouse(code, form.getName()));
        return "redirect:/warehouses";
    }

    @PostMapping("/{warehouseId}/locations")
    public String createLocation(@PathVariable Long warehouseId,
                                 @Valid @ModelAttribute("locationForm") LocationForm form,
                                 BindingResult binding, Model model) {
        Warehouse warehouse = warehouses.findById(warehouseId)
            .orElseThrow(() -> new IllegalArgumentException("Warehouse not found: " + warehouseId));
        StorageLocation candidate = new StorageLocation(
            warehouse, form.getZone(), form.getAisle(), form.getRack(), form.getBin(), form.getCode()
        );
        if (locations.existsByWarehouseIdAndCode(warehouseId, candidate.getCode())) {
            binding.rejectValue("code", "duplicate", "Location code already exists in this warehouse");
        }
        if (binding.hasErrors()) {
            populateModel(model, new WarehouseForm(), form);
            return "warehouses/list";
        }
        locations.save(candidate);
        return "redirect:/warehouses";
    }

    private void populateModel(Model model, WarehouseForm warehouseForm, LocationForm locationForm) {
        model.addAttribute("warehouses", warehouses.findAllByOrderByCodeAsc());
        model.addAttribute("locations", locations.findAllByOrderByWarehouseCodeAscCodeAsc());
        model.addAttribute("warehouseForm", warehouseForm);
        model.addAttribute("locationForm", locationForm);
    }

    @Data
    public static class WarehouseForm {
        @NotBlank @Size(max = 32)
        private String code;
        @NotBlank @Size(max = 255)
        private String name;
    }

    @Data
    public static class LocationForm {
        @NotBlank @Size(max = 32)
        private String zone;
        @NotBlank @Size(max = 32)
        private String aisle;
        @NotBlank @Size(max = 32)
        private String rack;
        @NotBlank @Size(max = 32)
        private String bin;
        @Size(max = 128)
        private String code;
    }
}
