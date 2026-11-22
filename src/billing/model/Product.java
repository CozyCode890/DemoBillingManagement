package billing.model;

import java.math.BigDecimal;

public class Product {
    public String     barcode;
    public String     name;
    public String     unit;
    public BigDecimal taxRate;

    public Product(String barcode, String name, String unit, BigDecimal taxRate) {
        this.barcode = barcode;
        this.name    = name;
        this.unit    = unit;
        this.taxRate = taxRate;
    }

    @Override
    public String toString() {
        return barcode + " - " + name + " (" + unit + ")";
    }
}
