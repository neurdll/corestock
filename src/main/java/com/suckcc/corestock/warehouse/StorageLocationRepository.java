package com.suckcc.corestock.warehouse;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StorageLocationRepository extends JpaRepository<StorageLocation, Long> {
    List<StorageLocation> findByWarehouseIdOrderByCodeAsc(Long warehouseId);
    Optional<StorageLocation> findByWarehouseIdAndCode(Long warehouseId, String code);
    boolean existsByWarehouseIdAndCode(Long warehouseId, String code);

    @Query("SELECT l FROM StorageLocation l JOIN FETCH l.warehouse w ORDER BY w.code ASC, l.code ASC")
    List<StorageLocation> findAllByOrderByWarehouseCodeAscCodeAsc();
}
