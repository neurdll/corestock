package com.suckcc.corestock.warehouse;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorageLocationRepository extends JpaRepository<StorageLocation, Long> {
    List<StorageLocation> findByWarehouseIdOrderByCodeAsc(Long warehouseId);
    Optional<StorageLocation> findByWarehouseIdAndCode(Long warehouseId, String code);
    boolean existsByWarehouseIdAndCode(Long warehouseId, String code);
    List<StorageLocation> findAllByOrderByWarehouseCodeAscCodeAsc();
}
