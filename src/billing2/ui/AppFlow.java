package billing2.ui;

import billing2.dao.AccountDao;
import billing2.model.Account;
import billing2.model.QuickAccount;
import billing2.util.QuickLogin;

import javax.swing.*;

/**
 * Luồng mở app: tự đăng nhập (nếu cấu hình) -> hoặc màn hình đăng nhập -> cửa sổ chính.
 * Tự đăng nhập VẪN xác thực thật; thất bại thì rơi về màn hình đăng nhập kèm lý do.
 */
public final class AppFlow {

    /** account != null: vào thẳng cửa sổ chính; notice != null: lý do tự đăng nhập thất bại. */
    public record AutoLoginResult(Account account, String notice) { }

    /** Thứ tự ưu tiên: tham số --login=user[:pass]  >  khóa quick.login.auto  >  không tự đăng nhập. */
    public static AutoLoginResult tryAutoLogin(String[] args) {
        String arg = null;
        for (String a : args) {
            if (a == null || a.isBlank()) continue;
            if (a.startsWith("--login=")) arg = a.substring("--login=".length()).trim();
            else System.out.println("[ARGUMENT ERROR] Unknown argument '" + a
                    + "'. Only --login=<username>[:<password>] is supported.");
        }
        if (arg != null && arg.isEmpty()) arg = null;
        if (!QuickLogin.isEnabled()) {
            if (arg != null) System.out.println("[AUTO-LOGIN] Ignored --login=" + arg
                    + " because quick.login.enabled=false in config.properties.");
            return new AutoLoginResult(null, null);
        }

        String username, password;
        if (arg != null && arg.contains(":")) {                         // --login=user:pass
            username = arg.substring(0, arg.indexOf(':')).trim();
            password = arg.substring(arg.indexOf(':') + 1);              // KHÔNG trim mật khẩu
            if (username.isEmpty() || password.isEmpty()) {
                return failed(username, "Argument --login=" + arg + " is missing the username or password");
            }
        } else {                                                        // --login=user hoặc quick.login.auto
            String who = arg != null ? arg : QuickLogin.autoUsername();
            if (who.isEmpty()) return new AutoLoginResult(null, null);
            QuickAccount q = QuickLogin.find(who);
            if (q == null) return failed(who, "Cannot find account '" + who + "' in quick.login.accounts");
            username = q.username();
            password = q.password();
        }

        try {
            Account acc = new AccountDao().authenticate(username, password);
            if (acc == null) return failed(username, "Incorrect password for account '" + username + "'");
            QuickLogin.saveLastUsername(acc.getUsername());
            System.out.println("[AUTO-LOGIN] Logged in automatically as '" + username
                    + "'. Set quick.login.auto= in config.properties to turn it off.");
            return new AutoLoginResult(acc, null);
        } catch (Exception ex) {
            return failed(username, "Cannot authenticate '" + username + "': " + ex.getMessage());
        }
    }

    private static AutoLoginResult failed(String username, String reason) {
        System.out.println("[AUTO-LOGIN] " + reason + " -- please login manually.");
        return new AutoLoginResult(null, "Cannot auto login with account '" + username + "'. Please login manually!");
    }

    /** Màn hình đăng nhập (modal); đăng nhập xong thì mở cửa sổ chính. */
    public static void showLogin(String notice) {
        SwingUtilities.invokeLater(() -> {
            LoginDialog login = new LoginDialog(null, notice);
            login.setVisible(true);
            if (login.isSucceeded()) openMainWindow(false);
        });
    }

    public static void openMainWindow(boolean autoLogin) {
        SwingUtilities.invokeLater(() -> new MainWindow(autoLogin).setVisible(true));
    }

    private AppFlow() { }
}
