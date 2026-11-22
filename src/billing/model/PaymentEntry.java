package billing.model;

import java.math.BigDecimal;

public class PaymentEntry {
    public static final String CASH    = "CASH";
    public static final String CARD    = "CARD";
    public static final String EWALLET = "EWALLET";

    public String     method;
    public BigDecimal amount;
    public BigDecimal tendered;
    public String     detail1;
    public String     detail2;

    public PaymentEntry(String method, BigDecimal amount, BigDecimal tendered,
                        String detail1, String detail2) {
        this.method   = method;
        this.amount   = amount;
        this.tendered = tendered;
        this.detail1  = detail1;
        this.detail2  = detail2;
    }

    public PaymentEntry(String method, BigDecimal amount, String detail1, String detail2) {
        this(method, amount, null, detail1, detail2);
    }

    public String wireDetail1() {
        if (CASH.equals(method)) {
            BigDecimal t = (tendered != null) ? tendered : amount;
            return t == null ? null : t.toPlainString();
        }
        return detail1;
    }

    public BigDecimal change() {
        if (tendered == null || amount == null) return BigDecimal.ZERO;
        BigDecimal c = tendered.subtract(amount);
        return c.signum() < 0 ? BigDecimal.ZERO : c;
    }

    public String methodLabel() {
        PayMethod m = PayMethod.of(method);
        return m == null ? method : m.label();
    }

    public String methodDisplayLabel() {
        PayMethod m = PayMethod.of(method);
        return m == null ? method : m.displayLabel();
    }
}
