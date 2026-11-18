package billing2.util;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * Đọc config.properties một lần rồi tra theo khóa (các khóa KHÔNG thuộc kết nối DB --
 * phần db.* do billing2.db.Db tự đọc). Đọc lỗi không làm app chết: dùng giá trị mặc định.
 */
public final class AppConfig {

    private static final Properties P = new Properties();

    static {
        try (InputStream in = new FileInputStream("config.properties")) {
            P.load(in);
        } catch (Exception e) {
            System.out.println("[AppConfig] Cannot read config.properties, using default values.");
        }
    }

    /** Giá trị đã trim; khóa để trống (vd quick.login.auto=) coi như không khai báo. */
    public static String get(String key, String fallback) {
        String v = P.getProperty(key);
        return (v == null || v.isBlank()) ? fallback : v.trim();
    }

    /** Chỉ true / 1 / yes là bật; gõ sai (vd "ture") thì coi như tắt -- an toàn hơn. */
    public static boolean getBoolean(String key, boolean fallback) {
        String v = get(key, null);
        if (v == null) return fallback;
        return v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes");
    }

    private AppConfig() { }
}
