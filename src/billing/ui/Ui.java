package billing.ui;

import billing.db.QueryResult;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * ====================================================================
 *  Ui -- BẢN CHÉP DUY NHẤT của lớp giữa bàn phím và BigDecimal,
 *        và của lớp giữa BigDecimal và màn hình.
 * ====================================================================
 *
 *  <p>Trước khối 5 bộ định dạng số bị chép <b>bốn</b> lần ({@code Ui},
 *  {@code InvoiceViewPanel}, và hai lần trong {@code SearchPanel}), còn việc
 *  đọc số từ bàn phím thì mỗi chỗ tự làm theo một cách -- phần lớn bằng
 *  {@code new BigDecimal(textField.getText())}, nên thu ngân gõ {@code 500.000}
 *  là ghi vào database <b>500 đồng</b>, sai đúng 1000 lần, không một cảnh báo
 *  nào (plan/14 lỗi #1).
 *
 *  <p><b>Nguyên tắc:</b> con số trên màn hình phải là con số trong database, và
 *  mỗi phép đọc số / mỗi phép định dạng số chỉ tồn tại ở MỘT chỗ. Hệ quả:
 *  <ul>
 *    <li>không {@code new BigDecimal(textField.getText())} ở bất kỳ đâu --
 *        đọc số đi qua {@link #readMoney} / {@link #readQuantity};</li>
 *    <li>không {@code new DecimalFormat(...)} ở bất kỳ file nào khác --
 *        hiện số đi qua {@link #money} / {@link #qty} / {@link #percent}.</li>
 *  </ul>
 */
public class Ui {

    public static final Font MONO  = new Font("Consolas", Font.PLAIN, 13);
    public static final Font BOLD  = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font BIG   = new Font("Segoe UI", Font.BOLD, 20);

    // =================================================================
    //  HIỆN SỐ -- hợp đồng plan/14 mục 3.4
    // =================================================================

    private static final DecimalFormat MONEY;     // #,##0      -> 135.100
    private static final DecimalFormat QTY;       // #,##0.###  -> 1,5
    private static final DecimalFormat QTY_IN;    // 0.###      -> 1.5  (điền vào ô nhập)
    private static final DecimalFormat PERCENT;   // #0.##      -> 8
    static {
        // Locale.forLanguageTag chứ không new Locale("vi","VN"): hàm dựng đó
        // đã deprecated và là cảnh báo biên dịch duy nhất còn lại của dự án.
        // Dấu nghìn '.' và dấu thập phân ',' vẫn đặt tay: ta phụ thuộc vào
        // chúng theo hợp đồng, không phụ thuộc vào dữ liệu locale của JDK.
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.forLanguageTag("vi-VN"));
        sym.setGroupingSeparator('.');
        sym.setDecimalSeparator(',');

        MONEY   = new DecimalFormat("#,##0", sym);
        QTY     = new DecimalFormat("#,##0.###", sym);
        PERCENT = new DecimalFormat("#0.##", sym);
        PERCENT.setGroupingUsed(false);

        // Ô NHẬP số lượng: KHÔNG dấu nghìn, và dấu thập phân là '.'.
        //
        // Không dấu nghìn vì ô nhập hiểu cả '.' và ',' là dấu thập phân (mục
        // 3.3), nên điền "1.500" vào đó sẽ bị đọc thành 1,5 -- đó là lý do
        // khối 3 dựng riêng một format cho giá trị điền sẵn.
        //
        // Dấu thập phân '.' chứ không ',': bản khối 3 cho ra "1,5", vẫn đọc
        // được (readQuantity đổi ',' thành '.') nhưng "1.5" thì KHÔNG THỂ bị
        // đọc nhầm thành dấu nghìn dù người dùng có sửa thêm vào ô hay không.
        DecimalFormatSymbols dot = new DecimalFormatSymbols(Locale.forLanguageTag("vi-VN"));
        dot.setDecimalSeparator('.');
        QTY_IN = new DecimalFormat("0.###", dot);
        QTY_IN.setGroupingUsed(false);
    }

    /**
     * Mọi cột tiền: {@code 135100 -> "135.100đ"}.
     *
     * <p>Nhận {@code Object} để {@code fillFormatted} và các chỗ đọc thẳng từ
     * JDBC dùng chung một đường. {@code null -> "0đ"}, không bao giờ ném; giá
     * trị không phải số thì trả nguyên văn.
     */
    public static String money(Object v) {
        if (v == null)            return "0đ";
        if (v instanceof Number)  return MONEY.format(v) + "đ";
        return v.toString();
    }

    /**
     * Số lượng: {@code 1.500 -> "1,5"} ; {@code 2.000 -> "2"} ; {@code 0.250 -> "0,25"}.
     * Dùng cho {@code Quantity} / {@code Return_Quantity} -- {@code DECIMAL(10,3)}.
     *
     * <p>Làm tròn về số nguyên là nói sai về dữ liệu đang nằm trong database:
     * {@code INV-0002} dòng 1 bán 1,5 KG thịt ba chỉ.
     */
    public static String qty(Object v) {
        if (v == null)            return "0";
        if (v instanceof Number)  return QTY.format(v);
        return v.toString();
    }

    /**
     * Phần trăm: {@code 8.00 -> "8%"} ; {@code 7.50 -> "7,5%"}.
     * Dùng cho {@code Tax_Rate} / {@code VAT_pct} / {@code Phan_tram_tra}.
     *
     * <p>Phần trăm KHÔNG thuộc nhóm tiền tệ. Xếp nó vào nhóm tiền là
     * {@code VAT_pct} hiện ra thành {@code "8đ"} (plan/14 lỗi #21).
     */
    public static String percent(Object v) {
        if (v == null)            return "";
        if (v instanceof Number)  return PERCENT.format(v) + "%";
        return v.toString();
    }

    /** Số nguyên (mã, số lượt, số hóa đơn): {@code 1234 -> "1.234"}. */
    public static String integer(Object v) {
        if (v == null)            return "0";
        if (v instanceof Number)  return MONEY.format(((Number) v).longValue());
        return v.toString();
    }

    /**
     * Giá trị ĐIỀN SẴN vào một ô nhập số lượng: {@code 1.5 -> "1.5"},
     * <b>không</b> dấu nghìn (plan/12 mục 9.2 điểm 3).
     */
    public static String qtyInput(BigDecimal v) {
        return v == null ? "" : QTY_IN.format(v);
    }

    /**
     * Giá trị ĐIỀN SẴN vào một ô nhập tiền: {@code 158000.00 -> "158.000"}.
     * Có dấu nghìn, vì {@link #readMoney} hiểu dấu nghìn (mục 3.3) và người
     * dùng đọc {@code 158.000} dễ hơn {@code 158000.00}.
     */
    public static String moneyInput(BigDecimal v) {
        return v == null ? "" : MONEY.format(v);
    }

    /**
     * Đổi giá trị thô từ JDBC thành BigDecimal. Cột {@code DECIMAL} của MySQL
     * về Java vốn là {@code BigDecimal} rồi (docs/05 mục 3) -- không qua
     * {@code double}, không parse chuỗi khi không cần.
     */
    public static BigDecimal dec(Object o) {
        if (o == null)               return BigDecimal.ZERO;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        if (o instanceof Number)     return new BigDecimal(o.toString());
        return new BigDecimal(String.valueOf(o).trim());
    }

    // =================================================================
    //  ĐỌC SỐ TỪ BÀN PHÍM -- hợp đồng plan/14 mục 3.3
    // =================================================================

    /**
     * Biểu thức của một số tiền người Việt gõ:
     * {@code 500.000 | 1.234.567 | 1.234,50 | 500000 | 0,5}.
     *
     * <p>Dấu {@code '.'} là dấu TÁCH NGHÌN và phải đủ 3 chữ số theo sau; dấu
     * thập phân là {@code ','}, tối đa 2 chữ số ({@code DECIMAL(12,2)}).
     * Dấu {@code '-'} được NHẬN ở đây để phép kiểm dấu bên dưới nói được câu
     * "phải lớn hơn 0" thay vì câu "không đọc được".
     */
    private static final Pattern MONEY_IN = Pattern.compile(
            "^-?(\\d{1,3}(\\.\\d{3})*|\\d+)(,\\d{1,2})?$");

    /** Bỏ khoảng trắng (cả khoảng trắng cứng) và chữ {@code đ}/{@code d} ở cuối. */
    private static String normalizeMoney(String input) {
        String s = input.trim().replace(" ", "").replace(" ", "");
        while (s.endsWith("đ") || s.endsWith("Đ") || s.endsWith("d") || s.endsWith("D")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /**
     * Đọc số tiền, KHÔNG hiện thông báo. {@code null} = không đọc được, kể cả
     * khi {@code <= 0}. Dùng cho nhãn vọng lại ({@link #attachMoneyEcho}).
     */
    public static BigDecimal parseMoney(String input) {
        if (input == null) return null;
        String s = normalizeMoney(input);
        if (s.isEmpty() || !MONEY_IN.matcher(s).matches()) return null;
        BigDecimal v = new BigDecimal(s.replace(".", "").replace(',', '.'));
        return v.signum() <= 0 ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Như {@link #readMoney(Component, String, String)} nhưng NHẬN số 0.
     *
     * <p>Vì sao cần hai biến thể: một dòng thanh toán 0 đồng là vô nghĩa (ô
     * tiền ở tab 1 phải từ chối), nhưng {@code Invoice_Line.Discount = 0.00}
     * là giá trị <b>bình thường nhất</b> của cột đó -- phần lớn dòng hàng
     * không có khuyến mãi. {@code Unit_Price} cũng vậy: bản trước khối 5 của
     * {@code EditLineDialog} nhận {@code >= 0} cho cả hai ô, và đó là đúng.
     */
    public static BigDecimal readMoneyAllowZero(Component parent, String input, String fieldLabel) {
        return readMoneyInternal(parent, input, fieldLabel, true);
    }

    /**
     * Đọc số tiền người dùng gõ. Trả {@code null} <b>sau khi đã hiện thông
     * báo</b> nếu không hợp lệ -- đúng khuôn {@link #readQuantity}, để chỗ gọi
     * chỉ cần {@code if (v == null) return;}.
     *
     * <p>Dấu {@code '.'} là dấu TÁCH NGHÌN; dấu thập phân là {@code ','}.
     * Ngược hoàn toàn với {@link #readQuantity}, và đây là <b>cố ý</b>:
     * plan/14 mục 3.3 giải thích vì sao một quy tắc không phục vụ được cả hai
     * (tiền thì lớn và có dấu nghìn, số lượng thì nhỏ và có phần lẻ).
     *
     * <p>{@code "500.000" -> 500000}. Trước khối 5:
     * {@code new BigDecimal("500.000") = 500}, ghi vào {@code Payment} 500
     * đồng, không một cảnh báo nào (plan/14 lỗi #1).
     *
     * @param fieldLabel tên ô để ghép vào thông báo, ví dụ {@code "So tien"}
     */
    public static BigDecimal readMoney(Component parent, String input, String fieldLabel) {
        return readMoneyInternal(parent, input, fieldLabel, false);
    }

    private static BigDecimal readMoneyInternal(Component parent, String input,
                                                String fieldLabel, boolean allowZero) {
        if (input == null) return null;                       // bấm Cancel
        String s = normalizeMoney(input);
        if (s.isEmpty()) {
            warn(parent, "Chua nhap " + fieldLabel + ".", "Chua nhap so tien");
            return null;
        }
        if (!MONEY_IN.matcher(s).matches()) {
            warn(parent, fieldLabel + " khong doc duoc: \"" + input + "\"\n\n"
                       + "Dau . la dau tach nghin (phai du 3 chu so),\n"
                       + "dau , la dau thap phan (toi da 2 chu so).\n\n"
                       + "Dung:  500.000   |   1.234,50   |   500000\n"
                       + "Sai :  500.00    |   1.2.3      |   1,234",
                    "So tien khong hop le");
            return null;
        }
        BigDecimal v = new BigDecimal(s.replace(".", "").replace(',', '.'));
        if (allowZero ? v.signum() < 0 : v.signum() <= 0) {
            warn(parent, fieldLabel + (allowZero ? " khong duoc am." : " phai lon hon 0."),
                    "So tien khong hop le");
            return null;
        }
        return v.setScale(2, RoundingMode.HALF_UP);           // DECIMAL(12,2)
    }

    /**
     * Đọc số lượng người dùng gõ. Trả {@code null} (và <b>đã</b> hiện thông
     * báo) nếu không hợp lệ.
     *
     * <p>Bản dời của {@code InvoiceViewPanel.readQuantity} (khối 3) lên chỗ
     * dùng chung được: trước khối 5 nó là {@code private} của một lớp, nên tab
     * 1 và {@code EditLineDialog} không dùng được và cả hai đang nhận
     * {@code 1.2345} để MySQL âm thầm làm tròn thành {@code 1.235}
     * (plan/14 lỗi #23).
     *
     * <p>Ba quy tắc của plan/12 mục 3.2:
     * <ol>
     *   <li>Quá 3 chữ số thập phân thì TỪ CHỐI, không âm thầm làm tròn --
     *       {@code DECIMAL(10,3)} sẽ lặng lẽ biến {@code 1.2345} thành
     *       {@code 1.235}.</li>
     *   <li>Không nhận dấu phân cách nghìn; cả {@code ','} và {@code '.'} đều
     *       là dấu thập phân.</li>
     *   <li>(Chỗ gọi lo) điền sẵn số lượng còn lại.</li>
     * </ol>
     *
     * <p>So sánh bằng {@code compareTo}, KHÔNG {@code equals}:
     * {@code BigDecimal("1.5").equals(BigDecimal("1.50"))} là {@code false}
     * (khác scale) -- một cái bẫy kinh điển làm phép kiểm "còn lại" sai ở biên.
     *
     * @param max số lượng tối đa, hoặc {@code null} khi không có trần (tab 1)
     * @param unit đơn vị tính, chỉ để ghép vào thông báo khi vượt {@code max}
     */
    public static BigDecimal readQuantity(Component parent, String input,
                                          BigDecimal max, String unit) {
        if (input == null) return null;                       // bấm Cancel
        String s = input.trim().replace(',', '.');
        if (s.isEmpty()) {
            warn(parent, "Chua nhap so luong.", "So luong khong hop le");
            return null;
        }
        if (s.indexOf('.') != s.lastIndexOf('.')) {
            warn(parent, "Chi duoc mot dau thap phan trong so luong: " + input,
                    "So luong khong hop le");
            return null;
        }
        BigDecimal q;
        try {
            q = new BigDecimal(s);
        } catch (NumberFormatException ex) {
            warn(parent, "So luong khong hop le: " + input
                       + "\n\nDau , hoac . la dau thap phan, KHONG phai dau tach nghin.\n"
                       + "Dung:  2   |   1,5   |   1.5   |   0,25",
                    "So luong khong hop le");
            return null;
        }
        if (q.scale() > 3) {
            warn(parent, "Toi da 3 chu so thap phan (cot DECIMAL(10,3)).\n"
                       + "Ban go: " + input,
                    "So luong khong hop le");
            return null;
        }
        if (q.signum() <= 0) {
            warn(parent, "So luong phai lon hon 0.", "So luong khong hop le");
            return null;
        }
        if (max != null && q.compareTo(max) > 0) {
            warn(parent, "Chi con " + qty(max) + " " + unit + " co the tra.",
                    "So luong khong hop le");
            return null;
        }
        return q;
    }

    /**
     * Gắn một nhãn <i>"= 500.000đ"</i> cạnh ô nhập tiền, cập nhật theo từng ký tự.
     *
     * <p>KHÔNG gọi database (khác plan/14 lỗi #14): chỉ parse trong bộ nhớ.
     * Không đọc được thì hiện {@code "= ?"} màu đỏ -- người dùng thấy app hiểu
     * gì <b>trước khi</b> bấm nút, chứ không phát hiện ra sau khi đã lưu.
     */
    public static JLabel attachMoneyEcho(JTextField field) {
        JLabel echo = new JLabel(" ");
        echo.setFont(BOLD);
        Runnable update = () -> {
            String raw = field.getText();
            if (raw == null || raw.trim().isEmpty()) {
                echo.setText(" ");
                return;
            }
            BigDecimal v = parseMoney(raw);
            if (v == null) {
                echo.setText("= ?");
                echo.setForeground(Color.RED.darker());
            } else {
                echo.setText("= " + money(v));
                echo.setForeground(new Color(0, 110, 0));
            }
        };
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { update.run(); }
            @Override public void removeUpdate(DocumentEvent e)  { update.run(); }
            @Override public void changedUpdate(DocumentEvent e) { update.run(); }
        });
        update.run();
        return echo;
    }

    /** Cảnh báo gọn một dòng. Bản dời của {@code InvoiceViewPanel.warn}. */
    public static void warn(Component parent, String message, String title) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.WARNING_MESSAGE);
    }

    // =================================================================
    //  BẢNG
    // =================================================================

    /** Tạo một JTable chỉ để xem (không cho sửa ô). */
    public static JTable readOnlyTable() {
        JTable t = new JTable(new DefaultTableModel() {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        });
        t.setRowHeight(24);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        t.getTableHeader().setFont(BOLD);
        return t;
    }

    /**
     * Đổ kết quả một câu SELECT thẳng vào JTable.
     *
     * Chỉ cần đúng 3 dòng: lấy tên cột, lấy dữ liệu, gán vào model.
     * Đây là lý do tôi tạo lớp QueryResult ở tầng db.
     */
    public static void fill(JTable table, QueryResult qr) {
        DefaultTableModel m = (DefaultTableModel) table.getModel();
        m.setDataVector(qr.rowArray(), qr.columnArray());
        autoSize(table);
    }

    /**
     * Xóa trắng một bảng.
     *
     * <p>Bản cũ gọi {@code QueryResult.run("SELECT '' WHERE 1=0")} ba lần chỉ
     * để vẽ ba bảng rỗng -- ba lượt đi database không cần thiết (plan/14 mục 5.9f).
     */
    public static void clear(JTable table) {
        DefaultTableModel m = (DefaultTableModel) table.getModel();
        m.setRowCount(0);
        m.setColumnCount(0);
    }

    /** Giãn cột cho vừa nội dung. */
    public static void autoSize(JTable table) {
        for (int c = 0; c < table.getColumnCount(); c++) {
            int width = 80;
            Object header = table.getColumnModel().getColumn(c).getHeaderValue();
            width = Math.max(width, String.valueOf(header).length() * 9 + 20);
            for (int r = 0; r < Math.min(table.getRowCount(), 40); r++) {
                Object v = table.getValueAt(r, c);
                width = Math.max(width, String.valueOf(v).length() * 8 + 20);
            }
            table.getColumnModel().getColumn(c).setPreferredWidth(Math.min(width, 400));
        }
    }

    /** Hộp thoại báo lỗi, hiện cả thông điệp gốc từ MySQL. */
    public static void error(Component parent, Throwable e) {
        e.printStackTrace();
        String msg = e.getMessage();
        if (msg != null && msg.length() > 1200) msg = msg.substring(0, 1200) + "...";
        JTextArea ta = new JTextArea(msg);
        ta.setEditable(false);
        ta.setFont(MONO);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        JScrollPane sp = new JScrollPane(ta);
        sp.setPreferredSize(new Dimension(650, 220));
        JOptionPane.showMessageDialog(parent, sp, "Co loi xay ra", JOptionPane.ERROR_MESSAGE);
    }

    public static JPanel titled(String title, JComponent inner) {
        JPanel p = new JPanel(new BorderLayout(6, 6));
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(inner, BorderLayout.CENTER);
        return p;
    }

    public static JTextArea sqlBox(String sql) {
        JTextArea ta = new JTextArea(sql);
        ta.setEditable(false);
        ta.setFont(MONO);
        ta.setBackground(new Color(30, 32, 38));
        ta.setForeground(new Color(190, 230, 190));
        ta.setCaretColor(Color.WHITE);
        ta.setMargin(new Insets(8, 8, 8, 8));
        return ta;
    }
}
