package billing2.model;

import java.math.BigDecimal;

/**
 * Một dòng của bảng Product.
 * KHÔNG có field price: giá thuộc về cặp (sản phẩm, ngày) nên nằm ở bảng Price_History.
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

    @Override
    public String toString() { return barcode + " - " + name + " (" + unit + ")"; }
}
