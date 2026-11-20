package com.suckcc.corestock.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ReceiveCommand(
    String sku,
    Long locationId,
    BigDecimal quantity,
    BigDecimal unitCost,
    String lotNumber,
    LocalDate expiryDate,
    List<String> serialCodes,
    String referenceDoc,
    String operatorUsername
) {}
