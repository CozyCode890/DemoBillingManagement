package billing.ui;

import billing.dao.InvoiceDao;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;

/**
 * ====================================================================
 *  EditLineDialog -- Hộp thoại Chỉnh sửa Dòng hàng trong Hóa đơn
 * ====================================================================
 *  Cho phép sửa thông tin một dòng hàng (Invoice_Line) trong DB:
 *  Barcode, Quantity, Unit_Price, Discount.
 *  Có cơ chế kiểm tra và cảnh báo nếu người dùng cố ý xóa các trường
 *  có ràng buộc NOT NULL (Quantity, Unit_Price, Barcode).
 *
 *  <p>Ba số nhận vào là {@code BigDecimal}, không phải {@code String}: cột
 *  {@code DECIMAL} của MySQL về Java vốn là {@code BigDecimal} rồi, và ô nhập
 *  được điền sẵn bằng {@code Ui.qtyInput} / {@code Ui.moneyInput} nên người
 *  dùng thấy {@code 1.5} và {@code 158.000} thay vì {@code 1.500} và
 *  {@code 158000.00}.
 *
 *  <p>Đọc số đi qua {@code Ui.readQuantity} / {@code Ui.readMoney}: ba khối
 *  {@code try { new BigDecimal(...) } catch} của bản cũ biến mất, và dòng hàng
 *  hết đường nhận {@code 1.2345} để {@code DECIMAL(10,3)} âm thầm làm tròn
 *  thành {@code 1.235} (plan/14 lỗi #1, #23).
 *
 *  <p><b>Hai ô cạnh nhau có hai quy tắc đọc số khác nhau</b> (plan/14 mục 3.3),
 *  nên mỗi ô có tooltip nêu đúng quy tắc của nó -- để sự khác biệt đó là một
 *  điều được nói ra, không phải một cái bẫy.
 */
public class EditLineDialog extends JDialog {

    private final String invoiceId;
    private final int lineNumber;
    private final InvoiceDao dao;
    private boolean saved = false;

    private final JTextField tfBarcode   = new JTextField(15);
    private final JTextField tfQuantity  = new JTextField(15);
    private final JTextField tfUnitPrice = new JTextField(15);
    private final JTextField tfDiscount  = new JTextField(15);

    public EditLineDialog(Window owner, InvoiceDao dao, String invoiceId, int lineNumber,
                          String barcode, BigDecimal quantity,
                          BigDecimal unitPrice, BigDecimal discount) {
        super(owner, I18n.msg("Sửa dòng hàng: ", "Edit Line: ") + lineNumber
                + I18n.msg(" (hóa đơn ", " (invoice ") + invoiceId + ")", ModalityType.APPLICATION_MODAL);
        this.dao = dao;
        this.invoiceId = invoiceId;
        this.lineNumber = lineNumber;

        setLayout(new BorderLayout(10, 10));
        setResizable(false);

        // Gán dữ liệu ban đầu.
        // Số lượng: KHÔNG dấu nghìn (ô nhập hiểu '.' là dấu thập phân).
        // Tiền: CÓ dấu nghìn (ô nhập hiểu '.' là dấu tách nghìn).
        tfBarcode.setText(barcode != null ? barcode : "");
        tfQuantity.setText(quantity == null ? "1" : Ui.qtyInput(quantity));
        tfUnitPrice.setText(unitPrice == null ? "0" : Ui.moneyInput(unitPrice));
        tfDiscount.setText(discount == null ? "0" : Ui.moneyInput(discount));

        tfQuantity.setToolTipText(I18n.msg(
                "<html>Dấu <b>,</b> hoặc <b>.</b> là dấu THẬP PHÂN, tối đa 3 chữ số.<br>"
                + "Đúng: <code>2</code> | <code>1,5</code> | <code>1.5</code></html>",
                "<html><b>,</b> or <b>.</b> is the DECIMAL point, up to 3 digits.<br>"
                + "Valid: <code>2</code> | <code>1,5</code> | <code>1.5</code></html>"));
        String moneyTip = I18n.msg(
                "<html>Dấu <b>.</b> là dấu TÁCH NGHÌN: <code>158.000</code> = 158 nghìn.<br>"
                + "Dấu <b>,</b> là dấu thập phân (tối đa 2 chữ số).</html>",
                "<html><b>.</b> separates THOUSANDS: <code>158.000</code> = 158 thousand.<br>"
                + "<b>,</b> is the decimal point (up to 2 digits).</html>");
        tfUnitPrice.setToolTipText(moneyTip);
        tfDiscount.setToolTipText(moneyTip);

        // Form nhập liệu
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(15, 20, 10, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // Mã vạch
        addFormRow(form, gbc, row++, I18n.msg("Mã vạch", "Barcode") + " *:", tfBarcode);
        // Số lượng
        addFormRow(form, gbc, row++, I18n.msg("Số lượng", "Quantity") + " *:", tfQuantity);
        // Đơn giá
        addFormRow(form, gbc, row++, I18n.msg("Đơn giá", "Unit price") + " *:", tfUnitPrice);
        // Giảm giá
        addFormRow(form, gbc, row++, I18n.msg("Giảm giá", "Discount") + " *:", tfDiscount);

        // Dòng chú thích
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3;
        JLabel lbNote = new JLabel(I18n.msg(
                "<html><i style='color:#c0392b;'>* Các ô đánh dấu (*) bắt buộc phải có giá trị.</i></html>",
                "<html><i style='color:#c0392b;'>* Fields marked (*) are required.</i></html>"));
        form.add(lbNote, gbc);

        // Nút Lưu / Hủy
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton btSave = new JButton(I18n.msg("Lưu", "Save"));
        JButton btCancel = new JButton(I18n.msg("Hủy bỏ", "Cancel"));

        btSave.setFont(Ui.BOLD);
        btSave.addActionListener(e -> doSave());
        btCancel.addActionListener(e -> dispose());

        buttons.add(btSave);
        buttons.add(btCancel);

        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
    }

    // Nút "Thu xoa (Set NULL)" đã bỏ (note của nhóm): nó chỉ minh họa ràng
    // buộc NOT NULL của database, nhân viên không cần.
    private void addFormRow(JPanel form, GridBagConstraints gbc, int row,
                            String label, JTextField field) {
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
        form.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        form.add(field, gbc);
    }

    private void doSave() {
        String barcode = tfBarcode.getText().trim();
        if (barcode.isEmpty()) {
            showNotNullWarning(I18n.msg("Mã vạch", "Barcode"));
            tfBarcode.requestFocus();
            return;
        }

        String qtyStr = tfQuantity.getText().trim();
        if (qtyStr.isEmpty()) {
            showNotNullWarning(I18n.msg("Số lượng", "Quantity"));
            tfQuantity.requestFocus();
            return;
        }

        String priceStr = tfUnitPrice.getText().trim();
        if (priceStr.isEmpty()) {
            showNotNullWarning(I18n.msg("Đơn giá", "Unit price"));
            tfUnitPrice.requestFocus();
            return;
        }

        String discStr = tfDiscount.getText().trim();
        if (discStr.isEmpty()) {
            showNotNullWarning(I18n.msg("Giảm giá", "Discount"));
            tfDiscount.requestFocus();
            return;
        }

        // Ba khối try { new BigDecimal(...) } catch của bản cũ biến mất.
        // Ui.read* tự hiện thông báo nêu rõ quy tắc đọc số của từng ô, nên chỗ
        // này chỉ cần "null thì dừng" (plan/14 mục 5.10).
        //
        // Số lượng: không trần (max = null) -- đây là sửa trực tiếp database,
        // không phải trả hàng.
        BigDecimal quantity = Ui.readQuantity(this, qtyStr, null, "");
        if (quantity == null) return;

        // Đơn giá và giảm giá NHẬN số 0: Discount = 0.00 là giá trị bình thường
        // nhất của cột đó.
        BigDecimal unitPrice = Ui.readMoneyAllowZero(this, priceStr, I18n.msg("Đơn giá", "Unit price"));
        if (unitPrice == null) return;

        BigDecimal discount = Ui.readMoneyAllowZero(this, discStr, I18n.msg("Giảm giá", "Discount"));
        if (discount == null) return;

        try {
            dao.updateInvoiceLine(invoiceId, lineNumber, barcode, quantity, unitPrice, discount);
            JOptionPane.showMessageDialog(this,
                    I18n.msg("Cập nhật dòng hàng " + lineNumber + " thành công!",
                             "Line " + lineNumber + " updated successfully!"),
                    I18n.msg("Thành công", "Success"), JOptionPane.INFORMATION_MESSAGE);
            saved = true;
            dispose();
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    private void showNotNullWarning(String fieldName) {
        JOptionPane.showMessageDialog(this,
                I18n.msg("Ô '" + fieldName + "' bắt buộc phải có giá trị, không được để trống!",
                         "Please fill in '" + fieldName + "', it cannot be empty!"),
                I18n.msg("Thiếu thông tin", "Missing Information"), JOptionPane.WARNING_MESSAGE);
    }

    public boolean isSaved() {
        return saved;
    }
}
