package com.suckcc.corestock.inventory;

import java.math.BigDecimal;

public record AdjustmentCommand(
    String sku,
    Long locationId,
    BigDecimal deltaQuantity,
    String lotNumber,
    String reason,
    String referenceDoc,
    String operatorUsername
) {}
