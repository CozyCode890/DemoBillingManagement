package billing2.dao;

import billing2.db.Db;
import billing2.db.SqlLog;
import billing2.model.Product;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO = Data Access Object. Dev SQL viết hàm / thủ tục trong sql/04_routines.sql,
 * Dev Java chỉ GỌI chúng qua CallableStatement rồi đổi kết quả thành object Java:
 *
 *     Swing (ui)  ->  DAO (dao)  ->  CallableStatement  ->  MySQL routines
 */
public class ProductDao {

    /** Toàn bộ sản phẩm cho ô chọn: sp_get_all_products(). */
    public List<Product> findAll() {
        String sql = "{call sp_get_all_products()}";
        long t0 = System.currentTimeMillis();
        List<Product> list = new ArrayList<>();

        try (Connection c = Db.getConnection();
             CallableStatement cs = c.prepareCall(sql);
             ResultSet rs = cs.executeQuery()) {
            while (rs.next()) {
                list.add(new Product(rs.getString("Barcode"), rs.getString("Name"),
                                     rs.getString("Unit"), rs.getBigDecimal("Tax_Rate")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Cannot read product list: " + e.getMessage(), e);
        }

        SqlLog.add(sql, null, System.currentTimeMillis() - t0, list.size());
        return list;
    }

    /**
     * TRÁI TIM CỦA ĐỀ BÀI: giá HỢP LỆ của sản phẩm TẠI MỘT NGÀY -- hóa đơn cũ giữ giá cũ.
     * Gọi Stored FUNCTION (trả về một giá trị):  "{? = call fn_get_product_price(?, ?)}"
     *   1. registerOutParameter(1, ...) -- dấu ? đầu tiên là giá trị trả về
     *   2. tham số đầu vào bắt đầu từ vị trí 2
     *   3. execute() rồi đọc kết quả ở vị trí 1
     *
     * @return null = sản phẩm chưa có giá nào trước ngày đó
     */
    public BigDecimal priceOn(String barcode, LocalDate date) {
        String sql = "{? = call fn_get_product_price(?, ?)}";
        long t0 = System.currentTimeMillis();
        BigDecimal price;

        try (Connection c = Db.getConnection();
             CallableStatement cs = c.prepareCall(sql)) {
            cs.registerOutParameter(1, Types.DECIMAL);
            cs.setString(2, barcode);
            cs.setDate(3, Date.valueOf(date));
            cs.execute();
            price = cs.getBigDecimal(1);
        } catch (SQLException e) {
            throw new RuntimeException("Cannot find the price: " + e.getMessage(), e);
        }

        SqlLog.add(sql, new Object[]{barcode, date}, System.currentTimeMillis() - t0, price == null ? 0 : 1);
        return price;
    }

    /**
     * Khuyến mãi đang chạy cho sản phẩm vào một ngày: sp_get_active_promo(p_barcode, p_date).
     * @return {Promo_ID, Discount_Percent, Rule_Description} hoặc null nếu không có
     */
    public Object[] activePromoOn(String barcode, LocalDate date) {
        String sql = "{call sp_get_active_promo(?, ?)}";
        long t0 = System.currentTimeMillis();
        Object[] result = null;

        try (Connection c = Db.getConnection();
             CallableStatement cs = c.prepareCall(sql)) {
            cs.setString(1, barcode);
            cs.setDate(2, Date.valueOf(date));
            try (ResultSet rs = cs.executeQuery()) {
                if (rs.next()) {
                    result = new Object[]{rs.getString("Promo_ID"), rs.getBigDecimal("Discount_Percent"),
                                          rs.getString("Rule_Description")};
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Cannot read promotion: " + e.getMessage(), e);
        }

        SqlLog.add(sql, new Object[]{barcode, date}, System.currentTimeMillis() - t0, result == null ? 0 : 1);
        return result;
    }
}
