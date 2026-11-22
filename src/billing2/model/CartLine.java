package billing2.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Một dòng trong "giỏ" của tab Lập hóa đơn, TRƯỚC khi lưu.
 * Bấm lưu -> mỗi CartLine thành một dòng Invoice_Line.
 *
 * Tiền luôn là BigDecimal, không bao giờ double: 0.1 + 0.2 của double ra
 * 0.30000000000000004. Quy tắc: cột DECIMAL trong SQL <-> BigDecimal trong Java.
 */
public class CartLine {

    public Product    product;
    public BigDecimal quantity;
    public BigDecimal unitPrice;   // giá trong Price_History tại NGÀY BÁN
    public BigDecimal discount;    // số tiền giảm của cả dòng
    public String     promoNote;   // chỉ để hiển thị, vd "PR01 (-10%)"

    public CartLine(Product product, BigDecimal quantity,
                    BigDecimal unitPrice, BigDecimal discount, String promoNote) {
        this.product   = product;
        this.quantity  = quantity;
        this.unitPrice = unitPrice;
        this.discount  = discount;
        this.promoNote = promoNote;
    }

    /** Thành tiền = số lượng x đơn giá - giảm giá. */
    public BigDecimal lineTotal() {
        return quantity.multiply(unitPrice).subtract(discount).setScale(2, RoundingMode.HALF_UP);
    }
}
