package billing2.ui;

import billing2.dao.InvoiceDao;
import billing2.db.QueryResult;
import billing2.model.InvoiceStatus;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;

/**
 * Phiếu in hóa đơn -- đọc LẠI từ database sau khi lưu (header + lines + payments),
 * nên con số trên phiếu đúng là con số đã nằm trong MySQL.
 */
public class ReceiptDialog {

    private record Data(QueryResult header, QueryResult lines, QueryResult payments) { }

    public static void show(Component parent, InvoiceDao dao, String invoiceId) {
        Ui.async(parent, () -> new Data(dao.invoiceHeader(invoiceId), dao.invoiceLines(invoiceId),
                                        dao.invoicePayments(invoiceId)), null, d -> {
            if (d.header().isEmpty()) {
                Ui.warn(parent, "Invoice " + invoiceId + " was saved but cannot be read back to print.", "Cannot Read Invoice");
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
            JDialog dialog = new JDialog(owner, "RECEIPT -- " + invoiceId, Dialog.ModalityType.APPLICATION_MODAL);
            JButton btPrint = new JButton("Print");
            btPrint.addActionListener(e -> {
                try {
                    pane.print();
                } catch (Exception ex) {
                    Ui.error(dialog, ex);
                }
            });
            JButton btClose = new JButton("Close");
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
        // Cột của sp_get_invoice_header: 0 Ma_HD, 1 Ngay, 2 Gio, 3 Trang_thai, 4 Quay, 5 Shop, 6 Dia_chi,
        // 7 Thu_ngan, 8 Khach_hang, 9 Tong_truoc_giam, 10 Tong_giam, 11 Tong_phai_tra, 12 Tien_hoan, 13 Tong_sau_dieu_chinh
        Object[] h = d.header().rows.get(0);
        BigDecimal due = Ui.dec(h[11]), refund = Ui.dec(h[12]);

        // Thanh toán: 0 Ma_TT, 1 So_tien, 2 Phuong_thuc, 3 Chi_tiet, 4 Tien_khach_dua (chỉ tiền mặt)
        BigDecimal recorded = BigDecimal.ZERO, tendered = BigDecimal.ZERO, change = BigDecimal.ZERO;
        StringBuilder pays = new StringBuilder();
        for (Object[] p : d.payments().rows) {
            BigDecimal amount = Ui.dec(p[1]);
            BigDecimal ch = p[4] == null ? BigDecimal.ZERO : Ui.dec(p[4]).subtract(amount).max(BigDecimal.ZERO);
            recorded = recorded.add(amount);
            if (p[4] != null) tendered = tendered.add(Ui.dec(p[4]));
            change = change.add(ch);                // tiền thối cộng theo TỪNG dòng tiền mặt
            pays.append(tr("lrrrl", esc(Ui.en(str(p[2]))), "<b>" + Ui.money(amount) + "</b>",
                    p[4] == null ? "" : Ui.money(p[4]), p[4] == null ? "" : Ui.money(ch), esc(str(p[3]))));
        }
        BigDecimal missing = due.subtract(recorded);

        StringBuilder s = new StringBuilder("<html><body style='font-family:Segoe UI,Arial,sans-serif;"
                + " color:#333333; padding:10px 18px; background-color:#ffffff;'><div align='center'>");
        s.append("<font size='+2'><b>").append(esc(str(h[5]))).append("</b></font><br/>")
         .append("<font size='-1' color='#555555'>").append(esc(str(h[6]))).append("</font><br/>")
         .append("<font size='+1'><b>RETAIL INVOICE</b></font></div>").append(DASH)
         .append(statusBlock(str(h[3]), missing, refund)).append(DASH);

        s.append(kv("Invoice ID", str(h[0]))).append(kv("Time", h[1] + " " + h[2]))
         .append(kv("Counter", str(h[4]))).append(kv("Cashier", str(h[7])))
         .append(kv("Customer", Ui.en(str(h[8])))).append(DASH);

        // Dòng hàng: 0 Dong, 1 San_pham, 2 SL, 3 DVT, 4 Don_gia, 5 Giam_gia, 6 Thanh_tien, 7 VAT_pct
        s.append("<table width='100%' cellpadding='3' cellspacing='0' style='font-size:12px;'>")
         .append(th("lrlrrrr", "Item", "Qty", "Unit", "Price", "Discount", "Total", "VAT %"));
        for (Object[] l : d.lines().rows) {
            s.append(tr("lrlrrrr", esc(str(l[1])), Ui.qty(l[2]), esc(str(l[3])), Ui.money(l[4]), Ui.money(l[5]),
                        "<b>" + Ui.money(l[6]) + "</b>", Ui.percent(l[7])));
        }
        s.append("</table><div align='right'><font size='-2' color='#888888'>unit prices include VAT"
                + " -- the tax rate is for information only</font></div>").append(DASH);

        s.append("<table width='100%' cellpadding='3' cellspacing='0' style='font-size:13px;'>")
         .append(total("Subtotal", Ui.money(h[9]), false))
         .append(total("Discount / Promotion", Ui.money(h[10]), false))
         .append(total("TOTAL", Ui.money(due), true));
        if (refund.signum() > 0) {
            s.append(total("Returned (refunded)", "-" + Ui.money(refund), false))
             .append(total("TOTAL AFTER RETURNS", Ui.money(h[13]), true));
        }
        s.append("</table>").append(DASH);

        s.append("<table width='100%' cellpadding='3' cellspacing='0' style='font-size:12px;'>")
         .append(th("lrrrl", "Method", "Amount", "Tendered", "Change", "Details"))
         .append(d.payments().rows.isEmpty() ? "<tr><td colspan='5' style='color:#999999;'>(no payments yet)</td></tr>" : pays.toString())
         .append("</table><table width='100%' cellpadding='3' cellspacing='0' style='font-size:13px;'>")
         .append(total("Cash tendered", Ui.money(tendered), false))
         .append(total("Recorded", Ui.money(recorded), false))
         .append(total("Change", Ui.money(change), true))
         .append("</table>").append(DASH)
         .append("<div align='center'><font size='-1' color='#777777'>Thank you &amp; see you again!</font></div>")
         .append("</body></html>");
        return s.toString();
    }

    private static String statusBlock(String raw, BigDecimal missing, BigDecimal refund) {
        InvoiceStatus st = InvoiceStatus.of(raw);
        String text = raw, color = "#555555";
        if (st == InvoiceStatus.PAID) {
            text  = missing.signum() > 0 ? "PAID -- remaining " + Ui.money(missing) : "PAID";
            color = missing.signum() > 0 ? "#d35400" : "#1b7f1b";
        } else if (st == InvoiceStatus.VOIDED) {
            text  = refund.signum() > 0 ? "ITEMS RETURNED -- SEE TOTAL AFTER RETURNS" : "VOIDED";
            color = refund.signum() > 0 ? "#d35400" : "#c0392b";
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

    /** align: một ký tự cho mỗi cột, 'l' = trái (được xuống dòng, vd tên hàng), 'r' = phải (số, không xuống dòng). */
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
