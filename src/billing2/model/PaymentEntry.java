package billing2.model;

import java.math.BigDecimal;

/**
 * Một lần thanh toán. Một hóa đơn có thể có NHIỀU lần (200.000 tiền mặt + phần còn lại quẹt thẻ).
 *
 * Tiền mặt có ba con số:
 *     tendered = Cash_Payment.Tendered_Amount   tiền khách ĐƯA    (thu ngân gõ)
 *     amount   = Payment.Amount                 tiền GHI NHẬN     (app tính)
 *     change   = tendered - amount              tiền THỐI         (không lưu ở đâu)
 */
public class PaymentEntry {

    public String     method;     // mã PayMethod: CASH / CARD / EWALLET
    public BigDecimal amount;     // tiền GHI NHẬN
    public BigDecimal tendered;   // tiền khách ĐƯA; null với thẻ / ví
    public String     detail1;    // CARD: số thẻ (che) | EWALLET: nhà cung cấp
    public String     detail2;    // CARD: mã chuẩn chi | EWALLET: mã giao dịch

    public PaymentEntry(String method, BigDecimal amount, BigDecimal tendered,
                        String detail1, String detail2) {
        this.method   = method;
        this.amount   = amount;
        this.tendered = tendered;
        this.detail1  = detail1;
        this.detail2  = detail2;
    }

    /** Giá trị cho tham số p_detail1 của sp_add_payment: tiền mặt gửi tiền khách ĐƯA (dạng chuỗi). */
    public String wireDetail1() {
        if (PayMethod.CASH.code().equals(method)) {
            BigDecimal t = (tendered != null) ? tendered : amount;
            return t == null ? null : t.toPlainString();
        }
        return detail1;
    }

    /** Tiền thối = khách đưa - ghi nhận (0 nếu không có tiền khách đưa). */
    public BigDecimal change() {
        if (tendered == null || amount == null) return BigDecimal.ZERO;
        BigDecimal c = tendered.subtract(amount);
        return c.signum() < 0 ? BigDecimal.ZERO : c;
    }

    public String methodLabel() {
        PayMethod m = PayMethod.of(method);
        return m == null ? method : m.label();
    }
}
