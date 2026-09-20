package billing.db;

import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * ====================================================================
 *  Db -- CỬA NGÕ DUY NHẤT để Java nói chuyện với MySQL.
 * ====================================================================
 *
 *  Java KHÔNG tự biết MySQL. Nó chỉ biết một bộ interface chuẩn tên là
 *  JDBC (java.sql.*). Muốn chạy được thì cần thêm một file .jar gọi là
 *  "driver" -- ở đây là mysql-connector-j.jar -- đóng vai trò phiên dịch:
 *
 *      Code Java  ->  JDBC (interface)  ->  Driver MySQL  ->  MySQL Server
 *
 *  Đó là lý do khi chạy app bạn phải có tham số -cp (classpath) trỏ tới
 *  file .jar đó. Thiếu nó sẽ báo "No suitable driver found".
 */
public class Db {

    private static String url;
    private static String user;
    private static String password;

    /** Đọc file config.properties một lần duy nhất khi lớp này được nạp. */
    static {
        Properties p = new Properties();
        try (InputStream in = new FileInputStream("config.properties")) {
            p.load(in);
        } catch (Exception e) {
            System.out.println("[Db] Khong doc duoc config.properties, dung gia tri mac dinh.");
        }
        url      = p.getProperty("db.url",
                     "jdbc:mysql://localhost:3306/retail_billing"
                   + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh");
        user     = p.getProperty("db.user", "root");
        password = p.getProperty("db.password", "");
    }

    /**
     * Mở MỘT kết nối mới tới MySQL.
     *
     * Ai gọi hàm này thì người đó có trách nhiệm ĐÓNG kết nối lại.
     * Cách an toàn nhất là dùng try-with-resources:
     *
     *      try (Connection c = Db.getConnection()) {  ...  }
     *
     * Java sẽ tự gọi c.close() kể cả khi có lỗi xảy ra giữa chừng.
     *
     * (Trong dự án thật người ta dùng "connection pool" như HikariCP để
     *  tái sử dụng kết nối cho nhanh. Ở đây mở mới mỗi lần cho dễ hiểu.)
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /** Thử kết nối -- dùng lúc khởi động app để báo lỗi sớm và rõ ràng. */
    public static String testConnection() {
        try (Connection c = Db.getConnection()) {
            return "OK - da ket noi toi " + c.getMetaData().getURL();
        } catch (SQLException e) {
            return "LOI - " + e.getMessage();
        }
    }

    public static String getUrl()  { return url; }
    public static String getUser() { return user; }
}
