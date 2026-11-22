package billing.dao;

import billing.db.QueryResult;
import billing.model.CustomerRef;
import billing.model.IdName;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Các truy vấn nhỏ để đổ dữ liệu vào ô chọn trên giao diện.
 * Dùng lại QueryResult nên chỉ còn đúng 1 dòng SQL mỗi hàm.
 */
public class LookupDao {

    /** Danh sách quầy, kèm địa chỉ cửa hàng (gọi Stored Procedure: sp_lookup_counters). */
    public List<IdName> counters() {
        QueryResult qr = QueryResult.call("{call sp_lookup_counters()}");
        return toIdNames(qr);
    }

    /** Danh sách thu ngân (gọi Stored Procedure: sp_lookup_cashiers). */
    public List<IdName> cashiers() {
        QueryResult qr = QueryResult.call("{call sp_lookup_cashiers()}");
        return toIdNames(qr);
    }

    /**
     * Danh sách quản lý (Role = MANAGER) để chọn người duyệt hủy hóa đơn / trả
     * hàng (BR3, BR15): sp_lookup_managers. SQL vẫn tự kiểm Role lần nữa trong
     * sp_void_invoice / sp_add_return -- danh sách này chỉ để chọn cho đúng.
     */
    public List<IdName> managers() {
        QueryResult qr = QueryResult.call("{call sp_lookup_managers()}");
        return toIdNames(qr);
    }

    /**
     * Ngày hiện tại THEO ĐỒNG HỒ SERVER (BR2). Tab 1 dùng ngày này để xem
     * trước giá và khuyến mãi, vì sp_add_invoice_line tính giá theo ngày của
     * server chứ không theo đồng hồ máy quầy -- lấy LocalDate.now() thì máy
     * quầy lệch ngày (hoặc giỏ hàng mở qua nửa đêm) sẽ cho số trên màn hình
     * khác số được lưu.
     *
     * <p>Câu SQL thô duy nhất của DAO và là CÓ CHỦ Ý: nó đọc đồng hồ, không
     * đọc dữ liệu nghiệp vụ nào, nên không cần một thủ tục riêng.
     */
    public LocalDate serverDate() {
        Object v = QueryResult.run("SELECT CURDATE()").rows.get(0)[0];
        if (v instanceof java.sql.Date d) return d.toLocalDate();
        if (v instanceof LocalDate d)     return d;
        return LocalDate.parse(String.valueOf(v));
    }

    /**
     * Tìm khách hàng theo TIỀN TỐ số điện thoại:
     * {@code sp_search_customers_by_phone(p_prefix)}.
     *
     * <p>Thủ tục, KHÔNG phải SQL thô: câu {@code SELECT ... WHERE Phone LIKE ?}
     * cũ là câu SQL thô cuối cùng của tầng DAO và nó phá quy ước
     * {@code Swing -> DAO -> Stored Procedure}.
     *
     * <p>Trả về {@link CustomerRef} với ba field riêng, KHÔNG ghép sẵn chuỗi
     * hiển thị: ghép chuỗi để hiện trên màn hình là việc của tầng UI. Bản cũ
     * ghép trong DAO nên chính cái chuỗi ghép đó bị đổ trở lại ô nhập
     * (plan/14 lỗi #12).
     *
     * <p>CHẠY NGOÀI EDT -- chỗ gọi ({@code CustomerPicker}) dùng
     * {@code SwingWorker} và hoãn 250ms, nên gõ 10 số là 1-2 câu SQL chứ không
     * phải 10 (plan/14 lỗi #14).
     *
     * @param phonePrefix tiền tố, phía UI đã chắc chắn là chuỗi toàn chữ số
     *                    dài {@code >= 3} (plan/14 mục 3.5)
     */
    public List<CustomerRef> searchCustomersByPhone(String phonePrefix) {
        List<CustomerRef> list = new ArrayList<>();
        if (phonePrefix == null || phonePrefix.isEmpty()) {
            return list;
        }

        QueryResult qr = QueryResult.call("{call sp_search_customers_by_phone(?)}", phonePrefix);
        for (Object[] row : qr.rows) {
            list.add(new CustomerRef(
                    String.valueOf(row[0]),     // Ma_KH
                    String.valueOf(row[1]),     // Ten
                    String.valueOf(row[2])));   // Dien_thoai
        }
        return list;
    }

    private List<IdName> toIdNames(QueryResult qr) {
        List<IdName> list = new ArrayList<>();
        for (Object[] row : qr.rows) {
            list.add(new IdName(String.valueOf(row[0]), String.valueOf(row[1])));
        }
        return list;
    }

    // Khong con customers(): thu tuc sp_lookup_customers VAN GIU trong SQL
    // (6 dong, vo hai, con gia tri minh hoa) nhung khong con man hinh nao goi
    // no -- cai gay nham la ham Java vi no lam nguoi doc tin rang co man hinh
    // dang dung. Tim khach hang di qua sp_search_customers_by_phone
    // (plan/14 muc 4.6).
}
