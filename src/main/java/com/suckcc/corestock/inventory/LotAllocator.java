package com.suckcc.corestock.inventory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class LotAllocator {

    public record Allocation(InventoryBalance balance, BigDecimal qty) {}

    private static final Comparator<InventoryBalance> FEFO_THEN_FIFO =
        Comparator.comparing(InventoryBalance::getExpiryDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(InventoryBalance::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private LotAllocator() {}

    public static List<Allocation> allocate(List<InventoryBalance> candidates, BigDecimal requestedQty) {
        StockMath.requirePositive(requestedQty, "requestedQty");
        List<InventoryBalance> ordered = candidates.stream()
            .filter(b -> b.availableQty().signum() > 0)
            .sorted(FEFO_THEN_FIFO)
            .toList();

        BigDecimal remaining = requestedQty;
        List<Allocation> plan = new ArrayList<>();
        for (InventoryBalance candidate : ordered) {
            if (remaining.signum() == 0) {
                break;
            }
            BigDecimal take = candidate.availableQty().min(remaining);
            plan.add(new Allocation(candidate, take));
            remaining = remaining.subtract(take);
        }
        if (remaining.signum() > 0) {
            throw new IllegalStateException("Insufficient available stock; short by " + remaining);
        }
        return plan;
    }
}
