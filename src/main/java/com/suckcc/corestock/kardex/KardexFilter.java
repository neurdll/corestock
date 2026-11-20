package com.suckcc.corestock.kardex;

import java.time.LocalDate;

public record KardexFilter(
    String sku,
    Long warehouseId,
    String lotNumber,
    String serialCode,
    LocalDate fromDate,
    LocalDate toDate
) {}
