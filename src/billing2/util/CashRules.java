package billing2.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * LUẬT TIỀN MẶT / SỐ LƯỢNG / THẺ của tab Lập hóa đơn -- dạng thuần (không Swing, không DB).
 *
 *  1. Khách đưa dư có hợp lý không (checkTendered): hợp lý khi ghép được từ các tờ tiền
 *     Việt Nam mà BỎ BẤT KỲ TỜ NÀO cũng không còn đủ trả = mọi tờ đều lớn hơn tiền thối.
 *         38.000 đưa  45.000 -> 20+20+5: bỏ tờ 5.000 vẫn đủ      -> SUSPICIOUS
 *         48.000 đưa 500.000 -> một tờ 500.000                    -> OK
 *        510.000 đưa 600.000 -> 500+100: bỏ tờ nào cũng thiếu     -> OK
 *     Thối >= 500.000 thì không tờ nào lớn hơn nó -> REJECT (chặn luôn số gõ nhầm cực lớn).
 *  2. Tách tiền thối thành các tờ (breakdown) -- nút "Change".
 *  3. Số lượng theo đơn vị (quantityProblem): KG / LITER cho số thập phân (tối đa 3 chữ số),
 *     PIECE / PACK / BOX chỉ số nguyên; tối đa 10.000 mỗi dòng.
 *  4. Chi tiết thẻ (cardDetailProblem): 4 số cuối + mã chuẩn chi 6 ký tự. KHÔNG lưu số thẻ đầy đủ.
 */
public final class CashRules {

    private CashRules() { }

    /** Mệnh giá tiền Việt Nam đang lưu hành, nhỏ -> lớn. */
    public static final long[] VND_NOTES = {
        100, 200, 500, 1_000, 2_000, 5_000, 10_000, 20_000, 50_000, 100_000, 200_000, 500_000
    };
    /** Trần của mọi cột tiền DECIMAL(12,2). */
    public static final BigDecimal MAX_MONEY    = new BigDecimal("9999999999.99");
    public static final BigDecimal MAX_LINE_QTY = new BigDecimal("10000");

    public enum Verdict { OK, SUSPICIOUS, REJECT }

    /** Kết luận về tiền khách đưa + câu giải thích cho thu ngân. */
    public record TenderCheck(Verdict verdict, String message) { }

    // ---------------------------------------------------------------
    //  1. Khách đưa dư
    // ---------------------------------------------------------------

    /** @param remaining số còn thiếu của hóa đơn (> 0) */
    public static TenderCheck checkTendered(BigDecimal tendered, BigDecimal remaining) {
        if (tendered.compareTo(MAX_MONEY) > 0) {
            return new TenderCheck(Verdict.REJECT, "The amount " + fmt(tendered)
                    + " is over the limit for one invoice (" + fmt(MAX_MONEY) + ")!");
        }
        BigDecimal change = tendered.subtract(remaining);
        if (change.signum() <= 0) {
            return new TenderCheck(Verdict.OK, null);                   // đưa đúng hoặc đưa một phần
        }
        if (tendered.stripTrailingZeros().scale() > 0) {
            return new TenderCheck(Verdict.REJECT, "Cash has no coins: " + fmt(tendered)
                    + " cannot be paid with banknotes!");
        }
        if (change.compareTo(BigDecimal.valueOf(VND_NOTES[VND_NOTES.length - 1])) >= 0) {
            return new TenderCheck(Verdict.REJECT, "The customer gave " + fmt(tendered) + " for "
                    + fmt(remaining) + " remaining: change " + fmt(change) + " >= 500.000,\n"
                    + "so at least one 500.000 note is extra. Please check the amount again!");
        }

        long p = tendered.longValueExact();
        if (representableWithNotesAbove(p, change)) {
            return new TenderCheck(Verdict.OK, null);
        }

        // Giải thích bằng cách ghép tham lam: luôn có một tờ <= tiền thối.
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
                ? "For example " + fmt(tendered) + " = " + ex + ":\n"
                  + "without the " + fmt(BigDecimal.valueOf(smallest)) + " note there is still "
                  + fmt(tendered.subtract(BigDecimal.valueOf(smallest))) + " >= " + fmt(remaining) + ".\n"
                : "";
        return new TenderCheck(Verdict.SUSPICIOUS, "The customer gave " + fmt(tendered) + " for "
                + fmt(remaining) + " remaining (change " + fmt(change) + ").\n\n"
                + "However it is made up, at least one banknote is EXTRA.\n" + hint);
    }

    /**
     * p có ghép được CHỈ từ các tờ lớn hơn tiền thối không. Các tờ được phép là đoạn cuối của
     * dãy 1-2-5; chia cho ước chung lớn nhất thì quy hoạch động tới 100 là đủ (trên đó luôn ghép được).
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

    // ---------------------------------------------------------------
    //  2. Tách tiền thối
    // ---------------------------------------------------------------

    /** Tách thành các tờ, tờ lớn trước (tham lam -- tối ưu với dãy 1-2-5). @return {mệnh giá, số tờ} */
    public static List<long[]> breakdown(BigDecimal amount) {
        return breakdownLong(amount.setScale(0, RoundingMode.DOWN).longValue());
    }

    /** Phần lẻ dưới tờ nhỏ nhất (100đ, kể cả xu) không tách được. */
    public static BigDecimal breakdownRemainder(BigDecimal amount) {
        long split = 0;
        for (long[] part : breakdown(amount)) split += part[0] * part[1];
        return amount.subtract(BigDecimal.valueOf(split));
    }

    private static List<long[]> breakdownLong(long amount) {
        List<long[]> out = new ArrayList<>();
        long left = amount;
        for (int i = VND_NOTES.length - 1; i >= 0; i--) {
            if (left >= VND_NOTES[i]) {
                out.add(new long[]{VND_NOTES[i], left / VND_NOTES[i]});
                left %= VND_NOTES[i];
            }
        }
        return out;
    }

    // ---------------------------------------------------------------
    //  3. Số lượng
    // ---------------------------------------------------------------

    /** Đơn vị bán theo số thập phân (cân / đong). */
    public static boolean allowsDecimal(String unit) {
        return "KG".equalsIgnoreCase(unit) || "LITER".equalsIgnoreCase(unit);
    }

    /** @return null nếu hợp lệ, ngược lại là câu báo lỗi */
    public static String quantityProblem(BigDecimal q, String unit) {
        if (q == null || q.signum() <= 0)       return "Quantity must be greater than 0!";
        if (q.compareTo(MAX_LINE_QTY) > 0)      return "At most 10,000 per line!";
        BigDecimal plain = q.stripTrailingZeros();
        if (plain.scale() > 3)                  return "At most 3 decimal places!";
        if (plain.scale() > 0 && !allowsDecimal(unit)) {
            return "Products sold by " + (unit == null ? "PIECE" : unit)
                 + " take whole numbers only (decimals are for KG / LITER)!";
        }
        return null;
    }

    /** Ký tự được gõ vào ô số lượng: chữ số và MỘT dấu , hoặc . (tối đa 3 số lẻ). */
    public static final Pattern QTY_TYPING = Pattern.compile("\\d{0,5}([.,]\\d{0,3})?");

    // ---------------------------------------------------------------
    //  4. Chi tiết thẻ ngân hàng
    // ---------------------------------------------------------------

    private static final Pattern LAST4 = Pattern.compile("\\d{4}");
    private static final Pattern AUTH  = Pattern.compile("[A-Z0-9]{6}");

    /** @return null nếu hợp lệ, ngược lại là câu báo lỗi */
    public static String cardDetailProblem(String last4, String auth) {
        if (last4 == null || !LAST4.matcher(last4.trim()).matches()) {
            return "The last 4 card digits must be exactly 4 digits (e.g. 1234)!";
        }
        if (auth == null || !AUTH.matcher(auth.trim().toUpperCase()).matches()) {
            return "The authorization code must be 6 letters or digits (on the POS slip, e.g. A1B2C3)!";
        }
        return null;
    }

    /** Dạng lưu vào Card_Payment.Card_number. */
    public static String maskedCard(String last4) {
        return "****-****-****-" + last4.trim();
    }

    /** Cùng định dạng với Ui.money ("135.100đ"), chép ở đây để util không phụ thuộc tầng giao diện. */
    private static String fmt(BigDecimal v) {
        DecimalFormatSymbols sym = new DecimalFormatSymbols();
        sym.setGroupingSeparator('.');
        sym.setDecimalSeparator(',');
        return new DecimalFormat("#,##0", sym).format(v) + "đ";
    }
}
