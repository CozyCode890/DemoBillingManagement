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
        super(owner, I18n.richText("Chỉnh sửa Dòng hàng: ") + lineNumber + " (HD: " + invoiceId + ")", ModalityType.APPLICATION_MODAL);
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

        tfQuantity.setToolTipText("<html>Dấu <b>,</b> hoặc <b>.</b> là dấu THẬP PHÂN,"
                + " tối đa 3 chữ số (cột DECIMAL(10,3)).<br>"
                + "Đúng: <code>2</code> | <code>1,5</code> | <code>1.5</code></html>");
        String moneyTip = "<html>Dấu <b>.</b> là dấu TÁCH NGHÌN: <code>158.000</code>"
                + " = 158 nghìn.<br>Dấu <b>,</b> là dấu thập phân (tối đa 2 chữ số).</html>";
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
        addFormRow(form, gbc, row++, "Mã vạch (Barcode) *:", tfBarcode, () -> trySetNull("Ma vach (Barcode)", tfBarcode));
        // Số lượng
        addFormRow(form, gbc, row++, "Số lượng (Quantity) *:", tfQuantity, () -> trySetNull("Số lượng (Quantity)", tfQuantity));
        // Đơn giá
        addFormRow(form, gbc, row++, "Đơn giá (Unit_Price) *:", tfUnitPrice, () -> trySetNull("Đơn giá (Unit_Price)", tfUnitPrice));
        // Giảm giá
        addFormRow(form, gbc, row++, "Giảm giá (Discount) *:", tfDiscount, () -> trySetNull("Giảm giá (Discount)", tfDiscount));

        // Dòng chú thích
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3;
        JLabel lbNote = new JLabel("<html><i style='color:#c0392b;'>* Toàn bộ các cột trong Invoice_Line đều có ràng buộc NOT NULL.<br>Nút 'Thử xóa (Set NULL)' sẽ minh họa cơ chế cảnh báo của Database.</i></html>");
        form.add(lbNote, gbc);

        // Nút Lưu / Hủy
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton btSave = new JButton("Lưu vào Database");
        JButton btCancel = new JButton("Hủy bỏ");

        btSave.setFont(Ui.BOLD);
        btSave.addActionListener(e -> doSave());
        btCancel.addActionListener(e -> dispose());

        buttons.add(btSave);
        buttons.add(btCancel);

        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        I18n.refreshTree(this);
        pack();
        setLocationRelativeTo(owner);
    }

    private void addFormRow(JPanel form, GridBagConstraints gbc, int row,
                            String label, JTextField field, Runnable onTrySetNull) {
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
        form.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        form.add(field, gbc);

        JButton btTryNull = new JButton("Thử xóa (Set NULL)");
        btTryNull.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btTryNull.addActionListener(e -> onTrySetNull.run());
        gbc.gridx = 2;
        form.add(btTryNull, gbc);
    }

    /** Minh họa hành động cố ý xóa trường NOT NULL về rỗng/NULL */
    private void trySetNull(String fieldName, JTextField targetField) {
        targetField.setText("");
        showNotNullWarning(fieldName);
    }

    private void doSave() {
        String barcode = tfBarcode.getText().trim();
        if (barcode.isEmpty()) {
            showNotNullWarning("Ma vach (Barcode)");
            tfBarcode.requestFocus();
            return;
        }

        String qtyStr = tfQuantity.getText().trim();
        if (qtyStr.isEmpty()) {
            showNotNullWarning("Số lượng (Quantity)");
            tfQuantity.requestFocus();
            return;
        }

        String priceStr = tfUnitPrice.getText().trim();
        if (priceStr.isEmpty()) {
            showNotNullWarning("Đơn giá (Unit_Price)");
            tfUnitPrice.requestFocus();
            return;
        }

        String discStr = tfDiscount.getText().trim();
        if (discStr.isEmpty()) {
            showNotNullWarning("Giảm giá (Discount)");
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
        BigDecimal unitPrice = Ui.readMoneyAllowZero(this, priceStr, "Đơn giá (Unit_Price)");
        if (unitPrice == null) return;

        BigDecimal discount = Ui.readMoneyAllowZero(this, discStr, "Giảm giá (Discount)");
        if (discount == null) return;

        try {
            dao.updateInvoiceLine(invoiceId, lineNumber, barcode, quantity, unitPrice, discount);
            I18n.showMessageDialog(this,
                    I18n.richText("Cập nhật dòng hàng ") + lineNumber + I18n.text(" thành công trong Database!"),
                    I18n.text("Thành công"), JOptionPane.INFORMATION_MESSAGE);
            saved = true;
            dispose();
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    private void showNotNullWarning(String fieldName) {
        I18n.showMessageDialog(this,
                I18n.richText("CẢNH BÁO DATABASE: Cột '") + fieldName + I18n.richText("' có ràng buộc NOT NULL trong Database!\n"
              + "Bạn không được phép xóa giá trị này về NULL hoặc để trống."),
                I18n.text("Cảnh báo ràng buộc NOT NULL"), JOptionPane.WARNING_MESSAGE);
    }

    public boolean isSaved() {
        return saved;
    }
}
