package billing.ui;

import billing.dao.InvoiceDao;
import billing.dao.LookupDao;
import billing.db.QueryResult;
import billing.model.IdName;
import billing.model.InvoiceStatus;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.math.BigDecimal;
import java.util.List;

/**
 * Tab 3 -- XEM HÓA ĐƠN. Trái: danh sách (sp_get_recent_invoices). Bấm một hóa đơn -> phải:
 * đầu hóa đơn + tổng tiền, các dòng gốc, hóa đơn điều chỉnh (sau trả hàng), thanh toán.
 *
 * Hóa đơn đã chốt là bản ghi audit: chỉ còn ba việc làm được trên nó --
 *   - Sửa khách hàng : đổi khách hàng (BR10), mọi trường khác bất biến (BR4)
 *   - Hủy hóa đơn    : Manager hủy hóa đơn PAID chưa có trả hàng (BR3)
 *   - Trả hàng       : Manager duyệt trả hàng, hóa đơn giữ PAID, trả được nhiều lần (BR15, BR16)
 * Không có đăng nhập, nên người duyệt được CHỌN trong danh sách Manager (sp_lookup_managers);
 * SQL vẫn tự kiểm Role = 'MANAGER' lần nữa.
 */
public class InvoiceViewPanel extends JPanel {

    private static final int[] DAYS = {7, 30, 90, 0};          // 0 = tất cả

    // Cột của sp_get_invoice_header: 3 Trang_thai, 12 Tien_hoan, 13 Tong_sau_dieu_chinh
    private static final int H_STATUS = 3, H_REFUND = 12, H_ADJUSTED = 13;

    private final InvoiceDao dao = new InvoiceDao();
    private final LookupDao  lookupDao = new LookupDao();

    private final JTable tbInvoices = Ui.readOnlyTable();
    private final JTable tbLines    = Ui.readOnlyTable();
    private final JTable tbAdjusted = Ui.readOnlyTable();
    private final JTable tbPayments = Ui.readOnlyTable();
    private final JTextArea taHeader = new JTextArea(8, 40);
    private final TitledBorder adjustedBorder = BorderFactory.createTitledBorder(noReturns());
    private final JComboBox<String> cbDays = new JComboBox<>(new String[]{
            I18n.text("7 ngày gần nhất", "Last 7 days"), I18n.text("30 ngày gần nhất", "Last 30 days"),
            I18n.text("90 ngày gần nhất", "Last 90 days"), I18n.text("Tất cả", "All")});
    private final JLabel lbCount = new JLabel(" ");

    private final JButton btEditCustomer = new JButton(I18n.text("Sửa khách hàng", "Edit customer"));
    private final JButton btVoid         = new JButton(I18n.text("Hủy hóa đơn", "Void invoice"));
    private final JButton btReturn       = new JButton(I18n.text("Trả hàng", "Return items"));

    public InvoiceViewPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        taHeader.setEditable(false);
        taHeader.setFont(Ui.MONO);
        btVoid.setForeground(new Color(190, 110, 20));
        btEditCustomer.setToolTipText(I18n.text(
                "Đổi khách hàng của hóa đơn -- mọi trường khác cố định từ khi hóa đơn đã thanh toán",
                "Change the customer of this invoice -- every other field is fixed once the invoice is paid"));
        btVoid.setToolTipText(I18n.text(
                "Quản lý hủy hóa đơn đã thanh toán chưa có trả hàng: hóa đơn vẫn còn nhưng không tính doanh thu",
                "A manager voids a paid invoice that has no returns: it is kept, but no longer counted in revenue"));
        btReturn.setToolTipText(I18n.text(
                "Quản lý duyệt trả hàng; hóa đơn giữ trạng thái Đã thanh toán và còn trả tiếp được",
                "A manager approves returning items; the invoice stays Paid and can take more returns later"));
        I18n.markDynamicText(lbCount);

        JButton btReload = new JButton(I18n.text("Tải lại", "Reload"));
        btReload.addActionListener(e -> reload());
        cbDays.setSelectedIndex(1);
        cbDays.addActionListener(e -> reload());

        JPanel south = new JPanel(new BorderLayout());
        south.add(lbCount, BorderLayout.NORTH);
        south.add(Ui.row(btEditCustomer, btVoid), BorderLayout.CENTER);
        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.setBorder(BorderFactory.createTitledBorder(I18n.text("Danh sách hóa đơn (bấm một dòng để xem chi tiết)",
                                                                  "Invoices (click one to see the details)")));
        left.add(Ui.row(new JLabel(I18n.text("Khoảng ngày:", "Date range:")), cbDays, btReload), BorderLayout.NORTH);
        left.add(new JScrollPane(tbInvoices), BorderLayout.CENTER);
        left.add(south, BorderLayout.SOUTH);

        JPanel original = Ui.titled(I18n.text("Hóa đơn gốc", "Original invoice"), new JScrollPane(tbLines));
        original.add(Ui.row(btReturn), BorderLayout.SOUTH);
        JPanel adjusted = new JPanel(new BorderLayout(2, 2));
        adjusted.setBorder(adjustedBorder);
        adjusted.add(new JScrollPane(tbAdjusted), BorderLayout.CENTER);
        JSplitPane lines = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, original, adjusted);
        lines.setResizeWeight(0.5);

        JPanel right = new JPanel(new GridLayout(3, 1, 6, 6));
        right.add(Ui.titled(I18n.text("Thông tin chung + tổng tiền", "Summary + totals"), new JScrollPane(taHeader)));
        right.add(lines);
        right.add(Ui.titled(I18n.text("Thanh toán", "Payments"), new JScrollPane(tbPayments)));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.42);
        add(split, BorderLayout.CENTER);

        btEditCustomer.addActionListener(e -> onEditCustomer());
        btVoid.addActionListener(e -> onVoidInvoice());
        btReturn.addActionListener(e -> onReturn());
        tbInvoices.setAutoCreateRowSorter(true);                // bấm tiêu đề cột để sắp xếp
        tbInvoices.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && selectedId() != null) showDetail(selectedId());
        });

        // Đổi ngôn ngữ: giá trị trong bảng (trạng thái, phương thức...) và khung chi tiết
        // được ghép lúc đổ dữ liệu, nên đổ lại theo ngôn ngữ mới.
        I18n.onLanguageChange(this, () -> {
            String id = selectedId();
            if (id != null) {
                refresh(id);
            } else {
                reload();
                adjustedBorder.setTitle(noReturns());       // viền ghép chữ, không nằm trong từ điển
                repaint();
            }
        });

        reload();
    }

    private static String noReturns() {
        return I18n.msg("Hóa đơn điều chỉnh -- (hóa đơn này chưa có trả hàng)",
                        "Adjusted invoice -- (no returns on this invoice)");
    }

    public void reload() {
        try {
            int days = DAYS[Math.max(cbDays.getSelectedIndex(), 0)];
            QueryResult qr = dao.recentInvoices(days);
            Ui.fill(tbInvoices, qr, new int[]{qr.columns.size() - 1}, null, null);    // cột cuối = tổng tiền
            lbCount.setText(I18n.msg(
                    "Đang hiện " + qr.rows.size() + " hóa đơn (" + (days == 0 ? "tất cả" : days + " ngày gần nhất") + ")",
                    "Showing " + qr.rows.size() + " invoice(s) (" + (days == 0 ? "all" : "last " + days + " days") + ")"));
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
                            v instanceof Number ? Ui.money(v) : v == null ? "" : Ui.val(v.toString())));
                }
            }
            taHeader.setText(sb.toString());
            taHeader.setCaretPosition(0);

            Ui.fill(tbLines, dao.invoiceLines(invoiceId), new int[]{4, 5, 6}, new int[]{2}, new int[]{7});
            Ui.fill(tbPayments, dao.invoicePayments(invoiceId), new int[]{1, 4}, null, null);

            BigDecimal refund = h.isEmpty() ? BigDecimal.ZERO : Ui.dec(h.rows.get(0)[H_REFUND]);
            if (refund.signum() > 0) {
                Ui.fill(tbAdjusted, dao.invoiceAdjusted(invoiceId), new int[]{7, 8, 9, 10}, new int[]{4, 5, 6}, null);
                adjustedBorder.setTitle(I18n.msg(
                        "Hóa đơn điều chỉnh (sau trả hàng) -- tổng mới " + Ui.money(h.rows.get(0)[H_ADJUSTED])
                                + ", đã hoàn " + Ui.money(refund),
                        "Adjusted invoice (after returns) -- new total " + Ui.money(h.rows.get(0)[H_ADJUSTED])
                                + ", refunded " + Ui.money(refund)));
            } else {
                Ui.clear(tbAdjusted);
                adjustedBorder.setTitle(noReturns());
            }
            repaint();
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
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

    private String requireInvoice(String actionVi, String actionEn) {
        String id = selectedId();
        if (id == null) {
            Ui.warn(this, I18n.msg("Vui lòng chọn một hóa đơn để " + actionVi + "!",
                                   "Please select an invoice to " + actionEn + "!"),
                    I18n.msg("Chưa chọn hóa đơn", "No Invoice Selected"));
        }
        return id;
    }

    private void notFound(String id) {
        Ui.warn(this, I18n.msg("Không tìm thấy hóa đơn " + id, "Cannot find invoice " + id),
                I18n.msg("Không tìm thấy", "Not Found"));
    }

    /** Danh sách Manager để chọn người duyệt; rỗng -> báo và trả null. */
    private List<IdName> managersOrWarn() {
        List<IdName> managers = lookupDao.managers();
        if (managers.isEmpty()) {
            Ui.warn(this, I18n.msg(
                    "Bảng Cashier không có nhân viên nào có vai trò MANAGER,\nnên không ai duyệt được thao tác này.",
                    "There is no employee with the MANAGER role in the Cashier table,\nso nobody can approve this action."),
                    I18n.msg("Không có quản lý", "No Manager"));
            return null;
        }
        return managers;
    }

    // =================================================================
    //  ĐỔI KHÁCH / HỦY HÓA ĐƠN
    // =================================================================

    private void onEditCustomer() {
        String id = requireInvoice("sửa", "edit");
        if (id == null) return;
        try {
            QueryResult qr = dao.invoiceEditFields(id);
            if (qr.isEmpty()) { notFound(id); return; }
            EditInvoiceDialog dlg = new EditInvoiceDialog(SwingUtilities.getWindowAncestor(this), dao, id, qr.rows.get(0));
            dlg.setVisible(true);
            if (dlg.isSaved()) refresh(id);
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    /**
     * HỦY khác XÓA: hóa đơn còn nguyên, chỉ không tính doanh thu và không trả hàng được nữa.
     * Chỉ hóa đơn PAID CHƯA có trả hàng mới hủy được (BR16) -- kiểm ở đây để báo sớm,
     * sp_void_invoice vẫn chặn lần nữa.
     */
    private void onVoidInvoice() {
        String id = requireInvoice("hủy", "void");
        if (id == null) return;
        String title = I18n.msg("Không hủy được hóa đơn", "Cannot Void Invoice");
        List<IdName> managers;
        try {
            QueryResult h = dao.invoiceHeader(id);
            if (h.isEmpty()) { notFound(id); return; }
            if (InvoiceStatus.of(String.valueOf(h.rows.get(0)[H_STATUS])) != InvoiceStatus.PAID) {
                Ui.warn(this, I18n.msg("Hóa đơn " + id + " đã bị hủy trước đó.", "Invoice " + id + " is already voided."), title);
                return;
            }
            if (Ui.dec(h.rows.get(0)[H_REFUND]).signum() > 0) {
                Ui.warn(this, I18n.msg(
                        "Hóa đơn " + id + " đã có trả hàng nên không hủy được.\n"
                      + "Nó phải giữ trạng thái Đã thanh toán để giữ doanh thu ròng và lịch sử trả hàng.",
                        "Invoice " + id + " already has returns, so it cannot be voided.\n"
                      + "It must stay Paid to keep its net revenue and return history."), title);
                return;
            }
            managers = managersOrWarn();
            if (managers == null) return;
        } catch (Exception ex) {
            Ui.error(this, ex);
            return;
        }

        JComboBox<IdName> cbManager = new JComboBox<>(managers.toArray(new IdName[0]));
        if (!Ui.ask(this, I18n.msg("Xác nhận hủy hóa đơn", "Confirm Void Invoice"), cbManager, I18n.msg(
                "Hủy hóa đơn " + id + "?\n\nHỦY KHÔNG PHẢI XÓA:\n"
              + "  - Hóa đơn vẫn còn nguyên: hàng, thanh toán\n"
              + "  - Không được tính vào doanh thu nữa\n"
              + "  - Không trả hàng được và KHÔNG mở lại được\n ",
                "Void invoice " + id + "?\n\nVoid is NOT Delete:\n"
              + "  - The invoice is kept: items and payments\n"
              + "  - It is no longer counted in revenue\n"
              + "  - No returns are possible and it CANNOT be reopened\n "),
                I18n.msg("Quản lý duyệt hủy:", "Approving manager:"), cbManager)) return;
        IdName manager = (IdName) cbManager.getSelectedItem();
        if (manager == null) return;

        Ui.async(this, () -> { dao.voidInvoice(id, manager.id); return id; }, null, v -> {
            JOptionPane.showMessageDialog(this, I18n.msg(
                    "Đã hủy hóa đơn " + id + " (duyệt bởi " + manager + ").\nHóa đơn vẫn còn nhưng không tính vào doanh thu.",
                    "Invoice " + id + " has been voided (approved by " + manager + ").\n"
                  + "It is kept, but no longer counted in revenue."),
                    I18n.msg("Đã hủy hóa đơn", "Invoice Voided"), JOptionPane.INFORMATION_MESSAGE);
            refresh(id);
        });
    }

    // =================================================================
    //  TRẢ HÀNG
    // =================================================================

    /** Chỉ hóa đơn PAID mới trả được; trả nhiều lần cho tới khi hết số lượng (BR16). */
    private void onReturn() {
        String id = requireInvoice("trả hàng", "return items from");
        if (id == null) return;
        try {
            QueryResult h = dao.invoiceHeader(id);
            if (h.isEmpty()) { notFound(id); return; }
            if (InvoiceStatus.of(String.valueOf(h.rows.get(0)[H_STATUS])) != InvoiceStatus.PAID) {
                Ui.warn(this, I18n.msg("Hóa đơn " + id + " đã bị hủy.\nChỉ hóa đơn đã thanh toán mới trả hàng được.",
                                       "Invoice " + id + " is voided.\nOnly paid invoices can be returned."),
                        I18n.msg("Không trả hàng được", "Cannot Return"));
                return;
            }
            QueryResult lines = dao.returnableLines(id);
            boolean anyLeft = false;
            for (Object[] r : lines.rows) if (Ui.dec(r[5]).signum() > 0) anyLeft = true;      // SL_con_lai
            if (!anyLeft) {
                Ui.warn(this, I18n.msg("Mọi mặt hàng của hóa đơn " + id + " đã được trả hết.",
                                       "Every item of invoice " + id + " has already been returned."),
                        I18n.msg("Không còn gì để trả", "Nothing to Return"));
                return;
            }
            List<IdName> managers = managersOrWarn();
            if (managers == null) return;
            ReturnDialog dlg = new ReturnDialog(SwingUtilities.getWindowAncestor(this), dao, id,
                    I18n.msg("Hóa đơn: " + id + "  |  Ngày: " + h.rows.get(0)[1] + "  |  Trạng thái: đã thanh toán",
                             "Invoice: " + id + "  |  Date: " + h.rows.get(0)[1] + "  |  Status: paid"),
                    lines, managers);
            dlg.setVisible(true);
            if (dlg.isSaved()) refresh(id);
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }
}
