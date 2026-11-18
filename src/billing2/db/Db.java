package billing2.db;

import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Db -- cửa ngõ DUY NHẤT để Java nói chuyện với MySQL.
 *
 *     Code Java  ->  JDBC (java.sql.*)  ->  Driver (lib/mysql-connector-j.jar)  ->  MySQL
 *
 * Thiếu file driver trong lib/ thì báo "No suitable driver found".
 */
public class Db {

    private static String url;
    private static String user;
    private static String password;

    /** Đọc config.properties một lần khi lớp được nạp (thư mục làm việc = gốc dự án). */
    static {
        Properties p = new Properties();
        try (InputStream in = new FileInputStream("config.properties")) {
            p.load(in);
        } catch (Exception e) {
            System.out.println("[Db] Cannot read config.properties, using default values.");
        }
        url      = p.getProperty("db.url", "jdbc:mysql://localhost:3306/retail_billing"
                 + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh");
        user     = p.getProperty("db.user", "root");
        password = p.getProperty("db.password", "");
    }

    /**
     * Mở MỘT kết nối mới. Ai mở thì người đó đóng -- dùng try-with-resources:
     *     try (Connection c = Db.getConnection()) { ... }
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /** Thử kết nối lúc khởi động để báo lỗi sớm và rõ ràng. */
    public static String testConnection() {
        try (Connection c = getConnection()) {
            return "OK - connected to " + c.getMetaData().getURL();
        } catch (SQLException e) {
            return "ERROR - " + e.getMessage();
        }
    }

    public static String getUrl()  { return url; }
    public static String getUser() { return user; }
}
