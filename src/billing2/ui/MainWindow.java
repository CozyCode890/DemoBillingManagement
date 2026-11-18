package billing2.ui;

import billing2.dao.AccountDao;
import billing2.model.Account;
import billing2.model.QuickAccount;
import billing2.model.UserSession;
import billing2.util.QuickLogin;

import javax.swing.*;
import java.awt.*;

/**
 * Cửa sổ chính: ba tab + thanh trạng thái (người dùng, Switch role, Log out).
 *   1. New Invoice  -> NewInvoicePanel      2. Invoices -> InvoiceViewPanel      3. Reports -> ReportPanel
 */
public class MainWindow extends JFrame {

    private final InvoiceViewPanel viewPanel  = new InvoiceViewPanel();
    private final NewInvoicePanel  newInvoice = new NewInvoicePanel(viewPanel::reload);   // lưu xong -> tab 2 tải lại
    private final AccountDao  accountDao     = new AccountDao();
    private final JLabel      lbInfo         = new JLabel();
    private final JButton     btSwitchRole   = new JButton("Switch role ▾");
    private final JButton     btLogout       = new JButton("Log out");
    private final JPopupMenu  switchRoleMenu = new JPopupMenu();

    /** @param autoLogin vào app không qua màn hình đăng nhập -> hiện "(AUTO LOGIN)" màu cam */
    public MainWindow(boolean autoLogin) {
        super("Retail Invoicing");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 820);
        setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(Ui.BOLD);
        tabs.addTab("1. New Invoice", newInvoice);
        tabs.addTab("2. Invoices", viewPanel);
        tabs.addTab("3. Reports", new ReportPanel());
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedComponent() == viewPanel) viewPanel.applyRolePermissions();
        });
        add(tabs, BorderLayout.CENTER);

        lbInfo.setFont(new Font("Consolas", Font.PLAIN, 12));
        JPanel left = Ui.row(lbInfo);
        if (autoLogin) {
            JLabel lbAuto = new JLabel("(AUTO LOGIN)");
            lbAuto.setForeground(new Color(190, 110, 0));
            lbAuto.setToolTipText("The app logged in automatically, without the login screen.");
            left.add(lbAuto);
        }
        btSwitchRole.setToolTipText("Switch to another account WITHOUT losing the bill in tab 1");
        btSwitchRole.addActionListener(e ->
                switchRoleMenu.show(btSwitchRole, 0, -switchRoleMenu.getPreferredSize().height));
        btLogout.setForeground(Ui.RED);
        btLogout.addActionListener(e -> doLogout());
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(210, 210, 210)));
        bar.add(left, BorderLayout.CENTER);
        bar.add(Ui.row(btSwitchRole, btLogout), BorderLayout.EAST);
        add(bar, BorderLayout.SOUTH);

        refreshUser();
    }

    /** Vẽ lại theo người dùng hiện tại: nhãn, menu đổi vai trò, quyền ở tab Invoices. */
    private void refreshUser() {
        Account u = UserSession.getCurrentUser();
        lbInfo.setText(u == null ? "(Not logged in)"
                : "  User: " + u.getFullName() + "  [Role: " + u.getRole().toUpperCase() + "]");
        switchRoleMenu.removeAll();
        for (QuickAccount acc : QuickLogin.accounts()) {
            if (u != null && acc.username().equalsIgnoreCase(u.getUsername())) continue;
            JMenuItem item = new JMenuItem(Ui.en(acc.label()) + " (" + acc.username() + ")");
            item.addActionListener(e -> switchRole(acc));
            switchRoleMenu.add(item);
        }
        btSwitchRole.setVisible(switchRoleMenu.getComponentCount() > 0);
        viewPanel.applyRolePermissions();
    }

    /** Đổi tài khoản mà KHÔNG đóng cửa sổ -- giỏ hàng ở tab 1 còn nguyên. Vẫn xác thực thật. */
    private void switchRole(QuickAccount target) {
        setBusy(true);
        Ui.async(this, () -> accountDao.authenticate(target.username(), target.password()), () -> setBusy(false), acc -> {
            if (acc == null) {
                Ui.warn(this, "Cannot log in with account '" + target.username() + "'.\n"
                        + "The password may have been changed.\n\nYou are still logged in with the current account.",
                        "Switch Role Failed");
                return;
            }
            UserSession.setCurrentUser(acc);
            refreshUser();
        });
    }

    private void setBusy(boolean busy) {
        btSwitchRole.setEnabled(!busy);
        btLogout.setEnabled(!busy);
        setCursor(Cursor.getPredefinedCursor(busy ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    private void doLogout() {
        String pending = newInvoice.pendingWorkSummary();
        String question = pending.isEmpty() ? "Are you sure to log out the system?"
                : "Tab \"New Invoice\" has " + pending + " unsaved.\nLogging out will discard everything. Log out anyway?\n\n"
                + "Tip: to see another role without losing the cart,\nuse the \"Switch role\" button next to it.";
        if (JOptionPane.showConfirmDialog(this, question, "Confirm Logout", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        dispose();
        UserSession.clear();
        AppFlow.showLogin(null);
    }
}
