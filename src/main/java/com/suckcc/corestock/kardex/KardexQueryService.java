package com.suckcc.corestock.kardex;

import com.suckcc.corestock.inventory.InventoryMovement;
import com.suckcc.corestock.inventory.InventoryMovementRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KardexQueryService {

    private final InventoryMovementRepository movements;

    public KardexQueryService(InventoryMovementRepository movements) {
        this.movements = movements;
    }

    @Transactional(readOnly = true)
    public List<InventoryMovement> search(KardexFilter filter) {
        Specification<InventoryMovement> spec = (root, query, cb) -> {
            if (query != null && !Long.class.equals(query.getResultType())) {
                root.fetch("product", JoinType.INNER);
                root.fetch("warehouse", JoinType.INNER);
                root.fetch("location", JoinType.INNER);
            }
            List<Predicate> predicates = new ArrayList<>();
            if (filter != null) {
                if (filter.sku() != null && !filter.sku().isBlank()) {
                    predicates.add(cb.equal(root.get("product").get("sku"), filter.sku().trim()));
                }
                if (filter.warehouseId() != null) {
                    predicates.add(cb.equal(root.get("warehouse").get("id"), filter.warehouseId()));
                }
                if (filter.lotNumber() != null && !filter.lotNumber().isBlank()) {
                    predicates.add(cb.equal(root.get("lotNumber"), filter.lotNumber().trim()));
                }
                if (filter.serialCode() != null && !filter.serialCode().isBlank()) {
                    predicates.add(cb.equal(root.get("serialCode"), filter.serialCode().trim()));
                }
                if (filter.fromDate() != null) {
                    Instant start = filter.fromDate().atStartOfDay(ZoneOffset.UTC).toInstant();
                    predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), start));
                }
                if (filter.toDate() != null) {
                    Instant end = filter.toDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
                    predicates.add(cb.lessThan(root.get("occurredAt"), end));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return movements.findAll(spec, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")));
    }
}
