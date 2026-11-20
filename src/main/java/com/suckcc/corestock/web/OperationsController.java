package com.suckcc.corestock.web;

import com.suckcc.corestock.catalog.ProductRepository;
import com.suckcc.corestock.inventory.AdjustmentCommand;
import com.suckcc.corestock.inventory.DispatchCommand;
import com.suckcc.corestock.inventory.DispatchService;
import com.suckcc.corestock.inventory.InventoryBalanceRepository;
import com.suckcc.corestock.inventory.ReceiveCommand;
import com.suckcc.corestock.inventory.ReceivingService;
import com.suckcc.corestock.inventory.ReservationCommand;
import com.suckcc.corestock.inventory.StockControlService;
import com.suckcc.corestock.transfer.TransferOrderRepository;
import com.suckcc.corestock.transfer.TransferService;
import com.suckcc.corestock.warehouse.StorageLocationRepository;
import com.suckcc.corestock.warehouse.WarehouseRepository;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class OperationsController {

    private final InventoryBalanceRepository balances;
    private final ProductRepository products;
    private final WarehouseRepository warehouses;
    private final StorageLocationRepository locations;
    private final TransferOrderRepository transfers;
    private final ReceivingService receivingService;
    private final DispatchService dispatchService;
    private final StockControlService stockControlService;
    private final TransferService transferService;

    public OperationsController(InventoryBalanceRepository balances,
                                ProductRepository products,
                                WarehouseRepository warehouses,
                                StorageLocationRepository locations,
                                TransferOrderRepository transfers,
                                ReceivingService receivingService,
                                DispatchService dispatchService,
                                StockControlService stockControlService,
                                TransferService transferService) {
        this.balances = balances;
        this.products = products;
        this.warehouses = warehouses;
        this.locations = locations;
        this.transfers = transfers;
        this.receivingService = receivingService;
        this.dispatchService = dispatchService;
        this.stockControlService = stockControlService;
        this.transferService = transferService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("balances", balances.findAllByOrderByProductSkuAscLocationWarehouseCodeAscLocationCodeAsc());
        return "ops/dashboard";
    }

    @GetMapping("/ops/receive")
    public String receiveForm(Model model) {
        model.addAttribute("products", products.findAllByOrderBySkuAsc());
        model.addAttribute("locations", locations.findAllByOrderByWarehouseCodeAscCodeAsc());
        return "ops/receive";
    }

    @PostMapping("/ops/receive")
    public String handleReceive(@RequestParam String sku,
                                @RequestParam Long locationId,
                                @RequestParam BigDecimal quantity,
                                @RequestParam BigDecimal unitCost,
                                @RequestParam(required = false) String lotNumber,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
                                @RequestParam(required = false) String serialCodes,
                                @RequestParam(required = false) String referenceDoc,
                                Principal principal,
                                RedirectAttributes ra) {
        try {
            receivingService.receive(new ReceiveCommand(
                sku, locationId, quantity, unitCost, lotNumber, expiryDate,
                splitLines(serialCodes), referenceDoc, actor(principal)
            ));
            ra.addFlashAttribute("message", "Inbound receipt recorded for " + sku);
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/ops/receive";
    }

    @GetMapping("/ops/dispatch")
    public String dispatchForm(Model model) {
        model.addAttribute("products", products.findAllByOrderBySkuAsc());
        model.addAttribute("warehouses", warehouses.findAllByOrderByCodeAsc());
        model.addAttribute("locations", locations.findAllByOrderByWarehouseCodeAscCodeAsc());
        return "ops/dispatch";
    }

    @PostMapping("/ops/dispatch")
    public String handleDispatch(@RequestParam String sku,
                                 @RequestParam Long warehouseId,
                                 @RequestParam(required = false) Long locationId,
                                 @RequestParam BigDecimal quantity,
                                 @RequestParam(required = false) String lotNumber,
                                 @RequestParam(required = false) String serialCodes,
                                 @RequestParam(required = false) String referenceDoc,
                                 Principal principal,
                                 RedirectAttributes ra) {
        try {
            dispatchService.dispatch(new DispatchCommand(
                sku, warehouseId, locationId, quantity, lotNumber,
                splitLines(serialCodes), referenceDoc, actor(principal)
            ));
            ra.addFlashAttribute("message", "Outbound dispatch completed for " + sku);
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/ops/dispatch";
    }

    @GetMapping("/ops/adjust")
    public String adjustForm(Model model) {
        model.addAttribute("products", products.findAllByOrderBySkuAsc());
        model.addAttribute("locations", locations.findAllByOrderByWarehouseCodeAscCodeAsc());
        return "ops/adjust";
    }

    @PostMapping("/ops/adjust")
    public String handleAdjust(@RequestParam String sku,
                               @RequestParam Long locationId,
                               @RequestParam BigDecimal deltaQuantity,
                               @RequestParam(required = false) String lotNumber,
                               @RequestParam String reason,
                               @RequestParam(required = false) String referenceDoc,
                               Principal principal,
                               RedirectAttributes ra) {
        try {
            stockControlService.adjust(new AdjustmentCommand(
                sku, locationId, deltaQuantity, lotNumber, reason, referenceDoc, actor(principal)
            ));
            ra.addFlashAttribute("message", "Adjustment logged for " + sku);
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/ops/adjust";
    }

    @PostMapping("/ops/reserve")
    public String handleReserve(@RequestParam String action,
                                @RequestParam String sku,
                                @RequestParam Long locationId,
                                @RequestParam BigDecimal quantity,
                                @RequestParam(required = false) String lotNumber,
                                @RequestParam(required = false) String serialCodes,
                                @RequestParam(required = false) String referenceDoc,
                                Principal principal,
                                RedirectAttributes ra) {
        try {
            ReservationCommand cmd = new ReservationCommand(
                sku, locationId, quantity, lotNumber, splitLines(serialCodes), referenceDoc, actor(principal)
            );
            if ("release".equalsIgnoreCase(action)) {
                stockControlService.release(cmd);
                ra.addFlashAttribute("message", "Released commitment for " + sku);
            } else {
                stockControlService.commit(cmd);
                ra.addFlashAttribute("message", "Committed stock for " + sku);
            }
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/ops/adjust";
    }

    @GetMapping("/ops/transfers")
    public String transfersView(Model model) {
        model.addAttribute("products", products.findAllByOrderBySkuAsc());
        model.addAttribute("locations", locations.findAllByOrderByWarehouseCodeAscCodeAsc());
        model.addAttribute("transfers", transfers.findAllByOrderByCreatedAtDesc());
        return "ops/transfers";
    }

    @PostMapping("/ops/transfers/ship")
    public String shipTransfer(@RequestParam String transferNumber,
                               @RequestParam String sku,
                               @RequestParam Long sourceLocationId,
                               @RequestParam Long destLocationId,
                               @RequestParam BigDecimal quantity,
                               @RequestParam(required = false) String lotNumber,
                               @RequestParam(required = false) String serialCodes,
                               Principal principal,
                               RedirectAttributes ra) {
        try {
            transferService.ship(new TransferService.ShipCommand(
                transferNumber, sku, sourceLocationId, destLocationId, quantity,
                lotNumber, splitLines(serialCodes), actor(principal)
            ));
            ra.addFlashAttribute("message", "Transfer " + transferNumber + " is IN_TRANSIT");
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/ops/transfers";
    }

    @PostMapping("/ops/transfers/{id}/receive")
    public String receiveTransfer(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        try {
            transferService.receive(id, actor(principal));
            ra.addFlashAttribute("message", "Transfer #" + id + " received and COMPLETED");
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/ops/transfers";
    }

    private static List<String> splitLines(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("[\\r\\n,]+"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }

    private static String actor(Principal principal) {
        return principal == null ? "system" : principal.getName();
    }
}
