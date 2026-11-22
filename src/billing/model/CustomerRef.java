package billing.model;

public class CustomerRef {
    public final String id;
    public final String name;
    public final String phone;

    public CustomerRef(String id, String name, String phone) {
        this.id    = id;
        this.name  = name;
        this.phone = phone;
    }

    public String display() {
        return name + " - " + phone + " (" + id + ")";
    }

    public String shortLabel() {
        return name + " (" + id + ")";
    }

    @Override
    public String toString() { return display(); }
}
