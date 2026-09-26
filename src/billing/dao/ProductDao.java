package billing.dao;

import billing.db.Db;
import billing.db.SqlLog;
import billing.model.Product;

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
 * ====================================================================
 *  DAO = Data Access Object (Gọi Stored Functions & Procedures)
 * ====================================================================
 *  Quy ước phân chia vai trò:
 *  - Dev SQL: Viết logic truy vấn, JOIN, tối ưu index trong 04_routines.sql.
 *  - Dev Java: KHÔNG viết câu lệnh SELECT phức tạp ở đây. Tầng DAO chỉ dùng
 *    CallableStatement để gọi các hàm/thủ tục mà Dev SQL đã tạo, rồi ánh xạ
 *    kết quả trả về thành các đối tượng Model của Java.
 *
 *      Swing (ui)  ->  DAO (dao)  ->  CallableStatement  ->  MySQL Routines
 */
public class ProductDao {

    /**
     * Lấy toàn bộ sản phẩm để đổ vào ô chọn trên giao diện.
     * Gọi Stored Procedure: sp_get_all_products()
     */
    public List<Product> findAll() {
        String sql = "{call sp_get_all_products()}";

        long t0 = System.currentTimeMillis();
        List<Product> list = new ArrayList<>();

        try (Connection c = Db.getConnection();
             CallableStatement cs = c.prepareCall(sql);
             ResultSet rs = cs.executeQuery()) {

            while (rs.next()) {
                // Ánh xạ các cột trả về từ Procedure vào Product model
                list.add(new Product(
                        rs.getString("Barcode"),
                        rs.getString("Name"),
                        rs.getString("Unit"),
                        rs.getBigDecimal("Tax_Rate")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Khong doc duoc danh sach san pham: " + e.getMessage(), e);
        }

        SqlLog.add(sql, null, System.currentTimeMillis() - t0, list.size());
        return list;
    }

    /**
     * ================================================================
     *  ỨNG DỤNG HÀM SQL (STORED FUNCTION) TRONG JDBC:
     * ================================================================
     *  Tìm giá HỢP LỆ của một sản phẩm TẠI MỘT NGÀY cụ thể.
     *  Gọi Stored Function: fn_get_product_price(p_barcode, p_date)
     *
     *  Cú pháp chuẩn của JDBC khi gọi Hàm trả về giá trị đơn (Scalar Function):
     *      "{? = call fn_get_product_price(?, ?)}"
     *
     *  Các bước:
     *   1. conn.prepareCall("{? = call fn_...(?)}")
     *   2. Đăng ký kiểu trả về của hàm ở tham số 1: cs.registerOutParameter(1, Types.DECIMAL)
     *   3. Truyền các tham số đầu vào bắt đầu từ tham số 2: cs.setString(2, ...), cs.setDate(3, ...)
     *   4. cs.execute()
     *   5. Lấy kết quả trả về từ tham số 1: cs.getBigDecimal(1)
     */
    public BigDecimal priceOn(String barcode, LocalDate date) {
        String sql = "{? = call fn_get_product_price(?, ?)}";

        long t0 = System.currentTimeMillis();
        BigDecimal price = null;

        try (Connection c = Db.getConnection();
             CallableStatement cs = c.prepareCall(sql)) {

            // Tham số 1: giá trị trả về của Function (OUT parameter)
            cs.registerOutParameter(1, Types.DECIMAL);

            // Tham số 2 & 3: các đối số truyền vào Function (IN parameters)
            cs.setString(2, barcode);
            cs.setDate(3, Date.valueOf(date));

            cs.execute();

            // Đọc giá trị trả về từ tham số 1
            price = cs.getBigDecimal(1);

        } catch (SQLException e) {
            throw new RuntimeException("Khong tim duoc gia: " + e.getMessage(), e);
        }

        SqlLog.add(sql, new Object[]{barcode, date}, System.currentTimeMillis() - t0,
                   price == null ? 0 : 1);
        return price;   // null = sản phẩm chưa từng có giá trước ngày đó
    }

    /**
     * Tìm phần trăm giảm giá đang áp dụng cho sản phẩm vào một ngày.
     * Gọi Stored Procedure: sp_get_active_promo(p_barcode, p_date)
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
                    result = new Object[]{
                            rs.getString("Promo_ID"),
                            rs.getBigDecimal("Discount_Percent"),
                            rs.getString("Rule_Description")};
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Khong doc duoc khuyen mai: " + e.getMessage(), e);
        }

        SqlLog.add(sql, new Object[]{barcode, date},
                   System.currentTimeMillis() - t0, result == null ? 0 : 1);
        return result;
    }
}
