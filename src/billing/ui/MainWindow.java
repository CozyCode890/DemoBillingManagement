package billing.ui;

import billing.dao.AccountDao;
import billing.model.Account;
import billing.model.QuickAccount;
import billing.model.UserSession;
import billing.util.QuickLogin;

import javax.swing.*;
import java.awt.*;

/**
 * ====================================================================
 *  MainWindow -- Cửa sổ chính của ứng dụng
 * ====================================================================
 *  Gom 4 màn hình vào các tab.
 *  Có thanh trạng thái hiển thị thông tin đăng nhập, nút Đổi vai trò và
 *  nút Đăng xuất.
 */
public class MainWindow extends JFrame {

    /** Màu của dấu hiệu "đang bỏ qua màn hình đăng nhập" -- xem plan/11 mục 3.3. */
    private static final Color COLOR_AUTO_LOGIN = new Color(190, 110, 0);

    private final InvoiceViewPanel viewPanel    = new InvoiceViewPanel();
    private final SearchPanel      searchPanel  = new SearchPanel();

    /**
     * Giữ làm field (bản cũ là biến cục bộ) để {@link #doLogout()} hỏi được
     * "tab Lập hoá đơn còn gì chưa lưu không" trước khi {@code dispose()}.
     */
    private final NewInvoicePanel newInvoice;

    private final AccountDao accountDao = new AccountDao();

    /** Phiên này có phải do auto-login tạo ra. Chỉ dùng để vẽ dấu hiệu. */
    private final boolean autoLogin;

    /** Giữ làm field để {@link #refreshUserInfo()} vẽ lại được khi đổi vai trò. */
    private final JLabel lbInfo = new JLabel();

    private final JButton btSwitchRole = new JButton(I18n.text("Đổi vai trò", "Switch Role"));
    private final JButton btLanguage = new JButton();
    private final JButton btLogout     = new JButton(I18n.text("Đăng xuất", "Log out"));
    private final JPopupMenu switchRoleMenu = new JPopupMenu();
    private final JMenuItem miLanguage = new JMenuItem();

    /** Hàm dựng cũ -- giữ lại để không phá chỗ gọi nào khác. */
    public MainWindow() {
        this(false);
    }

    /**
     * @param autoLogin true nếu phiên này vào app mà không qua màn hình đăng
     *                  nhập; thanh trạng thái sẽ hiện chữ
     *                  {@code (DANG NHAP TU DONG)} màu cam
     */
    public MainWindow(boolean autoLogin) {
        super("Retail Invoicing");
        this.autoLogin = autoLogin;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 820);
        setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(Ui.BOLD);

        // Lưu hóa đơn xong thì danh sách bên tab 2 tự tải lại.
        newInvoice = new NewInvoicePanel(viewPanel::reload);

        tabs.addTab(I18n.text("1. Lập hóa đơn", "1. Create Billing"),  newInvoice);
        tabs.addTab(I18n.text("2. Tra cứu", "2. Search"),              searchPanel);
        tabs.addTab(I18n.text("3. Xem hóa đơn", "3. Invoices"),        viewPanel);
        tabs.addTab(I18n.text("4. Báo cáo", "4. Reports"),             new ReportPanel());

        // Mỗi lần chuyển sang tab xem hoa đơn thì cập nhật quyền.
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedComponent() == viewPanel) {
                viewPanel.applyRolePermissions();
            }
        });

        add(tabs, BorderLayout.CENTER);
        add(statusBar(), BorderLayout.SOUTH);

        JMenu languageMenu = new JMenu(I18n.text("Ngôn ngữ", "Language"));
        miLanguage.addActionListener(e -> toggleLanguage());
        I18n.markDynamicText(miLanguage);
        languageMenu.add(miLanguage);
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(languageMenu);
        setJMenuBar(menuBar);
        btLanguage.addActionListener(e -> toggleLanguage());
        I18n.markDynamicText(btLanguage);
        refreshLanguageControls();

        // Áp dụng quyền người dùng cho tab xem hóa đơn
        viewPanel.applyRolePermissions();
        I18n.install(this);
    }

    // ------------------------------------------------------------------
    //  Thanh trạng thái
    // ------------------------------------------------------------------

    /** Thanh trạng thái dưới đáy cửa sổ: Tài khoản hiện tại + Đổi vai trò + Đăng xuất */
    private JComponent statusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(210, 210, 210)));

        lbInfo.setFont(new Font("Consolas", Font.PLAIN, 12));
        lbInfo.setForeground(new Color(60, 60, 60));
        lbInfo.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        refreshUserInfo();

        // BoxLayout chu khong phai FlowLayout: FlowLayout se XUONG DONG khi cua
        // so hep lai, lam thanh trang thai cao gap doi. BoxLayout thi chu bi cat
        // bot -- va tooltip cua lbInfo van cho doc du.
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
        left.setOpaque(false);
        left.add(lbInfo);

        // Dấu hiệu bắt buộc khi auto-login đang bật: bỏ qua màn hình đăng nhập
        // mà không có dấu hiệu gì là cách nhanh nhất để một bạn trong nhóm kết
        // luận "app này không có đăng nhập". Dấu hiệu còn lại là dòng
        // [AUTO-LOGIN] in ra console lúc khởi động.
        if (autoLogin) {
            JLabel lbAuto = new JLabel(I18n.text("(ĐĂNG NHẬP TỰ ĐỘNG)", "(AUTO LOGIN)"));
            lbAuto.setFont(new Font("Consolas", Font.BOLD, 12));
            lbAuto.setForeground(COLOR_AUTO_LOGIN);
            lbAuto.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            lbAuto.setToolTipText(I18n.text("Ứng dụng đã tự đăng nhập, không qua màn hình đăng nhập.",
                                            "The app logged in automatically, without the login screen."));
            left.add(lbAuto);
        }
        left.add(Box.createHorizontalGlue());

        btSwitchRole.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btSwitchRole.setForeground(new Color(40, 90, 160));
        btSwitchRole.setIcon(DROPDOWN_CARET);
        btSwitchRole.setHorizontalTextPosition(SwingConstants.LEFT);
        btSwitchRole.setIconTextGap(6);
        btSwitchRole.setFocusPainted(false);
        btSwitchRole.setToolTipText(I18n.text("Đổi sang tài khoản khác mà KHÔNG mất hóa đơn đang lập ở tab 1",
                                              "Switch to another account WITHOUT losing the bill in tab 1"));
        btSwitchRole.addActionListener(e -> {
            Dimension d = switchRoleMenu.getPreferredSize();
            // Hiện lên PHÍA TRÊN nút: thanh trạng thái nằm sát đáy cửa sổ.
            switchRoleMenu.show(btSwitchRole, 0, -d.height);
        });

        btLogout.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btLogout.setForeground(new Color(180, 40, 40));
        btLogout.setFocusPainted(false);
        btLogout.addActionListener(e -> doLogout());

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        right.setOpaque(false);
        right.add(btSwitchRole);
        right.add(btLanguage);
        right.add(btLogout);

        // Nhan nguoi dung o CENTER chu khong phai WEST: BorderLayout cap cho WEST
        // va EAST dung be rong mong muon cua chung, nen o cua so hep (do ~673px
        // can thiet > 640px) hai ben DE LEN NHAU. O CENTER thi phan con lai sau
        // khi tru hang nut moi la cho cua nhan -- chu bi cat, khong bi nut de len.
        bar.add(left, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);

        rebuildSwitchRoleMenu();
        return bar;
    }

    private void toggleLanguage() {
        I18n.toggle();
        refreshLanguageControls();
    }

    private void refreshLanguageControls() {
        btLanguage.setText(I18n.languageSwitchLabel());
        miLanguage.setText(I18n.languageSwitchLabel());
        String nextLanguage = I18n.isEnglish() ? "Tiếng Việt" : "English";
        String tip = I18n.isEnglish()
                ? "Switch the interface to " + nextLanguage
                : "Chuyển giao diện sang " + nextLanguage;
        btLanguage.setToolTipText(tip);
        miLanguage.setToolTipText(tip);
        rebuildSwitchRoleMenu();
        refreshUserInfo();
    }

    /** Vẽ lại nhãn người dùng ở thanh trạng thái theo {@link UserSession} hiện tại. */
    private void refreshUserInfo() {
        Account user = UserSession.getCurrentUser();
        String userInfo = (user != null)
                ? user.getFullName() + I18n.msg("  [Vai trò: ", "  [Role: ")
                  + user.getRole().toUpperCase() + "]"
                : I18n.msg("(Chưa đăng nhập)", "(Not logged in)");
        lbInfo.setText(I18n.msg("  Người dùng: ", "  User: ") + userInfo);
        // Tooltip de doc du khi cua so hep lam chu bi cat.
        lbInfo.setToolTipText(I18n.msg("Người dùng: ", "User: ") + userInfo);
    }

    /**
     * Dựng lại menu "Đổi vai trò": liệt kê các tài khoản nhanh <b>khác</b> tài
     * khoản đang dùng.
     *
     * <p>Dùng {@link JPopupMenu} chứ không dùng một nút "lật qua lật lại" vì
     * {@code quick.login.accounts} có thể có 3 mục trở lên, lúc đó một nút "lật"
     * là vô nghĩa.
     *
     * <p>Nút <b>ẩn hẳn</b> (không phải mờ đi) khi {@code quick.login.enabled=false}
     * hoặc không còn tài khoản nào khác để đổi: một nút bị khoá không giải thích
     * được gì cho người xem.
     */
    private void rebuildSwitchRoleMenu() {
        switchRoleMenu.removeAll();

        Account current = UserSession.getCurrentUser();
        String currentUsername = (current != null) ? current.getUsername() : "";

        for (QuickAccount acc : QuickLogin.accounts()) {
            if (acc.getUsername().equalsIgnoreCase(currentUsername)) {
                continue;
            }
            JMenuItem item = new JMenuItem(I18n.quickAccountLabel(acc.getLabel())
                    + " (" + acc.getUsername() + ")");
            I18n.markDynamicText(item);
            item.addActionListener(e -> switchRole(acc));
            switchRoleMenu.add(item);
        }

        boolean hasTargets = switchRoleMenu.getComponentCount() > 0;
        if (btSwitchRole.isVisible() != hasTargets) {
            btSwitchRole.setVisible(hasTargets);
            Container parent = btSwitchRole.getParent();
            if (parent != null) {
                parent.revalidate();
                parent.repaint();
            }
        }
    }

    // ------------------------------------------------------------------
    //  Đổi vai trò / Đăng xuất
    // ------------------------------------------------------------------

    /**
     * Đổi vai trò <b>tại chỗ</b>, không dựng lại cửa sổ.
     *
     * <p>Cách hiển nhiên là copy {@link #doLogout()}: dispose -> authenticate ->
     * {@code new MainWindow()}. <b>Không làm thế:</b> dựng lại cửa sổ nghĩa là
     * một {@code NewInvoicePanel} mới, tức là giỏ hàng / danh sách thanh toán /
     * khách hàng đang nhập mất sạch, và tab đang xem nhảy về tab 1. Đổi vai trò
     * là thao tác được bấm rất nhiều lần khi demo phân quyền nên mất dữ liệu
     * kiểu đó là không chấp nhận được.
     *
     * <p>Vẫn <b>xác thực thật</b> qua {@code sp_authenticate_account}: nếu mật
     * khẩu trong bảng {@code Account} đã bị đổi thì thao tác này phải thất bại
     * đúng như gõ tay (plan/11 mục 2.2).
     *
     * <p><b>LƯU Ý CHO KHỐI SAU:</b> toàn bộ phần giao diện phụ thuộc vai trò
     * hiện chỉ gồm hai chỗ -- {@code InvoiceViewPanel.applyRolePermissions()} và
     * nhãn người dùng ở thanh trạng thái. Nếu bạn thêm một chỗ nào nữa khoá/mở
     * UI theo vai trò thì <b>phải thêm vào hàm này</b>, nếu không nó sẽ giữ
     * trạng thái của vai trò cũ. (Chỗ lấy {@code supervisorId} cho nghiệp vụ trả
     * hàng trong {@code InvoiceViewPanel} đọc {@code UserSession} tại thời điểm
     * bấm nút nên tự đúng, không cần nhắc ở đây.)
     */
    private void switchRole(QuickAccount target) {
        setStatusBarBusy(true);
        new SwingWorker<Account, Void>() {
            @Override
            protected Account doInBackground() {
                return accountDao.authenticate(target.getUsername(), target.getPassword());
            }

            @Override
            protected void done() {
                setStatusBarBusy(false);
                Account acc;
                try {
                    acc = get();
                } catch (Exception ex) {
                    Ui.error(MainWindow.this, ex);
                    return;
                }
                if (acc == null) {
                    // Phiên hiện tại giữ nguyên -- không rơi về trạng thái chưa đăng nhập.
                    JOptionPane.showMessageDialog(MainWindow.this,
                        I18n.msg("Không đăng nhập được bằng tài khoản '" + target.getUsername() + "'.\n"
                               + "Có thể mật khẩu đã bị đổi.\n\n"
                               + "Bạn vẫn đang đăng nhập bằng tài khoản hiện tại.",
                                 "Cannot log in with account '" + target.getUsername() + "'.\n"
                               + "The password may have been changed.\n\n"
                               + "You are still logged in with the current account."),
                        I18n.msg("Đổi vai trò thất bại", "Switch Role Failed"), JOptionPane.WARNING_MESSAGE);
                    return;
                }
                UserSession.setCurrentUser(acc);     // 1. đổi phiên
                refreshUserInfo();                   // 2. vẽ lại nhãn thanh trạng thái
                viewPanel.applyRolePermissions();    // 3. khoá/mở lại các nút
                rebuildSwitchRoleMenu();             // 4. tài khoản hiện tại không còn trong menu
            }
        }.execute();
    }

    /** Khoá 2 nút của thanh trạng thái trong lúc đang xác thực. */
    private void setStatusBarBusy(boolean busy) {
        btSwitchRole.setEnabled(!busy);
        btLogout.setEnabled(!busy);
        setCursor(Cursor.getPredefinedCursor(busy ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    /** Xử lý hành động Đăng xuất: đóng MainWindow và quay lại LoginDialog */
    private void doLogout() {
        String question = I18n.msg("Bạn có chắc chắn muốn đăng xuất khỏi hệ thống?",
                                   "Are you sure to log out the system?");
        String pending = newInvoice.pendingWorkSummary();

        if (!pending.isEmpty()) {
            // Bản cũ chỉ hỏi "Ban co chac chan..." rồi dispose() -- xoá sạch giỏ
            // hàng đang nhập mà không hề nói. Dòng gợi ý phía dưới quan trọng hơn
            // vẻ ngoài của nó: nó dạy người dùng rằng đã có đường không mất dữ
            // liệu, đúng lúc họ đang định đi đường mất dữ liệu.
            question = I18n.msg(
                "Tab \"Lập hóa đơn\" đang có " + pending + " chưa lưu.\n"
              + "Đăng xuất sẽ mất toàn bộ. Vẫn đăng xuất?\n\n"
              + "Gợi ý: muốn xem giao diện của vai trò khác mà không mất giỏ hàng\n"
              + "thì dùng nút \"Đổi vai trò\" bên cạnh.",
                "Tab \"Create Billing\" has " + pending + " unsaved.\n"
              + "Logging out will discard everything. Log out anyway?\n\n"
              + "Tip: to see another role without losing the cart,\n"
              + "use the \"Switch Role\" button next to it.");
        }

        int choice = JOptionPane.showConfirmDialog(this, question,
                I18n.msg("Xác nhận đăng xuất", "Confirm Logout"), JOptionPane.YES_NO_OPTION);

        if (choice == JOptionPane.YES_OPTION) {
            this.dispose();           // Đóng cửa sổ chính hiện tại
            UserSession.clear();      // Xóa phiên đăng nhập

            // Mở lại hộp thoại Đăng nhập (AppFlow là chỗ duy nhất biết luồng này)
            AppFlow.showLogin(null);
        }
    }

    /** Draw the dropdown caret so it does not depend on font glyph coverage. */
    private static final Icon DROPDOWN_CARET = new Icon() {
        @Override public int getIconWidth() { return 8; }

        @Override public int getIconHeight() { return 6; }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            Color color = component.isEnabled()
                    ? component.getForeground()
                    : UIManager.getColor("Button.disabledText");
            g2.setColor(color == null ? Color.GRAY : color);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.fillPolygon(new int[] {x, x + 7, x + 3},
                    new int[] {y, y, y + 5}, 3);
            g2.dispose();
        }
    };
}
