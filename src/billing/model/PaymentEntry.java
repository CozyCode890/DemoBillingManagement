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
 */
public class PaymentEntry {

    public static final String CASH    = "CASH";
    public static final String CARD    = "CARD";
    public static final String EWALLET = "EWALLET";

    public String     method;
    public BigDecimal amount;
    public String     detail1;   // CASH: tiền khách đưa | CARD: số thẻ | EWALLET: nhà cung cấp
    public String     detail2;   // CARD: mã chuẩn chi   | EWALLET: mã giao dịch

    public PaymentEntry(String method, BigDecimal amount, String detail1, String detail2) {
        this.method  = method;
        this.amount  = amount;
        this.detail1 = detail1;
        this.detail2 = detail2;
    }

    public String methodLabel() {
        if (CASH.equals(method))    return "Tien mat";
        if (CARD.equals(method))    return "The ngan hang";
        if (EWALLET.equals(method)) return "Vi dien tu";
        return method;
    }
}
