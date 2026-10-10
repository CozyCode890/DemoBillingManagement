package billing.util;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * ====================================================================
 *  AppConfig -- Đọc config.properties một lần rồi cho tra cứu theo khoá
 * ====================================================================
 *
 * <p>Dùng cho các cấu hình <b>không</b> thuộc kết nối database.
 * {@code billing.db.Db} tự đọc phần {@code db.*} của chính nó và cố tình
 * không giữ lại đối tượng {@code Properties}: {@code Db} là "cửa ngõ duy
 * nhất tới MySQL" và cũng là file được dùng làm tài liệu dạy JDBC
 * ({@code docs/02-JDBC.md}), nên không nhồi thêm cấu hình không liên quan
 * vào đó.
 *
 * <p>Đường dẫn {@code config.properties} là <b>tương đối</b> -- giữ đúng
 * giả định đã có của {@code Db}: thư mục làm việc là gốc dự án
 * ({@code run.ps1} / {@code run.sh} đều {@code Set-Location} / {@code cd}
 * về đó trước khi gọi java).
 *
 * <p>Đọc cấu hình thất bại không bao giờ được làm app chết: in một dòng rồi
 * mọi lời gọi {@code get} trả về giá trị mặc định của chỗ gọi.
 */
public final class AppConfig {

    private static final Properties P = new Properties();

    static {
        try (InputStream in = new FileInputStream("config.properties")) {
            P.load(in);
        } catch (Exception e) {
            System.out.println("[AppConfig] Khong doc duoc config.properties, dung gia tri mac dinh.");
        }
    }

    /**
     * Giá trị của khoá, đã {@code trim()}.
     *
     * @param fallback dùng khi khoá không có hoặc giá trị chỉ gồm khoảng trắng
     *                 -- khoá để trống trong file (ví dụ {@code quick.login.auto=})
     *                 phải tương đương với không khai báo khoá đó
     */
    public static String get(String key, String fallback) {
        String v = P.getProperty(key);
        return (v == null || v.isBlank()) ? fallback : v.trim();
    }

    /**
     * {@code true} chỉ khi giá trị là {@code true} / {@code 1} / {@code yes}
     * (không phân biệt hoa thường). Mọi giá trị khác -- kể cả gõ sai như
     * {@code ture} -- cho ra {@code false}, tức là hành vi an toàn hơn.
     */
    public static boolean getBoolean(String key, boolean fallback) {
        String v = get(key, null);
        if (v == null) {
            return fallback;
        }
        return v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes");
    }

    private AppConfig() { }
}
