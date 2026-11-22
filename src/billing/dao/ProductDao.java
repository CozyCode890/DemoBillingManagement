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

public class ProductDao {
    public List<Product> findAll() {
        String sql = "{call sp_get_all_products()}";

        long t0 = System.currentTimeMillis();
        List<Product> list = new ArrayList<>();

        try (Connection c = Db.getConnection();
             CallableStatement cs = c.prepareCall(sql);
             ResultSet rs = cs.executeQuery()) {
            while (rs.next()) {
                list.add(new Product(
                        rs.getString("Barcode"),
                        rs.getString("Name"),
                        rs.getString("Unit"),
                        rs.getBigDecimal("Tax_Rate")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Cannot read product list: " + e.getMessage(), e);
        }

        SqlLog.add(sql, null, System.currentTimeMillis() - t0, list.size());
        return list;
    }

    public BigDecimal priceOn(String barcode, LocalDate date) {
        String sql = "{? = call fn_get_product_price(?, ?)}";

        long t0 = System.currentTimeMillis();
        BigDecimal price = null;

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

        SqlLog.add(sql, new Object[]{barcode, date}, System.currentTimeMillis() - t0,
                   price == null ? 0 : 1);
        return price;
    }

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
            throw new RuntimeException("Cannot read promotion: " + e.getMessage(), e);
        }

        SqlLog.add(sql, new Object[]{barcode, date},
                   System.currentTimeMillis() - t0, result == null ? 0 : 1);
        return result;
    }
}
