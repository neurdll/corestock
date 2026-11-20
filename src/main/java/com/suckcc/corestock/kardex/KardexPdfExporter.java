package com.suckcc.corestock.kardex;

import com.lowagie.text.Document;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.suckcc.corestock.inventory.InventoryMovement;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class KardexPdfExporter {

    public byte[] export(KardexFilter filter, List<InventoryMovement> movements) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Document doc = new Document(PageSize.A4.rotate())) {
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new Paragraph("CoreStock — Kardex Audit Report", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
            doc.add(new Paragraph("Generated at: " + Instant.now() + " | Filters: " + formatFilter(filter)));
            doc.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(9);
            table.setWidthPercentage(100);
            for (String h : new String[]{"Timestamp", "Type", "SKU", "Warehouse", "Location", "Lot/Serial", "Qty", "Cost", "Operator"}) {
                table.addCell(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
            }
            for (InventoryMovement m : movements) {
                table.addCell(m.getOccurredAt().toString());
                table.addCell(m.getMovementType().name());
                table.addCell(m.getProduct().getSku());
                table.addCell(m.getWarehouse().getCode());
                table.addCell(m.getLocation().getCode());
                table.addCell(m.getSerialCode() != null ? m.getSerialCode() : m.getLotNumber());
                table.addCell(m.getQuantity().toPlainString());
                table.addCell(m.getUnitCost().toPlainString());
                table.addCell(m.getOperatorUsername());
            }
            doc.add(table);
        }
        return out.toByteArray();
    }

    private static String formatFilter(KardexFilter f) {
        if (f == null) return "none";
        return "sku=" + safe(f.sku())
            + ", warehouseId=" + (f.warehouseId() == null ? "all" : f.warehouseId())
            + ", lot=" + safe(f.lotNumber())
            + ", serial=" + safe(f.serialCode())
            + ", from=" + (f.fromDate() == null ? "-" : f.fromDate())
            + ", to=" + (f.toDate() == null ? "-" : f.toDate());
    }

    private static String safe(String v) {
        return v == null || v.isBlank() ? "*" : v.trim();
    }
}
