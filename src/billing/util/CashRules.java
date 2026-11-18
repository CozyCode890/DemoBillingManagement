package billing.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * ====================================================================
 *  LUẬT TIỀN MẶT / SỐ LƯỢNG CỦA TAB 1 -- dạng THUẦN (không Swing, không DB)
 * ====================================================================
 *  Tách khỏi NewInvoicePanel để tools/check/ContractCheck kiểm trực tiếp,
 *  giống cách splitPayment được tách ra (plan/14 rủi ro #2).
 *
 *  1. KHÁCH ĐƯA DƯ CÓ HỢP LÝ KHÔNG ({@link #checkTendered}):
 *     Một số tiền khách đưa là hợp lý khi ghép được từ các tờ tiền Việt Nam
 *     mà BỎ BẤT KỲ TỜ NÀO RA THÌ KHÔNG CÒN ĐỦ TRẢ -- tức là khách không đưa
 *     thừa tờ nào. Tương đương: mọi tờ đều lớn hơn tiền thối.
 *         38.000 đưa  45.000  -> 20+20+5: bỏ tờ 5.000 còn 40.000, vẫn đủ -> X
 *         48.000 đưa 480.000  -> luôn thừa ít nhất một tờ                -> X
 *         48.000 đưa 500.000  -> một tờ 500.000                          -> OK
 *         48.000 đưa 600.000  -> 500+100: tờ 100.000 thừa                -> X
 *        510.000 đưa 600.000  -> 500+100: bỏ tờ nào cũng thiếu          -> OK
 *     Tiền thối >= 500.000 thì không tờ nào lớn hơn nó -> luôn sai. Nhờ vậy
 *     số tiền gõ nhầm cực lớn (99.999.999.999) bị chặn trước khi chạm cột
 *     DECIMAL(12,2) -- lỗi "out of range" trong note.
 *
 *  2. TÁCH TIỀN THỐI thành các tờ ({@link #breakdown}) -- nút "Thoi tien".
 *
 *  3. SỐ LƯỢNG theo đơn vị ({@link #quantityProblem}): KG / LITER cho số
 *     thập phân (tối đa 3 chữ số, khớp DECIMAL(10,3)); PIECE / PACK / BOX
 *     chỉ số nguyên. Trần 10.000 mỗi dòng.
 */
public final class CashRules {

    private CashRules() { }

    /** Mệnh giá tiền Việt Nam đang lưu hành, từ nhỏ đến lớn (đồng). */
    public static final long[] VND_NOTES = {
        100, 200, 500, 1_000, 2_000, 5_000, 10_000, 20_000,
        50_000, 100_000, 200_000, 500_000
    };

    /** Trần của mọi cột tiền DECIMAL(12,2): 9.999.999.999,99. */
    public static final BigDecimal MAX_MONEY = new BigDecimal("9999999999.99");

    /** Trần số lượng của một dòng hóa đơn. */
    public static final BigDecimal MAX_LINE_QTY = new BigDecimal("10000");

    // =================================================================
    //  1. KHÁCH ĐƯA DƯ
    // =================================================================

    /** Kết luận về một số tiền khách đưa. */
    public enum Verdict {
        /** Không thối, hoặc thối hợp lý. */
        OK,
        /** Có tờ thừa: hỏi lại thu ngân (có thể là "đưa lẻ để nhận chẵn"). */
        SUSPICIOUS,
        /** Không thể đúng: thối >= 500.000, có xu lẻ, hoặc vượt DECIMAL(12,2). */
        REJECT
    }

    /** Verdict kèm câu giải thích để hiện cho thu ngân. */
    public static final class TenderCheck {
        public final Verdict verdict;
        public final String  message;

        TenderCheck(Verdict verdict, String message) {
            this.verdict = verdict;
            this.message = message;
        }
    }

    /**
     * @param tendered  tiền khách đưa
     * @param remaining số còn thiếu của hóa đơn (> 0)
     */
    public static TenderCheck checkTendered(BigDecimal tendered, BigDecimal remaining) {
        if (tendered.compareTo(MAX_MONEY) > 0) {
            return new TenderCheck(Verdict.REJECT,
                    "So tien " + fmt(tendered) + " vuot gioi han cot tien cua database ("
                  + fmt(MAX_MONEY) + ").");
        }
        BigDecimal change = tendered.subtract(remaining);
        if (change.signum() <= 0) {
            return new TenderCheck(Verdict.OK, null);         // dua dung hoac dua mot phan
        }
        if (tendered.stripTrailingZeros().scale() > 0) {
            return new TenderCheck(Verdict.REJECT,
                    "Tien mat khong co xu le: " + fmt(tendered) + " khong dua duoc bang to tien.");
        }
        if (change.compareTo(BigDecimal.valueOf(VND_NOTES[VND_NOTES.length - 1])) >= 0) {
            return new TenderCheck(Verdict.REJECT,
                    "Khach dua " + fmt(tendered) + " cho so con thieu " + fmt(remaining)
                  + ": tien thoi " + fmt(change) + " >= 500.000,\n"
                  + "tuc la khach dua thua it nhat mot to 500.000. Kiem tra lai so tien.");
        }

        long p = tendered.longValueExact();
        if (representableWithNotesAbove(p, change)) {
            return new TenderCheck(Verdict.OK, null);
        }

        // Giai thich bang cach ghep tham lam: luon co mot to <= tien thoi.
        List<long[]> parts = breakdownLong(p);
        long smallest = parts.isEmpty() ? 0 : parts.get(parts.size() - 1)[0];
        StringBuilder ex = new StringBuilder();
        for (long[] part : parts) {
            for (int i = 0; i < part[1]; i++) {
                if (ex.length() > 0) ex.append(" + ");
                ex.append(fmt(BigDecimal.valueOf(part[0])));
            }
        }
        String hint = (smallest > 0 && change.compareTo(BigDecimal.valueOf(smallest)) >= 0)
                ? "Vi du " + fmt(tendered) + " = " + ex + ":\n"
                  + "bo to " + fmt(BigDecimal.valueOf(smallest)) + " ra van con "
                  + fmt(tendered.subtract(BigDecimal.valueOf(smallest))) + " >= "
                  + fmt(remaining) + ".\n"
                : "";
        return new TenderCheck(Verdict.SUSPICIOUS,
                "Khach dua " + fmt(tendered) + " cho so con thieu " + fmt(remaining)
              + " (thoi " + fmt(change) + ").\n\n"
              + "Du ghep bang cach nao, khach cung dua THUA it nhat mot to tien.\n"
              + hint);
    }

    /**
     * {@code p} có ghép được chỉ từ các tờ LỚN HƠN {@code change} không.
     * Các tờ được phép là một đoạn cuối của dãy 1-2-5; chia cho ước chung lớn
     * nhất g thì tập đơn vị luôn chứa 1, hoặc chứa {2, 5} (khi đó chỉ 1 và 3
     * là không ghép được). Quy hoạch động tới 100 là đủ; trên đó luôn ghép được.
     */
    static boolean representableWithNotesAbove(long p, BigDecimal change) {
        List<Long> allowed = new ArrayList<>();
        for (long n : VND_NOTES) {
            if (BigDecimal.valueOf(n).compareTo(change) > 0) allowed.add(n);
        }
        if (allowed.isEmpty()) return false;
        long g = 0;
        for (long n : allowed) g = gcd(g, n);
        if (p % g != 0) return false;
        long units = p / g;
        if (units > 100) return true;
        boolean[] ok = new boolean[(int) units + 1];
        ok[0] = true;
        for (int v = 1; v <= units; v++) {
            for (long n : allowed) {
                int u = (int) (n / g);
                if (u <= v && ok[v - u]) { ok[v] = true; break; }
            }
        }
        return ok[(int) units];
    }

    private static long gcd(long a, long b) {
        while (b != 0) { long t = a % b; a = b; b = t; }
        return a;
    }

    // =================================================================
    //  2. TÁCH TIỀN THỐI
    // =================================================================

    /**
     * Tách {@code amount} thành các tờ, tờ lớn trước (tham lam -- tối ưu với
     * dãy 1-2-5). Phần lẻ dưới 100 đồng không tách được thì bỏ qua; xem
     * {@link #breakdownRemainder}.
     *
     * @return danh sách {mệnh giá, số tờ}
     */
    public static List<long[]> breakdown(BigDecimal amount) {
        long whole = amount.setScale(0, RoundingMode.DOWN).longValue();
        return breakdownLong(whole);
    }

    /** Phần không tách được thành tờ (dưới 100 đồng, kể cả xu). */
    public static BigDecimal breakdownRemainder(BigDecimal amount) {
        long split = 0;
        for (long[] part : breakdown(amount)) split += part[0] * part[1];
        return amount.subtract(BigDecimal.valueOf(split));
    }

    private static List<long[]> breakdownLong(long amount) {
        List<long[]> out = new ArrayList<>();
        long left = amount;
        for (int i = VND_NOTES.length - 1; i >= 0; i--) {
            long n = VND_NOTES[i];
            if (left >= n) {
                out.add(new long[]{n, left / n});
                left %= n;
            }
        }
        return out;
    }

    // =================================================================
    //  3. SỐ LƯỢNG
    // =================================================================

    /** Đơn vị bán theo số thập phân (cân / đong). */
    public static boolean allowsDecimal(String unit) {
        return "KG".equalsIgnoreCase(unit) || "LITER".equalsIgnoreCase(unit);
    }

    /**
     * @return {@code null} nếu hợp lệ, ngược lại là câu báo lỗi
     */
    public static String quantityProblem(BigDecimal q, String unit) {
        if (q == null || q.signum() <= 0) {
            return "So luong phai lon hon 0.";
        }
        if (q.compareTo(MAX_LINE_QTY) > 0) {
            return "So luong moi dong toi da 10.000.";
        }
        BigDecimal plain = q.stripTrailingZeros();
        if (plain.scale() > 3) {
            return "Toi da 3 chu so thap phan (cot DECIMAL(10,3)).";
        }
        if (plain.scale() > 0 && !allowsDecimal(unit)) {
            return "San pham tinh theo " + (unit == null ? "cai" : unit)
                 + " chi ban so nguyen (so thap phan chi cho KG / LITER).";
        }
        return null;
    }

    /** Ký tự hợp lệ của ô số lượng khi đang gõ: chữ số và MỘT dấu , hoặc . */
    public static final Pattern QTY_TYPING = Pattern.compile("\\d{0,5}([.,]\\d{0,3})?");

    // =================================================================
    //  4. CHI TIẾT THẺ NGÂN HÀNG
    // =================================================================

    private static final Pattern LAST4 = Pattern.compile("\\d{4}");
    private static final Pattern AUTH  = Pattern.compile("[A-Z0-9]{6}");

    /**
     * Kiểm 4 số cuối thẻ và mã chuẩn chi (approval code) in trên phiếu máy
     * POS. App KHÔNG lưu số thẻ đầy đủ -- chỉ 4 số cuối, đúng thông lệ.
     *
     * @return {@code null} nếu hợp lệ, ngược lại là câu báo lỗi
     */
    public static String cardDetailProblem(String last4, String auth) {
        if (last4 == null || !LAST4.matcher(last4.trim()).matches()) {
            return "4 so cuoi cua the phai dung 4 chu so (vi du 1234).";
        }
        if (auth == null || !AUTH.matcher(auth.trim().toUpperCase()).matches()) {
            return "Ma chuan chi phai gom 6 ky tu chu hoac so (in tren phieu may POS, vi du A1B2C3).";
        }
        return null;
    }

    /** Dạng lưu vào Card_Payment.Card_number (VARCHAR(25)). */
    public static String maskedCard(String last4) {
        return "****-****-****-" + last4.trim();
    }

    /**
     * Cùng định dạng với {@code Ui.money} ("135.100đ"), chép lại ở đây để lớp
     * util không phụ thuộc ngược vào tầng giao diện.
     */
    private static String fmt(BigDecimal v) {
        java.text.DecimalFormatSymbols sym = new java.text.DecimalFormatSymbols();
        sym.setGroupingSeparator('.');
        sym.setDecimalSeparator(',');
        return new java.text.DecimalFormat("#,##0", sym).format(v) + "đ";
    }
}
