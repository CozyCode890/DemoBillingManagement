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

    /** Danh sách quầy, kèm địa chỉ cửa hàng (nhờ JOIN sang Shop). */
    public List<IdName> counters() {
        QueryResult qr = QueryResult.run(
                "SELECT c.Counter_ID, CONCAT(s.Shop_ID, ' - ', s.Address) AS Mo_ta " +
                "FROM   Counter c " +
                "JOIN   Shop    s ON s.Shop_ID = c.Shop_ID " +
                "ORDER BY c.Counter_ID");
        return toIdNames(qr);
    }

    public List<IdName> cashiers() {
        QueryResult qr = QueryResult.run(
                "SELECT Cashier_ID, Name FROM Cashier ORDER BY Cashier_ID");
        return toIdNames(qr);
    }

    /**
     * Danh sách khách hàng. Phần tử đầu tiên là "khách vãng lai"
     * (id = null) vì cột Customer_ID trên bảng Invoice cho phép NULL.
     */
    public List<IdName> customers() {
        QueryResult qr = QueryResult.run(
                "SELECT Customer_ID, CONCAT(Name, ' - ', Phone) AS Mo_ta " +
                "FROM   Customer ORDER BY Customer_ID");
        List<IdName> list = new ArrayList<>();
        list.add(new IdName(null, "(khach vang lai - khong luu)"));
        list.addAll(toIdNames(qr));
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
