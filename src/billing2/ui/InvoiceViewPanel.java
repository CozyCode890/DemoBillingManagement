package billing2.ui;

import billing2.dao.InvoiceDao;
import billing2.dao.LookupDao;
import billing2.db.QueryResult;
import billing2.model.Account;
import billing2.model.IdName;
import billing2.model.InvoiceStatus;
import billing2.model.UserSession;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.math.BigDecimal;
import java.util.List;

/**
 * Tab 2 -- XEM HÓA ĐƠN. Trái: danh sách (sp_get_recent_invoices). Bấm một hóa đơn -> phải:
 * đầu hóa đơn + tổng tiền, các dòng gốc, hóa đơn điều chỉnh (sau trả hàng), thanh toán.
 * Nút sửa / hủy / xóa / trả hàng CHỈ mở cho Quản lý (applyRolePermissions).
 */
public class InvoiceViewPanel extends JPanel {

    private static final String NO_RETURNS = "Adjusted invoice -- (no returns on this invoice)";
    private static final int[] DAYS = {7, 30, 90, 0};          // 0 = tất cả

    private final InvoiceDao dao = new InvoiceDao();
    private final LookupDao  lookupDao = new LookupDao();

    private final JTable tbInvoices = Ui.readOnlyTable();
    private final JTable tbLines    = Ui.readOnlyTable();
    private final JTable tbAdjusted = Ui.readOnlyTable();
    private final JTable tbPayments = Ui.readOnlyTable();
    private final JTextArea taHeader = new JTextArea(8, 40);
    private final TitledBorder adjustedBorder = BorderFactory.createTitledBorder(NO_RETURNS);
    private final JComboBox<String> cbDays = new JComboBox<>(new String[]{"Last 7 days", "Last 30 days", "Last 90 days", "All"});
    private final JLabel lbCount = new JLabel(" ");

    private final JButton btEditInvoice  = new JButton("Edit invoice");
    private final JButton btVoid         = new JButton("Void invoice");
    private final JButton btDelete       = new JButton("Delete invoice");
    private final JButton btEditLine     = new JButton("Edit line");
    private final JButton btDeleteLine   = new JButton("Delete line");
    private final JButton btReturn       = new JButton("Return items");
    private final JButton btCancelReturn = new JButton("Cancel return of this line");
    private final List<JButton> managerButtons =
            List.of(btEditInvoice, btVoid, btDelete, btEditLine, btDeleteLine, btReturn, btCancelReturn);

    public InvoiceViewPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        taHeader.setEditable(false);
        taHeader.setFont(Ui.MONO);
        btVoid.setForeground(new Color(190, 110, 20));
        btDelete.setForeground(Ui.RED);
        btDeleteLine.setForeground(Ui.RED);
        btCancelReturn.setForeground(Ui.RED);

        JButton btReload = new JButton("Reload");
        btReload.addActionListener(e -> reload());
        cbDays.setSelectedIndex(1);
        cbDays.addActionListener(e -> reload());

        JPanel south = new JPanel(new BorderLayout());
        south.add(lbCount, BorderLayout.NORTH);
        south.add(Ui.row(btEditInvoice, btVoid, btDelete), BorderLayout.CENTER);
        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.setBorder(BorderFactory.createTitledBorder("Invoices (click one to see the details)"));
        left.add(Ui.row(new JLabel("Date range:"), cbDays, btReload), BorderLayout.NORTH);
        left.add(new JScrollPane(tbInvoices), BorderLayout.CENTER);
        left.add(south, BorderLayout.SOUTH);

        JPanel original = Ui.titled("Original invoice", new JScrollPane(tbLines));
        original.add(Ui.row(btEditLine, btDeleteLine, btReturn), BorderLayout.SOUTH);
        JPanel adjusted = new JPanel(new BorderLayout(2, 2));
        adjusted.setBorder(adjustedBorder);
        adjusted.add(new JScrollPane(tbAdjusted), BorderLayout.CENTER);
        adjusted.add(Ui.row(btCancelReturn), BorderLayout.SOUTH);
        JSplitPane lines = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, original, adjusted);
        lines.setResizeWeight(0.5);

        JPanel right = new JPanel(new GridLayout(3, 1, 6, 6));
        right.add(Ui.titled("Summary + totals", new JScrollPane(taHeader)));
        right.add(lines);
        right.add(Ui.titled("Payments", new JScrollPane(tbPayments)));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.42);
        add(split, BorderLayout.CENTER);

        btEditInvoice.addActionListener(e -> onEditInvoice());
        btVoid.addActionListener(e -> onVoidInvoice());
        btDelete.addActionListener(e -> onDeleteInvoice());
        btEditLine.addActionListener(e -> onEditLine());
        btDeleteLine.addActionListener(e -> onDeleteLine());
        btReturn.addActionListener(e -> onReturn());
        btCancelReturn.addActionListener(e -> onCancelReturn());
        tbInvoices.setAutoCreateRowSorter(true);                // bấm tiêu đề cột để sắp xếp
        tbInvoices.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && selectedId() != null) showDetail(selectedId());
        });

        reload();
        applyRolePermissions();
    }

    /** Gọi lại mỗi lần đổi vai trò hoặc chuyển sang tab này. */
    public void applyRolePermissions() {
        Account user = UserSession.getCurrentUser();
        boolean manager = user != null && user.isManager();
        for (JButton b : managerButtons) {
            b.setEnabled(manager);
            b.setToolTipText(manager ? null : "This function is locked for Cashiers. Only Managers have permission.");
        }
    }

    public void reload() {
        try {
            int days = DAYS[Math.max(cbDays.getSelectedIndex(), 0)];
            QueryResult qr = dao.recentInvoices(days);
            Ui.fill(tbInvoices, qr, new int[]{qr.columns.size() - 1}, null, null);    // cột cuối = tổng tiền
            lbCount.setText("Showing " + qr.rows.size() + " invoice(s) (" + (days == 0 ? "all" : "last " + days + " days") + ")");
        } catch (Exception ex) {
            lbCount.setText(" ");
            Ui.error(this, ex);
        }
    }

    private String selectedId() {
        int r = tbInvoices.getSelectedRow();
        return r < 0 ? null : String.valueOf(tbInvoices.getValueAt(r, 0));
    }

    /** Bốn câu SELECT cho một cú bấm: đầu hóa đơn, các dòng, thanh toán, (nếu có trả hàng) hóa đơn điều chỉnh. */
    private void showDetail(String invoiceId) {
        try {
            QueryResult h = dao.invoiceHeader(invoiceId);
            StringBuilder sb = new StringBuilder();
            if (!h.isEmpty()) {
                Object[] row = h.rows.get(0);
                for (int i = 0; i < h.columns.size(); i++) {
                    Object v = row[i];
                    sb.append(String.format("%-20s : %s%n", Ui.col(h.columns.get(i)),
                            v instanceof Number ? Ui.money(v) : v == null ? "" : Ui.en(v.toString())));
                }
            }
            taHeader.setText(sb.toString());
            taHeader.setCaretPosition(0);

            Ui.fill(tbLines, dao.invoiceLines(invoiceId), new int[]{4, 5, 6}, new int[]{2}, new int[]{7});
            Ui.fill(tbPayments, dao.invoicePayments(invoiceId), new int[]{1, 4}, null, null);

            BigDecimal refund = h.isEmpty() ? BigDecimal.ZERO : Ui.dec(h.rows.get(0)[12]);       // Tien_hoan
            if (refund.signum() > 0) {
                Ui.fill(tbAdjusted, dao.invoiceAdjusted(invoiceId), new int[]{7, 8, 9, 10}, new int[]{4, 5, 6}, null);
                adjustedBorder.setTitle("Adjusted invoice (after returns) -- new total "
                        + Ui.money(h.rows.get(0)[13]) + ", refunded " + Ui.money(refund));
            } else {
                Ui.clear(tbAdjusted);
                adjustedBorder.setTitle(NO_RETURNS);
            }
            repaint();
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    private void clearDetail() {
        taHeader.setText("");
        Ui.clear(tbLines);
        Ui.clear(tbPayments);
        Ui.clear(tbAdjusted);
        adjustedBorder.setTitle(NO_RETURNS);
        repaint();
    }

    /** Sau khi sửa: tải lại danh sách, chọn lại đúng hóa đơn, vẽ lại chi tiết. */
    private void refresh(String invoiceId) {
        reload();
        for (int r = 0; r < tbInvoices.getRowCount(); r++) {
            if (invoiceId.equals(String.valueOf(tbInvoices.getValueAt(r, 0)))) {
                tbInvoices.setRowSelectionInterval(r, r);
                tbInvoices.scrollRectToVisible(tbInvoices.getCellRect(r, 0, true));
                break;
            }
        }
        showDetail(invoiceId);
    }

    private String requireInvoice(String action) {
        String id = selectedId();
        if (id == null) Ui.warn(this, "Please select an invoice to " + action + "!", "No Invoice Selected");
        return id;
    }

    private int selectedLine() {
        int r = tbLines.getSelectedRow();
        return r < 0 ? -1 : Integer.parseInt(String.valueOf(tbLines.getValueAt(r, 0)));
    }

    // =================================================================
    //  HÓA ĐƠN: sửa / hủy / xóa
    // =================================================================

    private void onEditInvoice() {
        String id = requireInvoice("edit");
        if (id == null) return;
        try {
            QueryResult qr = dao.invoiceEditFields(id);
            if (qr.isEmpty()) { Ui.warn(this, "Cannot find invoice " + id, "Not Found"); return; }
            EditInvoiceDialog dlg = new EditInvoiceDialog(SwingUtilities.getWindowAncestor(this), dao, id, qr.rows.get(0));
            dlg.setVisible(true);
            if (dlg.isSaved()) refresh(id);
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    /** HỦY khác XÓA: hóa đơn còn nguyên, chỉ không tính doanh thu và không trả hàng được nữa. */
    private void onVoidInvoice() {
        String id = requireInvoice("void");
        if (id == null) return;
        if (JOptionPane.showConfirmDialog(this, "Void invoice " + id + "?\n\nVoid is NOT Delete:\n"
                + "  - The invoice is kept: items, payments, returns\n"
                + "  - It is no longer counted in revenue\n"
                + "  - No more returns are possible\n"
                + "  - It CANNOT be reopened\n\nTo remove the invoice completely, use \"Delete invoice\".",
                "Confirm Void Invoice", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        Ui.async(this, () -> { dao.voidInvoice(id); return id; }, null, v -> {
            JOptionPane.showMessageDialog(this, "Invoice " + id + " has been voided.\n"
                    + "It is kept, but no longer counted in revenue.", "Invoice Voided", JOptionPane.INFORMATION_MESSAGE);
            refresh(id);
        });
    }

    private void onDeleteInvoice() {
        String id = requireInvoice("delete");
        if (id == null) return;
        if (JOptionPane.showConfirmDialog(this, "Are you sure to PERMANENTLY DELETE invoice " + id + "?\n\n"
                + "This will delete:\n  1. All returns\n  2. All payments and payment details\n"
                + "  3. All items\n  4. The invoice itself\n\nThis cannot be undone!",
                "Confirm Delete Invoice", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        try {
            dao.deleteInvoice(id);
            JOptionPane.showMessageDialog(this, "Invoice " + id + " deleted successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            clearDetail();
            reload();
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    // =================================================================
    //  DÒNG HÀNG: sửa / xóa
    // =================================================================

    private void onEditLine() {
        String id = selectedId();
        int lineNo = selectedLine();
        if (id == null || lineNo < 0) {
            Ui.warn(this, "Please select a line in the 'Original invoice' table to edit!", "No Line Selected");
            return;
        }
        try {
            QueryResult qr = dao.invoiceLineEditFields(id, lineNo);
            if (qr.isEmpty()) { Ui.warn(this, "Cannot find line " + lineNo, "Not Found"); return; }
            EditLineDialog dlg = new EditLineDialog(SwingUtilities.getWindowAncestor(this), dao, id, lineNo, qr.rows.get(0));
            dlg.setVisible(true);
            if (dlg.isSaved()) refresh(id);
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    private void onDeleteLine() {
        String id = selectedId();
        int lineNo = selectedLine();
        if (id == null || lineNo < 0) {
            Ui.warn(this, "Please select a line to delete!", "No Line Selected");
            return;
        }
        if (JOptionPane.showConfirmDialog(this, "Delete line " + lineNo + " from invoice " + id + "?",
                "Confirm Delete Line", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            dao.deleteInvoiceLine(id, lineNo);
            JOptionPane.showMessageDialog(this, "Line " + lineNo + " deleted successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            refresh(id);
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    // =================================================================
    //  TRẢ HÀNG / HỦY TRẢ HÀNG
    // =================================================================

    /** Chỉ hóa đơn PAID mới trả được; người duyệt = Cashier_ID của tài khoản đang đăng nhập. */
    private void onReturn() {
        String id = requireInvoice("return items from");
        if (id == null) return;
        Account user = UserSession.getCurrentUser();
        if (user == null || user.getCashierId() == null || user.getCashierId().isBlank()) {
            Ui.warn(this, "Account '" + (user == null ? "?" : user.getUsername()) + "' is not linked to any employee,\n"
                    + "so it cannot approve returns. Please tell your manager!", "Missing Approver");
            return;
        }
        try {
            QueryResult h = dao.invoiceHeader(id);
            String status = h.isEmpty() ? "" : String.valueOf(h.rows.get(0)[3]);
            if (InvoiceStatus.of(status) != InvoiceStatus.PAID) {
                Ui.warn(this, "Invoice " + id + " is voided or already has returns.\n"
                        + "Only paid invoices without returns can be returned.", "Cannot Return");
                return;
            }
            QueryResult lines = dao.returnableLines(id);
            if (lines.isEmpty()) {
                JOptionPane.showMessageDialog(this, "This invoice has no lines to return.");
                return;
            }
            ReturnDialog dlg = new ReturnDialog(SwingUtilities.getWindowAncestor(this), dao, id,
                    "Invoice: " + id + "  |  Date: " + h.rows.get(0)[1] + "  |  Status: paid",
                    lines, user.getCashierId(), approverLabel(user.getCashierId()));
            dlg.setVisible(true);
            if (dlg.isSaved()) refresh(id);
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    private String approverLabel(String cashierId) {
        for (IdName c : lookupDao.cashiers()) {
            if (cashierId.equals(c.id)) return c.name + " (" + cashierId + ")";
        }
        return cashierId;
    }

    /** Hủy mọi lượt trả của dòng đang chọn trong bảng điều chỉnh; hết dòng bị trả -> hóa đơn về PAID. */
    private void onCancelReturn() {
        String id = selectedId();
        int row = tbAdjusted.getSelectedRow();
        if (id == null || row < 0 || tbAdjusted.getColumnCount() < 10) {
            Ui.warn(this, "Please select a line in the 'Adjusted invoice' table to cancel its return!", "No Line Selected");
            return;
        }
        int lineNo = Integer.parseInt(String.valueOf(tbAdjusted.getValueAt(row, 1)));
        if (JOptionPane.showConfirmDialog(this, "Cancel the return of this line?\n\n"
                + "  Invoice : " + id + "\n"
                + "  Line    : " + lineNo + " (" + tbAdjusted.getValueAt(row, 2) + ")\n"
                + "  Qty     : " + tbAdjusted.getValueAt(row, 5) + "\n"
                + "  Refund recorded: " + tbAdjusted.getValueAt(row, 9) + "\n\n"
                + "If this is the last returned line, the invoice goes back to \"Paid\"\n"
                + "(as if nothing was returned). This cannot be undone!",
                "Confirm Cancel Return", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        try {
            int deleted = dao.deleteLineReturns(id, lineNo);
            if (deleted == 0) {
                Ui.warn(this, "Line " + lineNo + " has no returns (someone may have cancelled them already).", "Nothing Cancelled");
            } else {
                JOptionPane.showMessageDialog(this, "Cancelled " + deleted + " return(s) of line " + lineNo + ".",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
            }
            refresh(id);
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }
}
