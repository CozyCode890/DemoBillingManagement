package billing.ui;

import billing.db.Db;
import billing.model.Account;
import billing.model.UserSession;

import javax.swing.*;
import java.awt.*;

/**
 * ====================================================================
 *  MainWindow -- Cửa sổ chính của ứng dụng
 * ====================================================================
 *  Gom 4 màn hình vào các tab.
 *  Có thanh trạng thái hiển thị thông tin đăng nhập và Nút Đăng xuất.
 */
public class MainWindow extends JFrame {

    private final SqlLogPanel     logPanel     = new SqlLogPanel();
    private final InvoiceViewPanel viewPanel   = new InvoiceViewPanel();
    private final SearchPanel      searchPanel   = new SearchPanel();

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
        tabs.addTab("2. Xem hoa don (Database)",  viewPanel);
        tabs.addTab("3. Bao cao",      new ReportPanel());
        tabs.addTab("4. Nhat ky SQL",  logPanel);
        tabs.addTab("5. Tra cứu dữ liệu",  searchPanel);

        // Mỗi lần chuyển sang tab nhật ký thì làm mới nội dung.
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedComponent() == logPanel) {
                logPanel.refresh();
            } else if (tabs.getSelectedComponent() == viewPanel) {
                viewPanel.applyRolePermissions();
            }
        });

        add(tabs, BorderLayout.CENTER);
        add(statusBar(), BorderLayout.SOUTH);

        // Áp dụng quyền người dùng cho tab xem hóa đơn
        viewPanel.applyRolePermissions();
    }

    /** Thanh trạng thái dưới đáy cửa sổ: Kết nối + Tài khoản hiện tại + Nút Đăng xuất */
    private JComponent statusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(210, 210, 210)));

        Account user = UserSession.getCurrentUser();
        String userInfo = (user != null)
                ? user.getFullName() + "  [Vai tro: " + user.getRole().toUpperCase() + "]"
                : "(Chua dang nhap)";

        JLabel lbInfo = new JLabel("  Ket noi: " + Db.getUser() + " @ " + Db.getUrl()
                + "   |   Nguoi dung: " + userInfo);
        lbInfo.setFont(new Font("Consolas", Font.PLAIN, 12));
        lbInfo.setForeground(new Color(60, 60, 60));
        lbInfo.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        JButton btLogout = new JButton("Dang xuat");
        btLogout.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btLogout.setForeground(new Color(180, 40, 40));
        btLogout.setFocusPainted(false);
        btLogout.addActionListener(e -> doLogout());

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        right.add(btLogout);

        bar.add(lbInfo, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    /** Xử lý hành động Đăng xuất: đóng MainWindow và quay lại LoginDialog */
    private void doLogout() {
        int choice = JOptionPane.showConfirmDialog(this,
                "Ban co chac chan muon dang xuat khoi he thong?",
                "Xac nhan dang xuat", JOptionPane.YES_NO_OPTION);

        if (choice == JOptionPane.YES_OPTION) {
            this.dispose();           // Đóng cửa sổ chính hiện tại
            UserSession.clear();      // Xóa phiên đăng nhập

            // Mở lại hộp thoại Đăng nhập
            SwingUtilities.invokeLater(() -> {
                LoginDialog login = new LoginDialog(null);
                login.setVisible(true);
                if (login.isSucceeded()) {
                    new MainWindow().setVisible(true);
                }
            });
        }
    }
}
