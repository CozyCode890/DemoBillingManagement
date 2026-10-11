package billing.model;

/**
 * Phuong thuc thanh toan -- BAN CHEP DUY NHAT trong Java.
 *
 * Truoc khoi 5 bo ba gia tri nay bi chep 3 lan: hang so trong PaymentEntry,
 * mang String[] trong NewInvoicePanel (sinh ra cac chi so 0/1/2 rai khap
 * file), va CASE trong sp_get_invoice_payments. Them mot phuong thuc la phai
 * sua ba cho, ma khong cho nao bao neu quen -- dung loai loi ma khoi 4 tri
 * bang InvoiceStatus.
 *
 * NHAN PHAI GIONG HET chuoi ma SQL tra ve (04_routines.sql, CASE trong
 * sp_get_invoice_payments). Ban trong SQL GIU NGUYEN vi do la cot Phuong_thuc
 * ma tab 2 va tab 3 hien thi; neu hai ben lech nhau thi MOT hoa don se hien
 * hai cach viet cho cung mot phuong thuc (plan/14 muc 3.6).
 *
 * Ma (name()) la chuoi Java truyen cho tham so p_method cua sp_add_payment --
 * thu tuc do re nhanh ghi vao Cash_Payment / Card_Payment / EWallet_Payment.
 */
public enum PayMethod {

    CASH   ("Tien mat",      true),    // co tien khach dua -> co tien thoi
    CARD   ("The ngan hang", false),
    EWALLET("Vi dien tu",    false);

    private final String  label;
    private final boolean hasChange;

    PayMethod(String label, boolean hasChange) {
        this.label     = label;
        this.hasChange = hasChange;
    }

    /** Ma luu trong database / truyen cho sp_add_payment, dung bang ten hang so. */
    public String code() { return name(); }

    /** Nhan hien tren giao dien -- dung ba chuoi ma SQL tra ve. */
    public String label() { return label; }

    /**
     * Phuong thuc nay co "tien khach dua" va do do co "tien thoi" hay khong.
     *
     * CHO DUY NHAT tra loi cau hoi do -- thay cho phep so sanh
     * methodIndex == 0 rai khap NewInvoicePanel (plan/14 muc 3.1 diem 1-2).
     * Tien mat thi thu thua bao nhieu cung duoc vi thoi duoc; the va vi dien
     * tu thi KHONG co duong tra lai nen bi tu choi khi qua so con thieu.
     */
    public boolean hasChange() { return hasChange; }

    /**
     * Nhan cua o nhap so tien, doi theo phuong thuc: hop dong tien mat
     * (plan/14 muc 3.1) nhin thay duoc ngay tren man hinh, khong phai doc
     * tai lieu moi biet.
     */
    public String amountFieldLabel() {
        return hasChange ? "Khach dua" : "So tien";
    }

    /**
     * Doc tu chuoi lay tu database (hoac tu ma). Tra null khi khong khop --
     * KHONG nem IllegalArgumentException nhu Enum.valueOf: mot gia tri la
     * trong cot phuong thuc khong duoc phep lam sap man hinh.
     */
    public static PayMethod of(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        for (PayMethod m : values()) {
            if (m.name().equalsIgnoreCase(s) || m.label.equalsIgnoreCase(s)) return m;
        }
        return null;
    }

    /** JComboBox goi ham nay de ve mot dong -- nen o chon hien dung nhan. */
    @Override
    public String toString() { return label; }
}
