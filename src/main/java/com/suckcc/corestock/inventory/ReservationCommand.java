package com.suckcc.corestock.inventory;

import java.math.BigDecimal;
import java.util.List;

public record ReservationCommand(
    String sku,
    Long locationId,
    BigDecimal quantity,
    String lotNumber,
    List<String> serialCodes,
    String referenceDoc,
    String operatorUsername
) {}
