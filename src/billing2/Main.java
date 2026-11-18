package billing2;

import billing2.db.Db;
import billing2.db.SqlLog;
import billing2.model.UserSession;
import billing2.ui.AppFlow;

import javax.swing.*;

/**
 * Điểm khởi động của app gọn (billing2):
 *   1. Thử kết nối MySQL -- hỏng thì báo rõ ngay, không mở app.
 *   2. Ghi dòng mốc vào logs/sql.log (tab "SQL Log" của Setup App).
 *   3. Tự đăng nhập nếu cấu hình, không thì mở màn hình đăng nhập (AppFlow).
 */
public class Main {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());   // giao diện kiểu Windows
        } catch (Exception ignore) { }

        System.out.println("=== Retail Invoicing (billing2) ===");
        String status = Db.testConnection();
        System.out.println(status);
        if (status.startsWith("ERROR")) {
            JOptionPane.showMessageDialog(null, "Cannot connect to MySQL.\n\n" + status + "\n\n"
                    + "Open Setup.exe / Setup.app and press \"CHẠY ỨNG DỤNG\": it starts MySQL, loads the database\n"
                    + "and downloads the JDBC driver into lib/.", "Connection Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        SqlLog.appStart(Db.getUrl());

        AppFlow.AutoLoginResult auto = AppFlow.tryAutoLogin(args);
        if (auto.account() != null) {
            UserSession.setCurrentUser(auto.account());
            AppFlow.openMainWindow(true);
        } else {
            AppFlow.showLogin(auto.notice());
        }
    }
}
