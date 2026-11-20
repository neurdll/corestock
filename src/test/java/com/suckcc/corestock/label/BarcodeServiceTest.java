package com.suckcc.corestock.label;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class BarcodeServiceTest {

    private final BarcodeService barcodeService = new BarcodeService();

    @Test
    void encodesAndDecodesCode128PayloadRoundTrip() throws Exception {
        String payload = BarcodeService.buildPayload("SKU-900", "LOT-2026A");
        byte[] png = barcodeService.code128Png(payload, 360, 100);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image).isNotNull();

        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        Result decoded = new MultiFormatReader().decode(bitmap);
        assertThat(decoded.getText()).isEqualTo("SKU-900|LOT-2026A");
    }
}
