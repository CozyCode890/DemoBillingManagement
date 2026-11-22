package billing.ui;

import billing.dao.InvoiceDao;
import billing.db.QueryResult;
import billing.model.InvoiceStatus;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;

public class ReceiptDialog {
    private record Data(QueryResult header, QueryResult lines, QueryResult payments) { }

    public static void show(Component parent, InvoiceDao dao, String invoiceId) {
        Ui.async(parent, () -> new Data(dao.invoiceHeader(invoiceId), dao.invoiceLines(invoiceId),
                                        dao.invoicePayments(invoiceId)), null, d -> {
            if (d.header().isEmpty()) {
                Ui.warn(parent, I18n.msg("Hóa đơn " + invoiceId + " đã lưu nhưng không đọc lại được để in.",
                                         "Invoice " + invoiceId + " was saved but cannot be read back to print."),
                        I18n.msg("Không đọc được hóa đơn", "Cannot Read Invoice"));
                return;
            }
            JTextPane pane = new JTextPane();
            pane.setContentType("text/html");
            pane.setText(buildHtml(d));
            pane.setEditable(false);
            pane.setCaretPosition(0);
            JScrollPane scroll = new JScrollPane(pane);
            scroll.setPreferredSize(new Dimension(560, 640));

            Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
            JDialog dialog = new JDialog(owner, I18n.msg("HÓA ĐƠN -- ", "RECEIPT -- ") + invoiceId, Dialog.ModalityType.APPLICATION_MODAL);
            JButton btPrint = new JButton(I18n.text("In", "Print"));
            btPrint.addActionListener(e -> {
                try {
                    pane.print();
                } catch (Exception ex) {
                    Ui.error(dialog, ex);
                }
            });
            JButton btClose = new JButton(I18n.text("Đóng", "Close"));
            btClose.addActionListener(e -> dialog.dispose());
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 10));
            buttons.add(btPrint);
            buttons.add(btClose);
            dialog.add(scroll, BorderLayout.CENTER);
            dialog.add(buttons, BorderLayout.SOUTH);
            dialog.getRootPane().setDefaultButton(btClose);
            dialog.pack();
            dialog.setLocationRelativeTo(parent);
            dialog.setVisible(true);
        });
    }

    private static String buildHtml(Data d) {
        Object[] h = d.header().rows.get(0);
        BigDecimal due = Ui.dec(h[11]), refund = Ui.dec(h[12]);

        BigDecimal recorded = BigDecimal.ZERO, tendered = BigDecimal.ZERO, change = BigDecimal.ZERO;
        StringBuilder pays = new StringBuilder();
        for (Object[] p : d.payments().rows) {
            BigDecimal amount = Ui.dec(p[1]);
            BigDecimal ch = p[4] == null ? BigDecimal.ZERO : Ui.dec(p[4]).subtract(amount).max(BigDecimal.ZERO);
            recorded = recorded.add(amount);
            if (p[4] != null) tendered = tendered.add(Ui.dec(p[4]));
            change = change.add(ch);
            pays.append(tr("lrrrl", esc(Ui.val(str(p[2]))), "<b>" + Ui.money(amount) + "</b>",
                    p[4] == null ? "" : Ui.money(p[4]), p[4] == null ? "" : Ui.money(ch), esc(str(p[3]))));
        }
        BigDecimal missing = due.subtract(recorded);

        StringBuilder s = new StringBuilder("<html><body style='font-family:Segoe UI,Arial,sans-serif;"
                + " color:#333333; padding:10px 18px; background-color:#ffffff;'><div align='center'>");
        s.append("<font size='+2'><b>").append(esc(str(h[5]))).append("</b></font><br/>")
         .append("<font size='-1' color='#555555'>").append(esc(str(h[6]))).append("</font><br/>")
         .append("<font size='+1'><b>" + I18n.msg("HÓA ĐƠN BÁN LẺ", "RETAIL INVOICE") + "</b></font></div>").append(DASH)
         .append(statusBlock(str(h[3]), missing, refund)).append(DASH);

        s.append(kv(I18n.msg("Mã hóa đơn", "Invoice ID"), str(h[0]))).append(kv(I18n.msg("Thời gian", "Time"), h[1] + " " + h[2]))
         .append(kv(I18n.msg("Quầy", "Counter"), str(h[4]))).append(kv(I18n.msg("Thu ngân", "Cashier"), str(h[7])))
         .append(kv(I18n.msg("Khách hàng", "Customer"), Ui.val(str(h[8])))).append(DASH);

        s.append("<table width='100%' cellpadding='3' cellspacing='0' style='font-size:12px;'>")
         .append(th("lrlrrrr", I18n.msg("Mặt hàng", "Item"), I18n.msg("SL", "Qty"), I18n.msg("ĐVT", "Unit"),
                    I18n.msg("Đơn giá", "Price"), I18n.msg("Giảm", "Discount"), I18n.msg("Thành tiền", "Total"), "VAT %"));
        for (Object[] l : d.lines().rows) {
            s.append(tr("lrlrrrr", esc(str(l[1])), Ui.qty(l[2]), esc(str(l[3])), Ui.money(l[4]), Ui.money(l[5]),
                        "<b>" + Ui.money(l[6]) + "</b>", Ui.percent(l[7])));
        }
        s.append("</table><div align='right'><font size='-2' color='#888888'>" + I18n.msg(
                "đơn giá đã gồm VAT -- thuế suất chỉ để tham khảo", "unit prices include VAT -- the tax rate is for information only")
                + "</font></div>").append(DASH);

        s.append("<table width='100%' cellpadding='3' cellspacing='0' style='font-size:13px;'>")
         .append(total(I18n.msg("Tổng tiền hàng", "Subtotal"), Ui.money(h[9]), false))
         .append(total(I18n.msg("Giảm giá / Khuyến mãi", "Discount / Promotion"), Ui.money(h[10]), false))
         .append(total(I18n.msg("TỔNG CỘNG", "TOTAL"), Ui.money(due), true));
        if (refund.signum() > 0) {
            s.append(total(I18n.msg("Trả hàng (đã hoàn)", "Returned (refunded)"), "-" + Ui.money(refund), false))
             .append(total(I18n.msg("TỔNG SAU TRẢ HÀNG", "TOTAL AFTER RETURNS"), Ui.money(h[13]), true));
        }
        s.append("</table>").append(DASH);

        s.append("<table width='100%' cellpadding='3' cellspacing='0' style='font-size:12px;'>")
         .append(th("lrrrl", I18n.msg("Phương thức", "Method"), I18n.msg("Ghi nhận", "Amount"), I18n.msg("Khách đưa", "Tendered"),
                    I18n.msg("Tiền thối", "Change"), I18n.msg("Chi tiết", "Details")))
         .append(d.payments().rows.isEmpty() ? "<tr><td colspan='5' style='color:#999999;'>" + I18n.msg("(chưa có thanh toán)", "(no payments yet)") + "</td></tr>" : pays.toString())
         .append("</table><table width='100%' cellpadding='3' cellspacing='0' style='font-size:13px;'>")
         .append(total(I18n.msg("Khách đưa (tiền mặt)", "Cash tendered"), Ui.money(tendered), false))
         .append(total(I18n.msg("Đã ghi nhận", "Recorded"), Ui.money(recorded), false))
         .append(total(I18n.msg("Tiền thối", "Change"), Ui.money(change), true))
         .append("</table>").append(DASH)
         .append("<div align='center'><font size='-1' color='#777777'>" + I18n.msg("Cảm ơn quý khách &amp; hẹn gặp lại!", "Thank you &amp; see you again!") + "</font></div>")
         .append("</body></html>");
        return s.toString();
    }

    private static String statusBlock(String raw, BigDecimal missing, BigDecimal refund) {
        InvoiceStatus st = InvoiceStatus.of(raw);
        String text = raw, color = "#555555";
        if (st == InvoiceStatus.PAID) {
            text  = missing.signum() > 0 ? I18n.msg("ĐÃ THANH TOÁN -- còn thiếu ", "PAID -- remaining ") + Ui.money(missing)
                  : refund.signum() > 0  ? I18n.msg("ĐÃ THANH TOÁN (CÓ TRẢ HÀNG) -- XEM TỔNG SAU TRẢ HÀNG",
                                                    "PAID (WITH RETURNS) -- SEE TOTAL AFTER RETURNS")
                  : I18n.msg("ĐÃ THANH TOÁN", "PAID");
            color = missing.signum() > 0 || refund.signum() > 0 ? "#d35400" : "#1b7f1b";
        } else if (st == InvoiceStatus.VOIDED) {
            text  = I18n.msg("ĐÃ HỦY", "VOIDED");
            color = "#c0392b";
        }
        return "<div align='center'><font size='+1' color='" + color + "'><b>" + esc(text) + "</b></font></div>";
    }

    private static final String DASH = "<div style='border-top:1px dashed #bbbbbb; margin:8px 0;'></div>";

    private static String kv(String key, String value) {
        return "<div><span style='color:#555555;'><b>" + key + ":</b> </span>" + esc(value) + "</div>";
    }

    private static String total(String label, String value, boolean strong) {
        String b = strong ? "<b>" : "", eb = strong ? "</b>" : "";
        return "<tr><td>" + b + label + eb + "</td><td align='right' nowrap>" + b + value + eb + "</td></tr>";
    }

    private static String th(String align, String... cols) {
        StringBuilder s = new StringBuilder("<tr style='color:#666666;'>");
        for (int i = 0; i < cols.length; i++) {
            s.append(align.charAt(i) == 'r' ? "<th align='right' nowrap>" : "<th align='left'>").append(cols[i]).append("</th>");
        }
        return s.append("</tr>").toString();
    }

    private static String tr(String align, String... cells) {
        StringBuilder s = new StringBuilder("<tr>");
        for (int i = 0; i < cells.length; i++) {
            s.append(align.charAt(i) == 'r' ? "<td align='right' nowrap>" : "<td>").append(cells[i]).append("</td>");
        }
        return s.append("</tr>").toString();
    }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }

    private static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    private ReceiptDialog() { }
}
