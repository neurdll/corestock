package com.suckcc.corestock.kardex;

import com.suckcc.corestock.warehouse.WarehouseRepository;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/kardex")
public class KardexController {

    private final KardexQueryService queryService;
    private final KardexPdfExporter pdfExporter;
    private final WarehouseRepository warehouses;

    public KardexController(KardexQueryService queryService,
                            KardexPdfExporter pdfExporter,
                            WarehouseRepository warehouses) {
        this.queryService = queryService;
        this.pdfExporter = pdfExporter;
        this.warehouses = warehouses;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String sku,
                       @RequestParam(required = false) Long warehouseId,
                       @RequestParam(required = false) String lotNumber,
                       @RequestParam(required = false) String serialCode,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                       Model model) {
        KardexFilter filter = new KardexFilter(sku, warehouseId, lotNumber, serialCode, fromDate, toDate);
        model.addAttribute("filter", filter);
        model.addAttribute("warehouses", warehouses.findAllByOrderByCodeAsc());
        model.addAttribute("movements", queryService.search(filter));
        return "kardex/list";
    }

    @GetMapping(value = "/export.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportPdf(@RequestParam(required = false) String sku,
                                            @RequestParam(required = false) Long warehouseId,
                                            @RequestParam(required = false) String lotNumber,
                                            @RequestParam(required = false) String serialCode,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        KardexFilter filter = new KardexFilter(sku, warehouseId, lotNumber, serialCode, fromDate, toDate);
        byte[] pdf = pdfExporter.export(filter, queryService.search(filter));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename("kardex-report.pdf").build());
        return ResponseEntity.ok().headers(headers).contentType(MediaType.APPLICATION_PDF).body(pdf);
    }
}
