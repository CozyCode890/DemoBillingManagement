package billing2.util;

import billing2.model.QuickAccount;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

/**
 * Đăng nhập nhanh -- chỉ cho bản demo. Các khóa trong config.properties:
 *   quick.login.enabled   bật/tắt cả tính năng (tắt thì --login và auto-login cũng vô hiệu)
 *   quick.login.accounts  danh sách nút: user:pass[:nhãn],...
 *   quick.login.auto      tự đăng nhập bằng username này, bỏ qua màn hình đăng nhập
 *   quick.login.remember  nhớ tên đăng nhập lần trước (KHÔNG BAO GIỜ nhớ mật khẩu)
 */
public final class QuickLogin {

    private static final Path LAST_LOGIN = Paths.get("logs", "last-login.properties");

    public static boolean isEnabled() {
        return AppConfig.getBoolean("quick.login.enabled", false);
    }

    public static List<QuickAccount> accounts() {
        return isEnabled() ? QuickAccount.parse(AppConfig.get("quick.login.accounts", "")) : List.of();
    }

    /** Username của khóa quick.login.auto, hoặc "" nếu không cấu hình. */
    public static String autoUsername() {
        return isEnabled() ? AppConfig.get("quick.login.auto", "") : "";
    }

    /** Tài khoản nhanh theo username (không phân biệt hoa thường), hoặc null. */
    public static QuickAccount find(String username) {
        for (QuickAccount a : accounts()) {
            if (a.username().equalsIgnoreCase(username.trim())) return a;
        }
        return null;
    }

    /** Tên đăng nhập lần trước để điền sẵn vào ô username, hoặc "". */
    public static String lastUsername() {
        if (!AppConfig.getBoolean("quick.login.remember", false)) return "";
        try (InputStream in = Files.newInputStream(LAST_LOGIN)) {
            Properties p = new Properties();
            p.load(in);
            return p.getProperty("last.username", "").trim();
        } catch (Exception e) {
            return "";                          // chưa có file: lần đầu chạy
        }
    }

    public static void saveLastUsername(String username) {
        if (!AppConfig.getBoolean("quick.login.remember", false) || username == null || username.isBlank()) return;
        try {
            Files.createDirectories(LAST_LOGIN.getParent());
            Properties p = new Properties();
            p.setProperty("last.username", username.trim());
            try (OutputStream out = Files.newOutputStream(LAST_LOGIN)) {
                p.store(out, "Last username. Does NOT contain the password.");
            }
        } catch (Exception e) {
            System.err.println("[QuickLogin] Cannot write " + LAST_LOGIN.toAbsolutePath() + ": " + e.getMessage());
        }
    }

    private QuickLogin() { }
}
