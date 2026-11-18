package billing2.model;

/** Một khách hàng trong danh sách gợi ý của ô "Customer (phone)". */
public class CustomerRef {

    public final String id;      // Customer_ID
    public final String name;    // Customer.Name
    public final String phone;   // Customer.Phone (UNIQUE)

    public CustomerRef(String id, String name, String phone) {
        this.id    = id;
        this.name  = name;
        this.phone = phone;
    }

    /** Dòng trong popup gợi ý: "Nguyen Van A - 0901234567 (CUS001)". */
    public String display() { return name + " - " + phone + " (" + id + ")"; }

    /** Nhãn cạnh ô nhập sau khi chọn: "Nguyen Van A (CUS001)". */
    public String shortLabel() { return name + " (" + id + ")"; }

    @Override
    public String toString() { return display(); }
}
