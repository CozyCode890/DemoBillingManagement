package billing.model;

public enum PayMethod {
    CASH   ("Tien mat",      true),
    CARD   ("The ngan hang", false),
    EWALLET("Vi dien tu",    false);

    private final String  label;
    private final boolean hasChange;

    PayMethod(String label, boolean hasChange) {
        this.label     = label;
        this.hasChange = hasChange;
    }

    public String code() { return name(); }

    public String label() { return label; }

    public String displayLabel() {
        switch (this) {
            case CASH: return billing.util.Lang.pick("Tiền mặt", "Cash");
            case CARD: return billing.util.Lang.pick("Thẻ ngân hàng", "Bank card");
            default:   return billing.util.Lang.pick("Ví điện tử", "E-wallet");
        }
    }

    public boolean hasChange() { return hasChange; }

    public String amountFieldLabel() {
        return hasChange ? billing.util.Lang.pick("Khách đưa", "Tendered")
                         : billing.util.Lang.pick("Số tiền", "Amount");
    }

    public static PayMethod of(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        for (PayMethod m : values()) {
            if (m.name().equalsIgnoreCase(s) || m.label.equalsIgnoreCase(s)) return m;
        }
        return null;
    }

    @Override
    public String toString() { return label; }
}
