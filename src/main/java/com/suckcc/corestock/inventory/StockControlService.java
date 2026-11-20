package com.suckcc.corestock.inventory;

import com.suckcc.corestock.catalog.Product;
import com.suckcc.corestock.catalog.ProductRepository;
import com.suckcc.corestock.catalog.TrackingType;
import com.suckcc.corestock.warehouse.StorageLocation;
import com.suckcc.corestock.warehouse.StorageLocationRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockControlService {

    private final ProductRepository products;
    private final StorageLocationRepository locations;
    private final InventoryBalanceRepository balances;
    private final SerialNumberRepository serials;
    private final InventoryMovementRepository movements;

    public StockControlService(ProductRepository products,
                               StorageLocationRepository locations,
                               InventoryBalanceRepository balances,
                               SerialNumberRepository serials,
                               InventoryMovementRepository movements) {
        this.products = products;
        this.locations = locations;
        this.balances = balances;
        this.serials = serials;
        this.movements = movements;
    }

    @Transactional
    public InventoryBalance commit(ReservationCommand cmd) {
        return updateCommitment(cmd, true);
    }

    @Transactional
    public InventoryBalance release(ReservationCommand cmd) {
        return updateCommitment(cmd, false);
    }

    @Transactional
    public InventoryBalance adjust(AdjustmentCommand cmd) {
        if (cmd.reason() == null || cmd.reason().isBlank()) {
            throw new IllegalArgumentException("Adjustment reason is mandatory");
        }
        if (cmd.deltaQuantity() == null || cmd.deltaQuantity().signum() == 0) {
            throw new IllegalArgumentException("Adjustment deltaQuantity must be non-zero");
        }
        Product product = products.findBySkuForUpdate(cmd.sku().trim())
            .orElseThrow(() -> new IllegalArgumentException("Unknown SKU: " + cmd.sku()));
        if (product.getTrackingType() == TrackingType.SERIAL) {
            throw new IllegalArgumentException("Use serial receive/dispatch for SERIAL-tracked SKUs");
        }
        StorageLocation location = locations.findById(cmd.locationId())
            .orElseThrow(() -> new IllegalArgumentException("Unknown location: " + cmd.locationId()));

        String lot = cmd.lotNumber() == null ? "" : cmd.lotNumber().trim();
        InventoryBalance balance = balances.findForUpdate(product.getId(), location.getId(), lot)
            .orElseGet(() -> balances.saveAndFlush(new InventoryBalance(product, location, lot, null)));

        if (cmd.deltaQuantity().signum() > 0) {
            balance.addPhysical(cmd.deltaQuantity());
        } else {
            balance.subtractPhysical(cmd.deltaQuantity().abs());
        }
        InventoryBalance saved = balances.save(balance);
        movements.save(new InventoryMovement(
            product, location, MovementType.ADJUSTMENT, cmd.deltaQuantity(),
            product.getAverageCost(), lot, null, cmd.referenceDoc(), cmd.reason().trim(), cmd.operatorUsername()
        ));
        return saved;
    }

    private InventoryBalance updateCommitment(ReservationCommand cmd, boolean reserve) {
        StockMath.requirePositive(cmd.quantity(), "quantity");
        Product product = products.findBySkuForUpdate(cmd.sku().trim())
            .orElseThrow(() -> new IllegalArgumentException("Unknown SKU: " + cmd.sku()));
        StorageLocation location = locations.findById(cmd.locationId())
            .orElseThrow(() -> new IllegalArgumentException("Unknown location: " + cmd.locationId()));

        String lot = cmd.lotNumber() == null ? "" : cmd.lotNumber().trim();
        InventoryBalance balance = balances.findForUpdate(product.getId(), location.getId(), lot)
            .orElseThrow(() -> new IllegalStateException("No stock balance found for SKU " + product.getSku()));

        if (reserve) {
            balance.commit(cmd.quantity());
        } else {
            balance.release(cmd.quantity());
        }
        InventoryBalance saved = balances.save(balance);

        MovementType type = reserve ? MovementType.RESERVATION : MovementType.RELEASE;
        if (product.getTrackingType() == TrackingType.SERIAL) {
            List<String> codes = normalizeSerials(cmd.serialCodes(), cmd.quantity());
            for (String code : codes) {
                SerialNumber sn = serials.findBySerialCodeForUpdate(code)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown serial: " + code));
                if (reserve) {
                    sn.reserve();
                } else {
                    sn.release();
                }
                serials.save(sn);
                movements.save(new InventoryMovement(
                    product, location, type, BigDecimal.ONE, product.getAverageCost(),
                    lot, code, cmd.referenceDoc(), null, cmd.operatorUsername()
                ));
            }
        } else {
            movements.save(new InventoryMovement(
                product, location, type, cmd.quantity(), product.getAverageCost(),
                lot, null, cmd.referenceDoc(), null, cmd.operatorUsername()
            ));
        }
        return saved;
    }

    private List<String> normalizeSerials(List<String> raw, BigDecimal qty) {
        int expected = qty.intValueExact();
        List<String> codes = raw == null ? List.of() : raw.stream().map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
        if (codes.size() != expected) {
            throw new IllegalArgumentException("Expected " + expected + " serial codes");
        }
        return codes;
    }
}
