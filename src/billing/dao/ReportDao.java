package billing.dao;

import billing.db.QueryResult;

public class ReportDao {
    public static final String SQL_DAILY_TAKINGS =
            "-- [Goi Stored Procedure do Dev SQL thiet ke trong 04_routines.sql]\n" +
            "CALL sp_report_daily_takings();";

    public QueryResult dailyTakings() {
        return QueryResult.call("{call sp_report_daily_takings()}");
    }

    public static final String SQL_PRICE_RISE =
            "-- [Goi Stored Procedure do Dev SQL thiet ke trong 04_routines.sql]\n" +
            "CALL sp_report_price_rise();";

    public QueryResult priceRise() {
        return QueryResult.call("{call sp_report_price_rise()}");
    }

    public static final String SQL_BEST_CASHIER =
            "-- [Goi Stored Procedure do Dev SQL thiet ke trong 04_routines.sql]\n" +
            "CALL sp_report_best_cashier();";

    public QueryResult bestCashier() {
        return QueryResult.call("{call sp_report_best_cashier()}");
    }

    public static final String SQL_BIG_RETURNS =
            "-- [Goi Stored Procedure do Dev SQL thiet ke trong 04_routines.sql]\n" +
            "CALL sp_report_big_returns();";

    public QueryResult bigReturns() {
        return QueryResult.call("{call sp_report_big_returns()}");
    }

    public static final String SQL_OLD_PRICE_PROOF =
            "-- [Goi Stored Procedure do Dev SQL thiet ke trong 04_routines.sql]\n" +
            "CALL sp_report_old_price_proof();";

    public QueryResult oldPriceProof() {
        return QueryResult.call("{call sp_report_old_price_proof()}");
    }

    public static String sqlPrice30Days(String barcode) {
        return "-- [Goi Stored Procedure do Dev SQL thiet ke trong 04_routines.sql]\n"
             + "CALL sp_report_price_30_days('" + barcode + "');";
    }

    public QueryResult price30Days(String barcode) {
        return QueryResult.call("{call sp_report_price_30_days(?)}", barcode);
    }
}
