package billing;

import billing.db.Db;
import billing.db.SqlLog;
import billing.model.UserSession;
import billing.ui.AppFlow;
import billing.ui.I18n;

import javax.swing.*;

/**
 * ====================================================================
 *  ĐIỂM KHỞI ĐỘNG CỦA APP
 * ====================================================================
 *  Việc đầu tiên: thử kết nối MySQL. Nếu hỏng thì báo lỗi rõ ràng ngay
 *  thay vì để app mở ra rồi mỗi nút bấm lại văng một exception khó hiểu.
 *  Sau khi kết nối thành công: Hiển thị màn hình Đăng nhập (LoginDialog).
 */
public class Main {

    public static void main(String[] args) {

        // Giao diện theo phong cách Windows thay vì giao diện Java mặc định
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignore) { }

        System.out.println("=== Demo Retail Invoicing ===");
        System.out.println("Đang thử kết nối: " + Db.getUrl());

        String status = Db.testConnection();
        System.out.println(status);

        if (status.startsWith("LOI")) {
            String help =
                "Không kết nối được MySQL.\n\n"
              + status + "\n\n"
              + "Kiểm tra lần lượt:\n"
              + "  1. MySQL 8 đã chạy chưa?  (Services -> MySQL80 -> Running)\n"
              + "  2. Đã tạo database chưa?  Chạy file sql/01_schema.sql và sql/02_seed.sql\n"
              + "  3. Mật khẩu trong file config.properties đã đúng chưa?\n"
              + "  4. Nếu lỗi 'No suitable driver': thiếu file mysql-connector-j.jar\n"
              + "     trong thư mục lib/. Chạy setup.ps1 để tải về.\n";
            I18n.showMessageDialog(null, help, "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Đánh dấu mốc mở đầu phiên trong logs/sql.log -- file đó chứa nhật ký
        // của nhiều ngày, không có dòng mốc thì không biết câu nào thuộc phiên nào.
        // Xem tab "5. SQL Log" của Setup App.
        SqlLog.appStart(Db.getUrl());

        // --- Đăng nhập nhanh: thử auto-login trước khi mở màn hình đăng nhập --
        // Thứ tự ưu tiên: --login=... > quick.login.auto > hiện màn hình đăng nhập.
        // Auto-login VẪN xác thực thật qua sp_authenticate_account, và thất bại
        // thì rơi về màn hình đăng nhập kèm lý do -- không bao giờ làm app thoát.
        // Xem plan/11-KE-HOACH-KHOI-2-DANG-NHAP-NHANH.md muc 3.2.
        AppFlow.AutoLoginResult auto = AppFlow.tryAutoLogin(args);
        switch (auto.kind()) {
            case SUCCESS -> {
                AppFlow.announceAutoLogin(auto);
                UserSession.setCurrentUser(auto.account());
                AppFlow.openMainWindow(true);
            }
            case FAILED  -> AppFlow.showLogin(auto.message());   // sai mật khẩu / không tìm thấy
            case NONE    -> AppFlow.showLogin(null);             // đường mặc định
        }
    }
}

