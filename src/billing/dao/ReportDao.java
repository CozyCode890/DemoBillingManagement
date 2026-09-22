package billing.dao;

import billing.db.QueryResult;

/**
 * ====================================================================
 *  BÁO CÁO DO DEV SQL THIẾT KẾ VÀ TỐI ƯU
 * ====================================================================
 *  Trước đây: Tầng Java phải viết các câu lệnh SELECT rất dài, nhiều tầng
 *  JOIN và GROUP BY phức tạp.
 *
 *  Bây giờ (chuẩn hóa phân vai):
 *  - Dev SQL: Đóng gói toàn bộ câu truy vấn vào các Stored Procedure
 *    trong file sql/04_routines.sql, kiểm soát hiệu năng và index.
 *  - Dev Java: Chỉ cần gọi đúng tên Stored Procedure qua QueryResult.call()
 *    và nhận kết quả về đổ lên JTable.
 */
public class ReportDao {

    // =================================================================
    //  BÁO CÁO 1: Doanh thu theo ngày của từng cửa hàng, 7 ngày gần nhất
    // =================================================================
    //  Logic truy vấn phức tạp (JOIN 4 bảng, GROUP BY 2 cột) được Dev SQL
    //  đóng gói trong Stored Procedure: sp_report_daily_takings()
    // -----------------------------------------------------------------
    public static final String SQL_DAILY_TAKINGS =
            "-- [Goi Stored Procedure do Dev SQL thiet ke trong 04_routines.sql]\n" +
            "CALL sp_report_daily_takings();";

    public QueryResult dailyTakings() {
        return QueryResult.call("{call sp_report_daily_takings()}");
    }


    // =================================================================
    //  BÁO CÁO 2: Sản phẩm tăng giá hơn 10% trong năm nay
    // =================================================================
    //  Kỹ thuật SELF JOIN được đóng gói trong: sp_report_price_rise()
    // -----------------------------------------------------------------
    public static final String SQL_PRICE_RISE =
            "-- [Goi Stored Procedure do Dev SQL thiet ke trong 04_routines.sql]\n" +
            "CALL sp_report_price_rise();";

    public QueryResult priceRise() {
        return QueryResult.call("{call sp_report_price_rise()}");
    }


    // =================================================================
    //  BÁO CÁO 3: Thu ngân có giỏ hàng trung bình cao nhất
    // =================================================================
    //  Kỹ thuật 2 tầng AVG(SUM) gom theo thu ngân: sp_report_best_cashier()
    // -----------------------------------------------------------------
    public static final String SQL_BEST_CASHIER =
            "-- [Goi Stored Procedure do Dev SQL thiet ke trong 04_routines.sql]\n" +
            "CALL sp_report_best_cashier();";

    public QueryResult bestCashier() {
        return QueryResult.call("{call sp_report_best_cashier()}");
    }


    // =================================================================
    //  BÁO CÁO 4: Hóa đơn bị trả hàng quá nửa giá trị
    // =================================================================
    //  Tính tỷ lệ hoàn tiền theo sản phẩm trả: sp_report_big_returns()
    // -----------------------------------------------------------------
    public static final String SQL_BIG_RETURNS =
            "-- [Goi Stored Procedure do Dev SQL thiet ke trong 04_routines.sql]\n" +
            "CALL sp_report_big_returns();";

    public QueryResult bigReturns() {
        return QueryResult.call("{call sp_report_big_returns()}");
    }


    // =================================================================
    //  BÁO CÁO 5 (bonus): chứng minh hóa đơn cũ giữ giá cũ
    // =================================================================
    //  So sánh giá đóng băng trên hóa đơn và giá hôm nay: sp_report_old_price_proof()
    // -----------------------------------------------------------------
    public static final String SQL_OLD_PRICE_PROOF =
            "-- [Goi Stored Procedure do Dev SQL thiet ke trong 04_routines.sql]\n" +
            "CALL sp_report_old_price_proof();";

    public QueryResult oldPriceProof() {
        return QueryResult.call("{call sp_report_old_price_proof()}");
    }
}
