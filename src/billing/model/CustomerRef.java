package billing.model;

/**
 * Mot khach hang trong danh sach goi y cua o "Khach (SDT)".
 *
 * VI SAO KHONG DUNG IdName: IdName la cap (ma, ten) va o tab 1 no bi nhet ba
 * thong tin vao field `name` duoi dang chuoi ghep san
 * "CUS001 - Nguyen Van A - 0901234567" (ghep NGAY TRONG DAO). Chuoi do bi do
 * tro lai o nhap sau khi chon, nen lan go tiep theo tim theo mot chuoi vo
 * nghia -- goc cua plan/14 loi #10 va #12.
 *
 * Ba field rieng thi o nhap giu dung SO DIEN THOAI, ten di vao nhan ben canh,
 * va bat bien muc 3.5 kiem duoc:
 *     selectedCustomer != null  <=>  tfPhone.getText().trim() == selected.phone
 *
 * IdName KHONG doi: quay va thu ngan van dung dung nghia (ma, ten).
 */
public class CustomerRef {

    public final String id;      // Customer_ID
    public final String name;    // Customer.Name
    public final String phone;   // Customer.Phone -- khoa tu nhien, co UNIQUE KEY

    public CustomerRef(String id, String name, String phone) {
        this.id    = id;
        this.name  = name;
        this.phone = phone;
    }

    /** Dong trong popup goi y: "Nguyen Van A - 0901234567 (CUS001)". */
    public String display() {
        return name + " - " + phone + " (" + id + ")";
    }

    /** Nhan canh o nhap khi da chon: "Nguyen Van A (CUS001)". */
    public String shortLabel() {
        return name + " (" + id + ")";
    }

    @Override
    public String toString() { return display(); }
}
