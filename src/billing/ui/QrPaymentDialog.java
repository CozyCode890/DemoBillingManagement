package billing.ui;

import billing.util.AppConfig;
import billing.util.VietQRHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * ====================================================================
 *  QrPaymentDialog -- mã VietQR cho một số tiền, và nút xác nhận
 * ====================================================================
 *
 *  <p>Tách khỏi {@code NewInvoicePanel} ({@code handleQrPayment} +
 *  {@code showQrDialog}, 90 dòng) và sửa ba điều app đang nói không đúng:
 *
 *  <ul>
 *    <li><b>Tài khoản lấy từ cấu hình và CÓ TÁC DỤNG</b> (plan/14 lỗi #27).
 *        Bản cũ có ba lỗi xếp lên nhau: {@code getResourceAsStream} với
 *        classpath {@code out;lib\*} trả {@code null} nên {@code props.load}
 *        ném NPE; {@code config.properties} không có khoá {@code qr.*} nào; và
 *        ba field đọc được cũng không được truyền cho {@code VietQRHelper}.
 *        Giờ đọc qua {@link AppConfig} -- lớp khối 2 đã dựng đúng đường, đọc
 *        bằng {@code FileInputStream} nên KHÔNG phụ thuộc classpath.</li>
 *    <li><b>Số tiền không bị cắt phần lẻ im lặng:</b>
 *        {@code setScale(0, HALF_UP)} TRƯỚC {@code longValue()}. Bản cũ gọi
 *        {@code amount.longValue()} nên {@code 198.000,50} vào mã QR thành
 *        {@code 198000}.</li>
 *    <li><b>Nói thật rằng đối soát là thủ công.</b> Nút <i>Xác nhận</i> là
 *        người bấm, không phải cổng thanh toán -- hộp thoại ghi rõ điều đó.</li>
 *  </ul>
 *
 *  <p>Hộp thoại CÓ CHA ({@code getWindowAncestor}), khác
 *  {@code new JDialog((Frame) null, ...)} của bản cũ (lỗi #29).
 */
public class QrPaymentDialog {

    /** Kết quả: người dùng đã xác nhận nhận tiền, kèm mã giao dịch. */
    public static class Result {
        public final BigDecimal amount;
        public final String     transactionRef;

        Result(BigDecimal amount, String transactionRef) {
            this.amount         = amount;
            this.transactionRef = transactionRef;
        }
    }

    /** {@code qr.enabled=false} -> nút <i>Tao ma QR</i> ẩn hẳn, không mờ. */
    public static boolean isEnabled() {
        return AppConfig.getBoolean("qr.enabled", true);
    }

    /**
     * Hiện mã QR cho {@code amount}. Trả {@code null} nếu người dùng huỷ.
     *
     * @param amount số tiền cần thu, đã được chỗ gọi kiểm {@code > 0}
     */
    public static Result show(Component parent, BigDecimal amount) {
        String bin     = AppConfig.get("qr.bank.bin",     VietQRHelper.DEFAULT_BIN);
        String account = AppConfig.get("qr.account.no",   VietQRHelper.DEFAULT_ACCOUNT);
        String holder  = AppConfig.get("qr.account.name", "");

        // setScale TRUOC longValue: con so trong ma QR phai bang con so hien
        // tren hop thoai, va bang con so se ghi vao Payment.
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
        JDialog dialog = new JDialog(owner, "Thanh toan bang ma QR (VietQR)",
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

        info.add(centered("So tien trong ma QR: " + Ui.money(rounded), Ui.BOLD, null));
        if (!holder.isEmpty()) {
            info.add(centered("Chuyen toi: " + holder, null, null));
        }
        info.add(centered("Tai khoan: " + account + "  (BIN " + bin + ")", null, Color.DARK_GRAY));
        info.add(centered("Noi dung CK (Transaction_Ref): " + ref, null, Color.DARK_GRAY));
        info.add(Box.createRigidArea(new Dimension(0, 6)));
        info.add(centered("Ban demo: viec doi soat la THU CONG -- bam \"Xac nhan\" khi da thay tien vao.",
                          new Font("Segoe UI", Font.ITALIC, 12), Color.GRAY));
        dialog.add(info, BorderLayout.SOUTH);

        // Mảng 1 phần tử: hàm trong lambda không gán được biến địa phương.
        final Result[] out = new Result[1];

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton btConfirm = new JButton("Xac nhan da nhan tien");
        JButton btCancel  = new JButton("Huy bo");
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
