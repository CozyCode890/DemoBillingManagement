package billing.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class CartLine {
    public Product    product;
    public BigDecimal quantity;
    public BigDecimal unitPrice;
    public BigDecimal discount;
    public String     promoNote;

    public CartLine(Product product, BigDecimal quantity,
                    BigDecimal unitPrice, BigDecimal discount, String promoNote) {
        this.product   = product;
        this.quantity  = quantity;
        this.unitPrice = unitPrice;
        this.discount  = discount;
        this.promoNote = promoNote;
    }

    public BigDecimal lineTotal() {
        return quantity.multiply(unitPrice)
                       .subtract(discount)
                       .setScale(2, RoundingMode.HALF_UP);
    }
}
