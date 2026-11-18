package billing2.ui;

import billing2.dao.AccountDao;
import billing2.model.QuickAccount;
import billing2.model.UserSession;
import billing2.util.QuickLogin;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Màn hình đăng nhập: gõ tay, hoặc bấm nút "Quick login" (Alt+1, Alt+2...).
 * Cả hai đường đều xác thực THẬT qua AccountDao.authenticate() -> sp_authenticate_account.
 */
public class LoginDialog extends JDialog {

    private final JTextField     tfUsername = new JTextField(16);
    private final JPasswordField pfPassword = new JPasswordField(16);
    private final JLabel         lbStatus   = new JLabel(" ");
    private final JButton        btLogin    = new JButton("Login");
    private final List<JButton>  quickButtons = new ArrayList<>();
    private final AccountDao     accountDao = new AccountDao();
    private boolean succeeded;

    /** @param notice lý do tự đăng nhập thất bại (hiện màu cam), hoặc null */
    public LoginDialog(Frame parent, String notice) {
        super(parent, "Login -- Retail Invoicing", true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        JPanel center = new JPanel(new BorderLayout(6, 6));
        center.setBorder(BorderFactory.createEmptyBorder(16, 20, 6, 20));
        center.add(Ui.form("Username:", tfUsername, "Password:", pfPassword), BorderLayout.NORTH);

        List<QuickAccount> quick = QuickLogin.accounts();
        if (!quick.isEmpty()) {
            JPanel grid = new JPanel(new GridLayout(0, Math.min(quick.size(), 3), 6, 4));
            for (int i = 0; i < quick.size(); i++) {
                QuickAccount acc = quick.get(i);
                JButton bt = new JButton((i < 9 ? (i + 1) + ". " : "") + Ui.en(acc.label()) + " (" + acc.username() + ")");
                if (i < 9) bt.setMnemonic(Character.forDigit(i + 1, 10));      // Alt+1 ... Alt+9
                bt.addActionListener(e -> {
                    tfUsername.setText(acc.username());
                    pfPassword.setText(acc.password());
                    submit(acc.username(), acc.password());
                });
                quickButtons.add(bt);
                grid.add(bt);
            }
            center.add(Ui.titled("Quick login (sample accounts) -- click to fill in and login", grid), BorderLayout.CENTER);
        }

        btLogin.setFont(Ui.BOLD);
        btLogin.addActionListener(e -> doLogin());
        JButton btExit = new JButton("Exit");
        btExit.addActionListener(e -> System.exit(0));
        JPanel south = new JPanel(new BorderLayout());
        south.setBorder(BorderFactory.createEmptyBorder(0, 20, 8, 10));
        south.add(lbStatus, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        buttons.add(btLogin);
        buttons.add(btExit);
        south.add(buttons, BorderLayout.CENTER);

        add(center, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(btLogin);                // Enter = Login
        tfUsername.setText(QuickLogin.lastUsername());
        if (notice != null) setStatus(notice, new Color(190, 110, 0));
        pack();
        setResizable(false);
        setLocationRelativeTo(parent);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { System.exit(0); }
            @Override public void windowOpened(WindowEvent e) {
                (tfUsername.getText().isEmpty() ? tfUsername : pfPassword).requestFocusInWindow();
            }
        });
    }

    public boolean isSucceeded() { return succeeded; }

    private void doLogin() {
        String username = tfUsername.getText().trim();
        String password = new String(pfPassword.getPassword()).trim();
        if (username.isEmpty() || password.isEmpty()) {
            setStatus("Please enter your full Username and Password!", Ui.RED);
            (username.isEmpty() ? tfUsername : pfPassword).requestFocusInWindow();
            return;
        }
        submit(username, password);
    }

    /** Xác thực NGOÀI EDT. Đúng -> lưu phiên, đóng hộp thoại (AppFlow mở MainWindow). */
    private void submit(String username, String password) {
        setBusy(true);
        setStatus("Checking account...", Color.DARK_GRAY);
        Ui.async(this, () -> accountDao.authenticate(username, password),
                () -> { setBusy(false); setStatus(" ", Color.DARK_GRAY); }, acc -> {
                    if (acc == null) {
                        setStatus("Incorrect Username or Password! Please check again.", Ui.RED);
                        pfPassword.setText("");
                        pfPassword.requestFocusInWindow();
                        return;
                    }
                    QuickLogin.saveLastUsername(acc.getUsername());
                    UserSession.setCurrentUser(acc);
                    succeeded = true;
                    dispose();
                });
    }

    private void setBusy(boolean busy) {
        btLogin.setEnabled(!busy);
        quickButtons.forEach(b -> b.setEnabled(!busy));
        setCursor(Cursor.getPredefinedCursor(busy ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    /** Câu dài (lý do auto-login thất bại) thì tự xuống dòng. */
    private void setStatus(String message, Color color) {
        lbStatus.setForeground(color);
        lbStatus.setText(message.length() <= 60 ? message
                : "<html><div style='width:420px'>" + message.replace("&", "&amp;").replace("<", "&lt;") + "</div></html>");
    }
}
