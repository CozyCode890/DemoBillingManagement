package billing2.model;

/**
 * Cặp (mã, tên) cho các ô chọn quầy / thu ngân.
 * JComboBox hiện toString(), nhưng lấy ra vẫn là cả object -> có `id` để đưa vào SQL.
 */
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
