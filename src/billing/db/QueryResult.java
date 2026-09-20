package billing.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * ====================================================================
 *  QueryResult -- kết quả của một câu SELECT, ở dạng "bảng thô".
 * ====================================================================
 *
 *  Vì sao cần lớp này?
 *  Với các câu báo cáo (doanh thu, top thu ngân...) kết quả trả về
 *  KHÔNG tương ứng với một bảng nào trong database, nên viết một lớp
 *  Java riêng cho từng câu là rất phí. Thay vào đó ta đọc luôn tên cột
 *  từ ResultSetMetaData rồi đổ thẳng vào JTable.
 *
 *  Đây chính là cách mà các thư viện như Spring JdbcTemplate hoạt động
 *  bên trong -- chỉ là chúng làm phức tạp hơn nhiều.
 */
public class QueryResult {

    public final List<String>   columns = new ArrayList<>();
    public final List<Object[]> rows    = new ArrayList<>();

    /**
     * Chạy một câu SELECT và gói kết quả lại.
     *
     * 5 BƯỚC KINH ĐIỂN CỦA JDBC, đều nằm gọn trong hàm này:
     *   1. Mở Connection                      -> Db.getConnection()
     *   2. Chuẩn bị câu lệnh                   -> conn.prepareStatement(sql)
     *   3. Gắn tham số vào các dấu ?           -> ps.setObject(i, value)
     *   4. Thực thi và duyệt kết quả           -> ps.executeQuery() / rs.next()
     *   5. Đóng mọi thứ                        -> try-with-resources tự lo
     *
     * @param sql    câu SELECT, chỗ nào cần giá trị thì đặt dấu ?
     * @param params giá trị điền vào các dấu ? theo đúng thứ tự
     */
    public static QueryResult run(String sql, Object... params) {
        long t0 = System.currentTimeMillis();
        QueryResult qr = new QueryResult();

        // try-with-resources: 3 tài nguyên này sẽ tự động được đóng
        // theo thứ tự ngược lại, kể cả khi ném exception.
        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // Điền tham số. CHÚ Ý: JDBC đánh số từ 1, không phải 0.
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }

            try (ResultSet rs = ps.executeQuery()) {

                // Metadata cho ta biết kết quả có bao nhiêu cột, tên là gì.
                ResultSetMetaData md = rs.getMetaData();
                int n = md.getColumnCount();
                for (int i = 1; i <= n; i++) {
                    qr.columns.add(md.getColumnLabel(i));
                }

                // rs bắt đầu ở vị trí TRƯỚC dòng đầu tiên.
                // rs.next() nhảy tới dòng kế tiếp, trả false khi hết dữ liệu.
                while (rs.next()) {
                    Object[] row = new Object[n];
                    for (int i = 1; i <= n; i++) {
                        row[i - 1] = rs.getObject(i);
                    }
                    qr.rows.add(row);
                }
            }

        } catch (SQLException e) {
            // Trong app demo ta biến lỗi SQL thành RuntimeException để
            // tầng giao diện bắt và hiện hộp thoại báo lỗi.
            throw new RuntimeException("Loi SQL: " + e.getMessage()
                                     + "\n\nCau lenh:\n" + sql, e);
        }

        SqlLog.add(sql, params, System.currentTimeMillis() - t0, qr.rows.size());
        return qr;
    }

    public String[]   columnArray() { return columns.toArray(new String[0]); }
    public Object[][] rowArray()    { return rows.toArray(new Object[0][]); }

    public boolean isEmpty() { return rows.isEmpty(); }
}
