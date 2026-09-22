package billing.ui;

import billing.dao.InvoiceDao;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * ====================================================================
 *  EditInvoiceDialog -- Hộp thoại Chỉnh sửa thông tin Hóa đơn
 * ====================================================================
 *  Cho phép sửa thông tin phần đầu hóa đơn (Header) trực tiếp trong DB.
 *  Có cơ chế kiểm tra và cảnh báo nếu người dùng cố ý xóa các trường
 *  có ràng buộc NOT NULL (Date, Time, Status, Counter_ID, Cashier_ID).
 */
public class EditInvoiceDialog extends JDialog {

    private final String invoiceId;
    private final InvoiceDao dao;
    private boolean saved = false;

    private final JTextField tfInvoiceId  = new JTextField(15);
    private final JTextField tfDate       = new JTextField(15);
    private final JTextField tfTime       = new JTextField(15);
    private final JComboBox<String> cbStatus = new JComboBox<>(new String[]{"OPEN", "PAID", "VOIDED"});
    private final JTextField tfCounterId  = new JTextField(15);
    private final JTextField tfCashierId  = new JTextField(15);
    private final JTextField tfCustomerId = new JTextField(15);

    public EditInvoiceDialog(Window owner, InvoiceDao dao, String invoiceId,
                             String date, String time, String status,
                             String counterId, String cashierId, String customerId) {
        super(owner, "Chinh sua Hoa don: " + invoiceId, ModalityType.APPLICATION_MODAL);
        this.dao = dao;
        this.invoiceId = invoiceId;

        setLayout(new BorderLayout(10, 10));
        setResizable(false);

        // Gán dữ liệu ban đầu
        tfInvoiceId.setText(invoiceId);
        tfInvoiceId.setEditable(false); // Khóa chính không sửa
        tfDate.setText(date != null ? date : LocalDate.now().toString());
        tfTime.setText(time != null ? time : LocalTime.now().withNano(0).toString());
        if (status != null) cbStatus.setSelectedItem(status);
        tfCounterId.setText(counterId != null ? counterId : "");
        tfCashierId.setText(cashierId != null ? cashierId : "");
        tfCustomerId.setText(customerId != null ? customerId : "");

        // Form nhập liệu
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(15, 20, 10, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        addFormRow(form, gbc, row++, "Ma hoa don (PK):", tfInvoiceId, "(Khong the thay doi)");
        addFormRow(form, gbc, row++, "Ngay lap (YYYY-MM-DD) *:", tfDate, "(NOT NULL)");
        addFormRow(form, gbc, row++, "Gio lap (HH:MM:SS) *:", tfTime, "(NOT NULL)");
        addFormRow(form, gbc, row++, "Trang thai *:", cbStatus, "(NOT NULL)");
        addFormRow(form, gbc, row++, "Ma quay (Counter_ID) *:", tfCounterId, "(NOT NULL)");
        addFormRow(form, gbc, row++, "Ma thu ngan (Cashier_ID) *:", tfCashierId, "(NOT NULL)");

        // Dòng Customer_ID kèm nút Xóa mã khách (Set NULL)
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
        form.add(new JLabel("Ma khach hang:"), gbc);
        gbc.gridx = 1;
        form.add(tfCustomerId, gbc);

        JButton btSetNullCustomer = new JButton("Xoa ma khach (Set NULL)");
        btSetNullCustomer.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btSetNullCustomer.addActionListener(e -> {
            tfCustomerId.setText("");
            JOptionPane.showMessageDialog(this,
                    "Customer_ID la truong cho phep NULL (khach vang lai).\nGia tri nay se duoc cap nhat thanh NULL trong database.",
                    "Thong bao", JOptionPane.INFORMATION_MESSAGE);
        });
        gbc.gridx = 2;
        form.add(btSetNullCustomer, gbc);
        row++;

        // Chú thích
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3;
        JLabel lbNote = new JLabel("<html><i style='color:#c0392b;'>* Cac truong danh dau (*) co rang buoc NOT NULL. Neu xoa trang se bi he thong canh bao!</i></html>");
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
                            String label, JComponent comp, String hint) {
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
        form.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        form.add(comp, gbc);
        gbc.gridx = 2;
        JLabel lbHint = new JLabel(hint);
        lbHint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lbHint.setForeground(Color.GRAY);
        form.add(lbHint, gbc);
    }

    private void doSave() {
        // --- KIỂM TRA RÀNG BUỘC NOT NULL ---
        String dateStr = tfDate.getText().trim();
        if (dateStr.isEmpty()) {
            showNotNullWarning("Ngay lap (Date)");
            tfDate.requestFocus();
            return;
        }

        String timeStr = tfTime.getText().trim();
        if (timeStr.isEmpty()) {
            showNotNullWarning("Gio lap (Time)");
            tfTime.requestFocus();
            return;
        }

        String counterId = tfCounterId.getText().trim();
        if (counterId.isEmpty()) {
            showNotNullWarning("Ma quay (Counter_ID)");
            tfCounterId.requestFocus();
            return;
        }

        String cashierId = tfCashierId.getText().trim();
        if (cashierId.isEmpty()) {
            showNotNullWarning("Ma thu ngan (Cashier_ID)");
            tfCashierId.requestFocus();
            return;
        }

        LocalDate date;
        try {
            date = LocalDate.parse(dateStr);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Ngay lap phai dung dinh dang YYYY-MM-DD!", "Loi dinh dang", JOptionPane.WARNING_MESSAGE);
            return;
        }

        LocalTime time;
        try {
            time = LocalTime.parse(timeStr);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Gio lap phai dung dinh dang HH:MM:SS!", "Loi dinh dang", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String status = String.valueOf(cbStatus.getSelectedItem());
        String customerId = tfCustomerId.getText().trim();
        if (customerId.isEmpty()) {
            customerId = null; // Customer_ID cho phép NULL (khách vãng lai)
        }

        try {
            dao.updateInvoiceHeader(invoiceId, date, time, status, counterId, cashierId, customerId);
            JOptionPane.showMessageDialog(this,
                    "Cap nhat hoa don " + invoiceId + " thanh cong trong Database!",
                    "Thanh cong", JOptionPane.INFORMATION_MESSAGE);
            saved = true;
            dispose();
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    /** Bật cảnh báo vi phạm ràng buộc NOT NULL */
    private void showNotNullWarning(String fieldName) {
        JOptionPane.showMessageDialog(this,
                "CANH BAO DATABASE: Truong '" + fieldName + "' co rang buoc NOT NULL!\n"
              + "Ban khong duoc phep xoa gia tri nay ve NULL hoac de trong.",
                "Canh bao rang buoc NOT NULL", JOptionPane.WARNING_MESSAGE);
    }

    public boolean isSaved() {
        return saved;
    }
}
