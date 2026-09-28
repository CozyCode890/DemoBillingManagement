package billing.ui;

import billing.dao.AccountDao;
import billing.model.Account;
import billing.model.UserSession;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * ====================================================================
 *  LoginDialog -- Màn hình Đăng nhập hệ thống
 * ====================================================================
 *  Hiển thị đầu tiên khi mở app.
 *  - Nhập username và password.
 *  - Kiểm tra tài khoản trong bảng Account qua AccountDao.
 *  - Lưu thông tin vào UserSession và phân quyền cho giao diện.
 */
public class LoginDialog extends JDialog {

    private final JTextField tfUsername = new JTextField(16);
    private final JPasswordField pfPassword = new JPasswordField(16);
    private final AccountDao accountDao = new AccountDao();
    private boolean succeeded = false;

    public LoginDialog(Frame parent) {
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

        // Khung gợi ý tài khoản mẫu
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.gridwidth = 2;
        JLabel lbHint = new JLabel("<html><div style='background-color:#f0f4f8; padding:8px; border-radius:4px;'>"
                + "<b>Goi y tai khoan mau co san trong CSDL:</b><br>"
                + "• <b>cashier</b> / <b>cashier</b>: Thu ngan (Khoa nut sua/xoa tren UI)<br>"
                + "• <b>manager</b> / <b>manager</b>: Quan ly (Toan quyen sua/xoa tren UI)</div></html>");
        lbHint.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        form.add(lbHint, gbc);

        // Panel nút bấm
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton btLogin = new JButton("Dang nhap");
        JButton btExit  = new JButton("Thoat");

        btLogin.setFont(Ui.BOLD);
        btLogin.addActionListener(e -> doLogin());
        btExit.addActionListener(e -> System.exit(0));

        buttons.add(btLogin);
        buttons.add(btExit);

        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(btLogin);
        pack();
        setLocationRelativeTo(parent);

        // Khi bấm nút X trên cửa sổ thì thoát chương trình
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });
    }

    private void doLogin() {
        String username = tfUsername.getText().trim();
        String password = new String(pfPassword.getPassword()).trim();

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Vui long nhap day du Ten dang nhap va Mat khau!",
                    "Thieu thong tin", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            Account account = accountDao.authenticate(username, password);
            if (account != null) {
                UserSession.setCurrentUser(account);
                succeeded = true;
                dispose(); // Đóng LoginDialog để MainWindow hiện lên
            } else {
                JOptionPane.showMessageDialog(this,
                        "Sai ten dang nhap hoac mat khau! Vui long kiem tra lai.",
                        "Dang nhap that bai", JOptionPane.ERROR_MESSAGE);
                pfPassword.setText("");
                pfPassword.requestFocus();
            }
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    public boolean isSucceeded() {
        return succeeded;
    }
}
