package billing.ui;

import billing.dao.InvoiceDao;
import billing.model.InvoiceStatus;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * ====================================================================
 *  EditInvoiceDialog -- Hộp thoại Chỉnh sửa thông tin Hóa đơn
 * ====================================================================
 *  Cho phép sửa thông tin phần đầu hóa đơn (Header) trực tiếp trong DB.
 *  Có cơ chế kiểm tra và cảnh báo nếu người dùng cố ý xóa các trường
 *  có ràng buộc NOT NULL (Date, Time, Counter_ID, Cashier_ID).
 *
 *  Ô TRẠNG THÁI LÀ CHỈ ĐỌC từ khối 4: không phải vì phân quyền, mà vì không
 *  ai -- kể cả quản lý -- gõ ra được một trạng thái đúng. PAID do
 *  sp_settle_invoice quyết định khi tiền đã vào đủ, VOIDED do nút
 *  "Huy hoa don" (plan/13 mục 2.2). Hộp thoại này truyền xuống đúng cái
 *  trạng thái nó đọc lên, và sp_update_invoice_header dùng nó làm phép kiểm.
 */
public class EditInvoiceDialog extends JDialog {

    private final String invoiceId;
    private final InvoiceDao dao;
    private boolean saved = false;

    private final JTextField tfInvoiceId  = new JTextField(15);
    private final JTextField tfDate       = new JTextField(15);
    private final JTextField tfTime       = new JTextField(15);
    // Bộ giá trị nạp từ InvoiceStatus -- không còn mảng String[] ghi cứng.
    private final JComboBox<InvoiceStatus> cbStatus =
            new JComboBox<>(InvoiceStatus.values());
    private final JTextField tfCounterId  = new JTextField(15);
    private final JTextField tfCashierId  = new JTextField(15);
    private final JTextField tfCustomerId = new JTextField(15);

    /** Dòng cảnh báo lệch tiền -- chỉ hiện khi đã thu < phải trả (plan/13 lỗi #4). */
    private final JLabel lbMoneyGap = new JLabel(" ");

    /**
     * Trạng thái THÔ đọc từ database. Truyền xuống nguyên văn, kể cả khi
     * InvoiceStatus.of() không nhận ra nó: thủ tục so sánh với cột Status,
     * nên gửi lại đúng chuỗi đã đọc là cách duy nhất chắc chắn không bị từ chối.
     */
    private final String originalStatus;

    public EditInvoiceDialog(Window owner, InvoiceDao dao, String invoiceId,
                             String date, String time, String status,
                             String counterId, String cashierId, String customerId) {
        super(owner, I18n.msg("Sửa hóa đơn: ", "Edit Invoice: ") + invoiceId, ModalityType.APPLICATION_MODAL);
        this.dao = dao;
        this.invoiceId = invoiceId;
        this.originalStatus = status;

        setLayout(new BorderLayout(10, 10));
        setResizable(false);

        // Gán dữ liệu ban đầu
        tfInvoiceId.setText(invoiceId);
        tfInvoiceId.setEditable(false); // Khóa chính không sửa
        tfDate.setText(date != null ? date : LocalDate.now().toString());
        tfTime.setText(time != null ? time : LocalTime.now().withNano(0).toString());
        InvoiceStatus st = InvoiceStatus.of(status);
        if (st != null) cbStatus.setSelectedItem(st);
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
        addFormRow(form, gbc, row++, I18n.msg("Mã hóa đơn:", "Invoice ID:"), tfInvoiceId,
                   I18n.msg("(không thể thay đổi)", "(cannot be changed)"));
        addFormRow(form, gbc, row++, I18n.msg("Ngày lập (YYYY-MM-DD) *:", "Date (YYYY-MM-DD) *:"), tfDate, I18n.msg("(bắt buộc)", "(required)"));
        addFormRow(form, gbc, row++, I18n.msg("Giờ lập (HH:MM:SS) *:", "Time (HH:MM:SS) *:"), tfTime, I18n.msg("(bắt buộc)", "(required)"));
        // Trạng thái KHÔNG sửa bằng tay: không phải vì phân quyền, mà vì không
        // ai -- kể cả quản lý -- gõ ra được một trạng thái đúng (plan/13 mục 2.2).
        // Nhãn bỏ dấu '*' và "(NOT NULL)": chú thích đỏ ở dưới nói về các
        // trường BỊ CHẶN KHI ĐỂ TRỐNG, không còn đúng cho ô này.
        cbStatus.setEnabled(false);
        cbStatus.setToolTipText(I18n.msg("Trạng thái đổi tự động khi thu đủ tiền, "
                + "hoặc qua nút \"Hủy hóa đơn\" ở tab Xem hóa đơn",
                "The status changes automatically when fully paid, "
                + "or with the \"Void invoice\" button in the Invoices tab"));
        addFormRow(form, gbc, row++, I18n.msg("Trạng thái:", "Status:"), cbStatus,
                   I18n.msg("(chỉ đọc)", "(read only)"));

        // Dòng lệch tiền: trống (và vô hình) cho tới khi đọc xong hai số tiền.
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3;
        lbMoneyGap.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lbMoneyGap.setForeground(new Color(192, 57, 43));
        form.add(lbMoneyGap, gbc);
        gbc.gridwidth = 1;
        row++;

        addFormRow(form, gbc, row++, I18n.msg("Mã quầy *:", "Counter ID *:"), tfCounterId, I18n.msg("(bắt buộc)", "(required)"));
        addFormRow(form, gbc, row++, I18n.msg("Mã thu ngân *:", "Cashier ID *:"), tfCashierId, I18n.msg("(bắt buộc)", "(required)"));

        // Dòng Customer_ID kèm nút Xóa mã khách (Set NULL)
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
        form.add(new JLabel(I18n.msg("Mã khách hàng:", "Customer ID:")), gbc);
        gbc.gridx = 1;
        form.add(tfCustomerId, gbc);

        // Xoa ma khach = hoa don cua khach vang lai (Customer_ID ve NULL khi luu).
        JButton btSetNullCustomer = new JButton(I18n.msg("Đổi thành khách vãng lai", "Make walk-in customer"));
        btSetNullCustomer.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btSetNullCustomer.addActionListener(e -> tfCustomerId.setText(""));
        gbc.gridx = 2;
        form.add(btSetNullCustomer, gbc);
        row++;

        // Chú thích
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

        loadMoneyGap();
    }

    /**
     * Đọc "đã thu" và "phải trả" rồi cảnh báo nếu lệch.
     *
     * Đây là dấu vết nhìn thấy được của lỗi #4 (plan/13): quản lý sửa dòng hàng
     * làm tổng phải trả tăng, Payment giữ nguyên, trạng thái vẫn PAID. Khối 4
     * CỐ Ý không tự đổi trạng thái của hóa đơn đã thu tiền (mục 3.2) nên chỗ
     * duy nhất làm được gì là nói cho người dùng biết.
     *
     * CHỈ cảnh báo trên hóa đơn PAID: hóa đơn VOIDED đã ra khỏi vòng đời
     * thanh toán (trạng thái OPEN cũ không còn nữa).
     *
     * Hai lời gọi DAO này đi ra database, nên chạy NGOÀI EDT: dựng form xong
     * là hộp thoại hiện ra ngay, dòng cảnh báo điền sau.
     */
    private void loadMoneyGap() {
        if (InvoiceStatus.of(originalStatus) != InvoiceStatus.PAID) return;

        new SwingWorker<BigDecimal[], Void>() {
            @Override
            protected BigDecimal[] doInBackground() {
                return new BigDecimal[]{ dao.amountDue(invoiceId), dao.amountPaid(invoiceId) };
            }

            @Override
            protected void done() {
                BigDecimal due;
                BigDecimal paid;
                try {
                    BigDecimal[] both = get();
                    due  = both[0];
                    paid = both[1];
                } catch (Exception ex) {
                    // Không đọc được hai số tiền thì KHÔNG chặn việc sửa ngày/giờ:
                    // dòng cảnh báo là thông tin thêm, không phải điều kiện.
                    lbMoneyGap.setText(" ");
                    return;
                }

                if (paid.compareTo(due) >= 0) {
                    lbMoneyGap.setText(" ");
                    return;
                }

                lbMoneyGap.setText("<html><b>" + I18n.msg("Đã thu ", "Paid ") + Ui.money(paid) + " / "
                        + Ui.money(due) + I18n.msg("  --  LỆCH ", "  --  SHORT BY ") + Ui.money(due.subtract(paid))
                        + "</b><br>" + I18n.msg("Sửa dòng hàng xong làm lệch số tiền; trạng thái KHÔNG tự đổi lại.",
                                                "Editing the lines changed the total; the status does NOT change back.")
                        + "</html>");
                pack();
            }
        }.execute();
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
            showNotNullWarning(I18n.msg("Ngày lập", "Date"));
            tfDate.requestFocus();
            return;
        }

        String timeStr = tfTime.getText().trim();
        if (timeStr.isEmpty()) {
            showNotNullWarning(I18n.msg("Giờ lập", "Time"));
            tfTime.requestFocus();
            return;
        }

        String counterId = tfCounterId.getText().trim();
        if (counterId.isEmpty()) {
            showNotNullWarning(I18n.msg("Mã quầy", "Counter ID"));
            tfCounterId.requestFocus();
            return;
        }

        String cashierId = tfCashierId.getText().trim();
        if (cashierId.isEmpty()) {
            showNotNullWarning(I18n.msg("Mã thu ngân", "Cashier ID"));
            tfCashierId.requestFocus();
            return;
        }

        LocalDate date;
        try {
            date = LocalDate.parse(dateStr);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    I18n.msg("Ngày lập phải đúng định dạng YYYY-MM-DD!", "The date must be in YYYY-MM-DD format!"),
                    I18n.msg("Sai định dạng", "Invalid Format"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        LocalTime time;
        try {
            time = LocalTime.parse(timeStr);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    I18n.msg("Giờ lập phải đúng định dạng HH:MM:SS!", "The time must be in HH:MM:SS format!"),
                    I18n.msg("Sai định dạng", "Invalid Format"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Truyền xuống đúng trạng thái đã ĐỌC LÊN, không phải thứ người dùng
        // chọn (ô đã bị khóa nên hai thứ bằng nhau -- trừ khi cột Status chứa
        // một giá trị lạ mà InvoiceStatus.of() không nhận ra, lúc đó chuỗi thô
        // mới là cái khớp với database). sp_update_invoice_header dùng tham số
        // này làm phép kiểm, khác trạng thái trong DB là bị từ chối.
        String status = originalStatus;
        if (status == null) {
            Object sel = cbStatus.getSelectedItem();
            status = (sel == null) ? null : ((InvoiceStatus) sel).code();
        }
        String customerId = tfCustomerId.getText().trim();
        if (customerId.isEmpty()) {
            customerId = null; // Customer_ID cho phép NULL (khách vãng lai)
        }

        try {
            dao.updateInvoiceHeader(invoiceId, date, time, status, counterId, cashierId, customerId);
            JOptionPane.showMessageDialog(this,
                    I18n.msg("Cập nhật hóa đơn " + invoiceId + " thành công!",
                             "Invoice " + invoiceId + " updated successfully!"),
                    I18n.msg("Thành công", "Success"), JOptionPane.INFORMATION_MESSAGE);
            saved = true;
            dispose();
        } catch (Exception ex) {
            // Lỗi 45000 của sp_update_invoice_header là một lời giải thích
            // ("Khong huy hoa don bang hop thoai sua. Dung nut..."), hiện nó
            // kèm stack trace là làm người đọc tưởng app hỏng. Hàm này tự rơi
            // về Ui.error khi không phải lỗi nghiệp vụ.
            InvoiceViewPanel.showSqlFailure(this, ex, I18n.msg("Không cập nhật được hóa đơn", "Cannot Update Invoice"));
        }
    }

    /** Ô bắt buộc bị để trống. */
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
