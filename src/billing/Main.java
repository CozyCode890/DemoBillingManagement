package billing;

import billing.db.Db;
import billing.ui.MainWindow;

import javax.swing.*;

/**
 * ====================================================================
 *  ĐIỂM KHỞI ĐỘNG CỦA APP
 * ====================================================================
 *  Việc đầu tiên: thử kết nối MySQL. Nếu hỏng thì báo lỗi rõ ràng ngay
 *  thay vì để app mở ra rồi mỗi nút bấm lại văng một exception khó hiểu.
 */
public class Main {

    public static void main(String[] args) {

        // Giao diện theo phong cách Windows thay vì giao diện Java mặc định
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignore) { }

        System.out.println("=== Demo Retail Invoicing ===");
        System.out.println("Dang thu ket noi: " + Db.getUrl());

        String status = Db.testConnection();
        System.out.println(status);

        if (status.startsWith("LOI")) {
            String help =
                "Khong ket noi duoc MySQL.\n\n"
              + status + "\n\n"
              + "Kiem tra lan luot:\n"
              + "  1. MySQL 8 da chay chua?  (Services -> MySQL80 -> Running)\n"
              + "  2. Da tao database chua?  Chay file sql/01_schema.sql va sql/02_seed.sql\n"
              + "  3. Mat khau trong file config.properties da dung chua?\n"
              + "  4. Neu loi 'No suitable driver': thieu file mysql-connector-j.jar\n"
              + "     trong thu muc lib/. Chay setup.ps1 de tai ve.\n";
            JOptionPane.showMessageDialog(null, help, "Loi ket noi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Swing yêu cầu mọi thao tác giao diện chạy trên "Event Dispatch Thread".
        SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
    }
}
