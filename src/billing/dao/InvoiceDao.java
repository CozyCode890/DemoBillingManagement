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
     *      - Gọi sp_create_invoice_header: khóa quầy, sinh mã BR1 và ghi đầu hóa đơn
     *      - Gọi sp_add_invoice_line: ghi từng dòng hàng (batching)
     *      - Gọi fn_next_payment_id + sp_add_payment: ghi thanh toán
     *        (SQL tự rẽ nhánh bảng con)
     *      - Gọi sp_settle_invoice: CHỐT -- thiếu dòng hàng hoặc thu thiếu
     *        tiền thì thủ tục SIGNAL và cả transaction ROLLBACK
     *      conn.commit();
     *
     *  sp_create_invoice_header tạo mã SHOP-COUNTER-YYYYMMDD-SEQUENCE trong
     *  transaction và giữ khóa quầy đến khi commit. Vì vậy mã không được nhận
     *  từ giao diện/người gọi và hai checkout đồng thời không lấy cùng sequence.
     *
     * @return mã hóa đơn do sp_create_invoice_header sinh
     */
    public String createInvoice(LocalDate date, LocalTime time,
                                String counterId, String cashierId, String customerId,
                                List<CartLine> lines,
                                List<PaymentEntry> payments) {

        String fnPayId   = "{? = call fn_next_payment_id()}";
        String spHeader  = "{call sp_create_invoice_header(?, ?, ?, ?, ?, ?)}";
        String spLine    = "{call sp_add_invoice_line(?, ?, ?, ?, ?, ?)}";
        String spPayment = "{call sp_add_payment(?, ?, ?, ?, ?, ?)}";
        String spSettle  = "{call sp_settle_invoice(?)}";

        long t0 = System.currentTimeMillis();
        Connection conn = null;
        String invoiceId;

        try {
            conn = Db.getConnection();
            conn.setAutoCommit(false);          // ==== BẮT ĐẦU TRANSACTION ====

            // ---- 0. Sinh mã BR1 và ghi đầu hóa đơn trong một thủ tục --
            try (CallableStatement cs = conn.prepareCall(spHeader)) {
                cs.setDate(1, Date.valueOf(date));
                cs.setTime(2, Time.valueOf(time));
                cs.setString(3, counterId);
                cs.setString(4, cashierId);
                if (customerId == null) {
                    cs.setNull(5, Types.VARCHAR);
                } else {
                    cs.setString(5, customerId);
                }
                cs.registerOutParameter(6, Types.VARCHAR);
                cs.execute();
                invoiceId = cs.getString(6);
                SqlLog.add(spHeader, new Object[]{date, time, counterId,
                        cashierId, customerId, invoiceId}, 0, 1);
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

            // ---- 4. Chốt hóa đơn: phải có dòng hàng và đã thu đủ tiền ---
            // Đầu hóa đơn đã được ghi là PAID (không còn trạng thái OPEN --
            // 01_schema.sql mục 9). sp_settle_invoice là phép KIỂM cuối: thu
            // thiếu thì nó SIGNAL, khối catch bên dưới ROLLBACK, và hóa đơn
            // không bao giờ tồn tại. Tab 1 đã chặn trước ở giao diện.
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
            SqlLog.add("COMMIT;  -- save invoice successfully " + invoiceId,
                       null, System.currentTimeMillis() - t0, 0);
            return invoiceId;

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                    SqlLog.add("ROLLBACK;  -- rolled back everything due to an error", null, 0, 0);
                } catch (SQLException ignore) { }
            }
            throw new RuntimeException("Failed to save invoice: " + e.getMessage(), e);

        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); } catch (SQLException ignore) { }
                try { conn.close(); }            catch (SQLException ignore) { }
            }
        }
    }

    // =================================================================
    //  PHẦN 2 -- ĐỌC DỮ LIỆU QUA CÁC STORED PROCEDURE
    // =================================================================

    /**
     * Danh sách hóa đơn gần đây kèm tổng tiền: {@code sp_get_recent_invoices(p_days)}.
     *
     * <p>Tham số {@code days} thêm ở khối 6 và đó là một <b>đổi hợp đồng</b>
     * (docs/05 Quy tắc 2): {@code days > 0} là lọc theo khoảng ngày,
     * {@code 0} là <b>tất cả</b> hóa đơn -- đúng hành vi trước khối 6.
     *
     * <p>Lý do: với seed 91 ngày thì bảng này có ~550 dòng, và tab 3 không có
     * ô lọc nào (chỉ có {@code RowSorter} -- sắp xếp, không lọc). Lọc bằng
     * khoảng ngày thay vì {@code LIMIT n} để mọi hóa đơn <b>vẫn đến được</b>
     * khi người dùng chọn <i>Tất cả</i>: hóa đơn mẫu và hai hóa đơn từ năm
     * trước vẫn đến được cùng mọi hóa đơn khác (plan/15 mục 5.2).
     */
    public QueryResult recentInvoices(int days) {
        return QueryResult.call("{call sp_get_recent_invoices(?)}", days);
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

    /**
     * Hóa đơn điều chỉnh = Invoice_Line − Return: sp_get_invoice_adjusted(p_invoice_id).
     * Không có hóa đơn mới nào trong database -- đây chỉ là cách hiển thị
     * (mỗi dòng hóa đơn gốc kèm số lượng / thành tiền sau khi trừ phần trả).
     */
    public QueryResult invoiceAdjusted(String invoiceId) {
        return QueryResult.call("{call sp_get_invoice_adjusted(?)}", invoiceId);
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
            throw new RuntimeException("Cannot calculate refund amount for line "
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
                           "Cannot read total amount due for invoice ");
    }

    /**
     * Tổng tiền ĐÃ GHI NHẬN: fn_invoice_amount_paid(p_invoice_id).
     * Là SUM(Payment.Amount), KHÔNG phải tiền khách đưa
     * (tiền khách đưa nằm ở Cash_Payment.Tendered_Amount).
     */
    public BigDecimal amountPaid(String invoiceId) {
        return scalarMoney("{? = call fn_invoice_amount_paid(?)}", invoiceId,
                           "Cannot read total amount paid for invoice ");
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
            throw new RuntimeException("Failed to delete invoice " + invoiceId + ": " + e.getMessage(), e);
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
            throw new RuntimeException("Cannot void invoice " + invoiceId
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
            throw new RuntimeException("Failed to update invoice " + invoiceId + ": " + e.getMessage(), e);
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
            throw new RuntimeException("Failed to update line " + lineNumber + ": " + e.getMessage(), e);
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
            throw new RuntimeException("Failed to delete line " + lineNumber + ": " + e.getMessage(), e);
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
            throw new IllegalArgumentException("There are no lines to return");
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

            // Trả hàng xong -> hóa đơn gốc thành VOIDED, CÙNG transaction.
            // Không cần kiểm "đang PAID": sp_add_return ở trên đã SIGNAL nếu
            // hóa đơn không phải PAID. Từ đây hóa đơn không trả thêm được nữa;
            // doanh thu của nó tính theo hóa đơn điều chỉnh (Invoice_Line −
            // Return), xem fn_invoice_in_revenue ở 04_routines.sql.
            String spVoid = "{call sp_void_invoice(?)}";
            try (CallableStatement cs = conn.prepareCall(spVoid)) {
                cs.setString(1, invoiceId);
                cs.executeUpdate();
                SqlLog.add(spVoid, new Object[]{invoiceId}, 0, 1);
            }

            conn.commit();                      // ==== CHỐT: mọi thứ có hiệu lực ====
            SqlLog.add("COMMIT;  -- save return successfully for invoice " + invoiceId,
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
                    SqlLog.add("ROLLBACK;  -- rolled back everything due to an error", null, 0, 0);
                } catch (SQLException ignore) { }
            }
            // Giữ nguyên exception gốc làm cause: UI đọc getSQLState() của nó
            // để biết đây là SIGNAL '45000' (lỗi nghiệp vụ) hay lỗi thật.
            throw new RuntimeException("Failed to process return: " + e.getMessage(), e);

        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); } catch (SQLException ignore) { }
                try { conn.close(); }            catch (SQLException ignore) { }
            }
        }
    }

    /**
     * Hủy MỌI lượt trả hàng của một dòng: sp_delete_return(p_invoice_id,
     * p_line_number, OUT p_deleted).
     *
     * <p>Theo DÒNG chứ không theo Return_ID: Return_ID vẫn là khóa trong DB
     * nhưng đã ẩn khỏi giao diện (hóa đơn điều chỉnh hiện theo dòng). Thủ tục
     * có hai câu ghi -- DELETE, và UPDATE trả hóa đơn về PAID khi không còn
     * dòng nào bị trả -- nên chạy trong MỘT transaction: chết giữa chừng thì
     * hóa đơn không bị kẹt ở VOIDED mà không có dòng Return (lúc đó nó sẽ bị
     * coi là hóa đơn bị hủy và rơi khỏi doanh thu).
     *
     * @return số lượt trả đã xóa (0 = dòng này không có lượt trả nào)
     */
    public int deleteLineReturns(String invoiceId, int lineNumber) {
        String sql = "{call sp_delete_return(?, ?, ?)}";
        long t0 = System.currentTimeMillis();
        Connection c = null;

        try {
            c = Db.getConnection();
            c.setAutoCommit(false);
            int deleted;
            try (CallableStatement cs = c.prepareCall(sql)) {
                cs.setString(1, invoiceId);
                cs.setInt(2, lineNumber);
                cs.registerOutParameter(3, Types.INTEGER);
                cs.execute();
                deleted = cs.getInt(3);
            }
            c.commit();
            SqlLog.add(sql, new Object[]{invoiceId, lineNumber},
                       System.currentTimeMillis() - t0, deleted);
            return deleted;

        } catch (SQLException e) {
            if (c != null) {
                try { c.rollback(); } catch (SQLException ignore) { }
            }
            throw new RuntimeException("Failed to cancel the return of line " + lineNumber
                                       + ": " + e.getMessage(), e);
        } finally {
            if (c != null) {
                try { c.setAutoCommit(true); } catch (SQLException ignore) { }
                try { c.close(); }            catch (SQLException ignore) { }
            }
        }
    }
}

