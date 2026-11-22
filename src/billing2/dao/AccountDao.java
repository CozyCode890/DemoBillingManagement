package billing2.dao;

import billing2.db.Db;
import billing2.db.SqlLog;
import billing2.model.Account;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Đăng nhập: gọi sp_authenticate_account qua JDBC. */
public class AccountDao {

    /** @return Account nếu đúng tên + mật khẩu, null nếu sai. */
    public Account authenticate(String username, String password) {
        String sql = "{call sp_authenticate_account(?, ?)}";
        long t0 = System.currentTimeMillis();

        try (Connection conn = Db.getConnection();
             CallableStatement cs = conn.prepareCall(sql)) {

            cs.setString(1, username);
            cs.setString(2, password);

            Account acc = null;
            try (ResultSet rs = cs.executeQuery()) {
                if (rs.next()) {
                    acc = new Account(rs.getString("Username"), rs.getString("Role"),
                                      rs.getString("Full_Name"), rs.getString("Cashier_ID"));
                }
            }
            // Nhật ký SQL KHÔNG bao giờ ghi mật khẩu.
            SqlLog.add(sql, new Object[]{username, "******"}, System.currentTimeMillis() - t0, acc == null ? 0 : 1);
            return acc;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to authenticate account: " + e.getMessage(), e);
        }
    }
}
