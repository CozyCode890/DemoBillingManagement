package billing.dao;

import billing.db.Db;
import billing.db.QueryResult;
import billing.db.SqlLog;
import billing.model.CartLine;
import billing.model.PaymentEntry;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class InvoiceDao {

    // =================================================================
    //  PHẦN 1 -- GHI DỮ LIỆU (INSERT) VÀ TRANSACTION
    // =================================================================

    /**
     * ================================================================
     *  LƯU MỘT HÓA ĐƠN -- ví dụ kinh điển về TRANSACTION
     * ================================================================
     *
     *  Lưu một hóa đơn không phải một lệnh INSERT, mà là nhiều lệnh:
     *      1 dòng  Invoice
     *      N dòng  Invoice_Line
     *      M dòng  Payment  (+ 1 dòng bảng con cho mỗi Payment)
     *
     *  Nếu lệnh thứ 3 lỗi mà 2 lệnh đầu đã ghi xong thì database sẽ có
     *  một hóa đơn què: có đầu, thiếu dòng hàng. Đó là dữ liệu rác.
     *
     *  TRANSACTION giải quyết việc đó theo nguyên tắc "được ăn cả,
     *  ngã về không":
     *
     *      conn.setAutoCommit(false);   // mở transaction
     *      ... các lệnh INSERT ...
     *      conn.commit();               // tất cả cùng có hiệu lực
     *      // hoặc
     *      conn.rollback();             // tất cả cùng bị hủy bỏ
     *
     *  Mặc định JDBC để autoCommit = true, nghĩa là mỗi câu lệnh tự
     *  commit ngay -- không gộp nhóm được. Phải tắt nó đi.
     *
     *  QUAN TRỌNG: cả nhóm phải dùng CHUNG MỘT Connection. Hai kết nối
     *  khác nhau là hai transaction khác nhau, rollback sẽ không ăn.
     */
    public void createInvoice(String invoiceId,
                              LocalDate date, LocalTime time,
                              String counterId, String cashierId, String customerId,
                              List<CartLine> lines,
                              List<PaymentEntry> payments) {

        String sqlInvoice =
                "INSERT INTO Invoice " +
                "(Invoice_ID, `Date`, `Time`, Status, Counter_ID, Cashier_ID, Customer_ID) " +
                "VALUES (?, ?, ?, 'PAID', ?, ?, ?)";

        String sqlLine =
                "INSERT INTO Invoice_Line " +
                "(Invoice_ID, Line_Number, Barcode, Quantity, Unit_Price, Discount) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        String sqlPayment =
                "INSERT INTO Payment (Payment_ID, Amount, Invoice_ID) VALUES (?, ?, ?)";

        long t0 = System.currentTimeMillis();
        Connection conn = null;

        try {
            conn = Db.getConnection();
            conn.setAutoCommit(false);          // ==== BẮT ĐẦU TRANSACTION ====

            // ---- 1. Ghi phần đầu hóa đơn ----------------------------
            try (PreparedStatement ps = conn.prepareStatement(sqlInvoice)) {
                ps.setString(1, invoiceId);
                ps.setDate(2, Date.valueOf(date));
                ps.setTime(3, Time.valueOf(time));
                ps.setString(4, counterId);
                ps.setString(5, cashierId);
                // Khách vãng lai -> ghi NULL. KHÔNG dùng setString(6, null)
                // vì với vài driver nó không rõ kiểu; setNull rõ ràng hơn.
                if (customerId == null) {
                    ps.setNull(6, java.sql.Types.VARCHAR);
                } else {
                    ps.setString(6, customerId);
                }
                ps.executeUpdate();             // executeUpdate cho INSERT/UPDATE/DELETE
                SqlLog.add(sqlInvoice, new Object[]{invoiceId, date, time,
                        counterId, cashierId, customerId}, 0, 1);
            }

            // ---- 2. Ghi các dòng hàng (dùng batch cho nhanh) --------
            try (PreparedStatement ps = conn.prepareStatement(sqlLine)) {
                int lineNo = 1;
                for (CartLine cl : lines) {
                    ps.setString(1, invoiceId);
                    ps.setInt(2, lineNo);                 // đánh số 1, 2, 3... trong hóa đơn này
                    ps.setString(3, cl.product.barcode);
                    ps.setBigDecimal(4, cl.quantity);
                    ps.setBigDecimal(5, cl.unitPrice);    // GIÁ LÚC BÁN, đã tra từ Price_History
                    ps.setBigDecimal(6, cl.discount);
                    ps.addBatch();                        // xếp hàng, chưa gửi
                    lineNo++;
                }
                ps.executeBatch();                        // gửi tất cả trong 1 lượt
                SqlLog.add(sqlLine + "   [batch x " + lines.size() + "]",
                           null, 0, lines.size());
            }

            // ---- 3. Ghi các lần thanh toán -------------------------
            int paySeq = nextPaymentSeq(conn);
            for (PaymentEntry pe : payments) {
                String payId = String.format("PAY-%04d", paySeq++);

                try (PreparedStatement ps = conn.prepareStatement(sqlPayment)) {
                    ps.setString(1, payId);
                    ps.setBigDecimal(2, pe.amount);
                    ps.setString(3, invoiceId);
                    ps.executeUpdate();
                }

                // Ghi tiếp vào đúng bảng con theo phương thức
                String childSql;
                if (PaymentEntry.CASH.equals(pe.method)) {
                    childSql = "INSERT INTO Cash_Payment (Payment_ID, Tendered_Amount) VALUES (?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(childSql)) {
                        ps.setString(1, payId);
                        // Tendered_Amount là DECIMAL -> dùng setBigDecimal cho đúng kiểu
                        ps.setBigDecimal(2, new java.math.BigDecimal(pe.detail1));
                        ps.executeUpdate();
                    }
                } else if (PaymentEntry.CARD.equals(pe.method)) {
                    childSql = "INSERT INTO Card_Payment (Payment_ID, Card_number, Auth_code) VALUES (?, ?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(childSql)) {
                        ps.setString(1, payId);
                        ps.setString(2, pe.detail1);
                        ps.setString(3, pe.detail2);
                        ps.executeUpdate();
                    }
                } else {
                    childSql = "INSERT INTO EWallet_Payment (Payment_ID, Wallet_provider, Transaction_Ref) VALUES (?, ?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(childSql)) {
                        ps.setString(1, payId);
                        ps.setString(2, pe.detail1);
                        ps.setString(3, pe.detail2);
                        ps.executeUpdate();
                    }
                }
                SqlLog.add(sqlPayment + "  +  " + childSql,
                           new Object[]{payId, pe.amount, invoiceId}, 0, 2);
            }

            conn.commit();                      // ==== CHỐT: mọi thứ có hiệu lực ====
            SqlLog.add("COMMIT;  -- luu thanh cong hoa don " + invoiceId,
                       null, System.currentTimeMillis() - t0, 0);

        } catch (SQLException e) {
            // Có bất cứ lỗi nào -> hủy sạch, database trở về như chưa có gì.
            if (conn != null) {
                try {
                    conn.rollback();
                    SqlLog.add("ROLLBACK;  -- da huy toan bo, khong ghi gi ca",
                               null, 0, 0);
                } catch (SQLException ignore) { }
            }
            throw new RuntimeException("Luu hoa don that bai: " + e.getMessage(), e);

        } finally {
            // Trả kết nối về trạng thái bình thường rồi mới đóng.
            if (conn != null) {
                try { conn.setAutoCommit(true); } catch (SQLException ignore) { }
                try { conn.close(); }            catch (SQLException ignore) { }
            }
        }
    }

    /** Sinh mã hóa đơn kế tiếp: INV-0001, INV-0002, ... */
    public String nextInvoiceId() {
        QueryResult qr = QueryResult.run(
                "SELECT COALESCE(MAX(CAST(SUBSTRING(Invoice_ID, 5) AS UNSIGNED)), 0) + 1 AS ke_tiep " +
                "FROM   Invoice " +
                "WHERE  Invoice_ID REGEXP '^INV-[0-9]+$'");
        long n = ((Number) qr.rows.get(0)[0]).longValue();
        return String.format("INV-%04d", n);
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
    //  PHẦN 2 -- ĐỌC DỮ LIỆU (SELECT)
    // =================================================================

    /** Danh sách hóa đơn gần đây kèm tổng tiền. */
    public QueryResult recentInvoices() {
        return QueryResult.run(
                "SELECT  i.Invoice_ID                                  AS Ma_HD, " +
                "        i.`Date`                                      AS Ngay, " +
                "        i.`Time`                                      AS Gio, " +
                "        s.Shop_ID                                     AS Shop, " +
                "        c.Name                                        AS Thu_ngan, " +
                "        COALESCE(cu.Name, '(vang lai)')               AS Khach, " +
                "        i.Status                                      AS Trang_thai, " +
                "        SUM(l.Quantity * l.Unit_Price - l.Discount)   AS Tong_tien " +
                "FROM    Invoice      i " +
                "JOIN    Counter      ct ON ct.Counter_ID  = i.Counter_ID " +
                "JOIN    Shop         s  ON s.Shop_ID      = ct.Shop_ID " +
                "JOIN    Cashier      c  ON c.Cashier_ID   = i.Cashier_ID " +
                "LEFT JOIN Customer   cu ON cu.Customer_ID = i.Customer_ID " +
                "JOIN    Invoice_Line l  ON l.Invoice_ID   = i.Invoice_ID " +
                "GROUP BY i.Invoice_ID, i.`Date`, i.`Time`, s.Shop_ID, " +
                "         c.Name, cu.Name, i.Status " +
                "ORDER BY i.`Date` DESC, i.`Time` DESC");
    }

    /**
     * ĐỀ BÀI CÂU 1 -- các dòng của một hóa đơn.
     * Dấu ? sẽ được thay bằng mã hóa đơn một cách an toàn.
     */
    public QueryResult invoiceLines(String invoiceId) {
        return QueryResult.run(
                "SELECT  l.Line_Number                            AS Dong, " +
                "        p.Name                                   AS San_pham, " +
                "        l.Quantity                               AS SL, " +
                "        p.Unit                                   AS DVT, " +
                "        l.Unit_Price                             AS Don_gia, " +
                "        l.Discount                               AS Giam_gia, " +
                "        (l.Quantity * l.Unit_Price - l.Discount) AS Thanh_tien, " +
                "        p.Tax_Rate                               AS VAT_pct " +
                "FROM    Invoice_Line l " +
                "JOIN    Product      p ON p.Barcode = l.Barcode " +
                "WHERE   l.Invoice_ID = ? " +
                "ORDER BY l.Line_Number",
                invoiceId);
    }

    /** Phần đầu + tổng tiền của một hóa đơn. */
    public QueryResult invoiceHeader(String invoiceId) {
        return QueryResult.run(
                "SELECT  i.Invoice_ID                                  AS Ma_HD, " +
                "        i.`Date`                                      AS Ngay, " +
                "        i.`Time`                                      AS Gio, " +
                "        i.Status                                      AS Trang_thai, " +
                "        i.Counter_ID                                  AS Quay, " +
                "        s.Shop_ID                                     AS Shop, " +
                "        s.Address                                     AS Dia_chi, " +
                "        c.Name                                        AS Thu_ngan, " +
                "        COALESCE(cu.Name, '(khach vang lai)')         AS Khach_hang, " +
                "        SUM(l.Quantity * l.Unit_Price)                AS Tong_truoc_giam, " +
                "        SUM(l.Discount)                               AS Tong_giam, " +
                "        SUM(l.Quantity * l.Unit_Price - l.Discount)   AS Tong_phai_tra " +
                "FROM    Invoice      i " +
                "JOIN    Counter      ct ON ct.Counter_ID  = i.Counter_ID " +
                "JOIN    Shop         s  ON s.Shop_ID      = ct.Shop_ID " +
                "JOIN    Cashier      c  ON c.Cashier_ID   = i.Cashier_ID " +
                "LEFT JOIN Customer   cu ON cu.Customer_ID = i.Customer_ID " +
                "JOIN    Invoice_Line l  ON l.Invoice_ID   = i.Invoice_ID " +
                "WHERE   i.Invoice_ID = ? " +
                "GROUP BY i.Invoice_ID, i.`Date`, i.`Time`, i.Status, i.Counter_ID, " +
                "         s.Shop_ID, s.Address, c.Name, cu.Name",
                invoiceId);
    }

    /**
     * Các lần thanh toán của hóa đơn.
     * Dùng 3 LEFT JOIN sang 3 bảng con rồi CASE để gộp lại thành một cột
     * "chi tiết" -- vì mỗi Payment chỉ có mặt ở đúng một bảng con.
     */
    public QueryResult invoicePayments(String invoiceId) {
        return QueryResult.run(
                "SELECT  p.Payment_ID            AS Ma_TT, " +
                "        p.Amount                AS So_tien, " +
                "        CASE " +
                "          WHEN ca.Payment_ID IS NOT NULL THEN 'Tien mat' " +
                "          WHEN cd.Payment_ID IS NOT NULL THEN 'The ngan hang' " +
                "          WHEN ew.Payment_ID IS NOT NULL THEN 'Vi dien tu' " +
                "          ELSE '(khong ro)' END AS Phuong_thuc, " +
                "        CASE " +
                "          WHEN ca.Payment_ID IS NOT NULL THEN CONCAT('Khach dua: ', ca.Tendered_Amount) " +
                "          WHEN cd.Payment_ID IS NOT NULL THEN CONCAT(cd.Card_number, ' / ', cd.Auth_code) " +
                "          WHEN ew.Payment_ID IS NOT NULL THEN CONCAT(ew.Wallet_provider, ' / ', ew.Transaction_Ref) " +
                "          ELSE '' END           AS Chi_tiet " +
                "FROM    Payment p " +
                "LEFT JOIN Cash_Payment    ca ON ca.Payment_ID = p.Payment_ID " +
                "LEFT JOIN Card_Payment    cd ON cd.Payment_ID = p.Payment_ID " +
                "LEFT JOIN EWallet_Payment ew ON ew.Payment_ID = p.Payment_ID " +
                "WHERE   p.Invoice_ID = ? " +
                "ORDER BY p.Payment_ID",
                invoiceId);
    }

    /** Các lần trả hàng của hóa đơn. */
    public QueryResult invoiceReturns(String invoiceId) {
        return QueryResult.run(
                "SELECT  r.Line_Number                       AS Dong, " +
                "        pr.Name                             AS San_pham, " +
                "        r.Return_Date                       AS Ngay_tra, " +
                "        r.Return_Quantity                   AS SL_tra, " +
                "        ROUND(r.Return_Quantity / l.Quantity " +
                "              * (l.Quantity * l.Unit_Price - l.Discount), 2) AS Tien_hoan, " +
                "        c.Name                              AS Nguoi_duyet " +
                "FROM    `Return` r " +
                "JOIN    Invoice_Line l ON l.Invoice_ID = r.Invoice_ID " +
                "                      AND l.Line_Number = r.Line_Number " +
                "JOIN    Product pr ON pr.Barcode   = l.Barcode " +
                "JOIN    Cashier c  ON c.Cashier_ID = r.Approved_By " +
                "WHERE   r.Invoice_ID = ? " +
                "ORDER BY r.Line_Number, r.Return_ID",
                invoiceId);
    }
}
