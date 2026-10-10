package billing.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Một dòng hàng trong "giỏ" đang được thu ngân bấm, TRƯỚC khi lưu xuống
 * database. Khi bấm nút Lưu, mỗi CartLine sẽ thành một dòng Invoice_Line.
 *
 * VÌ SAO DÙNG BigDecimal MÀ KHÔNG DÙNG double?
 *   double là số thực nhị phân -> 0.1 + 0.2 ra 0.30000000000000004.
 *   Với tiền bạc thì sai một đồng cũng là sai. BigDecimal tính chính xác
 *   theo hệ thập phân, khớp với kiểu DECIMAL(12,2) bên MySQL.
 *   QUY TẮC: cột DECIMAL trong SQL  <->  BigDecimal trong Java.
 */
public class CartLine {

    public Product    product;
    public BigDecimal quantity;
    public BigDecimal unitPrice;   // lấy từ Price_History theo NGÀY bán
    public BigDecimal discount;    // số tiền giảm của cả dòng
    public String     promoNote;   // ghi chú khuyến mãi, chỉ để hiển thị

    public CartLine(Product product, BigDecimal quantity,
                    BigDecimal unitPrice, BigDecimal discount, String promoNote) {
        this.product   = product;
        this.quantity  = quantity;
        this.unitPrice = unitPrice;
        this.discount  = discount;
        this.promoNote = promoNote;
    }

    /** Thành tiền = số lượng * đơn giá - giảm giá (trước thuế) */
    public BigDecimal lineTotal() {
        // Tiền trước thuế: (số lượng * đơn giá) - giảm giá
        return quantity.multiply(unitPrice)
                       .subtract(discount)
                       .setScale(2, RoundingMode.HALF_UP);
    }
}