package billing.dao;

import billing.db.QueryResult;
import billing.model.IdName;

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
     * Danh sách khách hàng (gọi Stored Procedure: sp_lookup_customers).
     * Phần tử đầu tiên là "khách vãng lai" (id = null) vì cột Customer_ID
     * trên bảng Invoice cho phép NULL.
     */
    public List<IdName> customers() {
        QueryResult qr = QueryResult.call("{call sp_lookup_customers()}");
        List<IdName> list = new ArrayList<>();
        list.add(new IdName(null, "(khach vang lai - khong luu)"));
        list.addAll(toIdNames(qr));
        return list;
    }

    /**
     * Tìm khách hàng theo tiền tố số điện thoại.
     * @param phonePrefix tiền tố số điện thoại để tìm
     * @return danh sách khách hàng khớp (mỗi phần tử là IdName với id=Customer_ID, name="ID - Name - Phone")
     */
    public List<IdName> searchCustomersByPhonePrefix(String phonePrefix) {
        if (phonePrefix == null || phonePrefix.isEmpty()) {
            return new ArrayList<>();
        }

        String sql = "SELECT Customer_ID, Name, Phone FROM Customer WHERE Phone LIKE ?";
        QueryResult qr = QueryResult.run(sql, phonePrefix + "%");
        List<IdName> list = new ArrayList<>();

        for (Object[] row : qr.rows) {
            String id = String.valueOf(row[0]);
            String name = String.valueOf(row[1]);
            String phone = String.valueOf(row[2]);
            String display = id + " - " + name + " - " + phone;
            list.add(new IdName(id, display));
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
}