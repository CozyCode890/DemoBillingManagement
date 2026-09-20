package billing.ui;

import billing.db.Db;

import javax.swing.*;
import java.awt.*;

/** Cửa sổ chính: gom 4 màn hình vào các tab. */
public class MainWindow extends JFrame {

    private final SqlLogPanel     logPanel     = new SqlLogPanel();
    private final InvoiceViewPanel viewPanel   = new InvoiceViewPanel();

    public MainWindow() {
        super("Demo Retail Invoicing  --  Java Swing + JDBC + MySQL 8");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 820);
        setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(Ui.BOLD);

        // Lưu hóa đơn xong thì danh sách bên tab 2 tự tải lại.
        NewInvoicePanel newInvoice = new NewInvoicePanel(viewPanel::reload);

        tabs.addTab("1. Lap hoa don",  newInvoice);
        tabs.addTab("2. Xem hoa don",  viewPanel);
        tabs.addTab("3. Bao cao",      new ReportPanel());
        tabs.addTab("4. Nhat ky SQL",  logPanel);

        // Mỗi lần chuyển sang tab nhật ký thì làm mới nội dung.
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedComponent() == logPanel) logPanel.refresh();
        });

        add(tabs, BorderLayout.CENTER);
        add(statusBar(), BorderLayout.SOUTH);
    }

    private JComponent statusBar() {
        JLabel lb = new JLabel("  Ket noi: " + Db.getUser() + " @ " + Db.getUrl());
        lb.setFont(new Font("Consolas", Font.PLAIN, 11));
        lb.setForeground(new Color(90, 90, 90));
        lb.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        return lb;
    }
}
