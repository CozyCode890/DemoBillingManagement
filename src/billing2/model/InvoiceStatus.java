package billing2.model;

/**
 * Trạng thái hóa đơn -- cột Status là ENUM('PAID','VOIDED').
 *   PAID   : sp_settle_invoice chốt khi lưu (phải có dòng hàng và thu đủ tiền)
 *   VOIDED : nút "Void invoice", hoặc sau khi trả hàng. Không có đường quay lại PAID,
 *            trừ khi hủy hết các lượt trả hàng (sp_delete_return).
 */
public enum InvoiceStatus {
    PAID("Paid"),
    VOIDED("Voided");

    private final String label;

    InvoiceStatus(String label) { this.label = label; }

    /** Đọc từ chuỗi lấy từ database; giá trị lạ trả null thay vì ném lỗi làm sập màn hình. */
    public static InvoiceStatus of(String raw) {
        if (raw == null) return null;
        for (InvoiceStatus s : values()) {
            if (s.name().equalsIgnoreCase(raw.trim())) return s;
        }
        return null;
    }

    @Override
    public String toString() { return label + " (" + name() + ")"; }
}
