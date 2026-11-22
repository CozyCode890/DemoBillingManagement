package billing2.dao;

import billing2.db.QueryResult;
import billing2.model.CustomerRef;
import billing2.model.IdName;

import java.util.ArrayList;
import java.util.List;

/** Các truy vấn nhỏ đổ dữ liệu vào ô chọn: quầy, thu ngân, tìm khách theo số điện thoại. */
public class LookupDao {

    /** Danh sách quầy kèm cửa hàng: sp_lookup_counters(). */
    public List<IdName> counters() {
        return toIdNames(QueryResult.call("{call sp_lookup_counters()}"));
    }

    /** Danh sách thu ngân: sp_lookup_cashiers(). */
    public List<IdName> cashiers() {
        return toIdNames(QueryResult.call("{call sp_lookup_cashiers()}"));
    }

    /** Khách có số điện thoại BẮT ĐẦU bằng phonePrefix: sp_search_customers_by_phone(p_prefix). */
    public List<CustomerRef> searchCustomersByPhone(String phonePrefix) {
        List<CustomerRef> list = new ArrayList<>();
        if (phonePrefix == null || phonePrefix.isEmpty()) return list;

        QueryResult qr = QueryResult.call("{call sp_search_customers_by_phone(?)}", phonePrefix);
        for (Object[] row : qr.rows) {      // Ma_KH, Ten, Dien_thoai
            list.add(new CustomerRef(String.valueOf(row[0]), String.valueOf(row[1]), String.valueOf(row[2])));
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
