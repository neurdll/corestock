# 📦 CoreStock — IMS / WMS (Inventory & Warehouse Management System)

Sistema de gestión de inventarios y almacenes diseñado bajo principios de **contabilidad de movimientos (Kardex inmutable)**, trazabilidad granular (lotes y números de serie) y control estricto de concurrencia. 

Pensado para operar desde tiendas y ferreterías hasta refaccionarias y centros de distribución multi-almacén.

## 🎯 Propósito del Proyecto y Arquitectura

A diferencia de un CRUD tradicional donde el stock es un simple contador mutable, este sistema modela el inventario como un **libro mayor de transacciones**:

* **Kardex Inmutable:** El stock actual nunca se edita directamente; es el resultado auditado de cada entrada, salida, ajuste o traspaso registrado.
* **Control de Concurrencia:** Prevención de *race conditions* y stock negativo ante operaciones simultáneas mediante bloqueos optimistas/pesimistas a nivel de base de datos y transacciones ACID.
* **Trazabilidad Dual:** Soporte configurable por SKU para seguimiento por **Lotes** (con caducidad y despacho FIFO/FEFO) o por **Número de Serie** (unidad individual).
* **Traspasos en Dos Fases:** Los movimientos entre sucursales pasan por un estado intermedio `EN_TRÁNSITO`, asegurando que la mercancía no pueda venderse dos veces ni desaparezca del balance global.

## 🛠️ Stack Tecnológico

* **Lenguaje:** Java 25+
* **Framework Backend:** Spring Boot 4.x (Spring Web, Spring Data JPA, Bean Validation)
* **Motor de Plantillas / UI:** Thymeleaf + HTML / CSS (con soporte de eventos de teclado para escáneres HID)
* **Base de Datos:** PostgreSQL 18 (alpine)
* **Herramientas & DevOps:** Docker & Docker Compose (Spring Boot Docker Compose Support), Lombok, Maven/Gradle
* **Generación de Documentos:** ZXing / OpenPDF (para generación de etiquetas Code 128 y reportes Kardex)

## ✨ Alcance Funcional (MVP)

### 1. Catálogo e Inventario Base
* Gestión de productos (SKUs), categorías, unidades de medida y reglas de seguimiento (`ESTÁNDAR`, `POR_LOTE`, `POR_SERIE`).
* Cálculo automático de **Costo Promedio Ponderado** en cada recepción de compra.
* Distinción entre **Stock Físico**, **Stock Comprometido** (reservado) y **Stock Disponible**.

### 2. Estructura Multi-Almacén (WMS)
* Administración de múltiples almacenes/sucursales.
* Jerarquía de ubicaciones internas: `Almacén > Zona > Pasillo > Rack > Bin`.

### 3. Operaciones de Piso (Movimientos)
* **Entradas (Recepción):** Ingreso de mercancía contra orden de compra, asignando ubicación, lote/caducidad o lectura rápida de números de serie.
* **Salidas (Despacho / Picking):** Egreso de mercancía aplicando reglas automáticas **FIFO / FEFO** y validación de disponibilidad real.
* **Traspasos entre Almacenes:** Emisión de salida (`EN_TRÁNSITO`) en origen y confirmación de recepción en destino.
* **Ajustes de Inventario:** Correcciones por merma, daño o conteo cíclico con motivo obligatorio y registro de auditoría.

### 4. Códigos de Barras y Auditoría
* Generación e impresión de etiquetas con código de barras **Code 128** por SKU o Lote.
* Interfaz de escaneo rápido optimizada para lectores láser USB/Bluetooth (emulación de teclado).
* Consulta de **Kardex histórico** filtrable por producto, almacén, lote, serie y rango de fechas.

## 🗄️ Modelo de Datos (Entidades Principales)

* `Product`: Definición del SKU, costo promedio y tipo de trazabilidad.
* `Warehouse` & `StorageLocation`: Estructura física de almacenes y ubicaciones (`Bin`).
* `InventoryBalance`: Foto del stock actual por `(Producto + Ubicación + Lote)` con control de versión (`@Version`) para concurrencia.
* `SerialNumber`: Registro individual por unidad serializada y su estado actual (`AVAILABLE`, `RESERVED`, `SOLD`, `IN_TRANSIT`).
* `InventoryMovement`: Registro inmutable de cada transacción (tipo, cantidad, costo unitario, usuario, timestamp y documento de referencia).
* `TransferOrder`: Cabecera y detalle para el control de traspasos en dos pasos.
