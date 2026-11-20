# Plan: CoreStock IMS/WMS MVP

## Objective
Build an ACID-compliant inventory and warehouse management MVP backed by an immutable Kardex ledger, dual lot/serial traceability, two-phase transfers, Spring Security RBAC, Thymeleaf HID-scanner UI, and Code 128/PDF outputs.

## Architecture & Simplicity Check
- **DOP / Simple State:** JPA entities for persistence; immutable Java `record`s for commands (`ReceiveCommand`, `DispatchCommand`, `StockMath`) and pure functions for weighted-average cost & FIFO/FEFO allocation. Composite `StorageLocation` (warehouse + zone/aisle/rack/bin) instead of a 5-table tree; string `referenceDoc` instead of full PO/SO modules.
- **Dependencies Accepted:** `spring-boot-starter-security`, `zxing` (Code 128), `openpdf` (Kardex PDF). Rejected: MapStruct, CQRS/event-sourcing frameworks, multi-tenant layers.

## Execution Graph
- [ ] ISS-001: PostgreSQL 18 Compose and Flyway core DDL (no dependencies)
- [ ] ISS-002: Spring Security form login with ADMIN/OPERATOR roles (depends on ISS-001)
- [ ] ISS-003: Product catalog CRUD and tracking rules (depends on ISS-001, ISS-002)
- [ ] ISS-004: Warehouse and composite StorageLocation CRUD (depends on ISS-001, ISS-002)
- [ ] ISS-005: Balance, SerialNumber, Movement model & StockMath (depends on ISS-003, ISS-004)
- [ ] ISS-006: Inbound receiving with weighted-avg cost & traceability (depends on ISS-005)
- [ ] ISS-007: Outbound picking with FIFO/FEFO & stock lock (depends on ISS-005)
- [ ] ISS-008: Stock reservation and mandatory-reason adjustments (depends on ISS-005)
- [ ] ISS-009: Two-phase inter-warehouse transfers (EN_TRANSITO) (depends on ISS-006, ISS-007)
- [ ] ISS-010: Filterable Kardex ledger query and audit view (depends on ISS-006..ISS-009)
- [ ] ISS-011: Thymeleaf floor ops UI with HID scanner support (depends on ISS-006..ISS-009)
- [ ] ISS-012: Code 128 barcode label generation via ZXing (depends on ISS-003, ISS-005)
- [ ] ISS-013: Kardex PDF report export via OpenPDF (depends on ISS-010)

## Verification
Run `./mvnw test` against PostgreSQL 18 (concurrent threads on `ISS-006`..`ISS-009` verifying zero negative stock), plus end-to-end UI smoke check for login, scan-receive, FIFO/FEFO pick, two-phase transfer, Code 128 PNG, and Kardex PDF export.
