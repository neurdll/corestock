package com.suckcc.corestock.inventory;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM InventoryBalance b WHERE b.product.id = :productId AND b.location.id = :locationId AND b.lotNumber = :lotNumber")
    Optional<InventoryBalance> findForUpdate(@Param("productId") Long productId,
                                             @Param("locationId") Long locationId,
                                             @Param("lotNumber") String lotNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT b FROM InventoryBalance b
        WHERE b.product.id = :productId
          AND b.location.warehouse.id = :warehouseId
          AND (b.physicalQty - b.committedQty) > 0
        ORDER BY CASE WHEN b.expiryDate IS NULL THEN 1 ELSE 0 END, b.expiryDate ASC, b.id ASC
        """)
    List<InventoryBalance> findPickCandidatesForUpdate(@Param("productId") Long productId,
                                                       @Param("warehouseId") Long warehouseId);

    @Query("SELECT COALESCE(SUM(b.physicalQty), 0) FROM InventoryBalance b WHERE b.product.id = :productId")
    BigDecimal sumPhysicalQtyByProductId(@Param("productId") Long productId);

    List<InventoryBalance> findAllByOrderByProductSkuAscLocationWarehouseCodeAscLocationCodeAsc();
}
