package billing.dao;

import billing.db.Db;
import billing.db.SqlLog;
import billing.model.Account;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

/**
 * ====================================================================
 *  AccountDao -- Tầng truy cập dữ liệu Tài khoản
 * ====================================================================
 *  Thực thi gọi Stored Procedure `sp_authenticate_account` qua JDBC
 *  để kiểm tra tên đăng nhập và mật khẩu.
 */
public class AccountDao {

    /**
     * Xác thực thông tin đăng nhập của người dùng.
     *
     * @param username Tên đăng nhập (ví dụ: "cashier", "manager")
     * @param password Mật khẩu (ví dụ: "cashier", "manager")
     * @return Đối tượng Account nếu đăng nhập đúng, hoặc null nếu sai.
     */
    public Account authenticate(String username, String password) {
        String sql = "{call sp_authenticate_account(?, ?)}";
        long t0 = System.currentTimeMillis();

        try (Connection conn = Db.getConnection();
             CallableStatement cs = conn.prepareCall(sql)) {

            cs.setString(1, username);
            cs.setString(2, password);

            try (ResultSet rs = cs.executeQuery()) {
                if (rs.next()) {
                    String user = rs.getString("Username");
                    String role = rs.getString("Role");
                    String fullName = rs.getString("Full_Name");
                    // Đọc phòng thủ: xem ghi chú của hasColumn() bên dưới.
                    String cashierId = hasColumn(rs, "Cashier_ID") ? rs.getString("Cashier_ID") : null;

                    SqlLog.add(sql, new Object[]{username, "******"}, System.currentTimeMillis() - t0, 1);
                    return new Account(user, role, fullName, cashierId);
                }
            }

            SqlLog.add(sql, new Object[]{username, "******"}, System.currentTimeMillis() - t0, 0);
            return null;

        } catch (SQLException e) {
            throw new RuntimeException("Loi khi xac thuc tai khoan: " + e.getMessage(), e);
        }
    }

    /**
     * Kiểm tra kết quả trả về có cột này không -- để app còn đăng nhập được khi
     * database chưa nạp lại 04_routines.sql (xem plan/12 mục 5.2).
     *
     * Nếu gọi thẳng rs.getString("Cashier_ID") trên một ResultSet không có cột
     * đó, JDBC ném SQLException và KHÔNG AI đăng nhập được nữa -- hỏng cả app
     * chỉ vì một tính năng trả hàng. Thiếu cột thì cashierId = null, đăng nhập
     * vẫn chạy, chỉ nút "Tra hang" báo lỗi kèm cách sửa.
     */
    private static boolean hasColumn(ResultSet rs, String name) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            if (name.equalsIgnoreCase(md.getColumnLabel(i))) return true;
        }
        return false;
    }
}
