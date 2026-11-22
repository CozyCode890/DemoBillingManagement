package billing.model;

public enum InvoiceStatus {
    PAID  ("Da thanh toan"),
    VOIDED("Da huy");

    private final String label;

    InvoiceStatus(String label) { this.label = label; }

    public String code()  { return name(); }

    public String label() { return label; }

    public String display() { return label + " (" + name() + ")"; }

    public static InvoiceStatus of(String raw) {
        if (raw == null) return null;
        for (InvoiceStatus s : values()) {
            if (s.name().equalsIgnoreCase(raw.trim())) return s;
        }
        return null;
    }

    @Override
    public String toString() { return display(); }
}
