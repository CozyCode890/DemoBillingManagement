package billing2.dao;

import billing2.db.Db;
import billing2.db.QueryResult;
import billing2.db.SqlLog;
import billing2.model.CartLine;
import billing2.model.PaymentEntry;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Hóa đơn. Phía SQL viết thủ tục ghi / đọc (sql/04_routines.sql); phía Java giữ
 * ranh giới TRANSACTION (commit / rollback) và gọi các thủ tục trên CÙNG một Connection.
 */
public class InvoiceDao {

    /** Một dòng cần trả: dòng nào, trả bao nhiêu. Tiền hoàn KHÔNG ở đây -- SQL tự tính (fn_return_refund). */
    public record ReturnItem(String productName, int lineNumber, BigDecimal quantity) { }

    // =================================================================
    //  GHI: hai transaction lớn
    // =================================================================

    /**
     * LƯU MỘT HÓA ĐƠN TRONG MỘT TRANSACTION:
     *     conn.setAutoCommit(false)
     *       fn_next_invoice_id           -> mã hóa đơn (sinh TRONG transaction)
     *       sp_create_invoice_header     -> đầu hóa đơn
     *       sp_add_invoice_line          -> từng dòng hàng (batch)
     *       fn_next_payment_id + sp_add_payment -> từng lần thanh toán (SQL tự rẽ nhánh bảng con)
     *       sp_settle_invoice            -> CHỐT: thiếu dòng hoặc thu thiếu tiền thì SIGNAL
     *     conn.commit()   -- có lỗi ở bất kỳ bước nào: ROLLBACK, hóa đơn không tồn tại
     *
     * @param invoiceId null = tự sinh mã trong transaction
     * @return mã hóa đơn đã lưu
     */
    public String createInvoice(String invoiceId, LocalDate date, LocalTime time,
                                String counterId, String cashierId, String customerId,
                                List<CartLine> lines, List<PaymentEntry> payments) {
        String fnInvId   = "{? = call fn_next_invoice_id()}";
        String fnPayId   = "{? = call fn_next_payment_id()}";
        String spHeader  = "{call sp_create_invoice_header(?, ?, ?, ?, ?, ?)}";
        String spLine    = "{call sp_add_invoice_line(?, ?, ?, ?, ?, ?)}";
        String spPayment = "{call sp_add_payment(?, ?, ?, ?, ?, ?)}";
        String spSettle  = "{call sp_settle_invoice(?)}";

        long t0 = System.currentTimeMillis();
        Connection conn = null;
        try {
            conn = Db.getConnection();
            conn.setAutoCommit(false);                          // ==== BẮT ĐẦU TRANSACTION ====

            // 0. Mã hóa đơn
            if (invoiceId == null) {
                invoiceId = scalarString(conn, fnInvId);
                SqlLog.add(fnInvId, null, 0, 1);
            }

            // 1. Đầu hóa đơn
            try (CallableStatement cs = conn.prepareCall(spHeader)) {
                cs.setString(1, invoiceId);
                cs.setDate(2, Date.valueOf(date));
                cs.setTime(3, Time.valueOf(time));
                cs.setString(4, counterId);
                cs.setString(5, cashierId);
                if (customerId == null) cs.setNull(6, Types.VARCHAR);    // khách vãng lai
                else                    cs.setString(6, customerId);
                cs.executeUpdate();
                SqlLog.add(spHeader, new Object[]{invoiceId, date, time, counterId, cashierId, customerId}, 0, 1);
            }

            // 2. Các dòng hàng -- gom thành một batch
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
                SqlLog.add(spLine + "   [batch x " + lines.size() + "]", null, 0, lines.size());
            }

            // 3. Các lần thanh toán -- sp_add_payment tự ghi vào Cash_ / Card_ / EWallet_Payment
            try (CallableStatement csId = conn.prepareCall(fnPayId);
                 CallableStatement cs   = conn.prepareCall(spPayment)) {
                for (PaymentEntry pe : payments) {
                    csId.registerOutParameter(1, Types.VARCHAR);
                    csId.execute();                             // thấy được dòng Payment vừa INSERT ở vòng trước
                    String payId = csId.getString(1);
                    SqlLog.add(fnPayId, null, 0, 1);

                    String d1 = pe.wireDetail1();               // tiền mặt: tiền khách ĐƯA
                    cs.setString(1, payId);
                    cs.setString(2, invoiceId);
                    cs.setBigDecimal(3, pe.amount);             // Payment.Amount = tiền GHI NHẬN
                    cs.setString(4, pe.method);
                    cs.setString(5, d1);
                    cs.setString(6, pe.detail2);
                    cs.executeUpdate();
                    SqlLog.add(spPayment, new Object[]{payId, invoiceId, pe.amount, pe.method, d1, pe.detail2}, 0, 1);
                }
            }

            // 4. Chốt: phải có dòng hàng và đã thu đủ -- sai thì thủ tục SIGNAL -> catch -> ROLLBACK
            try (CallableStatement cs = conn.prepareCall(spSettle)) {
                cs.setString(1, invoiceId);
                cs.executeUpdate();
                SqlLog.add(spSettle, new Object[]{invoiceId}, 0, 1);
            }

            conn.commit();                                      // ==== CHỐT: mọi thứ có hiệu lực ====
            SqlLog.add("COMMIT;  -- save invoice successfully " + invoiceId, null, System.currentTimeMillis() - t0, 0);
            return invoiceId;

        } catch (SQLException e) {
            rollback(conn);
            throw new RuntimeException("Failed to save invoice: " + e.getMessage(), e);
        } finally {
            close(conn);
        }
    }

    /**
     * TRẢ HÀNG NHIỀU DÒNG TRONG MỘT TRANSACTION: sp_add_return cho từng dòng, rồi
     * sp_void_invoice -- hóa đơn gốc thành VOIDED nhưng doanh thu vẫn tính theo hóa đơn
     * điều chỉnh (fn_invoice_in_revenue). Một dòng lỗi -> ROLLBACK cả lượt.
     *
     * @param approvedByCashierId Cashier_ID của người duyệt (KHÔNG phải username)
     */
    public void processReturn(String invoiceId, List<ReturnItem> items, String approvedByCashierId) {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("There are no lines to return");

        String spReturn = "{call sp_add_return(?, ?, ?, ?, ?)}";
        String spVoid   = "{call sp_void_invoice(?)}";
        LocalDate today = LocalDate.now();                     // MỘT ngày cho cả lượt trả
        long t0 = System.currentTimeMillis();
        Connection conn = null;
        ReturnItem current = null;                              // dòng đang ghi -- để nhật ký thấy lần thất bại

        try {
            conn = Db.getConnection();
            conn.setAutoCommit(false);                          // ==== BẮT ĐẦU TRANSACTION ====

            try (CallableStatement cs = conn.prepareCall(spReturn)) {
                for (ReturnItem it : items) {
                    current = it;
                    cs.setString(1, invoiceId);
                    cs.setInt(2, it.lineNumber());
                    cs.setDate(3, Date.valueOf(today));
                    cs.setBigDecimal(4, it.quantity());
                    cs.setString(5, approvedByCashierId);
                    cs.executeUpdate();
                    SqlLog.add(spReturn, new Object[]{invoiceId, it.lineNumber(), today, it.quantity(), approvedByCashierId}, 0, 1);
                }
                current = null;
            }
            try (CallableStatement cs = conn.prepareCall(spVoid)) {
                cs.setString(1, invoiceId);
                cs.executeUpdate();
                SqlLog.add(spVoid, new Object[]{invoiceId}, 0, 1);
            }

            conn.commit();                                      // ==== CHỐT ====
            SqlLog.add("COMMIT;  -- save return successfully for invoice " + invoiceId, null, System.currentTimeMillis() - t0, 0);

        } catch (SQLException e) {
            if (current != null) {
                SqlLog.add(spReturn, new Object[]{invoiceId, current.lineNumber(), today, current.quantity(), approvedByCashierId}, 0, 0);
            }
            rollback(conn);
            // Giữ SQLException gốc làm cause: UI đọc SQLState '45000' = lỗi nghiệp vụ.
            throw new RuntimeException("Failed to process return: " + e.getMessage(), e);
        } finally {
            close(conn);
        }
    }

    /**
     * Hủy MỌI lượt trả của một dòng: sp_delete_return(.., OUT p_deleted). Không còn dòng nào
     * bị trả thì hóa đơn về lại PAID. Thủ tục có hai câu ghi nên chạy trong một transaction.
     * @return số lượt trả đã xóa (0 = dòng này không có lượt trả nào)
     */
    public int deleteLineReturns(String invoiceId, int lineNumber) {
        String sql = "{call sp_delete_return(?, ?, ?)}";
        long t0 = System.currentTimeMillis();
        Connection conn = null;
        try {
            conn = Db.getConnection();
            conn.setAutoCommit(false);
            int deleted;
            try (CallableStatement cs = conn.prepareCall(sql)) {
                cs.setString(1, invoiceId);
                cs.setInt(2, lineNumber);
                cs.registerOutParameter(3, Types.INTEGER);
                cs.execute();
                deleted = cs.getInt(3);
            }
            conn.commit();
            SqlLog.add(sql, new Object[]{invoiceId, lineNumber}, System.currentTimeMillis() - t0, deleted);
            return deleted;
        } catch (SQLException e) {
            rollback(conn);
            throw new RuntimeException("Failed to cancel the return of line " + lineNumber + ": " + e.getMessage(), e);
        } finally {
            close(conn);
        }
    }

    // =================================================================
    //  GHI: các thao tác một câu (chỉ Quản lý -- quyền kiểm ở UI)
    // =================================================================

    /** Xóa hẳn hóa đơn cùng dòng hàng, thanh toán, trả hàng: sp_delete_invoice. */
    public void deleteInvoice(String invoiceId) {
        exec("{call sp_delete_invoice(?)}", "Failed to delete invoice " + invoiceId, invoiceId);
    }

    /** HỦY khác XÓA: dữ liệu còn nguyên, chỉ không tính doanh thu nữa: sp_void_invoice. */
    public void voidInvoice(String invoiceId) {
        exec("{call sp_void_invoice(?)}", "Cannot void invoice " + invoiceId, invoiceId);
    }

    /**
     * Sửa đầu hóa đơn: sp_update_invoice_header.
     * @param status trạng thái HIỆN TẠI -- thủ tục dùng làm phép kiểm, không phải trạng thái muốn đặt
     */
    public void updateInvoiceHeader(String invoiceId, LocalDate date, LocalTime time, String status,
                                    String counterId, String cashierId, String customerId) {
        String customer = (customerId == null || customerId.isBlank()) ? null : customerId.trim();
        exec("{call sp_update_invoice_header(?, ?, ?, ?, ?, ?, ?)}", "Failed to update invoice " + invoiceId,
             invoiceId, date, time, status, counterId, cashierId, customer);
    }

    /** Sửa một dòng hàng: sp_update_invoice_line. */
    public void updateInvoiceLine(String invoiceId, int lineNumber, String barcode,
                                  BigDecimal quantity, BigDecimal unitPrice, BigDecimal discount) {
        exec("{call sp_update_invoice_line(?, ?, ?, ?, ?, ?)}", "Failed to update line " + lineNumber,
             invoiceId, lineNumber, barcode, quantity, unitPrice, discount);
    }

    /** Xóa một dòng hàng: sp_delete_invoice_line. */
    public void deleteInvoiceLine(String invoiceId, int lineNumber) {
        exec("{call sp_delete_invoice_line(?, ?)}", "Failed to delete line " + lineNumber, invoiceId, lineNumber);
    }

    // =================================================================
    //  ĐỌC
    // =================================================================

    /** Hóa đơn gần đây kèm tổng tiền; days = 0 là tất cả: sp_get_recent_invoices(p_days). */
    public QueryResult recentInvoices(int days) {
        return QueryResult.call("{call sp_get_recent_invoices(?)}", days);
    }

    /** Đầu hóa đơn + tổng tiền (cột 12 = tiền hoàn, cột 13 = tổng sau điều chỉnh). */
    public QueryResult invoiceHeader(String invoiceId) {
        return QueryResult.call("{call sp_get_invoice_header(?)}", invoiceId);
    }

    public QueryResult invoiceLines(String invoiceId) {
        return QueryResult.call("{call sp_get_invoice_lines(?)}", invoiceId);
    }

    public QueryResult invoicePayments(String invoiceId) {
        return QueryResult.call("{call sp_get_invoice_payments(?)}", invoiceId);
    }

    /** Hóa đơn điều chỉnh = Invoice_Line - Return (chỉ là cách hiển thị, không có hóa đơn mới). */
    public QueryResult invoiceAdjusted(String invoiceId) {
        return QueryResult.call("{call sp_get_invoice_adjusted(?)}", invoiceId);
    }

    /** Các dòng còn trả được, kèm SL_con_lai để chặn trả vượt. */
    public QueryResult returnableLines(String invoiceId) {
        return QueryResult.call("{call sp_get_returnable_lines(?)}", invoiceId);
    }

    /** Trường THÔ của đầu hóa đơn cho hộp thoại sửa: Ma_HD, Ngay, Gio, Trang_thai, Quay, Thu_ngan, Khach. */
    public QueryResult invoiceEditFields(String invoiceId) {
        return QueryResult.call("{call sp_get_invoice_edit_fields(?)}", invoiceId);
    }

    /** Trường THÔ của một dòng cho hộp thoại sửa: Barcode, Quantity, Unit_Price, Discount. */
    public QueryResult invoiceLineEditFields(String invoiceId, int lineNumber) {
        return QueryResult.call("{call sp_get_invoice_line_edit_fields(?, ?)}", invoiceId, lineNumber);
    }

    /** Tiền hoàn cho một số lượng trả -- Java KHÔNG tự tính, công thức chỉ nằm ở SQL. */
    public BigDecimal returnRefund(String invoiceId, int lineNumber, BigDecimal quantity) {
        return scalarDecimal("{? = call fn_return_refund(?, ?, ?)}", invoiceId, lineNumber, quantity);
    }

    /** Tổng phải thu (0 nếu hóa đơn không có dòng nào). */
    public BigDecimal amountDue(String invoiceId) {
        return scalarDecimal("{? = call fn_invoice_amount_due(?)}", invoiceId);
    }

    /** Tổng đã GHI NHẬN = SUM(Payment.Amount), không phải tiền khách đưa. */
    public BigDecimal amountPaid(String invoiceId) {
        return scalarDecimal("{? = call fn_invoice_amount_paid(?)}", invoiceId);
    }

    // =================================================================
    //  Hàm phụ
    // =================================================================

    /** Gọi một thủ tục ghi một câu. Lỗi -> ném kèm SQLException gốc (UI đọc SQLState 45000). */
    private void exec(String sql, String errorPrefix, Object... params) {
        long t0 = System.currentTimeMillis();
        try (Connection c = Db.getConnection();
             CallableStatement cs = c.prepareCall(sql)) {
            for (int i = 0; i < params.length; i++) {
                Object v = params[i];
                if (v == null)                         cs.setNull(i + 1, Types.VARCHAR);
                else if (v instanceof LocalDate d)     cs.setDate(i + 1, Date.valueOf(d));
                else if (v instanceof LocalTime t)     cs.setTime(i + 1, Time.valueOf(t));
                else                                   cs.setObject(i + 1, v);
            }
            cs.executeUpdate();
            SqlLog.add(sql, params, System.currentTimeMillis() - t0, 1);
        } catch (SQLException e) {
            SqlLog.add(sql, params, System.currentTimeMillis() - t0, 0);   // nhật ký thấy cả lần bị từ chối
            throw new RuntimeException(errorPrefix + ": " + e.getMessage(), e);
        }
    }

    /** Gọi một Stored Function trả về DECIMAL: "{? = call fn_...(?)}". */
    private BigDecimal scalarDecimal(String sql, Object... params) {
        long t0 = System.currentTimeMillis();
        try (Connection c = Db.getConnection();
             CallableStatement cs = c.prepareCall(sql)) {
            cs.registerOutParameter(1, Types.DECIMAL);
            for (int i = 0; i < params.length; i++) {
                cs.setObject(i + 2, params[i]);         // vị trí 1 là giá trị trả về
            }
            cs.execute();
            BigDecimal v = cs.getBigDecimal(1);
            SqlLog.add(sql, params, System.currentTimeMillis() - t0, 1);
            return v == null ? BigDecimal.ZERO : v;
        } catch (SQLException e) {
            throw new RuntimeException("Cannot read " + sql + ": " + e.getMessage(), e);
        }
    }

    /** Stored Function trả về chuỗi, chạy trên Connection CỦA TRANSACTION (thấy được dòng chưa commit). */
    private String scalarString(Connection conn, String sql) throws SQLException {
        try (CallableStatement cs = conn.prepareCall(sql)) {
            cs.registerOutParameter(1, Types.VARCHAR);
            cs.execute();
            return cs.getString(1);
        }
    }

    private static void rollback(Connection conn) {
        if (conn == null) return;
        try {
            conn.rollback();
            SqlLog.add("ROLLBACK;  -- rolled back everything due to an error", null, 0, 0);
        } catch (SQLException ignore) { }
    }

    private static void close(Connection conn) {
        if (conn == null) return;
        try { conn.setAutoCommit(true); } catch (SQLException ignore) { }
        try { conn.close(); }            catch (SQLException ignore) { }
    }
}
