package billing2.model;

/**
 * Phương thức thanh toán. Mã (name()) là chuỗi truyền cho tham số p_method của
 * sp_add_payment -- thủ tục đó tự rẽ nhánh ghi vào Cash_Payment / Card_Payment / EWallet_Payment.
 */
public enum PayMethod {

    CASH   ("Cash",      true),    // có tiền khách đưa -> có tiền thối
    CARD   ("Bank card", false),
    EWALLET("E-wallet",  false);

    private final String  label;
    private final boolean hasChange;

    PayMethod(String label, boolean hasChange) {
        this.label     = label;
        this.hasChange = hasChange;
    }

    public String code()  { return name(); }
    public String label() { return label; }

    /**
     * Chỉ tiền mặt có "tiền khách đưa" nên thu thừa được (thối lại);
     * thẻ và ví điện tử không có đường trả lại -> không được vượt số còn thiếu.
     */
    public boolean hasChange() { return hasChange; }

    /** Nhãn ô nhập tiền đổi theo phương thức. */
    public String amountFieldLabel() { return hasChange ? "Tendered" : "Amount"; }

    /** Đọc từ mã; mã lạ trả null. */
    public static PayMethod of(String code) {
        for (PayMethod m : values()) {
            if (m.name().equalsIgnoreCase(code)) return m;
        }
        return null;
    }

    @Override
    public String toString() { return label; }
}
