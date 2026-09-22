package billing.ui;

import billing.dao.InvoiceDao;
import billing.db.QueryResult;
import billing.model.Account;
import billing.model.UserSession;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;

/**
 * ====================================================================
 *  MÀN HÌNH 2 -- XEM HÓA ĐƠN & THAO TÁC TRỰC TIẾP DATABASE
 * ====================================================================
 *  Bên trái: danh sách hóa đơn + nút "Sửa hóa đơn" + nút "Xóa hóa đơn".
 *  Bên phải:
 *    1. Thông tin chung + tổng tiền
 *    2. Các dòng hàng (Invoice_Line) + nút "Sửa dòng", "Xóa dòng", "Thử xóa (Set NULL)"
 *    3. Thanh toán (Payment + bảng con)
 *    4. Trả hàng (Return)
 *
 *  PHÂN QUYỀN TRÊN UI (UI LOCKING):
 *  - Thu ngân (cashier): Các nút sửa/xóa bị khóa (disable).
 *  - Quản lý (manager): Các nút được kích hoạt (enable).
 *  (Lưu ý: Tầng DAO không khóa để phục vụ các mục đích nghiệp vụ phía sau).
 */
public class InvoiceViewPanel extends JPanel {

    private final InvoiceDao dao = new InvoiceDao();

    private final JTable tbInvoices = Ui.readOnlyTable();
    private final JTable tbLines    = Ui.readOnlyTable();
    private final JTable tbPayments = Ui.readOnlyTable();
    private final JTable tbReturns  = Ui.readOnlyTable();
    private final JTextArea taHeader = new JTextArea(8, 40);

    // Các button thao tác hóa đơn (bên trái)
    private final JButton btEditInvoice   = new JButton("Sua hoa don");
    private final JButton btDeleteInvoice = new JButton("Xoa hoa don");

    // Các button thao tác dòng hàng trong hóa đơn (bên phải)
    private final JButton btEditLine       = new JButton("Sua dong hang");
    private final JButton btDeleteLine     = new JButton("Xoa dong hang");
    private final JButton btClearLineValue = new JButton("Thu xoa gia tri (Set NULL)");

    public InvoiceViewPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        taHeader.setEditable(false);
        taHeader.setFont(Ui.MONO);

        // --- Cột trái: Danh sách hóa đơn và nút thao tác ---
        JButton btReload = new JButton("Tai lai danh sach");
        btReload.addActionListener(e -> reload());

        JPanel pnlInvoiceActions = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
        btEditInvoice.setFont(Ui.BOLD);
        btDeleteInvoice.setFont(Ui.BOLD);
        btDeleteInvoice.setForeground(new Color(180, 40, 40));

        btEditInvoice.addActionListener(e -> onEditInvoice());
        btDeleteInvoice.addActionListener(e -> onDeleteInvoice());

        pnlInvoiceActions.add(btEditInvoice);
        pnlInvoiceActions.add(btDeleteInvoice);

        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.add(btReload, BorderLayout.NORTH);
        left.add(new JScrollPane(tbInvoices), BorderLayout.CENTER);
        left.add(pnlInvoiceActions, BorderLayout.SOUTH);
        left.setBorder(BorderFactory.createTitledBorder("Danh sach hoa don (bam de xem chi tiet)"));

        // --- Cột phải: Chi tiết hóa đơn ---
        // Panel riêng cho dòng hàng kèm các nút thao tác dòng
        JPanel pnlLineActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        btEditLine.setFont(Ui.BOLD);
        btDeleteLine.setForeground(new Color(180, 40, 40));

        btEditLine.addActionListener(e -> onEditLine());
        btDeleteLine.addActionListener(e -> onDeleteLine());
        btClearLineValue.addActionListener(e -> onTryClearLineValue());

        pnlLineActions.add(btEditLine);
        pnlLineActions.add(btDeleteLine);
        pnlLineActions.add(btClearLineValue);

        JPanel pnlLinesContainer = new JPanel(new BorderLayout(2, 2));
        pnlLinesContainer.add(new JScrollPane(tbLines), BorderLayout.CENTER);
        pnlLinesContainer.add(pnlLineActions, BorderLayout.SOUTH);

        JPanel right = new JPanel(new GridLayout(4, 1, 6, 6));
        right.add(Ui.titled("Thong tin chung + tong tien", new JScrollPane(taHeader)));
        right.add(Ui.titled("Cac dong hang (Invoice_Line)", pnlLinesContainer));
        right.add(Ui.titled("Thanh toan (Payment + bang con)", new JScrollPane(tbPayments)));
        right.add(Ui.titled("Tra hang (Return)", new JScrollPane(tbReturns)));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.42);
        add(split, BorderLayout.CENTER);

        tbInvoices.getSelectionModel().addListSelectionListener(this::onSelect);
        reload();

        // Áp dụng phân quyền giao diện theo phiên làm việc hiện tại
        applyRolePermissions();
    }

    /**
     * ================================================================
     *  PHÂN QUYỀN TRÊN GIAO DIỆN (UI ROLE LOCKING)
     * ================================================================
     *  Kiểm tra tài khoản hiện tại từ UserSession:
     *  - Nếu là 'cashier' -> Toàn bộ nút Sửa/Xóa bị khóa (disabled)
     *  - Nếu là 'manager' -> Toàn bộ nút Sửa/Xóa được mở khóa (enabled)
     *  (Tuyệt đối không chặn ở DAO/Service theo yêu cầu của bạn)
     */
    public void applyRolePermissions() {
        Account user = UserSession.getCurrentUser();
        boolean isManager = (user != null && user.isManager());

        btEditInvoice.setEnabled(isManager);
        btDeleteInvoice.setEnabled(isManager);
        btEditLine.setEnabled(isManager);
        btDeleteLine.setEnabled(isManager);
        btClearLineValue.setEnabled(isManager);

        if (!isManager) {
            String lockHint = "Chuc nang nay bi khoa voi Thu ngan (Cashier). Chi Quan ly (Manager) moi co quyen.";
            btEditInvoice.setToolTipText(lockHint);
            btDeleteInvoice.setToolTipText(lockHint);
            btEditLine.setToolTipText(lockHint);
            btDeleteLine.setToolTipText(lockHint);
            btClearLineValue.setToolTipText(lockHint);
        } else {
            btEditInvoice.setToolTipText("Chinh sua thong tin hoa don truc tiep trong DB");
            btDeleteInvoice.setToolTipText("Xoa hoa don va toan bo du lieu lien quan khoi DB");
            btEditLine.setToolTipText("Chinh sua so luong, don gia dong hang trong DB");
            btDeleteLine.setToolTipText("Xoa dong hang khoi DB");
            btClearLineValue.setToolTipText("Thu nghiem co che canh bao xoa gia tri NOT NULL");
        }
    }

    public void reload() {
        try {
            Ui.fill(tbInvoices, dao.recentInvoices());
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    private String getSelectedInvoiceId() {
        int r = tbInvoices.getSelectedRow();
        if (r < 0) return null;
        return String.valueOf(tbInvoices.getValueAt(r, 0));
    }

    private void onSelect(ListSelectionEvent e) {
        if (e != null && e.getValueIsAdjusting()) return;
        String invoiceId = getSelectedInvoiceId();
        if (invoiceId == null) return;

        try {
            // --- 1. Phần đầu hóa đơn -------------------------------
            QueryResult h = dao.invoiceHeader(invoiceId);
            StringBuilder sb = new StringBuilder();
            if (!h.isEmpty()) {
                Object[] row = h.rows.get(0);
                for (int i = 0; i < h.columns.size(); i++) {
                    sb.append(String.format("%-18s : %s%n", h.columns.get(i), row[i]));
                }
            }
            taHeader.setText(sb.toString());
            taHeader.setCaretPosition(0);

            // --- 2, 3, 4 -------------------------------------------
            Ui.fill(tbLines,    dao.invoiceLines(invoiceId));
            Ui.fill(tbPayments, dao.invoicePayments(invoiceId));
            Ui.fill(tbReturns,  dao.invoiceReturns(invoiceId));

        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    // =================================================================
    //  CÁC THAO TÁC CHỈNH SỬA & XÓA TRỰC TIẾP TRONG DATABASE
    // =================================================================

    /** Chỉnh sửa thông tin Header hóa đơn */
    private void onEditInvoice() {
        String invoiceId = getSelectedInvoiceId();
        if (invoiceId == null) {
            JOptionPane.showMessageDialog(this, "Vui long chon mot hoa don de chinh sua!", "Chua chon hoa don", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            // Tra cứu thông tin gốc của hóa đơn
            String sql = "SELECT Invoice_ID, `Date`, `Time`, Status, Counter_ID, Cashier_ID, Customer_ID FROM Invoice WHERE Invoice_ID = ?";
            QueryResult qr = QueryResult.run(sql, invoiceId);
            if (qr.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Khong tim thay hoa don " + invoiceId, "Loi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Object[] r = qr.rows.get(0);
            String date = String.valueOf(r[1]);
            String time = String.valueOf(r[2]);
            String status = String.valueOf(r[3]);
            String counterId = String.valueOf(r[4]);
            String cashierId = String.valueOf(r[5]);
            String customerId = r[6] != null ? String.valueOf(r[6]) : "";

            Window window = SwingUtilities.getWindowAncestor(this);
            EditInvoiceDialog dialog = new EditInvoiceDialog(window, dao, invoiceId, date, time, status, counterId, cashierId, customerId);
            dialog.setVisible(true);

            if (dialog.isSaved()) {
                reload();
                onSelect(null);
            }

        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    /** Xóa hóa đơn trực tiếp trong database */
    private void onDeleteInvoice() {
        String invoiceId = getSelectedInvoiceId();
        if (invoiceId == null) {
            JOptionPane.showMessageDialog(this, "Vui long chon mot hoa don de xoa!", "Chua chon hoa don", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int choice = JOptionPane.showConfirmDialog(this,
                "Ban co chac chan muon XOA VINH VIEN hoa don " + invoiceId + " khoi Database?\n\n"
              + "Thao tac nay se xoa sach:\n"
              + "  1. Cac luot tra hang lien quan (Return)\n"
              + "  2. Cac chi tiet thanh toan (Cash/Card/EWallet_Payment)\n"
              + "  3. Cac luot thanh toan (Payment)\n"
              + "  4. Toan bo dong hang (Invoice_Line)\n"
              + "  5. Phan dau hoa don (Invoice)\n\n"
              + "Hanh dong khong the hoan tac!",
                "Xac nhan xoa hoa don khoi Database",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (choice == JOptionPane.YES_OPTION) {
            try {
                dao.deleteInvoice(invoiceId);
                JOptionPane.showMessageDialog(this, "Da xoa thanh cong hoa don " + invoiceId + " khoi Database!", "Thanh cong", JOptionPane.INFORMATION_MESSAGE);

                // Xóa trắng khung chi tiết
                taHeader.setText("");
                Ui.fill(tbLines, QueryResult.run("SELECT '' WHERE 1=0"));
                Ui.fill(tbPayments, QueryResult.run("SELECT '' WHERE 1=0"));
                Ui.fill(tbReturns, QueryResult.run("SELECT '' WHERE 1=0"));

                reload();
            } catch (Exception ex) {
                Ui.error(this, ex);
            }
        }
    }

    /** Chỉnh sửa một dòng hàng */
    private void onEditLine() {
        String invoiceId = getSelectedInvoiceId();
        int row = tbLines.getSelectedRow();
        if (invoiceId == null || row < 0) {
            JOptionPane.showMessageDialog(this, "Vui long chon mot dong hang trong bang 'Cac dong hang' de chinh sua!", "Chua chon dong hang", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            int lineNo = Integer.parseInt(String.valueOf(tbLines.getValueAt(row, 0)));

            // Lấy thông tin gốc của dòng hàng
            String sql = "SELECT Barcode, Quantity, Unit_Price, Discount FROM Invoice_Line WHERE Invoice_ID = ? AND Line_Number = ?";
            QueryResult qr = QueryResult.run(sql, invoiceId, lineNo);
            if (qr.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Khong tim thay dong hang so " + lineNo, "Loi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Object[] r = qr.rows.get(0);
            String barcode   = String.valueOf(r[0]);
            String quantity  = String.valueOf(r[1]);
            String unitPrice = String.valueOf(r[2]);
            String discount  = String.valueOf(r[3]);

            Window window = SwingUtilities.getWindowAncestor(this);
            EditLineDialog dialog = new EditLineDialog(window, dao, invoiceId, lineNo, barcode, quantity, unitPrice, discount);
            dialog.setVisible(true);

            if (dialog.isSaved()) {
                reload();
                onSelect(null);
            }

        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    /** Xóa một dòng hàng */
    private void onDeleteLine() {
        String invoiceId = getSelectedInvoiceId();
        int row = tbLines.getSelectedRow();
        if (invoiceId == null || row < 0) {
            JOptionPane.showMessageDialog(this, "Vui long chon mot dong hang de xoa!", "Chua chon dong hang", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int lineNo = Integer.parseInt(String.valueOf(tbLines.getValueAt(row, 0)));
        int choice = JOptionPane.showConfirmDialog(this,
                "Ban co chac chan muon xoa dong hang so " + lineNo + " khoi hoa don " + invoiceId + "?",
                "Xac nhan xoa dong hang", JOptionPane.YES_NO_OPTION);

        if (choice == JOptionPane.YES_OPTION) {
            try {
                dao.deleteInvoiceLine(invoiceId, lineNo);
                JOptionPane.showMessageDialog(this, "Da xoa thanh cong dong hang " + lineNo + "!", "Thanh cong", JOptionPane.INFORMATION_MESSAGE);
                reload();
                onSelect(null);
            } catch (Exception ex) {
                Ui.error(this, ex);
            }
        }
    }

    /** Nút thử xóa giá trị (Set NULL) để minh họa cơ chế cảnh báo của Database */
    private void onTryClearLineValue() {
        JOptionPane.showMessageDialog(this,
                "CANH BAO DATABASE:\n\n"
              + "Toan bo cac truong trong bang Invoice_Line (Quantity, Unit_Price, Barcode, Line_Number)\n"
              + "deu duoc dinh nghia rang buoc [NOT NULL] trong Schema!\n\n"
              + "Ban KHONG THE xoa bat ky gia tri nao ve NULL.\n"
              + "Hay su dung nut 'Sua dong hang' va bam 'Thu xoa (Set NULL)' de thay app ngan chan hanh vi nay!",
                "Canh bao rang buoc NOT NULL",
                JOptionPane.WARNING_MESSAGE);
    }
}
