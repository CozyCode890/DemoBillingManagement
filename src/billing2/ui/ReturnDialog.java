package billing2.ui;

import billing2.dao.InvoiceDao;
import billing2.db.QueryResult;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Hộp thoại TRẢ HÀNG. Mỗi dòng còn trả được (sp_get_returnable_lines) là một hàng của bảng;
 * gõ số lượng vào cột "Return qty" -> kiểm (<= số còn lại) -> SQL tính tiền hoàn (fn_return_refund)
 * -> Confirm -> InvoiceDao.processReturn(): một transaction, hóa đơn gốc thành VOIDED.
 */
public class ReturnDialog extends JDialog {

    private static final int QTY_COL = 6, REFUND_COL = 7;

    private final InvoiceDao dao;
    private final String invoiceId;
    private final List<Object[]> lines;     // Dong, San_pham, DVT, SL_mua, SL_da_tra, SL_con_lai, Don_gia, Giam_gia, Thanh_tien
    private final BigDecimal[] qty;         // số lượng trả đã kiểm của từng dòng (null = không trả)
    private final BigDecimal[] refund;      // tiền hoàn SQL tính cho số lượng đó
    private final DefaultTableModel model;
    private final JTable table;
    private final JLabel lbTotal = new JLabel(Ui.money(BigDecimal.ZERO));
    private final JButton btConfirm = new JButton("Confirm return");
    private boolean updating, saved;

    public ReturnDialog(Window owner, InvoiceDao dao, String invoiceId, String header,
                        QueryResult returnable, String approverId, String approverLabel) {
        super(owner, "Return items -- invoice " + invoiceId, ModalityType.APPLICATION_MODAL);
        this.dao = dao;
        this.invoiceId = invoiceId;
        this.lines = returnable.rows;
        this.qty = new BigDecimal[lines.size()];
        this.refund = new BigDecimal[lines.size()];

        model = new DefaultTableModel(new String[]{"Line", "Product", "Unit", "Bought", "Returned",
                "Remaining", "Return qty", "Refund"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return c == QTY_COL; }
        };
        for (Object[] r : lines) {
            model.addRow(new Object[]{r[0], r[1], r[2], Ui.qty(r[3]), Ui.qty(r[4]), Ui.qty(r[5]), "", ""});
        }
        model.addTableModelListener(e -> {
            if (!updating && e.getColumn() == QTY_COL && e.getFirstRow() >= 0) onQtyEdited(e.getFirstRow());
        });
        table = new JTable(model);
        table.setRowHeight(24);
        table.getTableHeader().setFont(Ui.BOLD);
        table.setPreferredScrollableViewportSize(new Dimension(760, Math.min(lines.size(), 10) * 24));
        table.putClientProperty("terminateEditOnFocusLost", true);

        JLabel lbHeader = new JLabel(header);
        lbHeader.setFont(Ui.BOLD);
        lbTotal.setFont(Ui.BOLD);
        lbTotal.setForeground(Ui.RED);
        btConfirm.setFont(Ui.BOLD);
        btConfirm.addActionListener(e -> confirm(approverId, approverLabel));
        JButton btCancel = new JButton("Cancel");
        btCancel.addActionListener(e -> dispose());

        JPanel south = new JPanel(new BorderLayout());
        south.add(Ui.row(new JLabel("Total refund:"), lbTotal, new JLabel("   Approver: " + approverLabel)), BorderLayout.WEST);
        south.add(Ui.row(btConfirm, btCancel), BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(Ui.row(lbHeader, new JLabel("   -- type the quantity to return in the 'Return qty' column")), BorderLayout.NORTH);
        content.add(new JScrollPane(table), BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
    }

    public boolean isSaved() { return saved; }

    private int lineNo(int row) { return Integer.parseInt(String.valueOf(lines.get(row)[0])); }

    /** Thu ngân vừa gõ số lượng trả cho một dòng: kiểm <= số còn lại, rồi hỏi SQL tiền hoàn. */
    private void onQtyEdited(int row) {
        String text = String.valueOf(model.getValueAt(row, QTY_COL)).trim();
        BigDecimal q = null, r = null;
        if (!text.isEmpty()) {
            q = Ui.readQuantity(this, text, Ui.dec(lines.get(row)[5]), String.valueOf(lines.get(row)[2]));
            if (q != null) {
                try {
                    r = dao.returnRefund(invoiceId, lineNo(row), q);   // Java KHÔNG tự tính tiền hoàn
                } catch (Exception ex) {
                    Ui.error(this, ex);
                    q = null;
                }
            }
        }
        qty[row] = q;
        refund[row] = q == null ? null : r;
        updating = true;
        model.setValueAt(q == null ? "" : Ui.qtyInput(q), row, QTY_COL);
        model.setValueAt(q == null ? "" : Ui.money(r), row, REFUND_COL);
        updating = false;
        lbTotal.setText(Ui.money(totalRefund()));
    }

    private BigDecimal totalRefund() {
        BigDecimal t = BigDecimal.ZERO;
        for (BigDecimal r : refund) if (r != null) t = t.add(r);
        return t;
    }

    private void confirm(String approverId, String approverLabel) {
        if (table.isEditing()) table.getCellEditor().stopCellEditing();     // chốt ô đang gõ dở
        List<InvoiceDao.ReturnItem> items = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (qty[i] != null) items.add(new InvoiceDao.ReturnItem(String.valueOf(lines.get(i)[1]), lineNo(i), qty[i]));
        }
        if (items.isEmpty()) {
            Ui.warn(this, "Please type a return quantity for at least one line!", "Nothing to Return");
            return;
        }
        BigDecimal total = totalRefund();
        if (JOptionPane.showConfirmDialog(this, "Return " + items.size() + " line(s) of invoice " + invoiceId + "?\n\n"
                + "Total refund: " + Ui.money(total) + "\nApprover: " + approverLabel + "\n\n"
                + "After this, invoice " + invoiceId + " CANNOT take more returns.\nRevenue follows the adjusted invoice.",
                "Confirm Return", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION) return;

        btConfirm.setEnabled(false);
        Ui.async(this, () -> { dao.processReturn(invoiceId, items, approverId); return items.size(); },
                () -> btConfirm.setEnabled(true), n -> {
                    saved = true;
                    dispose();
                    JOptionPane.showMessageDialog(getOwner(), "Recorded " + n + " return line(s) for invoice " + invoiceId
                            + ".\nTotal refund: " + Ui.money(total), "Success", JOptionPane.INFORMATION_MESSAGE);
                });
    }
}
