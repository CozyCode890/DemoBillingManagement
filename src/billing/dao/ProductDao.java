package billing.dao;

import billing.db.Db;
import billing.db.SqlLog;
import billing.model.Product;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * ====================================================================
 *  DAO = Data Access Object
 * ====================================================================
 *  Quy ước đặt tên rất phổ biến: mọi câu SQL liên quan tới một bảng thì
 *  gom vào một lớp *Dao. Giao diện (Swing) KHÔNG được viết SQL trực tiếp,
 *  nó chỉ gọi DAO. Nhờ vậy sau này đổi database hay đổi câu lệnh thì chỉ
 *  sửa một chỗ.
 *
 *      Swing (ui)  ->  DAO (dao)  ->  JDBC (db)  ->  MySQL
 */
public class ProductDao {

    /** Lấy toàn bộ sản phẩm để đổ vào ô chọn. */
    public List<Product> findAll() {
        String sql = "SELECT Barcode, Name, Unit, Tax_Rate "
                   + "FROM Product ORDER BY Name";

        long t0 = System.currentTimeMillis();
        List<Product> list = new ArrayList<>();

        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                // Đọc theo TÊN CỘT dễ đọc hơn đọc theo số thứ tự.
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
     * *** ĐÂY LÀ TRÁI TIM CỦA ĐỀ BÀI ***
     *
     * Tìm giá HỢP LỆ của một sản phẩm TẠI MỘT NGÀY cụ thể.
     *
     * Bảng Price_History lưu các mốc "từ ngày này giá là X". Muốn biết
     * giá ngày 15/03 thì ta lấy mốc gần nhất mà không vượt quá 15/03:
     *
     *      WHERE Valid_From_Date <= ngày_bán
     *      ORDER BY Valid_From_Date DESC
     *      LIMIT 1
     *
     * Nhờ truyền NGÀY CỦA HÓA ĐƠN vào đây (chứ không phải ngày hôm nay),
     * lập lại một hóa đơn cũ vẫn ra đúng giá cũ.
     */
    public BigDecimal priceOn(String barcode, LocalDate date) {
        String sql = "SELECT Price "
                   + "FROM   Price_History "
                   + "WHERE  Barcode = ? "
                   + "  AND  Valid_From_Date <= ? "
                   + "ORDER BY Valid_From_Date DESC "
                   + "LIMIT 1";

        long t0 = System.currentTimeMillis();
        BigDecimal price = null;

        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            // setString / setDate là cách AN TOÀN để đưa giá trị vào câu lệnh.
            // Driver sẽ tự bọc và escape -> không thể bị SQL Injection.
            // TUYỆT ĐỐI KHÔNG nối chuỗi kiểu: "... WHERE Barcode = '" + barcode + "'"
            ps.setString(1, barcode);
            ps.setDate(2, Date.valueOf(date));   // LocalDate -> java.sql.Date

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    price = rs.getBigDecimal("Price");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Khong tim duoc gia: " + e.getMessage(), e);
        }

        SqlLog.add(sql, new Object[]{barcode, date}, System.currentTimeMillis() - t0,
                   price == null ? 0 : 1);
        return price;   // null = sản phẩm chưa từng có giá trước ngày đó
    }

    /**
     * Tìm phần trăm giảm giá đang áp dụng cho sản phẩm vào một ngày.
     * Nếu sản phẩm dính nhiều khuyến mãi cùng lúc, lấy mức cao nhất.
     * Trả về null nếu không có khuyến mãi nào.
     */
    public Object[] activePromoOn(String barcode, LocalDate date) {
        String sql = "SELECT a.Promo_ID, a.Discount_Percent, p.Rule_Description "
                   + "FROM   Applies_To a "
                   + "JOIN   Promotion  p ON p.Promo_ID = a.Promo_ID "
                   + "WHERE  a.Barcode = ? "
                   + "  AND  ? BETWEEN a.Promo_Start_date AND a.Promo_End_date "
                   + "  AND  p.Status = 'ACTIVE' "
                   + "  AND  p.Expiry_Date >= ? "
                   + "ORDER BY a.Discount_Percent DESC "
                   + "LIMIT 1";

        long t0 = System.currentTimeMillis();
        Object[] result = null;

        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, barcode);
            ps.setDate(2, Date.valueOf(date));
            ps.setDate(3, Date.valueOf(date));

            try (ResultSet rs = ps.executeQuery()) {
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

        SqlLog.add(sql, new Object[]{barcode, date, date},
                   System.currentTimeMillis() - t0, result == null ? 0 : 1);
        return result;
    }
}
