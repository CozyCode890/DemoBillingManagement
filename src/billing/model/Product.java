package billing.model;

import java.math.BigDecimal;

/**
 * Một dòng của bảng Product.
 *
 * Lớp kiểu này gọi là POJO / Entity: mỗi thuộc tính ứng với một CỘT.
 * Ở đây tôi để field public cho ngắn gọn, dễ đọc. Dự án thật sẽ dùng
 * private + getter/setter.
 *
 * Chú ý: KHÔNG có field price. Giá không thuộc về sản phẩm, giá thuộc
 * về cặp (sản phẩm, ngày) -- nên nó nằm ở bảng Price_History.
 */
public class Product {

    public String     barcode;
    public String     name;
    public String     unit;      // PIECE / KG / LITER / PACK / BOX
    public BigDecimal taxRate;

    public Product(String barcode, String name, String unit, BigDecimal taxRate) {
        this.barcode = barcode;
        this.name    = name;
        this.unit    = unit;
        this.taxRate = taxRate;
    }

    /** Text hiển thị trong ô chọn sản phẩm trên giao diện. */
    @Override
    public String toString() {
        return barcode + " - " + name + " (" + unit + ")";
    }
}
