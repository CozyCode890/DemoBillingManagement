package billing.dao;

import billing.db.QueryResult;

/**
 * Bốn câu báo cáo mà đề bài yêu cầu.
 *
 * Tôi để hẳn chuỗi SQL ra thành hằng số public để màn hình Báo cáo có
 * thể HIỆN CÂU SQL NGAY BÊN CẠNH KẾT QUẢ. Bạn bấm nút -> thấy luôn câu
 * lệnh đã chạy và bảng kết quả của nó.
 */
public class ReportDao {

    // =================================================================
    //  BÁO CÁO 1: Doanh thu theo ngày của từng cửa hàng, 7 ngày gần nhất
    // =================================================================
    //  Hóa đơn KHÔNG trỏ thẳng tới cửa hàng. Phải đi vòng:
    //      Invoice -> Counter -> Shop
    //  Đó là cái giá của việc chuẩn hóa: dữ liệu không lặp lại, nhưng
    //  muốn ghép lại thì phải JOIN.
    // -----------------------------------------------------------------
    public static final String SQL_DAILY_TAKINGS =
            "SELECT  s.Shop_ID                                     AS Cua_hang,\n" +
            "        s.Address                                     AS Dia_chi,\n" +
            "        i.`Date`                                      AS Ngay,\n" +
            "        COUNT(DISTINCT i.Invoice_ID)                  AS So_hoa_don,\n" +
            "        SUM(l.Quantity * l.Unit_Price - l.Discount)   AS Doanh_thu\n" +
            "FROM    Invoice      i\n" +
            "JOIN    Counter      ct ON ct.Counter_ID = i.Counter_ID\n" +
            "JOIN    Shop         s  ON s.Shop_ID     = ct.Shop_ID\n" +
            "JOIN    Invoice_Line l  ON l.Invoice_ID  = i.Invoice_ID\n" +
            "WHERE   i.Status = 'PAID'\n" +
            "  AND   i.`Date` >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)\n" +
            "GROUP BY s.Shop_ID, s.Address, i.`Date`\n" +
            "ORDER BY i.`Date` DESC, s.Shop_ID";

    public QueryResult dailyTakings() {
        return QueryResult.run(SQL_DAILY_TAKINGS);
    }


    // =================================================================
    //  BÁO CÁO 2: Sản phẩm tăng giá hơn 10% trong năm nay
    // =================================================================
    //  Kỹ thuật SELF JOIN: nối Price_History với chính nó để đặt
    //  "giá mới" cạnh "giá ngay trước đó".
    //
    //      gia_moi : một mốc giá trong năm nay
    //      gia_cu  : mốc giá liền trước của CÙNG sản phẩm
    //
    //  Subquery MAX(...) chính là chỗ tìm "mốc liền trước".
    // -----------------------------------------------------------------
    public static final String SQL_PRICE_RISE =
            "SELECT  p.Barcode                                                      AS Ma_vach,\n" +
            "        p.Name                                                         AS San_pham,\n" +
            "        gia_cu.Price                                                   AS Gia_cu,\n" +
            "        gia_moi.Price                                                  AS Gia_moi,\n" +
            "        gia_moi.Valid_From_Date                                        AS Ap_dung_tu,\n" +
            "        ROUND((gia_moi.Price - gia_cu.Price) / gia_cu.Price * 100, 2)  AS Tang_phan_tram\n" +
            "FROM       Price_History gia_moi\n" +
            "JOIN       Price_History gia_cu\n" +
            "        ON gia_cu.Barcode = gia_moi.Barcode\n" +
            "       AND gia_cu.Valid_From_Date = (\n" +
            "               SELECT MAX(h.Valid_From_Date)\n" +
            "               FROM   Price_History h\n" +
            "               WHERE  h.Barcode = gia_moi.Barcode\n" +
            "                 AND  h.Valid_From_Date < gia_moi.Valid_From_Date)\n" +
            "JOIN       Product p ON p.Barcode = gia_moi.Barcode\n" +
            "WHERE   YEAR(gia_moi.Valid_From_Date) = YEAR(CURDATE())\n" +
            "  AND   gia_moi.Price > gia_cu.Price * 1.10\n" +
            "ORDER BY Tang_phan_tram DESC";

    public QueryResult priceRise() {
        return QueryResult.run(SQL_PRICE_RISE);
    }


    // =================================================================
    //  BÁO CÁO 3: Thu ngân có giỏ hàng trung bình cao nhất
    // =================================================================
    //  Bẫy hay gặp: viết AVG(Quantity * Unit_Price) trên Invoice_Line
    //  -> ra trung bình MỖI DÒNG, không phải mỗi HÓA ĐƠN. Sai.
    //
    //  Cách đúng gồm 2 tầng:
    //      tầng trong : tính tổng tiền của TỪNG hóa đơn
    //      tầng ngoài : AVG các tổng đó, gom theo thu ngân
    // -----------------------------------------------------------------
    public static final String SQL_BEST_CASHIER =
            "SELECT  c.Cashier_ID           AS Ma_TN,\n" +
            "        c.Name                 AS Ten_thu_ngan,\n" +
            "        COUNT(*)               AS So_hoa_don,\n" +
            "        ROUND(SUM(t.Tong), 2)  AS Tong_doanh_thu,\n" +
            "        ROUND(AVG(t.Tong), 2)  AS Gio_hang_TB\n" +
            "FROM    Cashier c\n" +
            "JOIN    Invoice i ON i.Cashier_ID = c.Cashier_ID AND i.Status = 'PAID'\n" +
            "JOIN   (SELECT Invoice_ID,\n" +
            "               SUM(Quantity * Unit_Price - Discount) AS Tong\n" +
            "        FROM   Invoice_Line\n" +
            "        GROUP BY Invoice_ID) t  ON t.Invoice_ID = i.Invoice_ID\n" +
            "GROUP BY c.Cashier_ID, c.Name\n" +
            "ORDER BY Gio_hang_TB DESC";

    public QueryResult bestCashier() {
        return QueryResult.run(SQL_BEST_CASHIER);
    }


    // =================================================================
    //  BÁO CÁO 4: Hóa đơn bị trả hàng quá nửa giá trị
    // =================================================================
    //  Tiền trả lại của một dòng tính theo TỶ LỆ số lượng:
    //      (SL trả / SL mua) * thành tiền của dòng
    //  Làm vậy thì phần giảm giá cũng được hoàn lại đúng tỷ lệ.
    // -----------------------------------------------------------------
    public static final String SQL_BIG_RETURNS =
            "SELECT  i.Invoice_ID                                 AS Ma_HD,\n" +
            "        i.`Date`                                     AS Ngay,\n" +
            "        tong.Tong_HD                                 AS Tong_hoa_don,\n" +
            "        ROUND(tra.Tien_tra, 2)                       AS Tien_tra_lai,\n" +
            "        ROUND(tra.Tien_tra / tong.Tong_HD * 100, 2)  AS Phan_tram_tra\n" +
            "FROM    Invoice i\n" +
            "JOIN   (SELECT Invoice_ID,\n" +
            "               SUM(Quantity * Unit_Price - Discount) AS Tong_HD\n" +
            "        FROM   Invoice_Line\n" +
            "        GROUP BY Invoice_ID) tong  ON tong.Invoice_ID = i.Invoice_ID\n" +
            "JOIN   (SELECT r.Invoice_ID,\n" +
            "               SUM( r.Return_Quantity / l.Quantity\n" +
            "                    * (l.Quantity * l.Unit_Price - l.Discount) ) AS Tien_tra\n" +
            "        FROM   `Return` r\n" +
            "        JOIN   Invoice_Line l ON l.Invoice_ID  = r.Invoice_ID\n" +
            "                             AND l.Line_Number = r.Line_Number\n" +
            "        GROUP BY r.Invoice_ID) tra ON tra.Invoice_ID = i.Invoice_ID\n" +
            "WHERE   tra.Tien_tra > tong.Tong_HD / 2\n" +
            "ORDER BY Phan_tram_tra DESC";

    public QueryResult bigReturns() {
        return QueryResult.run(SQL_BIG_RETURNS);
    }


    // =================================================================
    //  BÁO CÁO 5 (bonus): chứng minh hóa đơn cũ giữ giá cũ
    // =================================================================
    public static final String SQL_OLD_PRICE_PROOF =
            "SELECT  l.Invoice_ID     AS Ma_HD,\n" +
            "        i.`Date`         AS Ngay_ban,\n" +
            "        p.Name           AS San_pham,\n" +
            "        l.Unit_Price     AS Gia_ghi_tren_HD,\n" +
            "        (SELECT h.Price\n" +
            "         FROM   Price_History h\n" +
            "         WHERE  h.Barcode = l.Barcode\n" +
            "           AND  h.Valid_From_Date <= CURDATE()\n" +
            "         ORDER BY h.Valid_From_Date DESC\n" +
            "         LIMIT 1)        AS Gia_hom_nay\n" +
            "FROM    Invoice_Line l\n" +
            "JOIN    Invoice i ON i.Invoice_ID = l.Invoice_ID\n" +
            "JOIN    Product p ON p.Barcode    = l.Barcode\n" +
            "ORDER BY i.`Date`, l.Invoice_ID, l.Line_Number";

    public QueryResult oldPriceProof() {
        return QueryResult.run(SQL_OLD_PRICE_PROOF);
    }
}
