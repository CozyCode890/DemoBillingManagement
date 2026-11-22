package billing2.dao;

import billing2.db.QueryResult;

/**
 * Báo cáo: toàn bộ JOIN / GROUP BY nằm trong thủ tục của Dev SQL (sql/04_routines.sql),
 * Java chỉ gọi đúng tên thủ tục rồi đổ kết quả lên JTable.
 */
public class ReportDao {

    /** Doanh thu từng ngày của từng cửa hàng, 7 ngày gần nhất (JOIN 4 bảng, GROUP BY 2 cột). */
    public QueryResult dailyTakings()  { return QueryResult.call("{call sp_report_daily_takings()}"); }

    /** Sản phẩm tăng giá hơn 10% trong năm nay (SELF JOIN Price_History). */
    public QueryResult priceRise()     { return QueryResult.call("{call sp_report_price_rise()}"); }

    /** Thu ngân có giá trị giỏ hàng trung bình cao nhất (AVG của SUM, hai tầng). */
    public QueryResult bestCashier()   { return QueryResult.call("{call sp_report_best_cashier()}"); }

    /** Hóa đơn bị trả hàng quá nửa giá trị. */
    public QueryResult bigReturns()    { return QueryResult.call("{call sp_report_big_returns()}"); }

    /** Bonus: hóa đơn cũ giữ giá cũ -- so giá trên hóa đơn với giá hôm nay. */
    public QueryResult oldPriceProof() { return QueryResult.call("{call sp_report_old_price_proof()}"); }

    /** Giá của MỘT sản phẩm (số lượng 1) trong 30 ngày -- CTE đệ quy + LAG. */
    public QueryResult price30Days(String barcode) {
        return QueryResult.call("{call sp_report_price_30_days(?)}", barcode);
    }
}
