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
                          String barcode, String quantity, String unitPrice, String discount) {
        super(owner, "Chinh sua Dong hang: " + lineNumber + " (HD: " + invoiceId + ")", ModalityType.APPLICATION_MODAL);
        this.dao = dao;
        this.invoiceId = invoiceId;
        this.lineNumber = lineNumber;

        setLayout(new BorderLayout(10, 10));
        setResizable(false);

        // Gán dữ liệu ban đầu
        tfBarcode.setText(barcode != null ? barcode : "");
        tfQuantity.setText(quantity != null ? quantity : "1");
        tfUnitPrice.setText(unitPrice != null ? unitPrice : "0");
        tfDiscount.setText(discount != null ? discount : "0");

        // Form nhập liệu
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(15, 20, 10, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // Mã vạch
        addFormRow(form, gbc, row++, "Ma vach (Barcode) *:", tfBarcode, () -> trySetNull("Ma vach (Barcode)", tfBarcode));
        // Số lượng
        addFormRow(form, gbc, row++, "So luong (Quantity) *:", tfQuantity, () -> trySetNull("So luong (Quantity)", tfQuantity));
        // Đơn giá
        addFormRow(form, gbc, row++, "Don gia (Unit_Price) *:", tfUnitPrice, () -> trySetNull("Don gia (Unit_Price)", tfUnitPrice));
        // Giảm giá
        addFormRow(form, gbc, row++, "Giam gia (Discount) *:", tfDiscount, () -> trySetNull("Giam gia (Discount)", tfDiscount));

        // Dòng chú thích
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3;
        JLabel lbNote = new JLabel("<html><i style='color:#c0392b;'>* Toan bo cac cot trong Invoice_Line deu co rang buoc NOT NULL.<br>Nut 'Thu xoa (Set NULL)' se minh hoa co che canh bao cua Database.</i></html>");
        form.add(lbNote, gbc);

        // Nút Lưu / Hủy
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton btSave = new JButton("Luu vao Database");
        JButton btCancel = new JButton("Huy bo");

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

    private void addFormRow(JPanel form, GridBagConstraints gbc, int row,
                            String label, JTextField field, Runnable onTrySetNull) {
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
        form.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        form.add(field, gbc);

        JButton btTryNull = new JButton("Thu xoa (Set NULL)");
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
            showNotNullWarning("So luong (Quantity)");
            tfQuantity.requestFocus();
            return;
        }

        String priceStr = tfUnitPrice.getText().trim();
        if (priceStr.isEmpty()) {
            showNotNullWarning("Don gia (Unit_Price)");
            tfUnitPrice.requestFocus();
            return;
        }

        String discStr = tfDiscount.getText().trim();
        if (discStr.isEmpty()) {
            showNotNullWarning("Giam gia (Discount)");
            tfDiscount.requestFocus();
            return;
        }

        BigDecimal quantity;
        try {
            quantity = new BigDecimal(qtyStr);
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                JOptionPane.showMessageDialog(this, "So luong phai lon hon 0!", "Loi gia tri", JOptionPane.WARNING_MESSAGE);
                return;
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "So luong phai la so hop le!", "Loi gia tri", JOptionPane.WARNING_MESSAGE);
            return;
        }

        BigDecimal unitPrice;
        try {
            unitPrice = new BigDecimal(priceStr);
            if (unitPrice.compareTo(BigDecimal.ZERO) < 0) {
                JOptionPane.showMessageDialog(this, "Don gia khong duoc am!", "Loi gia tri", JOptionPane.WARNING_MESSAGE);
                return;
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Don gia phai la so hop le!", "Loi gia tri", JOptionPane.WARNING_MESSAGE);
            return;
        }

        BigDecimal discount;
        try {
            discount = new BigDecimal(discStr);
            if (discount.compareTo(BigDecimal.ZERO) < 0) {
                JOptionPane.showMessageDialog(this, "Giam gia khong duoc am!", "Loi gia tri", JOptionPane.WARNING_MESSAGE);
                return;
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Giam gia phai la so hop le!", "Loi gia tri", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            dao.updateInvoiceLine(invoiceId, lineNumber, barcode, quantity, unitPrice, discount);
            JOptionPane.showMessageDialog(this,
                    "Cap nhat dong hang " + lineNumber + " thanh cong trong Database!",
                    "Thanh cong", JOptionPane.INFORMATION_MESSAGE);
            saved = true;
            dispose();
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    private void showNotNullWarning(String fieldName) {
        JOptionPane.showMessageDialog(this,
                "CANH BAO DATABASE: Cot '" + fieldName + "' co rang buoc NOT NULL trong Database!\n"
              + "Ban khong duoc phep xoa gia tri nay ve NULL hoac de trong.",
                "Canh bao rang buoc NOT NULL", JOptionPane.WARNING_MESSAGE);
    }

    public boolean isSaved() {
        return saved;
    }
}
