package billing.db;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class QueryResult {
    public final List<String>   columns = new ArrayList<>();
    public final List<Object[]> rows    = new ArrayList<>();

    public static QueryResult run(String sql, Object... params) {
        long t0 = System.currentTimeMillis();
        QueryResult qr = new QueryResult();

        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }

            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int n = md.getColumnCount();
                for (int i = 1; i <= n; i++) {
                    qr.columns.add(md.getColumnLabel(i));
                }

                while (rs.next()) {
                    Object[] row = new Object[n];
                    for (int i = 1; i <= n; i++) {
                        row[i - 1] = rs.getObject(i);
                    }
                    qr.rows.add(row);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("SQL error: " + e.getMessage()
                                     + "\n\nStatement:\n" + sql, e);
        }

        SqlLog.add(sql, params, System.currentTimeMillis() - t0, qr.rows.size());
        return qr;
    }

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

                while (rs.next()) {
                    Object[] row = new Object[n];
                    for (int i = 1; i <= n; i++) {
                        row[i - 1] = rs.getObject(i);
                    }
                    qr.rows.add(row);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to call SQL procedure: " + e.getMessage()
                                     + "\n\nStatement:\n" + callSql, e);
        }

        SqlLog.add(callSql, params, System.currentTimeMillis() - t0, qr.rows.size());
        return qr;
    }

    public String[]   columnArray() { return columns.toArray(new String[0]); }
    public Object[][] rowArray()    { return rows.toArray(new Object[0][]); }

    public boolean isEmpty() { return rows.isEmpty(); }
}
