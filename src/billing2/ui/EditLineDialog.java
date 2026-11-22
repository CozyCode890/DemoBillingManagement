package billing2.ui;

import billing2.dao.InvoiceDao;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;

/** Sửa một dòng hàng (chỉ Quản lý): mã vạch, số lượng, đơn giá, giảm giá -> sp_update_invoice_line. */
public class EditLineDialog extends JDialog {

    private final JTextField tfBarcode  = new JTextField(15);
    private final JTextField tfQuantity = new JTextField(15);
    private final JTextField tfPrice    = new JTextField(15);
    private final JTextField tfDiscount = new JTextField(15);
    private boolean saved;

    /** @param f một hàng của sp_get_invoice_line_edit_fields: Barcode, Quantity, Unit_Price, Discount */
    public EditLineDialog(Window owner, InvoiceDao dao, String invoiceId, int lineNumber, Object[] f) {
        super(owner, "Edit Line " + lineNumber + " (invoice " + invoiceId + ")", ModalityType.APPLICATION_MODAL);
        tfBarcode.setText(f[0] == null ? "" : f[0].toString());
        tfQuantity.setText(Ui.qtyInput(Ui.dec(f[1])));
        tfPrice.setText(Ui.moneyInput(Ui.dec(f[2])));
        tfDiscount.setText(Ui.moneyInput(Ui.dec(f[3])));
        tfQuantity.setToolTipText("<html><b>,</b> or <b>.</b> is the DECIMAL point, up to 3 digits.</html>");
        tfPrice.setToolTipText("<html><b>.</b> separates THOUSANDS: <code>158.000</code> = 158 thousand.</html>");
        tfDiscount.setToolTipText(tfPrice.getToolTipText());

        JPanel form = Ui.form("Barcode *:", tfBarcode, "Quantity *:", tfQuantity,
                              "Unit price *:", tfPrice, "Discount *:", tfDiscount);
        form.setBorder(BorderFactory.createEmptyBorder(12, 16, 4, 16));
        JButton btSave = new JButton("Save");
        btSave.setFont(Ui.BOLD);
        btSave.addActionListener(e -> save(dao, invoiceId, lineNumber));
        JButton btCancel = new JButton("Cancel");
        btCancel.addActionListener(e -> dispose());

        add(form, BorderLayout.CENTER);
        add(Ui.requiredButtons(btSave, btCancel), BorderLayout.SOUTH);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    public boolean isSaved() { return saved; }

    private void save(InvoiceDao dao, String invoiceId, int lineNumber) {
        if (Ui.missing(this, tfBarcode, "Barcode") || Ui.missing(this, tfQuantity, "Quantity")
                || Ui.missing(this, tfPrice, "Unit price") || Ui.missing(this, tfDiscount, "Discount")) return;
        BigDecimal quantity  = Ui.readQuantity(this, tfQuantity.getText(), null, "");
        if (quantity == null) return;
        BigDecimal unitPrice = Ui.readMoneyAllowZero(this, tfPrice.getText(), "Unit price");
        if (unitPrice == null) return;
        BigDecimal discount  = Ui.readMoneyAllowZero(this, tfDiscount.getText(), "Discount");
        if (discount == null) return;
        try {
            dao.updateInvoiceLine(invoiceId, lineNumber, tfBarcode.getText().trim(), quantity, unitPrice, discount);
            JOptionPane.showMessageDialog(this, "Line " + lineNumber + " updated successfully!", "Success",
                    JOptionPane.INFORMATION_MESSAGE);
            saved = true;
            dispose();
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }
}
