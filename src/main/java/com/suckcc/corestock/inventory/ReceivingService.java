package com.suckcc.corestock.inventory;

import com.suckcc.corestock.catalog.Product;
import com.suckcc.corestock.catalog.ProductRepository;
import com.suckcc.corestock.catalog.TrackingType;
import com.suckcc.corestock.warehouse.StorageLocation;
import com.suckcc.corestock.warehouse.StorageLocationRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceivingService {

    private final ProductRepository products;
    private final StorageLocationRepository locations;
    private final InventoryBalanceRepository balances;
    private final SerialNumberRepository serials;
    private final InventoryMovementRepository movements;

    public ReceivingService(ProductRepository products,
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
    public InventoryBalance receive(ReceiveCommand cmd) {
        StockMath.requirePositive(cmd.quantity(), "quantity");
        if (cmd.unitCost() == null || cmd.unitCost().signum() < 0) {
            throw new IllegalArgumentException("unitCost must be non-negative");
        }
        Product product = products.findBySkuForUpdate(cmd.sku().trim())
            .orElseThrow(() -> new IllegalArgumentException("Unknown SKU: " + cmd.sku()));
        StorageLocation location = locations.findById(cmd.locationId())
            .orElseThrow(() -> new IllegalArgumentException("Unknown location: " + cmd.locationId()));

        String lot = cmd.lotNumber() == null ? "" : cmd.lotNumber().trim();
        List<String> serialList = validateTracking(product.getTrackingType(), cmd.quantity(), lot, cmd.serialCodes());

        BigDecimal currentGlobalStock = balances.sumPhysicalQtyByProductId(product.getId());
        BigDecimal nextAvg = StockMath.weightedAverageCost(
            currentGlobalStock, product.getAverageCost(), cmd.quantity(), cmd.unitCost()
        );
        product.updateAverageCost(nextAvg);
        products.save(product);

        InventoryBalance balance = lockOrCreateBalance(product, location, lot, cmd);
        balance.addPhysical(cmd.quantity());
        InventoryBalance savedBalance = balances.save(balance);

        if (product.getTrackingType() == TrackingType.SERIAL) {
            for (String code : serialList) {
                serials.save(new SerialNumber(product, code, location, lot));
                movements.save(new InventoryMovement(
                    product, location, MovementType.INBOUND, BigDecimal.ONE, cmd.unitCost(),
                    lot, code, cmd.referenceDoc(), null, cmd.operatorUsername()
                ));
            }
        } else {
            movements.save(new InventoryMovement(
                product, location, MovementType.INBOUND, cmd.quantity(), cmd.unitCost(),
                lot, null, cmd.referenceDoc(), null, cmd.operatorUsername()
            ));
        }

        return savedBalance;
    }

    private InventoryBalance lockOrCreateBalance(Product product, StorageLocation location, String lot, ReceiveCommand cmd) {
        return balances.findForUpdate(product.getId(), location.getId(), lot)
            .orElseGet(() -> {
                try {
                    return balances.saveAndFlush(new InventoryBalance(product, location, lot, cmd.expiryDate()));
                } catch (DataIntegrityViolationException race) {
                    return balances.findForUpdate(product.getId(), location.getId(), lot)
                        .orElseThrow(() -> race);
                }
            });
    }

    private List<String> validateTracking(TrackingType type, BigDecimal qty, String lot, List<String> rawSerials) {
        List<String> normalizedSerials = rawSerials == null
            ? List.of()
            : rawSerials.stream().map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();

        switch (type) {
            case STANDARD -> {
                if (!lot.isEmpty() || !normalizedSerials.isEmpty()) {
                    throw new IllegalArgumentException("STANDARD SKU cannot specify lot or serial numbers");
                }
            }
            case LOT -> {
                if (lot.isEmpty()) {
                    throw new IllegalArgumentException("LOT SKU requires a lotNumber");
                }
                if (!normalizedSerials.isEmpty()) {
                    throw new IllegalArgumentException("LOT SKU cannot accept serial numbers");
                }
            }
            case SERIAL -> {
                if (qty.stripTrailingZeros().scale() > 0) {
                    throw new IllegalArgumentException("SERIAL SKU quantity must be a whole number");
                }
                int expected = qty.intValueExact();
                if (normalizedSerials.size() != expected) {
                    throw new IllegalArgumentException("SERIAL SKU requires exactly " + expected + " distinct serials");
                }
                if (serials.existsBySerialCodeIn(normalizedSerials)) {
                    throw new IllegalArgumentException("One or more serial codes already exist");
                }
            }
        }
        return normalizedSerials;
    }
}
