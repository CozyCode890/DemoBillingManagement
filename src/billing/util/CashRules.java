package billing.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class CashRules {
    private CashRules() { }

    public static final long[] VND_NOTES = {
        100, 200, 500, 1_000, 2_000, 5_000, 10_000, 20_000,
        50_000, 100_000, 200_000, 500_000
    };

    public static final BigDecimal MAX_MONEY = new BigDecimal("9999999999.99");

    public static final BigDecimal MAX_LINE_QTY = new BigDecimal("10000");

    public enum Verdict {
        OK,
        SUSPICIOUS,
        REJECT
    }

    public static final class TenderCheck {
        public final Verdict verdict;
        public final String  message;

        TenderCheck(Verdict verdict, String message) {
            this.verdict = verdict;
            this.message = message;
        }
    }

    public static TenderCheck checkTendered(BigDecimal tendered, BigDecimal remaining) {
        if (tendered.compareTo(MAX_MONEY) > 0) {
            return new TenderCheck(Verdict.REJECT,
                    Lang.pick("Số tiền " + fmt(tendered) + " vượt mức tối đa của một hóa đơn ("
                            + fmt(MAX_MONEY) + ").",
                              "The amount " + fmt(tendered) + " is over the limit for one invoice ("
                            + fmt(MAX_MONEY) + ")!"));
        }
        BigDecimal change = tendered.subtract(remaining);
        if (change.signum() <= 0) {
            return new TenderCheck(Verdict.OK, null);
        }
        if (tendered.stripTrailingZeros().scale() > 0) {
            return new TenderCheck(Verdict.REJECT,
                    Lang.pick("Tiền mặt không có xu lẻ: " + fmt(tendered) + " không đưa được bằng tờ tiền.",
                              "Cash has no coins: " + fmt(tendered) + " cannot be paid with banknotes!"));
        }
        if (change.compareTo(BigDecimal.valueOf(VND_NOTES[VND_NOTES.length - 1])) >= 0) {
            return new TenderCheck(Verdict.REJECT,
                    Lang.pick("Khách đưa " + fmt(tendered) + " cho số còn thiếu " + fmt(remaining)
                            + ": tiền thối " + fmt(change) + " >= 500.000,\n"
                            + "tức là khách đưa thừa ít nhất một tờ 500.000. Kiểm tra lại số tiền.",
                              "The customer gave " + fmt(tendered) + " for " + fmt(remaining)
                            + " remaining: change " + fmt(change) + " >= 500.000,\n"
                            + "so at least one 500.000 note is extra. Please check the amount again!"));
        }

        long p = tendered.longValueExact();
        if (representableWithNotesAbove(p, change)) {
            return new TenderCheck(Verdict.OK, null);
        }

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
                ? Lang.pick("Ví dụ " + fmt(tendered) + " = " + ex + ":\n"
                          + "bỏ tờ " + fmt(BigDecimal.valueOf(smallest)) + " ra vẫn còn "
                          + fmt(tendered.subtract(BigDecimal.valueOf(smallest))) + " >= "
                          + fmt(remaining) + ".\n",
                            "For example " + fmt(tendered) + " = " + ex + ":\n"
                          + "without the " + fmt(BigDecimal.valueOf(smallest)) + " note there is still "
                          + fmt(tendered.subtract(BigDecimal.valueOf(smallest))) + " >= "
                          + fmt(remaining) + ".\n")
                : "";
        return new TenderCheck(Verdict.SUSPICIOUS,
                Lang.pick("Khách đưa " + fmt(tendered) + " cho số còn thiếu " + fmt(remaining)
                        + " (thối " + fmt(change) + ").\n\n"
                        + "Dù ghép bằng cách nào, khách cũng đưa THỪA ít nhất một tờ tiền.\n",
                          "The customer gave " + fmt(tendered) + " for " + fmt(remaining)
                        + " remaining (change " + fmt(change) + ").\n\n"
                        + "However it is made up, at least one banknote is EXTRA.\n")
              + hint);
    }

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

    public static List<long[]> breakdown(BigDecimal amount) {
        long whole = amount.setScale(0, RoundingMode.DOWN).longValue();
        return breakdownLong(whole);
    }

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

    public static boolean allowsDecimal(String unit) {
        return "KG".equalsIgnoreCase(unit) || "LITER".equalsIgnoreCase(unit);
    }

    public static String quantityProblem(BigDecimal q, String unit) {
        if (q == null || q.signum() <= 0) {
            return Lang.pick("Số lượng phải lớn hơn 0.", "Quantity must be greater than 0!");
        }
        if (q.compareTo(MAX_LINE_QTY) > 0) {
            return Lang.pick("Số lượng mỗi dòng tối đa 10.000.", "At most 10,000 per line!");
        }
        BigDecimal plain = q.stripTrailingZeros();
        if (plain.scale() > 3) {
            return Lang.pick("Tối đa 3 chữ số thập phân.", "At most 3 decimal places!");
        }
        if (plain.scale() > 0 && !allowsDecimal(unit)) {
            String u = unit == null ? "PIECE" : unit;
            return Lang.pick("Sản phẩm tính theo " + u
                           + " chỉ bán số nguyên (số thập phân chỉ cho KG / LITER).",
                             "Products sold by " + u
                           + " take whole numbers only (decimals are for KG / LITER)!");
        }
        return null;
    }

    public static final Pattern QTY_TYPING = Pattern.compile("\\d{0,5}([.,]\\d{0,3})?");

    private static final Pattern LAST4 = Pattern.compile("\\d{4}");
    private static final Pattern AUTH  = Pattern.compile("[A-Z0-9]{6}");

    public static String cardDetailProblem(String last4, String auth) {
        if (last4 == null || !LAST4.matcher(last4.trim()).matches()) {
            return Lang.pick("4 số cuối của thẻ phải đúng 4 chữ số (ví dụ 1234).",
                             "The last 4 card digits must be exactly 4 digits (e.g. 1234)!");
        }
        if (auth == null || !AUTH.matcher(auth.trim().toUpperCase()).matches()) {
            return Lang.pick("Mã chuẩn chi phải gồm 6 ký tự chữ hoặc số (in trên phiếu máy POS, ví dụ A1B2C3).",
                             "The authorization code must be 6 letters or digits (on the POS slip, e.g. A1B2C3)!");
        }
        return null;
    }

    public static String maskedCard(String last4) {
        return "****-****-****-" + last4.trim();
    }

    private static String fmt(BigDecimal v) {
        java.text.DecimalFormatSymbols sym = new java.text.DecimalFormatSymbols();
        sym.setGroupingSeparator('.');
        sym.setDecimalSeparator(',');
        return new java.text.DecimalFormat("#,##0", sym).format(v) + "đ";
    }
}
