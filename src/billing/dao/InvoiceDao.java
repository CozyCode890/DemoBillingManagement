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
     *      - Gọi sp_create_invoice_header: tự sinh mã hóa đơn dạng
     *        &lt;Shop&gt;-&lt;Counter&gt;-&lt;YYYYMMDD&gt;-&lt;SEQ&gt; qua OUT parameter và ghi đầu hóa đơn
     *      - Gọi sp_add_invoice_line: ghi từng dòng hàng (batching), MySQL tự lấy
     *        giá, khuyến mãi, VAT và cộng dồn nếu trùng sản phẩm
     *      - Gọi fn_next_payment_id + sp_add_payment: ghi thanh toán
     *        (SQL tự rẽ nhánh bảng con)
     *      - Gọi sp_settle_invoice: CHỐT -- thiếu dòng hàng hoặc thu thiếu
     *        tiền thì thủ tục SIGNAL và cả transaction ROLLBACK
     *      conn.commit();
     *
     *  KHÔNG có tham số mã hóa đơn / ngày / giờ: cả ba do SERVER quyết (BR1,
     *  BR2) -- sp_create_invoice_header nhận NULL ở p_date / p_time và trigger
     *  trg_invoice_bi ghi đè bằng NOW(). Đơn giá / giảm giá / VAT trên từng
     *  CartLine cũng chỉ là bản XEM TRƯỚC của tab 1: sp_add_invoice_line tự
     *  tính lại theo ngày của server (BR5, BR8, BR11). Hai bên dùng chung
     *  công thức nên khớp nhau; tab 1 tính lại giỏ theo ngày server ngay
     *  trước khi gọi hàm này (NewInvoicePanel.save).
     *
     * @return mã hóa đơn vừa được server sinh ra
     */
    public String createInvoice(String counterId, String cashierId, String customerId,
                                List<CartLine> lines,
                                List<PaymentEntry> payments) {

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

            // ---- 1. Ghi phần đầu & tự sinh mã hóa đơn qua OUT param ---
            String invoiceId;
            try (CallableStatement cs = conn.prepareCall(spHeader)) {
                cs.setNull(1, Types.DATE);      // ngày / giờ: server tự lấy NOW() (BR2)
                cs.setNull(2, Types.TIME);
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
                SqlLog.add(spHeader, new Object[]{null, null,
                        counterId, cashierId, customerId, "OUT=" + invoiceId}, 0, 1);
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

                    // detail1 vẫn là CHUỖI (sp_add_payment giữ 6 tham số)
                    // nhưng nguồn của nó giờ là một
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
     * <p>Tham số {@code days} thêm ở khối 6 và đó là một <b>đổi hợp đồng</b>:
     * {@code days > 0} là lọc theo khoảng ngày,
     * {@code 0} là <b>tất cả</b> hóa đơn -- đúng hành vi trước khối 6.
     *
     * <p>Lý do: với seed 91 ngày thì bảng này có ~550 dòng, và tab 3 không có
     * ô lọc nào (chỉ có {@code RowSorter} -- sắp xếp, không lọc). Lọc bằng
     * khoảng ngày thay vì {@code LIMIT n} để mọi hóa đơn <b>vẫn đến được</b>
     * khi người dùng chọn <i>Tất cả</i> -- kể cả hóa đơn mẫu cũ nhất của seed
     * mà CÂU 1 trong 03_queries.sql lấy làm ví dụ.
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
    //  PHẦN 3 -- SAU KHI CHỐT: chỉ còn HỦY, ĐỔI KHÁCH, TRẢ HÀNG
    //  Hóa đơn đã chốt là bản ghi audit: không sửa ngày giờ / quầy / thu ngân,
    //  không sửa / xóa dòng hàng, không xóa hóa đơn hay lượt trả (BR4, BR8,
    //  BR16). Các thủ tục sp_delete_* / sp_update_invoice_line vẫn còn trong
    //  04_routines.sql nhưng luôn SIGNAL '45000', nên DAO không còn hàm gọi
    //  chúng -- ContractCheck gọi thẳng để chứng minh chúng từ chối.
    // =================================================================

    /**
     * Hủy một hóa đơn: sp_void_invoice(p_invoice_id, p_supervisor_id).
     *
     * <p>{@code supervisorId} phải là một nhân viên {@code Role = 'MANAGER'}
     * (BR3). Hóa đơn đã có trả hàng không hủy được (BR16) -- SQL chặn.
     * HỦY KHÁC XÓA: dữ liệu vẫn còn nguyên, chỉ không được tính vào doanh thu.
     *
     * @throws RuntimeException khi SQL từ chối. Exception gốc được giữ làm
     *         cause để UI đọc getSQLState() -- "45000" là lỗi nghiệp vụ.
     */
    public void voidInvoice(String invoiceId, String supervisorId) {
        String sql = "{call sp_void_invoice(?, ?)}";
        long t0 = System.currentTimeMillis();

        try (Connection c = Db.getConnection();
             CallableStatement cs = c.prepareCall(sql)) {

            cs.setString(1, invoiceId);
            cs.setString(2, supervisorId);
            cs.executeUpdate();

            SqlLog.add(sql, new Object[]{invoiceId, supervisorId}, System.currentTimeMillis() - t0, 1);

        } catch (SQLException e) {
            // Ghi lại cả lần thử thất bại: nhật ký SQL (tab SQL Log của Setup App)
            // phải thấy được câu gọi vừa bị từ chối, với 0 dòng ảnh hưởng.
            SqlLog.add(sql, new Object[]{invoiceId, supervisorId}, System.currentTimeMillis() - t0, 0);
            throw new RuntimeException("Cannot void invoice " + invoiceId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Đổi khách hàng của một hóa đơn (BR10) -- trường DUY NHẤT còn sửa được.
     *
     * <p>sp_update_invoice_header vẫn nhận đủ 7 tham số (hợp đồng cũ), nhưng
     * ngày / giờ / trạng thái / quầy / thu ngân phải BẰNG giá trị đang có. Nên
     * hàm này đọc lại chúng bằng sp_get_invoice_edit_fields rồi gửi nguyên
     * văn -- UI không phải giữ và gửi lại các trường bất biến. Không có khe hở
     * giữa hai câu: trigger trg_invoice_bu cấm đổi các trường đó.
     *
     * @param customerId mã khách; null hoặc rỗng = khách vãng lai
     */
    public void updateInvoiceCustomer(String invoiceId, String customerId) {
        QueryResult f = invoiceEditFields(invoiceId);
        if (f.isEmpty()) {
            throw new IllegalArgumentException("Invoice " + invoiceId + " not found");
        }
        // 0 Ma_HD | 1 Ngay | 2 Gio | 3 Trang_thai | 4 Quay | 5 Thu_ngan | 6 Khach
        Object[] r = f.rows.get(0);
        String cus = (customerId == null || customerId.isBlank()) ? null : customerId.trim();

        String sql = "{call sp_update_invoice_header(?, ?, ?, ?, ?, ?, ?)}";
        long t0 = System.currentTimeMillis();

        try (Connection c = Db.getConnection();
             CallableStatement cs = c.prepareCall(sql)) {

            cs.setString(1, invoiceId);
            cs.setObject(2, r[1]);
            cs.setObject(3, r[2]);
            cs.setObject(4, r[3]);
            cs.setObject(5, r[4]);
            cs.setObject(6, r[5]);
            if (cus == null) cs.setNull(7, Types.VARCHAR); else cs.setString(7, cus);
            cs.executeUpdate();

            SqlLog.add(sql, new Object[]{invoiceId, r[1], r[2], r[3], r[4], r[5], cus},
                       System.currentTimeMillis() - t0, 1);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to update invoice " + invoiceId + ": " + e.getMessage(), e);
        }
    }

    /**
     * ================================================================
     *  GHI MỘT LẦN TRẢ HÀNG GỒM NHIỀU DÒNG -- TRONG MỘT TRANSACTION
     * ================================================================
     *  Hoặc tất cả các dòng được ghi, hoặc không dòng nào. Hóa đơn gốc GIỮ
     *  NGUYÊN 'PAID' (BR16), nên một hóa đơn trả được nhiều lần cho tới khi
     *  hết số lượng; doanh thu tính theo Invoice_Line − Return.
     *
     *  Ngày trả KHÔNG lấy từ máy quầy: truyền NULL để sp_add_return dùng
     *  CURDATE() của server -- cùng lý do với ngày giờ hóa đơn (BR2). Máy quầy
     *  chỉnh lùi ngày thì Return_Date có thể rơi TRƯỚC ngày bán và bị
     *  trg_return_bi từ chối; chỉnh tới thì R8 (ngày trả <= hôm nay) bị vi phạm.
     *
     *  Tiền hoàn KHÔNG được truyền vào: nó là giá trị dẫn xuất, SQL tự tính
     *  bằng fn_return_refund.
     *
     * @param approvedByManagerId  Cashier_ID của Manager duyệt (BR15) -- SQL kiểm Role
     * @throws RuntimeException khi SQL từ chối. Thông điệp gốc của MySQL được
     *         giữ lại, kể cả thông điệp của SIGNAL '45000' trong sp_add_return,
     *         để tầng UI phân biệt được lỗi nghiệp vụ với lỗi thật.
     */
    public void processReturn(String invoiceId, List<ReturnItem> items, String approvedByManagerId) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("There are no lines to return");
        }

        String spReturn = "{call sp_add_return(?, ?, ?, ?, ?)}";

        long t0 = System.currentTimeMillis();
        Connection conn = null;

        // Mục đang được ghi, để nhật ký SQL còn thấy được CẢ LẦN THỬ THẤT BẠI.
        ReturnItem current = null;

        try {
            conn = Db.getConnection();
            conn.setAutoCommit(false);          // ==== BẮT ĐẦU TRANSACTION ====

            try (CallableStatement cs = conn.prepareCall(spReturn)) {
                for (ReturnItem it : items) {
                    current = it;
                    cs.setString(1, invoiceId);
                    cs.setInt(2, it.getLineNumber());
                    cs.setNull(3, Types.DATE);  // NULL -> CURDATE() của server
                    cs.setBigDecimal(4, it.getQuantity());
                    cs.setString(5, approvedByManagerId);
                    cs.executeUpdate();

                    SqlLog.add(spReturn, new Object[]{invoiceId, it.getLineNumber(),
                            null, it.getQuantity(), approvedByManagerId}, 0, 1);
                    current = null;             // dòng này đã ghi xong
                }
            }

            conn.commit();                      // ==== CHỐT: mọi thứ có hiệu lực ====
            SqlLog.add("COMMIT;  -- save return successfully for invoice " + invoiceId,
                       null, System.currentTimeMillis() - t0, 0);

        } catch (SQLException e) {
            if (current != null) {
                SqlLog.add(spReturn, new Object[]{invoiceId, current.getLineNumber(),
                        null, current.getQuantity(), approvedByManagerId}, 0, 0);
            }
            if (conn != null) {
                try {
                    conn.rollback();
                    SqlLog.add("ROLLBACK;  -- rolled back everything due to an error", null, 0, 0);
                } catch (SQLException ignore) { }
            }
            throw new RuntimeException("Failed to process return: " + e.getMessage(), e);

        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); } catch (SQLException ignore) { }
                try { conn.close(); }            catch (SQLException ignore) { }
            }
        }
    }
}
