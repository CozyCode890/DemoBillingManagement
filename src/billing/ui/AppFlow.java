package billing.ui;

import billing.dao.AccountDao;
import billing.model.Account;
import billing.model.QuickAccount;
import billing.model.UserSession;
import billing.util.LoginPrefs;
import billing.util.QuickLogin;

import javax.swing.SwingUtilities;

/**
 * ====================================================================
 *  AppFlow -- Điều phối luồng vào app: đăng nhập -> cửa sổ chính
 * ====================================================================
 *
 *  <p>Đoạn "mở LoginDialog, nếu thành công thì mở MainWindow" trước đây bị copy
 *  ở hai chỗ ({@code Main.main} và {@code MainWindow.doLogout}), và khối 2 thêm
 *  chỗ thứ ba (auto-login thất bại thì rơi về màn hình đăng nhập). Ba bản copy
 *  của cùng 5 dòng là chỗ để chúng lệch nhau, nên gom về đây.
 *
 *  <p>Lớp này cũng giữ phần thử auto-login ({@link #tryAutoLogin(String[])}) --
 *  auto-login là một trong ba đường vào app, để cùng chỗ thì còn đọc được thứ
 *  tự ưu tiên; và để {@code Main.main()} không phình lên, vì đó là file đầu tiên
 *  người học đọc.
 *
 *  <p>Đặc tả: {@code plan/11-KE-HOACH-KHOI-2-DANG-NHAP-NHANH.md} mục 3.2 và 5.5.
 */
public final class AppFlow {

    private static final String ARG_PREFIX = "--login=";

    /** Ba kết cục có thể của một lần thử auto-login. */
    public enum Kind {
        /** Đã xác thực thành công, {@code UserSession} sẵn sàng cho MainWindow. */
        SUCCESS,
        /** Có cấu hình auto-login nhưng không vào được -- phải hiện màn hình đăng nhập kèm lý do. */
        FAILED,
        /** Không cấu hình gì (hoặc tính năng đang tắt) -- đường mặc định. */
        NONE
    }

    /**
     * Kết quả một lần thử auto-login.
     *
     * <p>({@code record} là cách viết ngắn của Java 16+ cho một lớp chỉ chứa dữ
     * liệu: trình biên dịch tự sinh hàm dựng, các hàm đọc {@code kind()},
     * {@code account()}... và {@code equals}/{@code hashCode}/{@code toString}.)
     *
     * @param kind     kết cục
     * @param account  tài khoản đã xác thực, chỉ khác null khi {@code SUCCESS}
     * @param username tên đăng nhập đã thử
     * @param source   nguồn cấu hình, để in ra console cho người dùng biết vì sao bị bỏ qua đăng nhập
     * @param message  lý do thất bại, hiện ở dòng trạng thái của màn hình đăng nhập
     */
    public record AutoLoginResult(Kind kind, Account account, String username,
                                  String source, String message) {

        static AutoLoginResult none() {
            return new AutoLoginResult(Kind.NONE, null, "", "", null);
        }

        static AutoLoginResult failed(String username, String message) {
            return new AutoLoginResult(Kind.FAILED, null, username, "", message);
        }

        static AutoLoginResult success(Account account, String source) {
            return new AutoLoginResult(Kind.SUCCESS, account, account.getUsername(), source, null);
        }
    }

    // ------------------------------------------------------------------
    //  Auto-login
    // ------------------------------------------------------------------

    /**
     * Thử đăng nhập sẵn trước khi mở màn hình đăng nhập.
     *
     * <p>Thứ tự ưu tiên (mục 3.2 của kế hoạch):
     * <pre>
     *   1. Tham số dòng lệnh   --login=&lt;username&gt;  hoặc  --login=&lt;username&gt;:&lt;password&gt;
     *   2. Khoá cấu hình       quick.login.auto=&lt;username&gt;
     *   3. Không có gì         -> NONE, hiện màn hình đăng nhập như bình thường
     * </pre>
     *
     * <p>Mọi mức trên đều bị vô hiệu nếu {@code quick.login.enabled=false}.
     *
     * <p><b>Luôn xác thực thật</b> qua {@code AccountDao.authenticate()} ->
     * {@code sp_authenticate_account}: đăng nhập nhanh là điền nhanh, không
     * phải bỏ qua xác thực (mục 2.2). Nếu một bạn trong nhóm đổi mật khẩu trong
     * bảng {@code Account} thì đường này <b>phải</b> thất bại đúng như gõ tay.
     *
     * <p>Thất bại không bao giờ làm app thoát: luôn trả về {@code FAILED} kèm lý
     * do để hiện ở dòng trạng thái của màn hình đăng nhập.
     *
     * <p>Chạy trên luồng {@code main}, trước khi có EDT nào -- giống
     * {@code Db.testConnection()} đang làm lúc khởi động, nên không thêm kiểu
     * rủi ro mới.
     */
    public static AutoLoginResult tryAutoLogin(String[] args) {
        String fromArgs = parseLoginArg(args);

        if (!QuickLogin.isEnabled()) {
            if (fromArgs != null) {
                System.out.println("[AUTO-LOGIN] Bo qua tham so --login=" + fromArgs
                    + " vi quick.login.enabled=false trong config.properties.");
            }
            return AutoLoginResult.none();
        }

        String username;
        String password;
        String source;

        if (fromArgs != null) {
            int colon = fromArgs.indexOf(':');
            if (colon >= 0) {
                // --login=user:pass -- dùng thẳng cặp này, không cần có trong
                // quick.login.accounts. Đường dành cho Run configuration của IDE
                // khi nhóm đã tự thêm tài khoản vào DB.
                username = fromArgs.substring(0, colon).trim();
                password = fromArgs.substring(colon + 1);   // KHÔNG trim mật khẩu
                source = "tham so --login=<user>:<pass>";
                if (username.isEmpty() || password.isEmpty()) {
                    return AutoLoginResult.failed(username,
                        "Tham so --login=" + fromArgs + " thieu ten dang nhap hoac mat khau"
                      + " -- hay dang nhap bang tay.");
                }
            } else {
                QuickAccount q = QuickLogin.find(fromArgs);
                if (q == null) {
                    return AutoLoginResult.failed(fromArgs,
                        "Khong tim thay tai khoan '" + fromArgs + "' trong quick.login.accounts"
                      + " -- hay dang nhap bang tay.");
                }
                username = q.getUsername();
                password = q.getPassword();
                source = "tham so --login";
            }
        } else {
            String auto = QuickLogin.autoUsername();
            if (auto.isEmpty()) {
                return AutoLoginResult.none();
            }
            QuickAccount q = QuickLogin.find(auto);
            if (q == null) {
                return AutoLoginResult.failed(auto,
                    "Khong tim thay tai khoan '" + auto + "' trong quick.login.accounts"
                  + " (khoa quick.login.auto) -- hay dang nhap bang tay.");
            }
            username = q.getUsername();
            password = q.getPassword();
            source = "config quick.login.auto";
        }

        try {
            Account acc = new AccountDao().authenticate(username, password);
            if (acc == null) {
                return AutoLoginResult.failed(username,
                    "Tai khoan '" + username + "' sai mat khau -- hay dang nhap bang tay.");
            }
            LoginPrefs.saveLastUsername(acc.getUsername());
            return AutoLoginResult.success(acc, source);
        } catch (Exception ex) {
            return AutoLoginResult.failed(username,
                "Khong xac thuc duoc '" + username + "': " + ex.getMessage()
              + " -- hay dang nhap bang tay.");
        }
    }

    /**
     * Lấy giá trị của tham số {@code --login=...}.
     *
     * <p>Tham số lạ chỉ in một dòng cảnh báo rồi <b>vẫn chạy tiếp</b> như không
     * có tham số: thoát app vì một tham số gõ sai là phản ứng quá đáng với một
     * app demo.
     *
     * @return giá trị sau dấu {@code =}, hoặc null nếu không có / để trống
     */
    private static String parseLoginArg(String[] args) {
        if (args == null) {
            return null;
        }
        String value = null;
        for (String a : args) {
            if (a == null || a.isBlank()) {
                continue;
            }
            if (a.startsWith(ARG_PREFIX)) {
                value = a.substring(ARG_PREFIX.length()).trim();
            } else {
                System.out.println("[LOI THAM SO] Khong hieu tham so '" + a
                    + "'. Chi ho tro --login=<username>[:<password>].");
            }
        }
        return (value == null || value.isEmpty()) ? null : value;
    }

    /**
     * In dấu hiệu auto-login ra console.
     *
     * <p>Bỏ qua màn hình đăng nhập mà không có dấu hiệu gì là cách nhanh nhất để
     * một bạn trong nhóm kết luận "app này không có đăng nhập". Dấu hiệu thứ hai
     * là chữ {@code (DANG NHAP TU DONG)} màu cam trên thanh trạng thái của
     * {@code MainWindow} (mục 3.3).
     */
    public static void announceAutoLogin(AutoLoginResult r) {
        System.out.println("[AUTO-LOGIN] Da dang nhap san bang '" + r.username()
            + "' (nguon: " + r.source() + ")."
            + " Dat quick.login.auto= trong config.properties de tat.");
    }

    // ------------------------------------------------------------------
    //  Mở cửa sổ
    // ------------------------------------------------------------------

    /**
     * Mở màn hình đăng nhập (trên EDT); đăng nhập thành công thì mở MainWindow.
     *
     * @param notice thông báo hiện ở dòng trạng thái của dialog, null nếu không có
     */
    public static void showLogin(String notice) {
        SwingUtilities.invokeLater(() -> {
            LoginDialog login = new LoginDialog(null, notice);
            login.setVisible(true);
            if (login.isSucceeded()) {
                openMainWindow(false);
            }
        });
    }

    /**
     * Mở cửa sổ chính với phiên đã có trong {@link UserSession}.
     *
     * @param autoLogin true nếu phiên này do auto-login tạo ra -- thanh trạng
     *                  thái sẽ hiện chữ {@code (DANG NHAP TU DONG)}
     */
    public static void openMainWindow(boolean autoLogin) {
        SwingUtilities.invokeLater(() -> new MainWindow(autoLogin).setVisible(true));
    }

    private AppFlow() { }
}
