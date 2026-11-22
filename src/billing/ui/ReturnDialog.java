package billing.ui;

import billing.dao.InvoiceDao;
import billing.db.QueryResult;
import billing.model.IdName;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ReturnDialog extends JDialog {
    private static final int QTY_COL = 6, REFUND_COL = 7;

    private final InvoiceDao dao;
    private final String invoiceId;
    private final List<Object[]> lines;
    private final BigDecimal[] qty;
    private final BigDecimal[] refund;
    private final DefaultTableModel model;
    private final JTable table;
    private final JLabel lbTotal = new JLabel(Ui.money(BigDecimal.ZERO));
    private final JButton btConfirm = new JButton(I18n.text("Xác nhận trả hàng", "Confirm return"));
    private final JComboBox<IdName> cbApprover;
    private boolean updating, saved;

    public ReturnDialog(Window owner, InvoiceDao dao, String invoiceId, String header,
                        QueryResult returnable, List<IdName> managers) {
        super(owner, I18n.msg("Trả hàng -- hóa đơn ", "Return items -- invoice ") + invoiceId, ModalityType.APPLICATION_MODAL);
        this.dao = dao;
        this.invoiceId = invoiceId;
        this.lines = returnable.rows;
        this.qty = new BigDecimal[lines.size()];
        this.refund = new BigDecimal[lines.size()];
        this.cbApprover = new JComboBox<>(managers.toArray(new IdName[0]));

        model = new DefaultTableModel(new String[]{
                I18n.text("Dòng", "Line"), I18n.text("Sản phẩm", "Product"), I18n.text("ĐVT", "Unit"),
                I18n.text("SL mua", "Bought"), I18n.text("SL đã trả", "Returned"), I18n.text("SL còn lại", "Remaining"),
                I18n.text("SL trả", "Return qty"), I18n.text("Tiền hoàn", "Refund")}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return c == QTY_COL && Ui.dec(lines.get(r)[5]).signum() > 0; }
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
        I18n.markDynamicText(lbHeader);
        lbHeader.setFont(Ui.BOLD);
        lbTotal.setFont(Ui.BOLD);
        lbTotal.setForeground(Ui.RED);
        I18n.markDynamicText(lbTotal);
        btConfirm.setFont(Ui.BOLD);
        btConfirm.addActionListener(e -> confirm());
        JButton btCancel = new JButton(I18n.text("Hủy bỏ", "Cancel"));
        btCancel.addActionListener(e -> dispose());

        JPanel south = new JPanel(new BorderLayout());
        south.add(Ui.row(new JLabel(I18n.text("Tổng tiền hoàn:", "Total refund:")), lbTotal,
                         new JLabel(I18n.text("   Quản lý duyệt:", "   Approved by (manager):")), cbApprover),
                  BorderLayout.WEST);
        south.add(Ui.row(btConfirm, btCancel), BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(Ui.row(lbHeader, new JLabel(I18n.text("   -- gõ số lượng trả vào cột 'SL trả'",
                "   -- type the quantity to return in the 'Return qty' column"))), BorderLayout.NORTH);
        content.add(new JScrollPane(table), BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
    }

    public boolean isSaved() { return saved; }

    private int lineNo(int row) { return Integer.parseInt(String.valueOf(lines.get(row)[0])); }

    private void onQtyEdited(int row) {
        String text = String.valueOf(model.getValueAt(row, QTY_COL)).trim();
        BigDecimal q = null, r = null;
        if (!text.isEmpty()) {
            q = Ui.readQuantity(this, text, Ui.dec(lines.get(row)[5]), String.valueOf(lines.get(row)[2]));
            if (q != null) {
                try {
                    r = dao.returnRefund(invoiceId, lineNo(row), q);
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

    private void confirm() {
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
        IdName approver = (IdName) cbApprover.getSelectedItem();
        if (approver == null) {
            Ui.warn(this, I18n.msg("Vui lòng chọn quản lý duyệt lần trả hàng này!",
                                   "Please choose the manager who approves this return!"),
                    I18n.msg("Chưa chọn người duyệt", "No Approver"));
            return;
        }
        List<InvoiceDao.ReturnItem> items = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (qty[i] != null) items.add(new InvoiceDao.ReturnItem(String.valueOf(lines.get(i)[1]), lineNo(i), qty[i]));
        }
        if (items.isEmpty()) {
            Ui.warn(this, I18n.msg("Vui lòng gõ số lượng trả cho ít nhất một dòng!",
                                   "Please type a return quantity for at least one line!"),
                    I18n.msg("Chưa có gì để trả", "Nothing to Return"));
            return;
        }
        BigDecimal total = totalRefund();
        if (JOptionPane.showConfirmDialog(this, I18n.msg(
                "Trả " + items.size() + " dòng của hóa đơn " + invoiceId + "?\n\n"
              + "Tổng tiền hoàn: " + Ui.money(total) + "\nNgười duyệt: " + approver + "\n\n"
              + "Hóa đơn vẫn ở trạng thái Đã thanh toán; doanh thu tính theo hóa đơn điều chỉnh.\n"
              + "Lượt trả được ghi vĩnh viễn và KHÔNG hủy được.",
                "Return " + items.size() + " line(s) of invoice " + invoiceId + "?\n\n"
              + "Total refund: " + Ui.money(total) + "\nApprover: " + approver + "\n\n"
              + "The invoice stays Paid; revenue follows the adjusted invoice.\n"
              + "Returns are recorded permanently and CANNOT be cancelled."),
                I18n.msg("Xác nhận trả hàng", "Confirm Return"), JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION) return;

        btConfirm.setEnabled(false);
        Ui.async(this, () -> { dao.processReturn(invoiceId, items, approver.id); return items.size(); },
                () -> btConfirm.setEnabled(true), n -> {
                    saved = true;
                    dispose();
                    JOptionPane.showMessageDialog(getOwner(), I18n.msg(
                            "Đã ghi " + n + " dòng trả hàng cho hóa đơn " + invoiceId + ".\nTổng tiền hoàn: " + Ui.money(total),
                            "Recorded " + n + " return line(s) for invoice " + invoiceId + ".\nTotal refund: " + Ui.money(total)),
                            I18n.msg("Thành công", "Success"), JOptionPane.INFORMATION_MESSAGE);
                });
    }
}
