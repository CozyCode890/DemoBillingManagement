package billing.model;

/**
 * Cặp (mã, tên) dùng cho các ô chọn (JComboBox) trên giao diện:
 * quầy, thu ngân, khách hàng...
 *
 * JComboBox hiển thị kết quả của toString(), nhưng khi lấy giá trị ra
 * ta vẫn có nguyên object nên lấy được `id` để đưa vào câu SQL.
 */
public class IdName {

    public final String id;
    public final String name;

    public IdName(String id, String name) {
        this.id   = id;
        this.name = name;
    }

    @Override
    public String toString() {
        return (name == null || name.isEmpty()) ? id : id + " - " + name;
    }
}
