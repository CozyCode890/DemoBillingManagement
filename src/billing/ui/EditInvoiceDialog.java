package billing.ui;

import billing.dao.InvoiceDao;

import javax.swing.*;
import java.awt.*;

/**
 * Đổi khách hàng của một hóa đơn (BR10) -> InvoiceDao.updateInvoiceCustomer.
 * Mọi trường khác CHỈ ĐỌC: ngày giờ theo server (BR2), quầy / thu ngân gắn cứng (BR4),
 * trạng thái chỉ đổi qua "Hủy hóa đơn" (BR3). SQL chặn lần nữa (sp_update_invoice_header,
 * trg_invoice_bu).
 */
public class EditInvoiceDialog extends JDialog {

    private final JTextField tfCustomer = new JTextField(15);
    private boolean saved;

    /** @param f một hàng của sp_get_invoice_edit_fields: Ma_HD, Ngay, Gio, Trang_thai, Quay, Thu_ngan, Khach */
    public EditInvoiceDialog(Window owner, InvoiceDao dao, String invoiceId, Object[] f) {
        super(owner, I18n.msg("Sửa khách hàng của hóa đơn: ", "Edit Invoice Customer: ") + invoiceId,
              ModalityType.APPLICATION_MODAL);
        tfCustomer.setText(str(f[6]));
        tfCustomer.setToolTipText(I18n.text("Mã khách hàng, ví dụ KH001. Để trống = khách vãng lai.",
                                            "Customer ID, e.g. KH001. Leave empty for a walk-in customer."));
        JButton btWalkIn = new JButton(I18n.text("Đổi thành khách vãng lai", "Make walk-in customer"));
        btWalkIn.addActionListener(e -> tfCustomer.setText(""));

        JPanel form = Ui.form(I18n.text("Mã hóa đơn:", "Invoice ID:"), readOnly(invoiceId),
                I18n.text("Ngày:", "Date:"), readOnly(str(f[1])),
                I18n.text("Giờ:", "Time:"), readOnly(str(f[2])),
                I18n.text("Trạng thái:", "Status:"), readOnly(Ui.status(f[3])),
                I18n.text("Mã quầy:", "Counter ID:"), readOnly(str(f[4])),
                I18n.text("Mã thu ngân:", "Cashier ID:"), readOnly(str(f[5])),
                I18n.text("Mã khách hàng:", "Customer ID:"), Ui.row(tfCustomer, btWalkIn));
        form.setBorder(BorderFactory.createEmptyBorder(12, 16, 4, 16));

        JLabel note = new JLabel(I18n.text(
                "Chỉ đổi được khách hàng; các trường khác cố định từ khi hóa đơn đã thanh toán. ",
                "Only the customer can be changed; the other fields are fixed once the invoice is paid. "));
        note.setFont(note.getFont().deriveFont(Font.ITALIC));
        JButton btSave = new JButton(I18n.text("Lưu", "Save"));
        btSave.setFont(Ui.BOLD);
        btSave.addActionListener(e -> save(dao, invoiceId));
        JButton btCancel = new JButton(I18n.text("Hủy bỏ", "Cancel"));
        btCancel.addActionListener(e -> dispose());
        JPanel south = new JPanel(new BorderLayout());
        south.setBorder(BorderFactory.createEmptyBorder(0, 16, 10, 10));
        south.add(note, BorderLayout.NORTH);
        south.add(Ui.row(btSave, btCancel), BorderLayout.EAST);

        add(form, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(btSave);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    public boolean isSaved() { return saved; }

    private void save(InvoiceDao dao, String invoiceId) {
        String cus = tfCustomer.getText().trim();
        try {
            dao.updateInvoiceCustomer(invoiceId, cus);   // "" -> NULL = khách vãng lai; mã lạ -> SQL từ chối
            JOptionPane.showMessageDialog(this,
                    I18n.msg("Hóa đơn " + invoiceId + ": khách hàng = " + (cus.isEmpty() ? "khách vãng lai" : cus) + ".",
                             "Invoice " + invoiceId + ": customer set to " + (cus.isEmpty() ? "walk-in" : cus) + "."),
                    I18n.msg("Thành công", "Success"), JOptionPane.INFORMATION_MESSAGE);
            saved = true;
            dispose();
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    private static JTextField readOnly(String text) {
        JTextField t = new JTextField(text, 15);
        t.setEditable(false);
        return t;
    }

    private static String str(Object o) { return o == null ? "" : o.toString(); }
}
