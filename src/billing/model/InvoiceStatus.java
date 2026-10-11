package billing.model;

/**
 * Trang thai hoa don -- BAN CHEP DUY NHAT cua bo gia tri trong Java.
 *
 * Truoc khoi 4, bo ba gia tri nay bi chep 3 lan: ENUM trong 01_schema.sql,
 * mang String[] trong EditInvoiceDialog, va literal 'PAID'/'VOIDED' trong
 * cau WHERE cua SearchPanel. Them mot trang thai la phai sua ba cho, ma
 * khong cho nao bao neu quen (plan/13 loi #6).
 *
 * Vong doi: OPEN -> PAID (sp_settle_invoice, khi da thu du tien)
 *           OPEN/PAID -> VOIDED (sp_void_invoice)
 *           VOIDED khong co duong ra (plan/13 muc 3.2).
 */
public enum InvoiceStatus {
    OPEN  ("Dang lap"),
    PAID  ("Da thanh toan"),
    VOIDED("Da huy");

    private final String label;

    InvoiceStatus(String label) { this.label = label; }

    /** Ma luu trong database, dung bang ten hang so. */
    public String code()  { return name(); }

    /** Nhan tieng Viet khong dau de hien tren giao dien. */
    public String label() { return label; }

    /** Nhan kem ma, vi du "Da thanh toan (PAID)". */
    public String display() { return label + " (" + name() + ")"; }

    /**
     * Doc tu chuoi lay tu database. Tra null khi khong khop -- KHONG nem
     * IllegalArgumentException nhu Enum.valueOf: mot gia tri la trong cot
     * Status khong duoc phep lam sap man hinh xem hoa don.
     */
    public static InvoiceStatus of(String raw) {
        if (raw == null) return null;
        for (InvoiceStatus s : values()) {
            if (s.name().equalsIgnoreCase(raw.trim())) return s;
        }
        return null;
    }

    /** Hien o JComboBox / JLabel: nhan kem ma, khong phai ten hang so tho. */
    @Override
    public String toString() { return display(); }
}
