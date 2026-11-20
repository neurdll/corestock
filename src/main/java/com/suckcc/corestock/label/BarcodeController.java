package com.suckcc.corestock.label;

import com.suckcc.corestock.catalog.Product;
import com.suckcc.corestock.catalog.ProductRepository;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/labels")
public class BarcodeController {

    private final BarcodeService barcodeService;
    private final ProductRepository products;

    public BarcodeController(BarcodeService barcodeService, ProductRepository products) {
        this.barcodeService = barcodeService;
        this.products = products;
    }

    @GetMapping(value = "/code128.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> code128Png(@RequestParam String sku,
                                             @RequestParam(required = false) String lotNumber) {
        String payload = BarcodeService.buildPayload(sku, lotNumber);
        return ResponseEntity.ok(barcodeService.code128Png(payload, 360, 100));
    }

    @GetMapping("/print")
    public String printLabel(@RequestParam String sku,
                             @RequestParam(required = false) String lotNumber,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
                             Model model) {
        Product product = products.findBySku(sku.trim())
            .orElseThrow(() -> new IllegalArgumentException("Unknown SKU: " + sku));
        model.addAttribute("product", product);
        model.addAttribute("lotNumber", lotNumber == null ? "" : lotNumber.trim());
        model.addAttribute("expiryDate", expiryDate);
        model.addAttribute("payload", BarcodeService.buildPayload(product.getSku(), lotNumber));
        return "labels/print";
    }
}
