package billing.ui;

import billing.dao.AccountDao;
import billing.model.Account;
import billing.model.QuickAccount;
import billing.model.UserSession;
import billing.util.LoginPrefs;
import billing.util.QuickLogin;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * ====================================================================
 *  LoginDialog -- Màn hình Đăng nhập hệ thống
 * ====================================================================
 *  Hiển thị đầu tiên khi mở app.
 *  - Nhập username và password.
 *  - Kiểm tra tài khoản trong bảng Account qua AccountDao.
 *  - Lưu thông tin vào UserSession và phân quyền cho giao diện.
 *
 *  <p>Khối 2 thêm hàng nút <b>"Đăng nhập nhanh"</b> (bật/tắt bằng
 *  {@code quick.login.enabled}). Nút nhanh chỉ <b>điền sẵn rồi bấm Đăng nhập
 *  hộ</b>: nó vẫn gọi {@code AccountDao.authenticate()} nên mật khẩu sai trong
 *  bảng {@code Account} vẫn thất bại đúng như gõ tay.
 *  Xem {@code plan/11-KE-HOACH-KHOI-2-DANG-NHAP-NHANH.md} mục 2.2 và 4.1.
 *
 *  <p>Màn hình này <b>không in mật khẩu</b> ra bất cứ đâu -- khung gợi ý cũ
 *  (in sẵn {@code cashier / cashier}) đã bị bỏ; nhãn nút nhanh chỉ có vai trò
 *  và tên đăng nhập.
 */
public class LoginDialog extends JDialog {

    private static final Color COLOR_ERROR  = new Color(180, 40, 40);
    private static final Color COLOR_NOTICE = new Color(190, 110, 0);
    private static final Color COLOR_INFO   = new Color(60, 60, 60);

    /** Nhiều hơn số này thì xếp thêm hàng, để dialog không phình ngang. */
    private static final int MAX_QUICK_BUTTONS_PER_ROW = 3;

    private final JTextField tfUsername = new JTextField(16);
    private final JPasswordField pfPassword = new JPasswordField(16);
    private final AccountDao accountDao = new AccountDao();

    private final JLabel lbStatus = new JLabel(" ");
    private final JButton btLogin = new JButton("Dang nhap");
    private final JButton btExit  = new JButton("Thoat");

    /** Các nút nhanh, giữ lại để {@link #setBusy} khoá/mở được cả hàng. */
    private final List<JButton> quickButtons = new ArrayList<>();

    private boolean succeeded = false;

    /** Hàm dựng cũ -- giữ lại để không phá chỗ gọi nào khác. */
    public LoginDialog(Frame parent) {
        this(parent, null);
    }

    /**
     * @param notice thông báo hiện sẵn ở dòng trạng thái, null nếu không có.
     *               Dùng khi auto-login thất bại: người dùng phải thấy lý do vì
     *               sao mình vẫn đang đứng ở màn hình đăng nhập.
     */
    public LoginDialog(Frame parent, String notice) {
        super(parent, "Dang nhap he thong -- Retail Invoicing", true);
        setLayout(new BorderLayout(10, 10));
        setResizable(false);

        // Panel nhập liệu
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(18, 24, 12, 24));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        JLabel lbUser = new JLabel("Ten dang nhap:");
        lbUser.setFont(Ui.BOLD);
        form.add(lbUser, gbc);

        gbc.gridx = 1;
        form.add(tfUsername, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        JLabel lbPass = new JLabel("Mat khau:");
        lbPass.setFont(Ui.BOLD);
        form.add(lbPass, gbc);

        gbc.gridx = 1;
        form.add(pfPassword, gbc);

        // Hàng nút "Đăng nhập nhanh" -- biến mất HOÀN TOÀN khi tính năng tắt
        // hoặc danh sách rỗng (kể cả đường phân cách và 2 dòng hướng dẫn).
        // Một hàng nút bị setEnabled(false) không giải thích được gì cho người
        // xem; không có thì rõ hơn.
        List<QuickAccount> quickAccounts = QuickLogin.accounts();
        if (!quickAccounts.isEmpty()) {
            gbc.gridx = 0; gbc.gridy = 2;
            gbc.gridwidth = 2;
            form.add(quickPanel(quickAccounts), gbc);
            gbc.gridwidth = 1;
        }

        btLogin.setFont(Ui.BOLD);
        btLogin.addActionListener(e -> doLogin());
        btExit.addActionListener(e -> System.exit(0));

        add(form, BorderLayout.CENTER);
        add(southPanel(), BorderLayout.SOUTH);

        getRootPane().setDefaultButton(btLogin);

        // Nhớ tên đăng nhập gần nhất (KHÔNG BAO GIỜ nhớ mật khẩu -- mục 2.3).
        String last = LoginPrefs.loadLastUsername();
        if (!last.isEmpty()) {
            tfUsername.setText(last);
        }

        if (notice != null && !notice.isBlank()) {
            setStatus(notice, COLOR_NOTICE);
        }

        pack();
        setLocationRelativeTo(parent);

        addWindowListener(new WindowAdapter() {
            /** Khi bấm nút X trên cửa sổ thì thoát chương trình. */
            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }

            /** Username đã điền sẵn thì con trỏ nhảy luôn sang ô mật khẩu. */
            @Override
            public void windowOpened(WindowEvent e) {
                if (tfUsername.getText().isEmpty()) {
                    tfUsername.requestFocusInWindow();
                } else {
                    pfPassword.requestFocusInWindow();
                }
            }
        });
    }

    // ------------------------------------------------------------------
    //  Dựng giao diện
    // ------------------------------------------------------------------

    /** Khung "Đăng nhập nhanh": hàng nút + 2 dòng hướng dẫn. Không chứa mật khẩu. */
    private JComponent quickPanel(List<QuickAccount> accounts) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Dang nhap nhanh (tai khoan mau cua ban demo)"));

        int cols = Math.min(accounts.size(), MAX_QUICK_BUTTONS_PER_ROW);
        int rows = (accounts.size() + cols - 1) / cols;
        JPanel grid = new JPanel(new GridLayout(rows, cols, 6, 4));
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);

        for (int i = 0; i < accounts.size(); i++) {
            QuickAccount acc = accounts.get(i);
            // Nhãn "1. Thu ngan (cashier)" + mnemonic '1' -> Alt+1.
            // Không dùng F1/F2: theo quy ước F1 là Trợ giúp.
            // Mục thứ 10 trở đi không có phím tắt (hết số) -- không phải lỗi.
            String prefix = (i < 9) ? (i + 1) + ". " : "";
            JButton bt = new JButton(prefix + acc.getButtonLabel());
            if (i < 9) {
                bt.setMnemonic(Character.forDigit(i + 1, 10));
            }
            bt.setToolTipText("Dien san va dang nhap bang tai khoan '" + acc.getUsername() + "'");
            bt.addActionListener(e -> quickLogin(acc));
            quickButtons.add(bt);
            grid.add(bt);
        }

        panel.add(grid);
        panel.add(Box.createVerticalStrut(4));
        panel.add(hint("Bam mot nut de dien san va dang nhap ngay."));
        panel.add(hint("Phim tat: Alt+1 ... Alt+" + Math.min(accounts.size(), 9)
                     + ".  Enter = Dang nhap."));
        return panel;
    }

    private JLabel hint(String text) {
        JLabel lb = new JLabel(text);
        lb.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lb.setForeground(new Color(90, 90, 90));
        lb.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lb;
    }

    /**
     * Panel phía nam: dòng trạng thái ở <b>hàng riêng</b> phía trên, rồi 2 nút.
     *
     * <p>Dòng trạng thái phải được cấp bề rộng không phụ thuộc vào nội dung lúc
     * {@code pack()} (khi đó nó đang là chuỗi rỗng), nếu không thì mọi thông báo
     * sau đó bị cắt mất -- dialog {@code setResizable(false)} nên không có lần
     * {@code pack()} thứ hai để sửa. Đặt nó ở {@code NORTH} (một hàng riêng, ăn
     * hết bề ngang) cho nó nhiều chỗ nhất mà không phải nới rộng dialog ra chỉ
     * để chứa một câu thông báo.
     */
    private JComponent southPanel() {
        JPanel south = new JPanel(new BorderLayout(0, 2));
        south.setBorder(BorderFactory.createEmptyBorder(0, 24, 10, 10));

        lbStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lbStatus.setForeground(COLOR_INFO);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        buttons.add(btLogin);
        buttons.add(btExit);

        south.add(lbStatus, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.CENTER);
        return south;
    }

    // ------------------------------------------------------------------
    //  Đăng nhập
    // ------------------------------------------------------------------

    /** Nút "Đăng nhập": lấy những gì người dùng đã gõ. */
    private void doLogin() {
        String username = tfUsername.getText().trim();
        String password = new String(pfPassword.getPassword()).trim();

        if (username.isEmpty() || password.isEmpty()) {
            // Dòng trạng thái thay cho JOptionPane: một hộp thoại phải bấm OK
            // mới làm tiếp được là thêm một cú bấm nữa vào đúng cái vòng lặp
            // mà khối này đang muốn rút ngắn.
            setStatus("Vui long nhap day du Ten dang nhap va Mat khau.", COLOR_ERROR);
            (username.isEmpty() ? tfUsername : pfPassword).requestFocusInWindow();
            return;
        }
        submit(username, password);
    }

    /** Nút nhanh: điền sẵn 2 ô cho người dùng thấy rồi xác thực như thường. */
    private void quickLogin(QuickAccount acc) {
        tfUsername.setText(acc.getUsername());
        pfPassword.setText(acc.getPassword());
        submit(acc.getUsername(), acc.getPassword());
    }

    /**
     * Xác thực <b>ngoài EDT</b>.
     *
     * <p>Bản cũ gọi {@code Db.getConnection()} ngay trên EDT: nếu MySQL vừa bị
     * tắt thì {@code DriverManager} chờ tới khi hết thời gian chờ TCP -- dialog
     * đứng hình, không vẽ lại, người dùng bấm thêm 3-4 lần nữa và mỗi lần là một
     * kết nối JDBC mới đang chờ. Nút nhanh làm chuyện đó <i>dễ xảy ra hơn</i> vì
     * bấm nút nhanh hơn gõ mật khẩu.
     */
    private void submit(String username, String password) {
        setBusy(true, "Dang kiem tra tai khoan...");
        new SwingWorker<Account, Void>() {
            @Override
            protected Account doInBackground() {
                return accountDao.authenticate(username, password);   // luồng nền
            }

            @Override
            protected void done() {
                setBusy(false, "");
                try {
                    Account acc = get();
                    if (acc == null) {
                        setStatus("Sai ten dang nhap hoac mat khau.", COLOR_ERROR);
                        pfPassword.setText("");
                        pfPassword.requestFocusInWindow();
                    } else {
                        LoginPrefs.saveLastUsername(acc.getUsername());
                        UserSession.setCurrentUser(acc);
                        succeeded = true;
                        dispose();   // Đóng LoginDialog để MainWindow hiện lên
                    }
                } catch (Exception ex) {
                    // Ngoại lệ thật (mất kết nối DB, thiếu driver) vẫn dùng hộp
                    // thoại: nó dài và người dùng cần đọc kỹ.
                    Ui.error(LoginDialog.this, ex);
                }
            }
        }.execute();
    }

    /**
     * Khoá <b>tất cả</b> nút trong lúc đang xác thực (Đăng nhập, Thoát và mọi
     * nút nhanh) -> không thể xếp hàng hai lượt xác thực, kể cả khi bấm nút
     * nhanh 5 lần thật nhanh.
     */
    private void setBusy(boolean busy, String message) {
        btLogin.setEnabled(!busy);
        btExit.setEnabled(!busy);
        for (JButton bt : quickButtons) {
            bt.setEnabled(!busy);
        }
        setStatus(message, COLOR_INFO);
        setCursor(Cursor.getPredefinedCursor(busy ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    /** Bề rộng để ngắt dòng cho thông báo dài (lý do auto-login thất bại). */
    private static final int STATUS_WRAP_PX = 420;

    private void setStatus(String message, Color color) {
        lbStatus.setForeground(color);
        if (message == null || message.isEmpty()) {
            // " " chứ không phải "": JLabel rỗng có chiều cao 0, thanh trạng thái
            // sẽ nhảy lên nhảy xuống mỗi lần đổi thông báo.
            lbStatus.setText(" ");
        } else if (message.length() <= 60) {
            lbStatus.setText(message);
        } else {
            // Thông báo dài (ví dụ lý do auto-login thất bại) được ngắt dòng.
            // Chỉ xảy ra với thông báo truyền vào hàm dựng, tức là TRƯỚC pack(),
            // nên dialog vẫn đủ cao để hiện 2-3 dòng.
            lbStatus.setText("<html><div style='width:" + STATUS_WRAP_PX + "px'>"
                + escapeHtml(message) + "</div></html>");
        }
    }

    /** Giá trị trong config.properties có thể chứa {@code < > &} -- đừng để nó thành thẻ HTML. */
    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public boolean isSucceeded() {
        return succeeded;
    }
}
