package com.suckcc.corestock.transfer;

import com.suckcc.corestock.catalog.Product;
import com.suckcc.corestock.catalog.ProductRepository;
import com.suckcc.corestock.catalog.TrackingType;
import com.suckcc.corestock.inventory.InventoryBalance;
import com.suckcc.corestock.inventory.InventoryBalanceRepository;
import com.suckcc.corestock.inventory.InventoryMovement;
import com.suckcc.corestock.inventory.InventoryMovementRepository;
import com.suckcc.corestock.inventory.MovementType;
import com.suckcc.corestock.inventory.SerialNumber;
import com.suckcc.corestock.inventory.SerialNumberRepository;
import com.suckcc.corestock.inventory.StockMath;
import com.suckcc.corestock.warehouse.StorageLocation;
import com.suckcc.corestock.warehouse.StorageLocationRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    public record ShipCommand(
        String transferNumber,
        String sku,
        Long sourceLocationId,
        Long destLocationId,
        BigDecimal quantity,
        String lotNumber,
        List<String> serialCodes,
        String operatorUsername
    ) {}

    private final TransferOrderRepository transfers;
    private final ProductRepository products;
    private final StorageLocationRepository locations;
    private final InventoryBalanceRepository balances;
    private final SerialNumberRepository serials;
    private final InventoryMovementRepository movements;

    public TransferService(TransferOrderRepository transfers,
                           ProductRepository products,
                           StorageLocationRepository locations,
                           InventoryBalanceRepository balances,
                           SerialNumberRepository serials,
                           InventoryMovementRepository movements) {
        this.transfers = transfers;
        this.products = products;
        this.locations = locations;
        this.balances = balances;
        this.serials = serials;
        this.movements = movements;
    }

    @Transactional
    public TransferOrder ship(ShipCommand cmd) {
        StockMath.requirePositive(cmd.quantity(), "quantity");
        StorageLocation src = locations.findById(cmd.sourceLocationId())
            .orElseThrow(() -> new IllegalArgumentException("Unknown source location: " + cmd.sourceLocationId()));
        StorageLocation dst = locations.findById(cmd.destLocationId())
            .orElseThrow(() -> new IllegalArgumentException("Unknown dest location: " + cmd.destLocationId()));
        if (src.getWarehouse().getId().equals(dst.getWarehouse().getId())) {
            throw new IllegalArgumentException("Transfers require distinct source and destination warehouses");
        }

        Product product = products.findBySkuForUpdate(cmd.sku().trim())
            .orElseThrow(() -> new IllegalArgumentException("Unknown SKU: " + cmd.sku()));
        TransferOrder order = new TransferOrder(cmd.transferNumber(), src.getWarehouse(), dst.getWarehouse(), cmd.operatorUsername());

        if (product.getTrackingType() == TrackingType.SERIAL) {
            shipSerials(order, product, src, dst, cmd);
        } else {
            String lot = cmd.lotNumber() == null ? "" : cmd.lotNumber().trim();
            InventoryBalance originBal = balances.findForUpdate(product.getId(), src.getId(), lot)
                .orElseThrow(() -> new IllegalStateException("No origin balance for SKU " + product.getSku()));
            originBal.subtractPhysical(cmd.quantity());
            balances.save(originBal);
            order.addLine(new TransferOrderLine(product, src, dst, lot, null, cmd.quantity()));
            movements.save(new InventoryMovement(
                product, src, MovementType.TRANSFER_OUT, cmd.quantity(), product.getAverageCost(),
                lot, null, order.getTransferNumber(), null, cmd.operatorUsername()
            ));
        }

        return transfers.save(order);
    }

    @Transactional
    public TransferOrder receive(Long transferOrderId, String operatorUsername) {
        TransferOrder order = transfers.findByIdForUpdate(transferOrderId)
            .orElseThrow(() -> new IllegalArgumentException("Transfer order not found: " + transferOrderId));
        order.markReceived();

        for (TransferOrderLine line : order.getLines()) {
            Product product = line.getProduct();
            StorageLocation dst = line.getDestLocation();
            LocalDate originExpiry = balances.findForUpdate(product.getId(), line.getSourceLocation().getId(), line.getLotNumber())
                .map(InventoryBalance::getExpiryDate)
                .orElse(null);

            InventoryBalance dstBal = lockOrCreateDest(product, dst, line.getLotNumber(), originExpiry);
            dstBal.addPhysical(line.getQuantity());
            balances.save(dstBal);

            if (line.getSerialCode() != null) {
                SerialNumber sn = serials.findBySerialCodeForUpdate(line.getSerialCode())
                    .orElseThrow(() -> new IllegalStateException("Missing serial: " + line.getSerialCode()));
                sn.receiveAt(dst);
                serials.save(sn);
            }

            movements.save(new InventoryMovement(
                product, dst, MovementType.TRANSFER_IN, line.getQuantity(), product.getAverageCost(),
                line.getLotNumber(), line.getSerialCode(), order.getTransferNumber(), null, operatorUsername
            ));
        }
        return transfers.save(order);
    }

    private void shipSerials(TransferOrder order, Product product, StorageLocation src, StorageLocation dst, ShipCommand cmd) {
        int expected = cmd.quantity().intValueExact();
        List<String> codes = cmd.serialCodes() == null
            ? List.of()
            : cmd.serialCodes().stream().map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
        if (codes.size() != expected) {
            throw new IllegalArgumentException("Expected " + expected + " serial codes");
        }
        for (String code : codes) {
            SerialNumber sn = serials.findBySerialCodeForUpdate(code)
                .orElseThrow(() -> new IllegalArgumentException("Unknown serial: " + code));
            if (sn.getLocation() == null || !sn.getLocation().getId().equals(src.getId())) {
                throw new IllegalStateException("Serial not in source location: " + code);
            }
            InventoryBalance bal = balances.findForUpdate(product.getId(), src.getId(), sn.getLotNumber())
                .orElseThrow(() -> new IllegalStateException("Missing balance for serial " + code));
            bal.subtractPhysical(BigDecimal.ONE);
            balances.save(bal);
            sn.markInTransit();
            serials.save(sn);
            order.addLine(new TransferOrderLine(product, src, dst, sn.getLotNumber(), code, BigDecimal.ONE));
            movements.save(new InventoryMovement(
                product, src, MovementType.TRANSFER_OUT, BigDecimal.ONE, product.getAverageCost(),
                sn.getLotNumber(), code, order.getTransferNumber(), null, cmd.operatorUsername()
            ));
        }
    }

    private InventoryBalance lockOrCreateDest(Product product, StorageLocation dst, String lot, LocalDate expiry) {
        return balances.findForUpdate(product.getId(), dst.getId(), lot)
            .orElseGet(() -> {
                try {
                    return balances.saveAndFlush(new InventoryBalance(product, dst, lot, expiry));
                } catch (DataIntegrityViolationException race) {
                    return balances.findForUpdate(product.getId(), dst.getId(), lot)
                        .orElseThrow(() -> race);
                }
            });
    }
}
