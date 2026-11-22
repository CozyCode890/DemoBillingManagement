package billing.ui;

import billing.util.AppConfig;
import billing.util.VietQRHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class QrPaymentDialog {
    public static class Result {
        public final BigDecimal amount;
        public final String     transactionRef;

        Result(BigDecimal amount, String transactionRef) {
            this.amount         = amount;
            this.transactionRef = transactionRef;
        }
    }

    public static boolean isEnabled() {
        return AppConfig.getBoolean("qr.enabled", true);
    }

    public static Result show(Component parent, BigDecimal amount) {
        String bin     = AppConfig.get("qr.bank.bin",     VietQRHelper.DEFAULT_BIN);
        String account = AppConfig.get("qr.account.no",   VietQRHelper.DEFAULT_ACCOUNT);
        String holder  = AppConfig.get("qr.account.name", "");

        BigDecimal rounded = amount.setScale(0, RoundingMode.HALF_UP);
        String ref = "TXN" + (System.currentTimeMillis() % 10000000);

        BufferedImage qrImg;
        try {
            qrImg = VietQRHelper.generateQRCodeImage(bin, account, rounded.longValue(), ref, 320);
        } catch (Exception ex) {
            Ui.error(parent, ex);
            return null;
        }

        Window owner = (parent == null) ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, I18n.msg("Thanh toán bằng mã QR (VietQR)", "QR Code Payment (VietQR)"),
                                     Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(10, 10));
        ((JPanel) dialog.getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

        JLabel qrLabel = new JLabel(new ImageIcon(
                qrImg.getScaledInstance(300, 300, Image.SCALE_SMOOTH)));
        qrLabel.setHorizontalAlignment(SwingConstants.CENTER);
        dialog.add(qrLabel, BorderLayout.CENTER);

        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        info.add(centered(I18n.msg("Số tiền trong mã QR: ", "Amount in QR code: ") + Ui.money(rounded), Ui.BOLD, null));
        if (!holder.isEmpty()) {
            info.add(centered(I18n.msg("Chuyển tới: ", "Transfer to: ") + holder, null, null));
        }
        info.add(centered(I18n.msg("Tài khoản: ", "Account: ") + account + "  (BIN " + bin + ")", null, Color.DARK_GRAY));
        info.add(centered(I18n.msg("Nội dung chuyển khoản: ", "Transfer note: ") + ref, null, Color.DARK_GRAY));
        info.add(Box.createRigidArea(new Dimension(0, 6)));
        info.add(centered(I18n.msg("Kiểm tra tiền đã vào tài khoản rồi mới bấm \"Xác nhận đã nhận tiền\".",
                                   "Check that the money has arrived before clicking \"Confirm payment received\"."),
                          new Font("Segoe UI", Font.ITALIC, 12), Color.GRAY));
        dialog.add(info, BorderLayout.SOUTH);

        final Result[] out = new Result[1];

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton btConfirm = new JButton(I18n.msg("Xác nhận đã nhận tiền", "Confirm payment received"));
        JButton btCancel  = new JButton(I18n.msg("Hủy bỏ", "Cancel"));
        btConfirm.setFont(Ui.BOLD);
        btConfirm.addActionListener(e -> {
            out[0] = new Result(rounded.setScale(2, RoundingMode.HALF_UP), ref);
            dialog.dispose();
        });
        btCancel.addActionListener(e -> dialog.dispose());
        buttons.add(btConfirm);
        buttons.add(btCancel);
        dialog.add(buttons, BorderLayout.PAGE_END);

        dialog.getRootPane().setDefaultButton(btConfirm);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);

        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        return out[0];
    }

    private static JLabel centered(String text, Font font, Color color) {
        JLabel l = new JLabel(text);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        if (font != null)  l.setFont(font);
        if (color != null) l.setForeground(color);
        return l;
    }

    private QrPaymentDialog() { }
}
