package billing.dao;

import billing.db.Db;
import billing.db.QueryResult;
import billing.db.SqlLog;
import billing.model.CartLine;
import billing.model.PaymentEntry;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * ====================================================================
 *  InvoiceDao -- Thao tác với Hóa đơn qua Stored Procedures & Functions
 * ====================================================================
 *  Minh họa cách Dev Java Backend và Dev SQL làm việc cùng nhau:
 *  - Phía SQL: Thiết kế các thủ tục ghi/đọc hóa đơn trong sql/04_routines.sql.
 *  - Phía Java: Quản lý vòng đời Transaction (commit / rollback) ở tầng ứng dụng,
 *    đồng thời gọi tuần tự các Stored Procedure của Dev SQL trên cùng một Connection.
 */
public class InvoiceDao {

    // =================================================================
    //  PHẦN 1 -- GHI DỮ LIỆU QUA THỦ TỤC & QUẢN LÝ TRANSACTION
    // =================================================================

    /**
     * ================================================================
     *  LƯU MỘT HÓA ĐƠN -- TRANSACTION KẾT HỢP STORED PROCEDURES
     * ================================================================
     *  Java quản lý ranh giới nghiệp vụ (Business Transaction):
     *      conn.setAutoCommit(false);
     *      - Gọi sp_create_invoice_header: ghi đầu hóa đơn
     *      - Gọi sp_add_invoice_line: ghi từng dòng hàng (batching)
     *      - Gọi sp_add_payment: ghi thanh toán (SQL tự rẽ nhánh bảng con)
     *      conn.commit();
     */
    public void createInvoice(String invoiceId,
                              LocalDate date, LocalTime time,
                              String counterId, String cashierId, String customerId,
                              List<CartLine> lines,
                              List<PaymentEntry> payments) {

        String spHeader  = "{call sp_create_invoice_header(?, ?, ?, ?, ?, ?)}";
        String spLine    = "{call sp_add_invoice_line(?, ?, ?, ?, ?, ?)}";
        String spPayment = "{call sp_add_payment(?, ?, ?, ?, ?, ?)}";

        long t0 = System.currentTimeMillis();
        Connection conn = null;

        try {
            conn = Db.getConnection();
            conn.setAutoCommit(false);          // ==== BẮT ĐẦU TRANSACTION ====

            // ---- 1. Ghi phần đầu hóa đơn qua Stored Procedure -------
            try (CallableStatement cs = conn.prepareCall(spHeader)) {
                cs.setString(1, invoiceId);
                cs.setDate(2, Date.valueOf(date));
                cs.setTime(3, Time.valueOf(time));
                cs.setString(4, counterId);
                cs.setString(5, cashierId);
                if (customerId == null) {
                    cs.setNull(6, Types.VARCHAR);
                } else {
                    cs.setString(6, customerId);
                }
                cs.executeUpdate();
                SqlLog.add(spHeader, new Object[]{invoiceId, date, time,
                        counterId, cashierId, customerId}, 0, 1);
            }

            // ---- 2. Ghi các dòng hàng qua Stored Procedure (Batch) --
            try (CallableStatement cs = conn.prepareCall(spLine)) {
                int lineNo = 1;
                for (CartLine cl : lines) {
                    cs.setString(1, invoiceId);
                    cs.setInt(2, lineNo++);
                    cs.setString(3, cl.product.barcode);
                    cs.setBigDecimal(4, cl.quantity);
                    cs.setBigDecimal(5, cl.unitPrice);
                    cs.setBigDecimal(6, cl.discount);
                    cs.addBatch();
                }
                cs.executeBatch();
                SqlLog.add(spLine + "   [batch x " + lines.size() + "]",
                           null, 0, lines.size());
            }

            // ---- 3. Ghi các lần thanh toán qua Stored Procedure -----
            // Dev SQL tự lo việc rẽ nhánh lưu vào Cash_Payment, Card_Payment
            // hay EWallet_Payment trong sp_add_payment. Java không cần biết cấu trúc bảng con!
            int paySeq = nextPaymentSeq(conn);
            try (CallableStatement cs = conn.prepareCall(spPayment)) {
                for (PaymentEntry pe : payments) {
                    String payId = String.format("PAY-%04d", paySeq++);
                    cs.setString(1, payId);
                    cs.setString(2, invoiceId);
                    cs.setBigDecimal(3, pe.amount);
                    cs.setString(4, pe.method);
                    cs.setString(5, pe.detail1);
                    cs.setString(6, pe.detail2);
                    cs.executeUpdate();

                    SqlLog.add(spPayment, new Object[]{payId, invoiceId, pe.amount,
                            pe.method, pe.detail1, pe.detail2}, 0, 1);
                }
            }

            conn.commit();                      // ==== CHỐT: mọi thứ có hiệu lực ====
            SqlLog.add("COMMIT;  -- luu thanh cong hoa don " + invoiceId,
                       null, System.currentTimeMillis() - t0, 0);

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                    SqlLog.add("ROLLBACK;  -- da huy toan bo vi co loi", null, 0, 0);
                } catch (SQLException ignore) { }
            }
            throw new RuntimeException("Luu hoa don that bai: " + e.getMessage(), e);

        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); } catch (SQLException ignore) { }
                try { conn.close(); }            catch (SQLException ignore) { }
            }
        }
    }

    /**
     * Sinh mã hóa đơn kế tiếp (INV-0001, INV-0002...).
     * Ứng dụng Stored Function: fn_next_invoice_id()
     */
    public String nextInvoiceId() {
        String sql = "{? = call fn_next_invoice_id()}";
        long t0 = System.currentTimeMillis();

        try (Connection c = Db.getConnection();
             CallableStatement cs = c.prepareCall(sql)) {

            cs.registerOutParameter(1, Types.VARCHAR);
            cs.execute();
            String nextId = cs.getString(1);

            SqlLog.add(sql, null, System.currentTimeMillis() - t0, 1);
            return nextId;
        } catch (SQLException e) {
            throw new RuntimeException("Khong sinh duoc ma hoa don: " + e.getMessage(), e);
        }
    }

    /** Số thứ tự Payment kế tiếp. Dùng chung Connection của transaction. */
    private int nextPaymentSeq(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(CAST(SUBSTRING(Payment_ID, 5) AS UNSIGNED)), 0) + 1 " +
                     "FROM Payment WHERE Payment_ID REGEXP '^PAY-[0-9]+$'";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 1;
        }
    }


    // =================================================================
    //  PHẦN 2 -- ĐỌC DỮ LIỆU QUA CÁC STORED PROCEDURE
    // =================================================================

    /** Danh sách hóa đơn gần đây kèm tổng tiền: sp_get_recent_invoices() */
    public QueryResult recentInvoices() {
        return QueryResult.call("{call sp_get_recent_invoices()}");
    }

    /** Các dòng của một hóa đơn: sp_get_invoice_lines(p_invoice_id) */
    public QueryResult invoiceLines(String invoiceId) {
        return QueryResult.call("{call sp_get_invoice_lines(?)}", invoiceId);
    }

    /** Phần đầu + tổng tiền của một hóa đơn: sp_get_invoice_header(p_invoice_id) */
    public QueryResult invoiceHeader(String invoiceId) {
        return QueryResult.call("{call sp_get_invoice_header(?)}", invoiceId);
    }

    /** Các lần thanh toán của hóa đơn: sp_get_invoice_payments(p_invoice_id) */
    public QueryResult invoicePayments(String invoiceId) {
        return QueryResult.call("{call sp_get_invoice_payments(?)}", invoiceId);
    }

    /** Các lần trả hàng của hóa đơn: sp_get_invoice_returns(p_invoice_id) */
    public QueryResult invoiceReturns(String invoiceId) {
        return QueryResult.call("{call sp_get_invoice_returns(?)}", invoiceId);
    }
}


