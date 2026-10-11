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
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
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

    /**
     * Một mục trong danh sách trả hàng: dòng nào, trả bao nhiêu.
     *
     * KHÔNG chứa tiền hoàn -- tiền hoàn do SQL tính qua fn_return_refund
     * (plan/12 mục 2.2). Số lượng là BigDecimal vì Return_Quantity là
     * DECIMAL(10,3): có hoá đơn bán 1,5 KG thịt.
     */
    public static class ReturnItem {
        private final String     productName;   // chỉ để hiển thị / thông báo lỗi
        private final int        lineNumber;
        private final BigDecimal quantity;      // DECIMAL(10,3), không dùng double

        public ReturnItem(String productName, int lineNumber, BigDecimal quantity) {
            this.productName = productName;
            this.lineNumber = lineNumber;
            this.quantity = quantity;
        }

        public String getProductName() {
            return productName;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public BigDecimal getQuantity() {
            return quantity;
        }
    }

    // =================================================================
    //  PHẦN 1 -- GHI DỮ LIỆU QUA THỦ TỤC & QUẢN LÝ TRANSACTION
    // =================================================================

    /**
     * ================================================================
     *  LƯU MỘT HÓA ĐƠN -- TRANSACTION KẾT HỢP STORED PROCEDURES
     * ================================================================
     *  Java quản lý ranh giới nghiệp vụ (Business Transaction):
     *      conn.setAutoCommit(false);
     *      - Gọi fn_next_invoice_id: sinh mã hóa đơn (nếu chỗ gọi truyền null)
     *      - Gọi sp_create_invoice_header: ghi đầu hóa đơn
     *      - Gọi sp_add_invoice_line: ghi từng dòng hàng (batching)
     *      - Gọi fn_next_payment_id + sp_add_payment: ghi thanh toán
     *        (SQL tự rẽ nhánh bảng con)
     *      - Gọi sp_settle_invoice: chuyển sang PAID nếu đã thu đủ tiền
     *      conn.commit();
     *
     * @param invoiceId mã hóa đơn, hoặc {@code null} để tự sinh TRONG
     *        transaction. Truyền {@code null} là cách dùng đúng từ khối 5: bản
     *        cũ gọi {@link #nextInvoiceId()} trên MỘT CONNECTION KHÁC trước khi
     *        mở transaction, nên hai cửa sổ app cùng lưu là trùng khóa chính và
     *        thu ngân mất giỏ hàng (plan/14 lỗi #18).
     *
     *        <p>Khe hở còn lại -- hai transaction song song vẫn đọc được cùng
     *        {@code MAX(...)} trước khi cái nào {@code INSERT} -- KHÔNG được bịt
     *        ở đây, và đây là một ghi chú trung thực chứ không phải bỏ sót: bịt
     *        đúng cách là {@code INSERT ... SELECT} hoặc một bảng cấp số, tức là
     *        đổi {@code fn_next_invoice_id} và {@code sp_create_invoice_header}.
     *        Cái sửa được ở đây là thu hẹp cửa sổ từ "khoảng thời gian người
     *        dùng đọc hộp thoại" xuống "vài micro giây trong một transaction",
     *        và làm cho lần thất bại là một {@code ROLLBACK} sạch kèm thông điệp
     *        đọc hiểu được (plan/14 mục 5.4a).
     *
     * @return mã hóa đơn đã dùng (chính {@code invoiceId}, hoặc mã vừa sinh)
     */
    public String createInvoice(String invoiceId,
                                LocalDate date, LocalTime time,
                                String counterId, String cashierId, String customerId,
                                List<CartLine> lines,
                                List<PaymentEntry> payments) {

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
            conn.setAutoCommit(false);          // ==== BẮT ĐẦU TRANSACTION ====

            // ---- 0. Mã hóa đơn: sinh TRONG transaction ---------------
            if (invoiceId == null) {
                invoiceId = scalarString(conn, fnInvId);
                SqlLog.add(fnInvId, null, 0, 1);
            }

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
            //
            // Mã thanh toán do fn_next_payment_id() sinh, KHÔNG phải một bản
            // chép của thân hàm đó bằng SQL thô trong Java: quy tắc sinh mã là
            // một quy tắc nghiệp vụ, và đổi định dạng mã mà sửa một bên là hỏng
            // ngay (plan/14 mục 4.5).
            //
            // Gọi TRONG transaction, một lần cho mỗi dòng: dòng Payment vừa
            // INSERT ở vòng trước đã nhìn thấy được trên cùng Connection, nên
            // mã kế tiếp đúng.
            try (CallableStatement csId = conn.prepareCall(fnPayId);
                 CallableStatement cs   = conn.prepareCall(spPayment)) {
                for (PaymentEntry pe : payments) {
                    csId.registerOutParameter(1, Types.VARCHAR);
                    csId.execute();
                    String payId = csId.getString(1);
                    SqlLog.add(fnPayId, null, 0, 1);

                    // detail1 vẫn là CHUỖI (sp_add_payment giữ 6 tham số,
                    // docs/05 Quy tắc 2) nhưng nguồn của nó giờ là một
                    // BigDecimal có kiểu -- tiền khách đưa -- chứ không phải
                    // một bản sao của pe.amount (plan/14 lỗi #9).
                    String d1 = pe.wireDetail1();

                    cs.setString(1, payId);
                    cs.setString(2, invoiceId);
                    cs.setBigDecimal(3, pe.amount);     // Payment.Amount = tiền GHI NHẬN
                    cs.setString(4, pe.method);
                    cs.setString(5, d1);
                    cs.setString(6, pe.detail2);
                    cs.executeUpdate();

                    SqlLog.add(spPayment, new Object[]{payId, invoiceId, pe.amount,
                            pe.method, d1, pe.detail2}, 0, 1);
                }
            }

            // ---- 4. Chuyển trạng thái nếu đã thu đủ tiền --------------
            // Java KHÔNG tự quyết định PAID (plan/13 mục 2.2): thủ tục tự đọc
            // bảng Payment và so với tổng tiền các dòng hàng. Trả thiếu thì hóa
            // đơn ở lại OPEN, và đây KHÔNG phải lỗi.
            //
            // PHẢI nằm TRONG transaction: thủ tục đọc các dòng Payment vừa
            // INSERT mà chưa commit -- chỉ cùng một Connection mới thấy chúng.
            // executeUpdate được vì sp_settle_invoice không trả ResultSet.
            try (CallableStatement cs = conn.prepareCall(spSettle)) {
                cs.setString(1, invoiceId);
                cs.executeUpdate();
                SqlLog.add(spSettle, new Object[]{invoiceId}, 0, 1);
            }

            conn.commit();                      // ==== CHỐT: mọi thứ có hiệu lực ====
            SqlLog.add("COMMIT;  -- luu thanh cong hoa don " + invoiceId,
                       null, System.currentTimeMillis() - t0, 0);
            return invoiceId;

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

    /**
     * Gọi một Stored Function trả về chuỗi, TRÊN CONNECTION ĐANG CHO SẴN.
     *
     * <p>Khác {@link #nextInvoiceId()}: hàm đó tự mở Connection riêng, nên cái
     * nó đọc được là trạng thái đã commit của người khác. Ở đây ta cần đúng
     * Connection của transaction để thấy được những gì chính transaction này
     * vừa ghi (plan/14 mục 4.5, 5.4a).
     */
    private String scalarString(Connection conn, String callSql) throws SQLException {
        try (CallableStatement cs = conn.prepareCall(callSql)) {
            cs.registerOutParameter(1, Types.VARCHAR);
            cs.execute();
            return cs.getString(1);
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

    /**
     * Các trường THÔ của một hóa đơn cho hộp thoại sửa:
     * sp_get_invoice_edit_fields(p_invoice_id).
     *
     * Khác invoiceHeader(): trả về MÃ (Counter_ID, Cashier_ID, Customer_ID)
     * chứ không phải TÊN, và không join Invoice_Line nên hóa đơn chưa có dòng
     * hàng nào vẫn đọc được. Thứ tự 7 cột là hợp đồng với Java -- chỗ gọi đọc
     * theo chỉ số r[1]..r[6] (plan/13 mục 3.5).
     */
    public QueryResult invoiceEditFields(String invoiceId) {
        return QueryResult.call("{call sp_get_invoice_edit_fields(?)}", invoiceId);
    }

    /**
     * Các trường THÔ của một DÒNG HÀNG cho hộp thoại sửa:
     * {@code sp_get_invoice_line_edit_fields(p_invoice_id, p_line_number)}.
     *
     * <p>Thay câu SQL thô cuối cùng còn lại trong tầng UI
     * ({@code InvoiceViewPanel.onEditLine}). Thứ tự 4 cột
     * {@code Barcode, Quantity, Unit_Price, Discount} là hợp đồng với Java --
     * chỗ gọi đọc theo chỉ số {@code r[0]..r[3]}.
     *
     * <p>Trả 0 dòng khi không có dòng hàng đó: chỗ gọi tự báo "không tìm thấy".
     */
    public QueryResult invoiceLineEditFields(String invoiceId, int lineNumber) {
        return QueryResult.call("{call sp_get_invoice_line_edit_fields(?, ?)}",
                                invoiceId, lineNumber);
    }

    /** Các lần thanh toán của hóa đơn: sp_get_invoice_payments(p_invoice_id) */
    public QueryResult invoicePayments(String invoiceId) {
        return QueryResult.call("{call sp_get_invoice_payments(?)}", invoiceId);
    }

    /** Các lần trả hàng của hóa đơn: sp_get_invoice_returns(p_invoice_id) */
    public QueryResult invoiceReturns(String invoiceId) {
        return QueryResult.call("{call sp_get_invoice_returns(?)}", invoiceId);
    }

    /**
     * Các dòng còn trả được của hóa đơn: sp_get_returnable_lines(p_invoice_id).
     * Cột SL_con_lai là thứ mà hộp thoại trả hàng cần để chặn trả vượt.
     */
    public QueryResult returnableLines(String invoiceId) {
        return QueryResult.call("{call sp_get_returnable_lines(?)}", invoiceId);
    }

    /**
     * Tiền hoàn cho một số lượng của một dòng: fn_return_refund(...).
     *
     * Java KHÔNG tự tính số này, dù chỉ là một phép nhân -- công thức tiền hoàn
     * chỉ tồn tại ở một chỗ trong cả dự án, và chỗ đó là SQL (plan/12 mục 2.2).
     * Nhờ vậy con số trên hộp thoại xác nhận bằng đúng con số hiện ra ở tab 3
     * sau khi lưu.
     */
    public BigDecimal returnRefund(String invoiceId, int lineNumber, BigDecimal quantity) {
        String sql = "{? = call fn_return_refund(?, ?, ?)}";
        long t0 = System.currentTimeMillis();

        try (Connection c = Db.getConnection();
             CallableStatement cs = c.prepareCall(sql)) {

            cs.registerOutParameter(1, Types.DECIMAL);
            cs.setString(2, invoiceId);
            cs.setInt(3, lineNumber);
            cs.setBigDecimal(4, quantity);
            cs.execute();
            BigDecimal refund = cs.getBigDecimal(1);

            SqlLog.add(sql, new Object[]{invoiceId, lineNumber, quantity},
                       System.currentTimeMillis() - t0, 1);
            return refund == null ? BigDecimal.ZERO : refund;

        } catch (SQLException e) {
            throw new RuntimeException("Khong tinh duoc tien hoan cho dong "
                                       + lineNumber + ": " + e.getMessage(), e);
        }
    }

    /**
     * Tổng tiền hóa đơn PHẢI thu: fn_invoice_amount_due(p_invoice_id).
     *
     * Trả 0 cho hóa đơn không có dòng hàng nào, nên NGƯỜI GỌI phải tự biết
     * hóa đơn có dòng hàng hay không trước khi so sánh: 0 >= 0 là true
     * (plan/13 mục 3.4 điểm 2). Java đọc hai số này CHỈ để hiển thị -- phép
     * so sánh quyết định trạng thái chỉ tồn tại trong sp_settle_invoice.
     */
    public BigDecimal amountDue(String invoiceId) {
        return scalarMoney("{? = call fn_invoice_amount_due(?)}", invoiceId,
                           "Khong doc duoc tong phai tra cua hoa don ");
    }

    /**
     * Tổng tiền ĐÃ GHI NHẬN: fn_invoice_amount_paid(p_invoice_id).
     * Là SUM(Payment.Amount), KHÔNG phải tiền khách đưa
     * (tiền khách đưa nằm ở Cash_Payment.Tendered_Amount).
     */
    public BigDecimal amountPaid(String invoiceId) {
        return scalarMoney("{? = call fn_invoice_amount_paid(?)}", invoiceId,
                           "Khong doc duoc tong da thu cua hoa don ");
    }

    /** Khuôn chung của hai hàm tiền ở trên -- cùng một câu gọi, khác tên hàm. */
    private BigDecimal scalarMoney(String sql, String invoiceId, String errorPrefix) {
        long t0 = System.currentTimeMillis();

        try (Connection c = Db.getConnection();
             CallableStatement cs = c.prepareCall(sql)) {

            cs.registerOutParameter(1, Types.DECIMAL);
            cs.setString(2, invoiceId);
            cs.execute();
            BigDecimal amount = cs.getBigDecimal(1);

            SqlLog.add(sql, new Object[]{invoiceId}, System.currentTimeMillis() - t0, 1);
            return amount == null ? BigDecimal.ZERO : amount;

        } catch (SQLException e) {
            throw new RuntimeException(errorPrefix + invoiceId + ": " + e.getMessage(), e);
        }
    }


    // =================================================================
    //  PHẦN 3 -- THAO TÁC SỬA / XÓA TRỰC TIẾP TRONG DATABASE (DAO)
    //  (Lưu ý: Không kiểm tra quyền ở đây, quyền được kiểm soát tại UI)
    // =================================================================

    /**
     * Xóa hoàn toàn một hóa đơn và toàn bộ dữ liệu phụ thuộc trong database.
     * Sử dụng thủ tục sp_delete_invoice().
     */
    public void deleteInvoice(String invoiceId) {
        String sql = "{call sp_delete_invoice(?)}";
        long t0 = System.currentTimeMillis();

        try (Connection c = Db.getConnection();
             CallableStatement cs = c.prepareCall(sql)) {

            cs.setString(1, invoiceId);
            cs.executeUpdate();

            SqlLog.add(sql, new Object[]{invoiceId}, System.currentTimeMillis() - t0, 1);

        } catch (SQLException e) {
            throw new RuntimeException("Loi khi xoa hoa don " + invoiceId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Hủy một hóa đơn: sp_void_invoice(p_invoice_id).
     *
     * HỦY KHÁC XÓA: dữ liệu vẫn còn nguyên (dòng hàng, thanh toán, trả hàng),
     * chỉ không được tính vào báo cáo nữa vì các câu truy vấn báo cáo đều lọc
     * WHERE Status = 'PAID'. Muốn xóa sạch thì dùng deleteInvoice().
     *
     * Đây là ĐƯỜNG DUY NHẤT tới VOIDED: hộp thoại sửa hóa đơn không đổi được
     * trạng thái nữa (plan/13 mục 3.2).
     *
     * @throws RuntimeException khi SQL từ chối. Exception gốc được giữ làm
     *         cause để UI đọc getSQLState() -- "45000" là lỗi nghiệp vụ
     *         (hóa đơn đã hủy trước đó, hoặc không tìm thấy hóa đơn).
     */
    public void voidInvoice(String invoiceId) {
        String sql = "{call sp_void_invoice(?)}";
        long t0 = System.currentTimeMillis();

        try (Connection c = Db.getConnection();
             CallableStatement cs = c.prepareCall(sql)) {

            cs.setString(1, invoiceId);
            cs.executeUpdate();

            SqlLog.add(sql, new Object[]{invoiceId}, System.currentTimeMillis() - t0, 1);

        } catch (SQLException e) {
            // Ghi lại cả lần thử thất bại: nhật ký SQL (tab 5 Setup App) phải
            // thấy được câu gọi vừa bị từ chối, với 0 dòng ảnh hưởng.
            SqlLog.add(sql, new Object[]{invoiceId}, System.currentTimeMillis() - t0, 0);
            // Giữ nguyên exception gốc làm cause -- UI đọc getSQLState() của nó.
            throw new RuntimeException("Khong huy duoc hoa don " + invoiceId
                                       + ": " + e.getMessage(), e);
        }
    }

    /**
     * Cập nhật thông tin phần đầu hóa đơn (Header).
     * Sử dụng thủ tục sp_update_invoice_header().
     *
     * @param status trạng thái HIỆN TẠI của hóa đơn, KHÔNG phải trạng thái
     *        muốn đặt. sp_update_invoice_header dùng tham số này làm phép
     *        kiểm: khác trạng thái trong DB là bị từ chối bằng SIGNAL '45000'.
     *        Mọi chuyển đổi trạng thái đi qua voidInvoice() hoặc
     *        sp_settle_invoice (plan/13 mục 3.2).
     */
    public void updateInvoiceHeader(String invoiceId, LocalDate date, LocalTime time,
                                    String status, String counterId, String cashierId, String customerId) {
        String sql = "{call sp_update_invoice_header(?, ?, ?, ?, ?, ?, ?)}";
        long t0 = System.currentTimeMillis();

        try (Connection c = Db.getConnection();
             CallableStatement cs = c.prepareCall(sql)) {

            cs.setString(1, invoiceId);
            cs.setDate(2, Date.valueOf(date));
            cs.setTime(3, Time.valueOf(time));
            cs.setString(4, status);
            cs.setString(5, counterId);
            cs.setString(6, cashierId);
            if (customerId == null || customerId.trim().isEmpty()) {
                cs.setNull(7, Types.VARCHAR);
            } else {
                cs.setString(7, customerId.trim());
            }

            cs.executeUpdate();
            SqlLog.add(sql, new Object[]{invoiceId, date, time, status, counterId, cashierId, customerId},
                       System.currentTimeMillis() - t0, 1);

        } catch (SQLException e) {
            throw new RuntimeException("Loi khi cap nhat hoa don " + invoiceId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Cập nhật một dòng hàng trong hóa đơn.
     * Sử dụng thủ tục sp_update_invoice_line().
     */
    public void updateInvoiceLine(String invoiceId, int lineNumber, String barcode,
                                  BigDecimal quantity, BigDecimal unitPrice, BigDecimal discount) {
        String sql = "{call sp_update_invoice_line(?, ?, ?, ?, ?, ?)}";
        long t0 = System.currentTimeMillis();

        try (Connection c = Db.getConnection();
             CallableStatement cs = c.prepareCall(sql)) {

            cs.setString(1, invoiceId);
            cs.setInt(2, lineNumber);
            cs.setString(3, barcode);
            cs.setBigDecimal(4, quantity);
            cs.setBigDecimal(5, unitPrice);
            cs.setBigDecimal(6, discount);

            cs.executeUpdate();
            SqlLog.add(sql, new Object[]{invoiceId, lineNumber, barcode, quantity, unitPrice, discount},
                       System.currentTimeMillis() - t0, 1);

        } catch (SQLException e) {
            throw new RuntimeException("Loi khi cap nhat dong hang " + lineNumber + ": " + e.getMessage(), e);
        }
    }

    /**
     * Xóa một dòng hàng trong hóa đơn.
     * Sử dụng thủ tục sp_delete_invoice_line().
     */
    public void deleteInvoiceLine(String invoiceId, int lineNumber) {
        String sql = "{call sp_delete_invoice_line(?, ?)}";
        long t0 = System.currentTimeMillis();

        try (Connection c = Db.getConnection();
             CallableStatement cs = c.prepareCall(sql)) {

            cs.setString(1, invoiceId);
            cs.setInt(2, lineNumber);

            cs.executeUpdate();
            SqlLog.add(sql, new Object[]{invoiceId, lineNumber}, System.currentTimeMillis() - t0, 1);

        } catch (SQLException e) {
            throw new RuntimeException("Loi khi xoa dong hang " + lineNumber + ": " + e.getMessage(), e);
        }
    }

    /**
     * ================================================================
     *  GHI MỘT LẦN TRẢ HÀNG GỒM NHIỀU DÒNG -- TRONG MỘT TRANSACTION
     * ================================================================
     *  Hoặc tất cả các dòng được ghi, hoặc không dòng nào.
     *
     *  Hàm này KHÔNG trả boolean: lỗi thì NÉM, giống 5 hàm ghi DB còn lại của
     *  lớp này. Bản cũ trả `false` và in nguyên nhân thật ra System.err, nên
     *  người dùng chỉ thấy câu "that bai, thu lai sau" trong khi lỗi thật là
     *  "sp_add_return does not exist" -- thử lại bao lâu cũng vậy.
     *
     *  Tiền hoàn KHÔNG được truyền vào: nó là giá trị dẫn xuất, SQL tự tính
     *  bằng fn_return_refund (plan/12 mục 2.2).
     *
     * @param invoiceId            mã hóa đơn
     * @param items                các dòng cần trả, kèm số lượng
     * @param approvedByCashierId  mã nhân viên duyệt -- Cashier_ID, KHÔNG phải username
     * @throws RuntimeException khi SQL từ chối. Thông điệp gốc của MySQL được
     *         giữ lại, kể cả thông điệp của SIGNAL '45000' trong sp_add_return,
     *         để tầng UI phân biệt được lỗi nghiệp vụ với lỗi thật.
     */
    public void processReturn(String invoiceId, List<ReturnItem> items, String approvedByCashierId) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Khong co dong hang nao de tra");
        }

        String spReturn = "{call sp_add_return(?, ?, ?, ?, ?)}";

        long t0 = System.currentTimeMillis();
        Connection conn = null;

        // MỘT mốc ngày cho cả lượt trả. Bản cũ gọi LocalDate.now() trong vòng
        // lặp: bấm lúc 23:59:59.9 thì các dòng của cùng một lượt trả rơi vào
        // hai ngày khác nhau.
        LocalDate today = LocalDate.now();

        // Mục đang được ghi, để nhật ký SQL còn thấy được CẢ LẦN THỬ THẤT BẠI
        // (plan/12 kịch bản #55: tab 5 phải có dòng sp_add_return rồi ROLLBACK).
        ReturnItem current = null;

        try {
            conn = Db.getConnection();
            conn.setAutoCommit(false);          // ==== BẮT ĐẦU TRANSACTION ====

            // prepareCall NGOÀI vòng lặp -- chuẩn bị lại câu gọi cho từng dòng
            // là vô ích (sp_add_payment ở trên đã làm đúng cách này).
            try (CallableStatement cs = conn.prepareCall(spReturn)) {
                for (ReturnItem it : items) {
                    current = it;
                    cs.setString(1, invoiceId);
                    cs.setInt(2, it.getLineNumber());
                    cs.setDate(3, Date.valueOf(today));
                    cs.setBigDecimal(4, it.getQuantity());
                    cs.setString(5, approvedByCashierId);
                    cs.executeUpdate();

                    SqlLog.add(spReturn, new Object[]{invoiceId, it.getLineNumber(),
                            today, it.getQuantity(), approvedByCashierId}, 0, 1);
                    current = null;             // dòng này đã ghi xong
                }
            }

            conn.commit();                      // ==== CHỐT: mọi thứ có hiệu lực ====
            SqlLog.add("COMMIT;  -- luu thanh cong tra hang hoa don " + invoiceId,
                       null, System.currentTimeMillis() - t0, 0);

        } catch (SQLException e) {
            // Ghi lại câu gọi vừa bị từ chối: "0 dong" cộng với dòng ROLLBACK
            // ngay sau đó kể đủ câu chuyện cho người đọc nhật ký.
            if (current != null) {
                SqlLog.add(spReturn, new Object[]{invoiceId, current.getLineNumber(),
                        today, current.getQuantity(), approvedByCashierId}, 0, 0);
            }
            if (conn != null) {
                try {
                    conn.rollback();
                    SqlLog.add("ROLLBACK;  -- da huy toan bo vi co loi", null, 0, 0);
                } catch (SQLException ignore) { }
            }
            // Giữ nguyên exception gốc làm cause: UI đọc getSQLState() của nó
            // để biết đây là SIGNAL '45000' (lỗi nghiệp vụ) hay lỗi thật.
            throw new RuntimeException("Xu ly tra hang that bai: " + e.getMessage(), e);

        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); } catch (SQLException ignore) { }
                try { conn.close(); }            catch (SQLException ignore) { }
            }
        }
    }

    /**
     * Hủy một lượt trả hàng đã ghi: sp_delete_return(...).
     *
     * @return số dòng đã xóa (0 nghĩa là không tìm thấy lượt trả đó)
     */
    public int deleteReturn(String invoiceId, int lineNumber, int returnId) {
        String sql = "{call sp_delete_return(?, ?, ?)}";
        long t0 = System.currentTimeMillis();

        try (Connection c = Db.getConnection();
             CallableStatement cs = c.prepareCall(sql)) {

            cs.setString(1, invoiceId);
            cs.setInt(2, lineNumber);
            cs.setInt(3, returnId);

            int affected = cs.executeUpdate();
            SqlLog.add(sql, new Object[]{invoiceId, lineNumber, returnId},
                       System.currentTimeMillis() - t0, affected);
            return affected;

        } catch (SQLException e) {
            throw new RuntimeException("Loi khi huy luot tra lan " + returnId
                                       + " cua dong " + lineNumber + ": " + e.getMessage(), e);
        }
    }
}

