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

/**
 * Sinh mã VietQR (EMVCo / Napas 247).
 *
 * <p><b>Tài khoản nhận tiền là THAM SỐ, không phải hằng số.</b> Trước khối 5
 * hai hằng số dưới đây là đường DUY NHẤT, nên mọi mã QR đều trỏ về một số tài
 * khoản ghi cứng trong mã nguồn và không có cách nào đổi mà không biên dịch lại
 * -- kể cả khi {@code config.properties} có khoá đúng (plan/14 lỗi #27).
 *
 * <p>Hai hằng số vẫn ở lại, nhưng với vai trò mới: <b>giá trị mặc định</b> mà
 * {@code QrPaymentDialog} truyền cho {@code AppConfig.get(...)}.
 */
public class VietQRHelper {

    /** MB Bank -- giá trị mặc định của khoá {@code qr.bank.bin}. */
    public static final String DEFAULT_BIN = "970422";
    /** Giá trị mặc định của khoá {@code qr.account.no}. */
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

    /**
     * Sinh chuỗi EMVCo Napas247 chuẩn quét được trên MoMo và tất cả App Ngân Hàng.
     *
     * <p>Hai tham số {@code bin} / {@code accountNo} đặt ở ĐẦU danh sách, trước
     * {@code amount}: đây là một API nội bộ với 2 chỗ gọi, không phải thủ tục
     * SQL có hợp đồng thứ tự tham số, nên đặt <i>chủ thể</i> trước <i>số tiền</i>
     * đọc tự nhiên hơn (plan/14 mục 5.6f).
     *
     * @param bin       mã BIN ngân hàng, rỗng/null = {@link #DEFAULT_BIN}
     * @param accountNo số tài khoản, rỗng/null = {@link #DEFAULT_ACCOUNT}
     */
    public static String buildEMVCoPayload(String bin, String accountNo, long amount, String ref) {
        if (bin == null || bin.isBlank())             bin = DEFAULT_BIN;
        if (accountNo == null || accountNo.isBlank()) accountNo = DEFAULT_ACCOUNT;

        // Tag 38: Beneficiary Info (Napas VietQR)
        String subBeneficiary = tlv("00", bin) + tlv("01", accountNo);
        String tag38 = tlv("00", "A000000727") + tlv("01", subBeneficiary) + tlv("02", "QRIBFTTA");

        // Tag 62: Additional Data (Ref / Nội dung CK)
        String tag62 = tlv("08", ref);

        StringBuilder payload = new StringBuilder();
        payload.append(tlv("00", "01"));         // Format
        payload.append(tlv("01", "12"));         // Dynamic QR
        payload.append(tlv("38", tag38));        // Merchant info
        payload.append(tlv("53", "704"));        // Currency VND
        payload.append(tlv("54", String.valueOf(amount))); // Amount
        payload.append(tlv("58", "VN"));         // Country
        payload.append(tlv("62", tag62));        // Reference
        payload.append("6304");                  // CRC header

        String crc = calcCRC16(payload.toString());
        payload.append(crc);
        return payload.toString();
    }

    /**
     * Tạo BufferedImage từ chuỗi EMVCo bằng ZXing.
     *
     * @param bin       mã BIN ngân hàng (từ {@code qr.bank.bin})
     * @param accountNo số tài khoản nhận tiền (từ {@code qr.account.no})
     */
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