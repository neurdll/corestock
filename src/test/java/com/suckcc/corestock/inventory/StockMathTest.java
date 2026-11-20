package com.suckcc.corestock.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class StockMathTest {

    @Test
    void computesAvailableQtyAndRejectsOverCommitment() {
        assertThat(StockMath.availableQty(new BigDecimal("10.0000"), new BigDecimal("3.5000")))
            .isEqualByComparingTo("6.5000");

        assertThatThrownBy(() -> StockMath.availableQty(new BigDecimal("5"), new BigDecimal("6")))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void computesWeightedAverageCostFromZeroAndPositiveStock() {
        BigDecimal firstAvg = StockMath.weightedAverageCost(
            BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10"), new BigDecimal("25.50")
        );
        assertThat(firstAvg).isEqualByComparingTo("25.5000");

        BigDecimal nextAvg = StockMath.weightedAverageCost(
            new BigDecimal("10"), firstAvg, new BigDecimal("10"), new BigDecimal("30.50")
        );
        assertThat(nextAvg).isEqualByComparingTo("28.0000");
    }

    @Test
    void rejectsNonPositiveQuantities() {
        assertThatThrownBy(() -> StockMath.requirePositive(BigDecimal.ZERO, "qty"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
