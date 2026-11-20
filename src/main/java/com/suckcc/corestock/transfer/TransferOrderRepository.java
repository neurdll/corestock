package com.suckcc.corestock.transfer;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferOrderRepository extends JpaRepository<TransferOrder, Long> {

    Optional<TransferOrder> findByTransferNumber(String transferNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TransferOrder t WHERE t.id = :id")
    Optional<TransferOrder> findByIdForUpdate(@Param("id") Long id);

    @Query("""
        SELECT t FROM TransferOrder t
        JOIN FETCH t.sourceWarehouse
        JOIN FETCH t.destWarehouse
        ORDER BY t.createdAt DESC
        """)
    List<TransferOrder> findAllByOrderByCreatedAtDesc();
}
