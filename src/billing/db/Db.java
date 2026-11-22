package billing.db;

import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class Db {
    private static String url;
    private static String user;
    private static String password;

    static {
        Properties p = new Properties();
        try (InputStream in = new FileInputStream("config.properties")) {
            p.load(in);
        } catch (Exception e) {
            System.out.println("[Db] Cannot read config.properties, using default values.");
        }
        url      = p.getProperty("db.url",
                     "jdbc:mysql://localhost:3306/retail_billing"
                   + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh");
        user     = p.getProperty("db.user", "root");
        password = p.getProperty("db.password", "");
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public static String testConnection() {
        try (Connection c = Db.getConnection()) {
            return "OK - connected to " + c.getMetaData().getURL();
        } catch (SQLException e) {
            return "LOI - " + e.getMessage();
        }
    }

    public static String getUrl()  { return url; }
    public static String getUser() { return user; }
}
