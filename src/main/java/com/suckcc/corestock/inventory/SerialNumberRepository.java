package com.suckcc.corestock.inventory;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SerialNumberRepository extends JpaRepository<SerialNumber, Long> {

    Optional<SerialNumber> findBySerialCode(String serialCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SerialNumber s WHERE s.serialCode = :serialCode")
    Optional<SerialNumber> findBySerialCodeForUpdate(@Param("serialCode") String serialCode);

    List<SerialNumber> findBySerialCodeIn(Collection<String> serialCodes);

    boolean existsBySerialCodeIn(Collection<String> serialCodes);
}
