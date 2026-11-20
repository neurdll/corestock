package com.suckcc.corestock.inventory;

import com.suckcc.corestock.catalog.Product;
import com.suckcc.corestock.catalog.ProductRepository;
import com.suckcc.corestock.catalog.TrackingType;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DispatchService {

    private final ProductRepository products;
    private final InventoryBalanceRepository balances;
    private final SerialNumberRepository serials;
    private final InventoryMovementRepository movements;

    public DispatchService(ProductRepository products,
                           InventoryBalanceRepository balances,
                           SerialNumberRepository serials,
                           InventoryMovementRepository movements) {
        this.products = products;
        this.balances = balances;
        this.serials = serials;
        this.movements = movements;
    }

    @Transactional
    public List<InventoryMovement> dispatch(DispatchCommand cmd) {
        StockMath.requirePositive(cmd.quantity(), "quantity");
        Product product = products.findBySkuForUpdate(cmd.sku().trim())
            .orElseThrow(() -> new IllegalArgumentException("Unknown SKU: " + cmd.sku()));

        if (product.getTrackingType() == TrackingType.SERIAL) {
            return dispatchSerials(product, cmd);
        }

        List<InventoryBalance> candidates = loadLockedCandidates(product.getId(), cmd);
        List<LotAllocator.Allocation> allocations = LotAllocator.allocate(candidates, cmd.quantity());
        List<InventoryMovement> recorded = new java.util.ArrayList<>();

        for (LotAllocator.Allocation alloc : allocations) {
            InventoryBalance b = alloc.balance();
            b.subtractPhysical(alloc.qty());
            balances.save(b);
            recorded.add(movements.save(new InventoryMovement(
                product, b.getLocation(), MovementType.OUTBOUND, alloc.qty(),
                product.getAverageCost(), b.getLotNumber(), null,
                cmd.referenceDoc(), null, cmd.operatorUsername()
            )));
        }
        return recorded;
    }

    private List<InventoryMovement> dispatchSerials(Product product, DispatchCommand cmd) {
        if (cmd.quantity().stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("SERIAL SKU quantity must be a whole number");
        }
        int expected = cmd.quantity().intValueExact();
        List<String> codes = cmd.serialCodes() == null
            ? List.of()
            : cmd.serialCodes().stream().map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
        if (codes.size() != expected) {
            throw new IllegalArgumentException("SERIAL SKU requires exactly " + expected + " serials");
        }

        List<InventoryMovement> recorded = new java.util.ArrayList<>();
        for (String code : codes) {
            SerialNumber sn = serials.findBySerialCodeForUpdate(code)
                .orElseThrow(() -> new IllegalArgumentException("Unknown serial: " + code));
            if (!sn.getProduct().getId().equals(product.getId())) {
                throw new IllegalArgumentException("Serial does not belong to SKU " + product.getSku());
            }
            if (sn.getLocation() == null || !sn.getLocation().getWarehouse().getId().equals(cmd.warehouseId())) {
                throw new IllegalStateException("Serial not located in warehouse: " + code);
            }
            var originLocation = sn.getLocation();
            InventoryBalance bal = balances.findForUpdate(product.getId(), originLocation.getId(), sn.getLotNumber())
                .orElseThrow(() -> new IllegalStateException("Missing balance for serial " + code));
            bal.subtractPhysical(BigDecimal.ONE);
            balances.save(bal);
            sn.markSold();
            serials.save(sn);
            recorded.add(movements.save(new InventoryMovement(
                product, originLocation, MovementType.OUTBOUND, BigDecimal.ONE,
                product.getAverageCost(), sn.getLotNumber(), code,
                cmd.referenceDoc(), null, cmd.operatorUsername()
            )));
        }
        return recorded;
    }

    private List<InventoryBalance> loadLockedCandidates(Long productId, DispatchCommand cmd) {
        if (cmd.locationId() != null) {
            String lot = cmd.lotNumber() == null ? "" : cmd.lotNumber().trim();
            if (!lot.isEmpty()) {
                return balances.findForUpdate(productId, cmd.locationId(), lot).map(List::of).orElse(List.of());
            }
            return balances.findPickCandidatesForUpdate(productId, cmd.warehouseId()).stream()
                .filter(b -> b.getLocation().getId().equals(cmd.locationId()))
                .toList();
        }
        List<InventoryBalance> all = balances.findPickCandidatesForUpdate(productId, cmd.warehouseId());
        if (cmd.lotNumber() != null && !cmd.lotNumber().isBlank()) {
            String lot = cmd.lotNumber().trim();
            return all.stream().filter(b -> lot.equals(b.getLotNumber())).toList();
        }
        return all;
    }
}
