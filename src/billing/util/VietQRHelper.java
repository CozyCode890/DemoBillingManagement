package billing.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class VietQRHelper {
    public static final String DEFAULT_BIN = "970422";
    public static final String DEFAULT_ACCOUNT = "90902241107";

    private static String tlv(String tag, String value) {
        if (value == null) value = "";
        return String.format("%s%02d%s", tag, value.length(), value);
    }

    private static String calcCRC16(String str) {
        int crc = 0xFFFF;
        for (byte b : str.getBytes(StandardCharsets.US_ASCII)) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x8000) != 0) {
                    crc = (crc << 1) ^ 0x1021;
                } else {
                    crc <<= 1;
                }
                crc &= 0xFFFF;
            }
        }
        return String.format("%04X", crc);
    }

    public static String buildEMVCoPayload(String bin, String accountNo, long amount, String ref) {
        if (bin == null || bin.isBlank())             bin = DEFAULT_BIN;
        if (accountNo == null || accountNo.isBlank()) accountNo = DEFAULT_ACCOUNT;

        String subBeneficiary = tlv("00", bin) + tlv("01", accountNo);
        String tag38 = tlv("00", "A000000727") + tlv("01", subBeneficiary) + tlv("02", "QRIBFTTA");

        String tag62 = tlv("08", ref);

        StringBuilder payload = new StringBuilder();
        payload.append(tlv("00", "01"));
        payload.append(tlv("01", "12"));
        payload.append(tlv("38", tag38));
        payload.append(tlv("53", "704"));
        payload.append(tlv("54", String.valueOf(amount)));
        payload.append(tlv("58", "VN"));
        payload.append(tlv("62", tag62));
        payload.append("6304");

        String crc = calcCRC16(payload.toString());
        payload.append(crc);
        return payload.toString();
    }

    public static BufferedImage generateQRCodeImage(String bin, String accountNo,
                                                    long amount, String ref, int size)
            throws Exception {
        String emvcoData = buildEMVCoPayload(bin, accountNo, amount, ref);
        QRCodeWriter qrWriter = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 1);

        BitMatrix matrix = qrWriter.encode(emvcoData, BarcodeFormat.QR_CODE, size, size, hints);
        return MatrixToImageWriter.toBufferedImage(matrix);
    }
}
