package billing.model;

public class IdName {
    public final String id;
    public final String name;

    public IdName(String id, String name) {
        this.id   = id;
        this.name = name;
    }

    @Override
    public String toString() {
        return (name == null || name.isEmpty()) ? id : id + " - " + name;
    }
}
