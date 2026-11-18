package billing.ui;

import billing.dao.InvoiceDao;
import billing.db.QueryResult;
import billing.model.InvoiceStatus;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.math.BigDecimal;

/**
 * ====================================================================
 *  ReceiptDialog -- HÓA ĐƠN IN RA, ĐỌC TỪ DATABASE
 * ====================================================================
 *
 *  <p>Thay {@code NewInvoicePanel.showReceiptPopup} (186 dòng dựng HTML từ giỏ
 *  hàng trong bộ nhớ). <b>6 trong 29 lỗi của khối 5 nằm trong đúng 186 dòng
 *  đó</b>, và mọi lỗi ấy là một dạng của "màn hình nói khác database":
 *
 *  <ul>
 *    <li>#4 số lượng bị làm tròn về số nguyên ({@code quantity.intValue()}) --
 *        {@code 1,5 KG} thịt in thành {@code 1};</li>
 *    <li>#5 nhánh tiền mặt là CODE CHẾT: so {@code "Tiền mặt"} (có dấu) với
 *        {@code methodLabel()} trả {@code "Tien mat"} (không dấu), nên 12 dòng
 *        "Tiền khách đưa / Tiền thừa thối lại" <b>chưa từng chạy một lần nào</b>;</li>
 *    <li>#6 chỉ đọc {@code payments.get(0)} -- mất hẳn lần thanh toán thứ hai,
 *        trên một khung mang tên "một hoá đơn có thể có NHIỀU dòng thanh toán";</li>
 *    <li>#9 tiền khách đưa và tiền ghi nhận bị trộn vào một số;</li>
 *    <li>#29 tên cửa hàng ghi cứng {@code "SIÊU THỊ MINI MART"} trong khi
 *        {@code sp_get_invoice_header} trả về {@code Shop} và {@code Dia_chi} thật;</li>
 *    <li>#28 nút "In hóa đơn" chỉ hiện <i>"Đã gửi lệnh in hóa đơn."</i> rồi
 *        không in gì.</li>
 *  </ul>
 *
 *  <p><b>Cách sửa cả sáu bằng một quyết định:</b> đọc lại từ database sau khi
 *  {@code commit}, bằng ba thủ tục đã có -- {@code sp_get_invoice_header},
 *  {@code sp_get_invoice_lines}, {@code sp_get_invoice_payments}. Hoá đơn in ra
 *  <b>không thể</b> nói khác tab 3 vì nó đọc cùng nguồn (plan/14 mục 5.7).
 *  Không cần thủ tục mới.
 */
public class ReceiptDialog {

    /** Ba kết quả đọc được, gói lại để truyền từ luồng nền về EDT. */
    private static class Data {
        QueryResult header;
        QueryResult lines;
        QueryResult payments;
    }

    /**
     * Đọc hoá đơn {@code invoiceId} rồi hiện hoá đơn in ra.
     *
     * <p>Ba câu gọi thủ tục chạy NGOÀI EDT. Đọc thất bại thì báo lỗi và không
     * hiện gì -- hoá đơn <b>đã</b> được lưu, nên đây không phải lỗi của việc lưu.
     */
    public static void show(Component parent, InvoiceDao dao, String invoiceId) {
        new SwingWorker<Data, Void>() {
            @Override
            protected Data doInBackground() {
                Data d = new Data();
                d.header   = dao.invoiceHeader(invoiceId);
                d.lines    = dao.invoiceLines(invoiceId);
                d.payments = dao.invoicePayments(invoiceId);
                return d;
            }

            @Override
            protected void done() {
                Data d;
                try {
                    d = get();
                } catch (Exception ex) {
                    Ui.error(parent, ex);
                    return;
                }
                if (d.header.isEmpty()) {
                    Ui.warn(parent, "Da luu hoa don " + invoiceId
                                  + " nhung khong doc lai duoc de in.",
                            "Khong doc duoc hoa don");
                    return;
                }
                display(parent, invoiceId, d);
            }
        }.execute();
    }

    // -----------------------------------------------------------------

    private static void display(Component parent, String invoiceId, Data d) {
        Window owner = (parent == null) ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, I18n.translate("HOA DON BAN LE -- ") + invoiceId,
                                     Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.getContentPane().setLayout(new BorderLayout());

        JTextPane pane = new JTextPane();
        pane.setContentType("text/html");
        pane.setText(buildHtml(d));
        pane.setEditable(false);
        pane.setBackground(Color.WHITE);
        pane.setCaretPosition(0);
        I18n.onLanguageChange(pane, () -> {
            pane.setText(buildHtml(d));
            pane.setCaretPosition(0);
        });
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) {
                I18n.removeLanguageChangeListener(pane);
            }
        });

        JScrollPane scroll = new JScrollPane(pane);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setPreferredSize(new Dimension(520, 600));
        dialog.getContentPane().add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 10));

        JButton btPrint = new JButton("In hoa don");
        btPrint.addActionListener(e -> doPrint(dialog, pane));

        JButton btClose = new JButton("Dong");
        btClose.addActionListener(e -> dialog.dispose());

        buttons.add(btPrint);
        buttons.add(btClose);
        dialog.getContentPane().add(buttons, BorderLayout.SOUTH);

        dialog.getRootPane().setDefaultButton(btClose);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);

        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    /**
     * IN THẬT (plan/14 lỗi #28).
     *
     * <p>{@code JTextComponent.print()} tự mở hộp thoại máy in của hệ điều hành
     * và trả {@code false} khi người dùng bấm Cancel. Không có máy in thì chính
     * hộp thoại đó nói ra, chứ không phải app nói dối là đã in.
     *
     * <p>Đúng là lời gọi này CHẶN EDT trong lúc hộp thoại máy in mở -- nhưng đó
     * là hộp thoại của hệ điều hành và người dùng đang chủ động chờ nó, khác hẳn
     * việc chặn EDT vì một câu SQL (plan/14 rủi ro #8).
     */
    private static void doPrint(Component parent, JTextPane pane) {
        try {
            pane.print();                 // false = nguoi dung bam Cancel -> khong noi gi
        } catch (Exception ex) {
            Ui.error(parent, ex);
        }
    }

    // -----------------------------------------------------------------
    //  Dựng HTML -- MỌI con số đi qua Ui.money / Ui.qty / Ui.percent
    // -----------------------------------------------------------------

    private static String buildHtml(Data d) {
        // sp_get_invoice_header: 0 Ma_HD | 1 Ngay | 2 Gio | 3 Trang_thai | 4 Quay
        //   5 Shop | 6 Dia_chi | 7 Thu_ngan | 8 Khach_hang
        //   9 Tong_truoc_giam | 10 Tong_giam | 11 Tong_phai_tra
        //   12 Tien_hoan | 13 Tong_sau_dieu_chinh   (hoa don dieu chinh)
        Object[] h = d.header.rows.get(0);
        String     invoiceId = str(h[0]);
        String     date      = str(h[1]);
        String     time      = str(h[2]);
        String     statusRaw = str(h[3]);
        String     counter   = str(h[4]);
        String     shop      = str(h[5]);
        String     address   = str(h[6]);
        String     cashier   = str(h[7]);
        String     customer  = str(h[8]);
        BigDecimal beforeDisc = Ui.dec(h[9]);
        BigDecimal totalDisc  = Ui.dec(h[10]);
        BigDecimal due        = Ui.dec(h[11]);
        // Hai cot cuoi them khi co hoa don dieu chinh; doc phong thu theo
        // do dai de phieu van in duoc neu database chua nap routine moi.
        BigDecimal refund     = h.length > 13 ? Ui.dec(h[12]) : BigDecimal.ZERO;
        BigDecimal adjusted   = h.length > 13 ? Ui.dec(h[13]) : due;

        BigDecimal recorded = BigDecimal.ZERO;
        BigDecimal tendered = BigDecimal.ZERO;
        for (Object[] p : d.payments.rows) {
            recorded = recorded.add(Ui.dec(p[1]));
            if (p[4] != null) tendered = tendered.add(Ui.dec(p[4]));
        }
        BigDecimal change  = tendered.subtract(recorded);
        if (change.signum() < 0) change = BigDecimal.ZERO;
        BigDecimal missing = due.subtract(recorded);

        StringBuilder s = new StringBuilder(4096);
        s.append("<html><body style=\"font-family: 'Segoe UI', Arial, sans-serif;")
         .append(" color:#333333; padding:10px 18px; background-color:#ffffff;\">");

        // --- Đầu hoá đơn: TÊN SHOP THẬT từ bảng Shop (lỗi #29) ----------
        s.append("<div align=\"center\">");
        if (!shop.isEmpty()) {
            s.append("<font size=\"+2\"><b>").append(esc(shop)).append("</b></font><br/>");
        }
        if (!address.isEmpty()) {
            s.append("<font size=\"-1\" color=\"#555555\">").append(esc(address)).append("</font><br/>");
        }
        s.append("<font size=\"+1\"><b>").append(I18n.translate("HOA DON BAN LE"))
         .append("</b></font>");
        s.append("</div>");
        s.append(dash());

        // --- Dòng trạng thái, chữ to ---
        s.append(statusBlock(statusRaw, missing, refund));
        s.append(dash());

        // --- Thông tin giao dịch ---------------------------------------
        s.append(kv(I18n.msg("Mã HĐ", "Invoice ID"), esc(invoiceId)));
        s.append(kv(I18n.msg("Thời gian", "Time"), esc(date) + " " + esc(time)));
        s.append(kv(I18n.msg("Quầy", "Counter"), esc(counter)));
        s.append(kv(I18n.msg("Thu ngân", "Cashier"), esc(cashier)));
        s.append(kv(I18n.msg("Khách hàng", "Customer"), esc(customer)));
        s.append(dash());

        // --- Các dòng hàng: SL qua Ui.qty (lỗi #4), VAT qua Ui.percent ---
        // sp_get_invoice_lines: 0 Dong | 1 San_pham | 2 SL | 3 DVT
        //   4 Don_gia | 5 Giam_gia | 6 Thanh_tien | 7 VAT_pct
        s.append("<table width=\"100%\" cellpadding=\"3\" cellspacing=\"0\" border=\"0\"")
         .append(" style=\"font-size:12px;\">");
        s.append("<tr style=\"color:#666666;\">")
         .append("<th align=\"left\">").append(I18n.translate("Ten hang")).append("</th>")
         .append("<th align=\"right\" nowrap>").append(I18n.translate("SL")).append("</th>")
         .append("<th align=\"left\">").append(I18n.translate("DVT")).append("</th>")
         .append("<th align=\"right\" nowrap>").append(I18n.translate("Don gia")).append("</th>")
         .append("<th align=\"right\" nowrap>").append(I18n.translate("Giam gia")).append("</th>")
         .append("<th align=\"right\" nowrap>").append(I18n.translate("Thanh tien")).append("</th>")
         .append("<th align=\"right\" nowrap>").append(I18n.translate("VAT %")).append("</th>")
         .append("</tr>");
        for (Object[] l : d.lines.rows) {
            s.append("<tr>")
             .append("<td align=\"left\" style=\"color:#222222;\">").append(esc(str(l[1]))).append("</td>")
             .append("<td align=\"right\" nowrap>").append(Ui.qty(l[2])).append("</td>")
             .append("<td align=\"left\" style=\"color:#555555;\">").append(esc(str(l[3]))).append("</td>")
             .append("<td align=\"right\" nowrap>").append(Ui.money(l[4])).append("</td>")
             .append("<td align=\"right\" nowrap>").append(Ui.money(l[5])).append("</td>")
             .append("<td align=\"right\" nowrap><b>").append(Ui.money(l[6])).append("</b></td>")
             .append("<td align=\"right\" nowrap style=\"color:#555555;\">")
             .append(Ui.percent(l[7])).append("</td>")
             .append("</tr>");
        }
        s.append("</table>");
        s.append("<div align=\"right\"><font size=\"-2\" color=\"#888888\">")
         .append(I18n.translate("don gia da gom VAT -- thue suat chi la thong tin"))
         .append("</font></div>");
        s.append(dash());

        // --- Tổng tiền: ba con số của sp_get_invoice_header -------------
        s.append("<table width=\"100%\" cellpadding=\"3\" cellspacing=\"0\" border=\"0\"")
         .append(" style=\"font-size:13px;\">");
        s.append(row2(I18n.msg("Tổng tiền hàng", "Subtotal"), Ui.money(beforeDisc), false));
        s.append(row2(I18n.msg("Giảm giá / Khuyến mãi", "Discount / Promotion"), Ui.money(totalDisc), false));
        s.append(row2(I18n.msg("TỔNG CỘNG", "TOTAL"), Ui.money(due), true));
        if (refund.signum() > 0) {
            // Hoa don dieu chinh = Invoice_Line - Return: hoa don goc da
            // VOIDED vi tra hang, con lai la gia tri sau khi tru phan tra.
            s.append(row2(I18n.msg("Trả hàng (đã hoàn)", "Returned (refunded)"), "-" + Ui.money(refund), false));
            s.append(row2(I18n.msg("TỔNG SAU ĐIỀU CHỈNH", "TOTAL AFTER RETURNS"), Ui.money(adjusted), true));
        }
        s.append("</table>");
        s.append(dash());

        // --- Thanh toán: TẤT CẢ các dòng (lỗi #6), có khách đưa / thối (#5, #9) ---
        // sp_get_invoice_payments: 0 Ma_TT | 1 So_tien | 2 Phuong_thuc
        //   3 Chi_tiet | 4 Tien_khach_dua
        s.append("<table width=\"100%\" cellpadding=\"3\" cellspacing=\"0\" border=\"0\"")
         .append(" style=\"font-size:12px;\">");
        s.append("<tr style=\"color:#666666;\">")
         .append("<th align=\"left\">").append(I18n.translate("Phuong thuc")).append("</th>")
         .append("<th align=\"right\" nowrap>").append(I18n.translate("So tien")).append("</th>")
         .append("<th align=\"right\" nowrap>").append(I18n.translate("Khach dua")).append("</th>")
         .append("<th align=\"right\" nowrap>").append(I18n.translate("Tien thoi")).append("</th>")
         .append("<th align=\"left\">").append(I18n.translate("Chi tiet")).append("</th>")
         .append("</tr>");
        if (d.payments.rows.isEmpty()) {
            s.append("<tr><td colspan=\"5\" style=\"color:#999999;\">")
             .append(I18n.translate("(chua co dong thanh toan nao)")).append("</td></tr>");
        }
        for (Object[] p : d.payments.rows) {
            BigDecimal amt  = Ui.dec(p[1]);
            boolean    hasT = p[4] != null;
            BigDecimal ten  = hasT ? Ui.dec(p[4]) : BigDecimal.ZERO;
            BigDecimal ch   = hasT ? ten.subtract(amt) : BigDecimal.ZERO;
            if (ch.signum() < 0) ch = BigDecimal.ZERO;

            s.append("<tr>")
             .append("<td align=\"left\">").append(esc(str(p[2]))).append("</td>")
             .append("<td align=\"right\" nowrap><b>").append(Ui.money(amt)).append("</b></td>")
             .append("<td align=\"right\" nowrap>").append(hasT ? Ui.money(ten) : "").append("</td>")
             .append("<td align=\"right\" nowrap>").append(hasT ? Ui.money(ch) : "").append("</td>")
             .append("<td align=\"left\" style=\"color:#555555;\">").append(esc(str(p[3]))).append("</td>")
             .append("</tr>");
        }
        s.append("</table>");

        s.append("<table width=\"100%\" cellpadding=\"3\" cellspacing=\"0\" border=\"0\"")
         .append(" style=\"font-size:13px;\">");
        s.append(row2(I18n.msg("Tiền khách đưa", "Cash tendered"), Ui.money(tendered), false));
        s.append(row2(I18n.msg("Đã ghi nhận", "Recorded"), Ui.money(recorded), false));
        s.append(row2(I18n.msg("Tiền thối lại", "Change"), Ui.money(change), true));
        s.append("</table>");

        // --- Chân hoá đơn ----------------------------------------------
        s.append(dash());
        s.append("<div align=\"center\"><font size=\"-1\" color=\"#777777\">")
         .append(I18n.translate("Cam on Quy khach &amp; Hen gap lai!"))
         .append("</font></div>");

        s.append("</body></html>");
        return s.toString();
    }

    /**
     * Dòng trạng thái.
     *
     * <p>Trạng thái {@code OPEN} đã bị bỏ, nên một hóa đơn lưu được thì đã
     * thu đủ tiền lúc lập. {@code missing} vẫn được hiện cho hóa đơn PAID nếu
     * sau đó quản lý sửa dòng hàng làm tổng tăng (EditInvoiceDialog cảnh báo
     * cùng tình huống này) -- một tờ in ra trông như đã thu đủ trong khi
     * thiếu tiền là sai (plan/14 mục 5.7).
     */
    private static String statusBlock(String statusRaw, BigDecimal missing, BigDecimal refund) {
        InvoiceStatus st = InvoiceStatus.of(statusRaw);
        String text;
        String color;
        if (st == InvoiceStatus.PAID) {
            text  = I18n.msg("ĐÃ THANH TOÁN", "PAID");
            color = "#1b7f1b";
            if (missing.signum() > 0) {
                text += I18n.msg(" -- còn thiếu ", " -- remaining ") + Ui.money(missing);
                color = "#d35400";
            }
        } else if (st == InvoiceStatus.VOIDED && refund.signum() > 0) {
            // VOIDED vi TRA HANG, khong phai bi huy: in "Da huy" o day la noi
            // sai -- doanh thu cua hoa don nay van duoc tinh (phan con lai).
            text  = I18n.msg("ĐÃ TRẢ HÀNG -- XEM TỔNG SAU ĐIỀU CHỈNH",
                             "ITEMS RETURNED -- SEE TOTAL AFTER RETURNS");
            color = "#d35400";
        } else if (st == InvoiceStatus.VOIDED) {
            text  = I18n.msg("ĐÃ HỦY", "VOIDED");
            color = "#c0392b";
        } else {
            text  = statusRaw;                  // gia tri la trong cot Status
            color = "#555555";
        }
        return "<div align=\"center\"><font size=\"+1\" color=\"" + color + "\"><b>"
             + esc(text) + "</b></font></div>";
    }

    private static String dash() {
        return "<div style=\"border-top:1px dashed #bbbbbb; margin:8px 0;\"></div>";
    }

    private static String kv(String key, String value) {
        return "<div><span style=\"color:#555555;\"><b>" + I18n.translate(key) + ":</b> </span>" + value + "</div>";
    }

    private static String row2(String label, String value, boolean strong) {
        String open  = strong ? "<b>" : "";
        String close = strong ? "</b>" : "";
        return "<tr><td align=\"left\"><span style=\"color:#555555;\">" + open + I18n.translate(label) + close
             + "</span></td><td align=\"right\" nowrap>" + open + value + close + "</td></tr>";
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private ReceiptDialog() { }
}
