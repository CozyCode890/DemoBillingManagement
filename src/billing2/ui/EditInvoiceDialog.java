package billing2.ui;

import billing2.dao.InvoiceDao;
import billing2.model.InvoiceStatus;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Sửa đầu hóa đơn (chỉ Quản lý): ngày, giờ, quầy, thu ngân, khách -> sp_update_invoice_header.
 * Trạng thái CHỈ ĐỌC: đổi trạng thái chỉ qua nút "Void invoice" hoặc trả hàng.
 */
public class EditInvoiceDialog extends JDialog {

    private final JTextField tfDate     = new JTextField(15);
    private final JTextField tfTime     = new JTextField(15);
    private final JTextField tfCounter  = new JTextField(15);
    private final JTextField tfCashier  = new JTextField(15);
    private final JTextField tfCustomer = new JTextField(15);
    private final JLabel     lbMoneyGap = new JLabel(" ");
    private boolean saved;

    /** @param f một hàng của sp_get_invoice_edit_fields: Ma_HD, Ngay, Gio, Trang_thai, Quay, Thu_ngan, Khach */
    public EditInvoiceDialog(Window owner, InvoiceDao dao, String invoiceId, Object[] f) {
        super(owner, "Edit Invoice: " + invoiceId, ModalityType.APPLICATION_MODAL);
        String status = str(f[3]);
        tfDate.setText(str(f[1]));
        tfTime.setText(str(f[2]));
        tfCounter.setText(str(f[4]));
        tfCashier.setText(str(f[5]));
        tfCustomer.setText(str(f[6]));

        JTextField tfId = new JTextField(invoiceId);
        JTextField tfStatus = new JTextField(String.valueOf(InvoiceStatus.of(status)));
        tfId.setEditable(false);
        tfStatus.setEditable(false);
        tfStatus.setToolTipText("The status changes only with \"Void invoice\" or a return in the Invoices tab");
        JButton btWalkIn = new JButton("Make walk-in customer");
        btWalkIn.addActionListener(e -> tfCustomer.setText(""));
        lbMoneyGap.setForeground(new Color(192, 57, 43));

        JPanel form = Ui.form("Invoice ID (cannot change):", tfId, "Date (YYYY-MM-DD) *:", tfDate,
                "Time (HH:MM:SS) *:", tfTime, "Status (read only):", tfStatus, "Counter ID *:", tfCounter,
                "Cashier ID *:", tfCashier, "Customer ID:", Ui.row(tfCustomer, btWalkIn), "", lbMoneyGap);
        form.setBorder(BorderFactory.createEmptyBorder(12, 16, 4, 16));

        JButton btSave = new JButton("Save");
        btSave.setFont(Ui.BOLD);
        btSave.addActionListener(e -> save(dao, invoiceId, status));
        JButton btCancel = new JButton("Cancel");
        btCancel.addActionListener(e -> dispose());

        add(form, BorderLayout.CENTER);
        add(Ui.requiredButtons(btSave, btCancel), BorderLayout.SOUTH);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);

        // Hóa đơn PAID mà tiền đã thu < tổng phải trả (do sửa dòng hàng): cảnh báo lệch tiền.
        if (InvoiceStatus.of(status) == InvoiceStatus.PAID) {
            Ui.async(this, () -> new BigDecimal[]{dao.amountDue(invoiceId), dao.amountPaid(invoiceId)}, null, m -> {
                if (m[1].compareTo(m[0]) < 0) {
                    lbMoneyGap.setText("<html><b>Paid " + Ui.money(m[1]) + " / " + Ui.money(m[0]) + "  --  SHORT BY "
                            + Ui.money(m[0].subtract(m[1])) + "</b><br>Editing the lines changed the total; "
                            + "the status does NOT change back.</html>");
                    pack();
                }
            });
        }
    }

    public boolean isSaved() { return saved; }

    private void save(InvoiceDao dao, String invoiceId, String status) {
        if (Ui.missing(this, tfDate, "Date") || Ui.missing(this, tfTime, "Time")
                || Ui.missing(this, tfCounter, "Counter ID") || Ui.missing(this, tfCashier, "Cashier ID")) return;
        LocalDate date;
        LocalTime time;
        try {
            date = LocalDate.parse(tfDate.getText().trim());
        } catch (Exception ex) {
            Ui.warn(this, "The date must be in YYYY-MM-DD format!", "Invalid Format");
            return;
        }
        try {
            time = LocalTime.parse(tfTime.getText().trim());
        } catch (Exception ex) {
            Ui.warn(this, "The time must be in HH:MM:SS format!", "Invalid Format");
            return;
        }
        try {
            // Khách để trống = khách vãng lai (Customer_ID NULL) -- DAO đổi "" thành NULL.
            dao.updateInvoiceHeader(invoiceId, date, time, status, tfCounter.getText().trim(),
                                    tfCashier.getText().trim(), tfCustomer.getText());
            JOptionPane.showMessageDialog(this, "Invoice " + invoiceId + " updated successfully!", "Success",
                    JOptionPane.INFORMATION_MESSAGE);
            saved = true;
            dispose();
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    private static String str(Object o) { return o == null ? "" : o.toString(); }
}
