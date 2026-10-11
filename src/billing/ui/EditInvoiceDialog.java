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
        super(owner, "Chinh sua Hoa don: " + invoiceId, ModalityType.APPLICATION_MODAL);
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
        addFormRow(form, gbc, row++, "Ma hoa don (PK):", tfInvoiceId, "(Khong the thay doi)");
        addFormRow(form, gbc, row++, "Ngay lap (YYYY-MM-DD) *:", tfDate, "(NOT NULL)");
        addFormRow(form, gbc, row++, "Gio lap (HH:MM:SS) *:", tfTime, "(NOT NULL)");
        // Trạng thái KHÔNG sửa bằng tay: không phải vì phân quyền, mà vì không
        // ai -- kể cả quản lý -- gõ ra được một trạng thái đúng (plan/13 mục 2.2).
        // Nhãn bỏ dấu '*' và "(NOT NULL)": chú thích đỏ ở dưới nói về các
        // trường BỊ CHẶN KHI ĐỂ TRỐNG, không còn đúng cho ô này.
        cbStatus.setEnabled(false);
        cbStatus.setToolTipText("Trang thai doi tu dong khi thu du tien, "
                + "hoac qua nut \"Huy hoa don\" o tab Xem hoa don");
        addFormRow(form, gbc, row++, "Trang thai:", cbStatus, "(chi doc)");

        // Dòng lệch tiền: trống (và vô hình) cho tới khi đọc xong hai số tiền.
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3;
        lbMoneyGap.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lbMoneyGap.setForeground(new Color(192, 57, 43));
        form.add(lbMoneyGap, gbc);
        gbc.gridwidth = 1;
        row++;

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

        loadMoneyGap();
    }

    /**
     * Đọc "đã thu" và "phải trả" rồi cảnh báo nếu lệch.
     *
     * Đây là dấu vết nhìn thấy được của lỗi #4 (plan/13): quản lý sửa dòng hàng
     * làm tổng phải trả tăng, Payment giữ nguyên, trạng thái vẫn PAID. Khối 4
     * CỐ Ý không tự chuyển PAID -> OPEN (chuyển ngược bị cấm, mục 3.2) nên chỗ
     * duy nhất làm được gì là nói cho người dùng biết.
     *
     * CHỈ cảnh báo trên hóa đơn PAID. Trên hóa đơn OPEN thì paid < due là
     * trạng thái BÌNH THƯỜNG (đó chính là định nghĩa của OPEN), báo động ở đó
     * là báo động giả.
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

                lbMoneyGap.setText("<html><b>Da thu " + Ui.money(paid) + " / "
                        + Ui.money(due) + "  --  LECH " + Ui.money(due.subtract(paid))
                        + "</b><br>Sua dong hang xong lam lech so tien. "
                        + "Trang thai KHONG tu doi nguoc -- xem plan/13 muc 2.3.</html>");
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
                    "Cap nhat hoa don " + invoiceId + " thanh cong trong Database!",
                    "Thanh cong", JOptionPane.INFORMATION_MESSAGE);
            saved = true;
            dispose();
        } catch (Exception ex) {
            // Lỗi 45000 của sp_update_invoice_header là một lời giải thích
            // ("Khong huy hoa don bang hop thoai sua. Dung nut..."), hiện nó
            // kèm stack trace là làm người đọc tưởng app hỏng. Hàm này tự rơi
            // về Ui.error khi không phải lỗi nghiệp vụ.
            InvoiceViewPanel.showSqlFailure(this, ex, "Khong cap nhat duoc hoa don");
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
