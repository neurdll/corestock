package com.suckcc.corestock.inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class StockMath {

    public static final int SCALE = 4;

    private StockMath() {}

    public static BigDecimal availableQty(BigDecimal physical, BigDecimal committed) {
        if (physical == null || committed == null) {
            throw new IllegalArgumentException("Quantities cannot be null");
        }
        if (physical.signum() < 0 || committed.signum() < 0) {
            throw new IllegalArgumentException("Quantities cannot be negative");
        }
        if (committed.compareTo(physical) > 0) {
            throw new IllegalStateException("Committed quantity cannot exceed physical quantity");
        }
        return physical.subtract(committed);
    }

    public static BigDecimal weightedAverageCost(BigDecimal currentStock,
                                                   BigDecimal currentAvgCost,
                                                   BigDecimal incomingQty,
                                                   BigDecimal incomingUnitCost) {
        requirePositive(incomingQty, "incomingQty");
        if (incomingUnitCost == null || incomingUnitCost.signum() < 0) {
            throw new IllegalArgumentException("incomingUnitCost must be non-negative");
        }
        BigDecimal safeStock = currentStock == null ? BigDecimal.ZERO : currentStock;
        BigDecimal safeAvg = currentAvgCost == null ? BigDecimal.ZERO : currentAvgCost;
        if (safeStock.signum() < 0 || safeAvg.signum() < 0) {
            throw new IllegalArgumentException("Existing stock and cost must be non-negative");
        }
        if (safeStock.signum() == 0) {
            return incomingUnitCost.setScale(SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal totalValue = safeStock.multiply(safeAvg).add(incomingQty.multiply(incomingUnitCost));
        BigDecimal totalQty = safeStock.add(incomingQty);
        return totalValue.divide(totalQty, SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal requirePositive(BigDecimal qty, String field) {
        if (qty == null || qty.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be greater than zero");
        }
        return qty;
    }
}
