package com.suckcc.corestock.label;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import java.io.ByteArrayOutputStream;
import org.springframework.stereotype.Service;

@Service
public class BarcodeService {

    public static String buildPayload(String sku, String lotNumber) {
        String cleanSku = sku == null ? "" : sku.trim();
        if (cleanSku.isEmpty()) {
            throw new IllegalArgumentException("SKU is required for barcode payload");
        }
        String cleanLot = lotNumber == null ? "" : lotNumber.trim();
        return cleanLot.isEmpty() ? cleanSku : cleanSku + "|" + cleanLot;
    }

    public byte[] code128Png(String payload, int width, int height) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Barcode payload cannot be blank");
        }
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(payload.trim(), BarcodeFormat.CODE_128, width, height);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to render Code 128 barcode", ex);
        }
    }
}
