package billing.model;

import java.math.BigDecimal;

/**
 * Một lần thanh toán. Một hóa đơn có thể có NHIỀU đối tượng kiểu này
 * (ví dụ: 200.000 tiền mặt + phần còn lại quẹt thẻ).
 *
 * `method` quyết định app sẽ ghi thêm vào bảng con nào:
 *      CASH    -> Cash_Payment
 *      CARD    -> Card_Payment
 *      EWALLET -> EWallet_Payment
 *
 * HỢP ĐỒNG TIỀN MẶT (plan/14 mục 3.1) -- ba con số, ba chỗ ở:
 *      tendered = Cash_Payment.Tendered_Amount   tiền khách ĐƯA   (thu ngân gõ)
 *      amount   = Payment.Amount                 tiền GHI NHẬN   (app tính)
 *      change   = tendered - amount              tiền THỐI       (không lưu ở đâu)
 *
 * Trước khối 5, `detail1` được đặt BẰNG `amount` nên cột "khách đưa bao nhiêu
 * tiền" của database chỉ đúng khi thu ngân làm thêm một thao tác tay mà không
 * có gì nhắc (plan/14 lỗi #9).
 */
public class PaymentEntry {

    public static final String CASH    = "CASH";
    public static final String CARD    = "CARD";
    public static final String EWALLET = "EWALLET";

    public String     method;
    /** Tiền GHI NHẬN -- vào Payment.Amount, và fn_invoice_amount_paid đọc nó. */
    public BigDecimal amount;
    /** Tiền khách ĐƯA. {@code null} cho thẻ / ví điện tử (hai phương thức đó không có). */
    public BigDecimal tendered;
    public String     detail1;   // CARD: số thẻ | EWALLET: nhà cung cấp  (CASH: không dùng)
    public String     detail2;   // CARD: mã chuẩn chi   | EWALLET: mã giao dịch

    /**
     * Hàm dựng đầy đủ.
     *
     * @param tendered tiền khách đưa, {@code null} cho thẻ / ví
     */
    public PaymentEntry(String method, BigDecimal amount, BigDecimal tendered,
                        String detail1, String detail2) {
        this.method   = method;
        this.amount   = amount;
        this.tendered = tendered;
        this.detail1  = detail1;
        this.detail2  = detail2;
    }

    /** Hàm dựng cũ (4 tham số) -- giữ lại để không phá chỗ gọi nào. */
    public PaymentEntry(String method, BigDecimal amount, String detail1, String detail2) {
        this(method, amount, null, detail1, detail2);
    }

    /**
     * Giá trị truyền cho tham số {@code p_detail1} của {@code sp_add_payment}.
     *
     * <p>CHỖ DUY NHẤT biết rằng thủ tục nhận tiền khách đưa dưới dạng
     * <b>chuỗi</b>: {@code sp_add_payment} giữ nguyên 6 tham số và vẫn
     * {@code CAST(p_detail1 AS DECIMAL(12,2))} cho tiền mặt (docs/05 Quy tắc 2
     * -- đổi chữ ký thủ tục là phá hợp đồng mà không đổi được gì).
     *
     * <p>Cái đổi ở khối 5 là <b>nguồn</b> của con số đó: một {@code BigDecimal}
     * có kiểu, không phải một bản sao của {@code amount} (plan/14 lỗi #9).
     */
    public String wireDetail1() {
        if (CASH.equals(method)) {
            BigDecimal t = (tendered != null) ? tendered : amount;
            return t == null ? null : t.toPlainString();
        }
        return detail1;
    }

    /** Tiền thối = khách đưa − ghi nhận. {@code 0} khi không có tiền khách đưa. */
    public BigDecimal change() {
        if (tendered == null || amount == null) return BigDecimal.ZERO;
        BigDecimal c = tendered.subtract(amount);
        return c.signum() < 0 ? BigDecimal.ZERO : c;
    }

    /**
     * Nhãn hiển thị -- đi qua {@link PayMethod} nên KHỚP TỪNG CHỮ với chuỗi mà
     * {@code sp_get_invoice_payments} trả về (plan/14 mục 3.6).
     *
     * <p>Không khớp phương thức nào thì trả nguyên mã, chứ không ném: một giá
     * trị lạ trong cột không được phép làm sập màn hình.
     */
    public String methodLabel() {
        PayMethod m = PayMethod.of(method);
        return m == null ? method : m.label();
    }
}
