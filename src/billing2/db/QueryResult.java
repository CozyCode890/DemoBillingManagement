package billing2.db;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả của một thủ tục SELECT ở dạng "bảng thô": tên cột + các dòng.
 *
 * Dùng cho kết quả KHÔNG ứng với lớp Java nào (báo cáo, chi tiết hóa đơn...):
 * đọc tên cột từ ResultSetMetaData rồi đổ thẳng vào JTable.
 */
public class QueryResult {

    public final List<String>   columns = new ArrayList<>();
    public final List<Object[]> rows    = new ArrayList<>();

    /**
     * Gọi một Stored Procedure trả về bảng: "{call sp_ten(?, ?)}".
     *
     * Các bước JDBC đều nằm trong hàm này:
     *   1. Mở Connection                -> Db.getConnection()
     *   2. Chuẩn bị câu gọi             -> conn.prepareCall(sql)
     *   3. Gắn tham số vào các dấu ?    -> cs.setObject(i, value)   (đánh số từ 1)
     *   4. Thực thi, duyệt kết quả      -> cs.executeQuery() / rs.next()
     *   5. Đóng mọi thứ                 -> try-with-resources tự lo
     */
    public static QueryResult call(String callSql, Object... params) {
        long t0 = System.currentTimeMillis();
        QueryResult qr = new QueryResult();

        try (Connection conn = Db.getConnection();
             CallableStatement cs = conn.prepareCall(callSql)) {

            for (int i = 0; i < params.length; i++) {
                cs.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = cs.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int n = md.getColumnCount();
                for (int i = 1; i <= n; i++) {
                    qr.columns.add(md.getColumnLabel(i));
                }
                while (rs.next()) {              // rs bắt đầu TRƯỚC dòng đầu tiên
                    Object[] row = new Object[n];
                    for (int i = 1; i <= n; i++) {
                        row[i - 1] = rs.getObject(i);
                    }
                    qr.rows.add(row);
                }
            }
        } catch (SQLException e) {
            // Đổi thành RuntimeException để tầng giao diện bắt và hiện hộp thoại.
            throw new RuntimeException("Failed to call SQL procedure: " + e.getMessage()
                                     + "\n\nStatement:\n" + callSql, e);
        }

        SqlLog.add(callSql, params, System.currentTimeMillis() - t0, qr.rows.size());
        return qr;
    }

    public String[]   columnArray() { return columns.toArray(new String[0]); }
    public Object[][] rowArray()    { return rows.toArray(new Object[0][]); }
    public boolean    isEmpty()     { return rows.isEmpty(); }
}
