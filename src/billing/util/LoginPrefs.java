package billing.util;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

/**
 * ====================================================================
 *  LoginPrefs -- Nhớ tên đăng nhập gần nhất
 * ====================================================================
 *
 *  <p><b>KHÔNG BAO GIỜ ghi mật khẩu.</b> Ghi mật khẩu ra một file trên đĩa là
 *  đúng thứ không nên dạy cho người mới, và hàng nút "Đăng nhập nhanh" đã giải
 *  quyết 100% nhu cầu đó cho các tài khoản mẫu.
 *  Xem plan/11-KE-HOACH-KHOI-2-DANG-NHAP-NHANH.md mục 2.3.
 *
 *  <p>File nằm trong {@code logs/} để dùng lại một thư mục đã bị
 *  {@code .gitignore} chặn, không phát minh chỗ lưu trạng thái cục bộ thứ hai
 *  cho một app demo. Tab 5 của Setup App tail {@code logs/sql.log} theo đường
 *  dẫn cố định (không quét thư mục) nên thêm file này vào đó không ảnh hưởng gì.
 *
 *  <p>Áp dụng đúng nguyên tắc an toàn của {@code SqlLog}: mọi thao tác file bọc
 *  trong {@code try/catch (Exception)}; lỗi đầu tiên in <b>một</b> dòng ra
 *  {@code System.err} rồi tắt hẳn việc đọc/ghi cho phần còn lại của phiên.
 *  Việc nhớ một cái tên không bao giờ được làm app đổ.
 */
public final class LoginPrefs {

    /** Đường dẫn tương đối: app billing luôn chạy với thư mục làm việc là gốc dự án. */
    private static final Path FILE = Paths.get("logs", "last-login.properties");

    private static final String KEY = "last.username";

    /** Bật lên khi đọc/ghi file thất bại: từ đó không thử nữa, cũng không in nữa. */
    private static boolean disabled = false;

    /** Tính năng này do {@code quick.login.remember} trong config.properties bật/tắt. */
    private static boolean enabled() {
        return AppConfig.getBoolean("quick.login.remember", false);
    }

    /**
     * Tên đăng nhập của lần trước, hoặc chuỗi rỗng nếu chưa có / không bật /
     * đọc lỗi. Không bao giờ trả về null.
     */
    public static synchronized String loadLastUsername() {
        if (disabled || !enabled()) {
            return "";
        }
        try {
            if (!Files.isRegularFile(FILE)) {
                return "";
            }
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(FILE)) {
                p.load(in);
            }
            String v = p.getProperty(KEY, "");
            return v.trim();
        } catch (Exception e) {
            fail("doc", e);
            return "";
        }
    }

    /**
     * Ghi lại tên đăng nhập. Lỗi ghi file chỉ in cảnh báo, không bao giờ ném ra.
     *
     * @param username tên đăng nhập vừa dùng; null hoặc rỗng thì không ghi gì
     */
    public static synchronized void saveLastUsername(String username) {
        if (disabled || !enabled() || username == null || username.isBlank()) {
            return;
        }
        try {
            Path parent = FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Properties p = new Properties();
            p.setProperty(KEY, username.trim());
            try (OutputStream out = Files.newOutputStream(FILE,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                p.store(out, "Ten dang nhap gan nhat. KHONG chua mat khau -- xem billing.util.LoginPrefs.");
            }
        } catch (Exception e) {
            fail("ghi", e);
        }
    }

    private static void fail(String what, Exception e) {
        disabled = true;
        System.err.println("[LoginPrefs] Khong " + what + " duoc " + FILE.toAbsolutePath()
            + " -- tat viec nho ten dang nhap cho phien nay. Ly do: " + e.getMessage());
    }

    private LoginPrefs() { }
}
