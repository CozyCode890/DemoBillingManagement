package billing;

import billing.db.Db;
import billing.db.SqlLog;
import billing.ui.MainWindow;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
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
              + "     trong thu muc lib/. Mo Setup.exe / Setup.app bam CHAY de tu tai.\n";
            JOptionPane.showMessageDialog(null, help, "Loi ket noi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        SqlLog.appStart(Db.getUrl());

        SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
    }
}
