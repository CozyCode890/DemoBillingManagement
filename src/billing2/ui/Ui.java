package billing2.ui;

import billing2.db.QueryResult;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Đồ nghề dùng chung của tầng giao diện: font, định dạng / đọc số kiểu Việt Nam,
 * bảng chỉ đọc, hộp thoại lỗi, chạy việc DB ngoài luồng giao diện.
 */
public class Ui {

    public static final Font  MONO  = new Font("Consolas", Font.PLAIN, 13);
    public static final Font  BOLD  = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font  BIG   = new Font("Segoe UI", Font.BOLD, 20);
    public static final Color GREEN = new Color(0, 110, 0);
    public static final Color RED   = new Color(180, 40, 40);

    // =================================================================
    //  SỐ: tiền "135.100đ" (. tách nghìn), số lượng "1,5" (, thập phân)
    // =================================================================

    private static final DecimalFormat MONEY, QTY, QTY_IN, PERCENT;
    static {
        DecimalFormatSymbols vn = new DecimalFormatSymbols(Locale.forLanguageTag("vi-VN"));
        vn.setGroupingSeparator('.');
        vn.setDecimalSeparator(',');
        MONEY   = new DecimalFormat("#,##0", vn);
        QTY     = new DecimalFormat("#,##0.###", vn);
        PERCENT = new DecimalFormat("#0.##", vn);
        PERCENT.setGroupingUsed(false);
        DecimalFormatSymbols dot = new DecimalFormatSymbols(Locale.forLanguageTag("vi-VN"));
        dot.setDecimalSeparator('.');
        QTY_IN = new DecimalFormat("0.###", dot);       // điền lại vào ô nhập: "1.5"
        QTY_IN.setGroupingUsed(false);
    }

    public static String money(Object v)   { return v == null ? "0đ" : v instanceof Number ? MONEY.format(v) + "đ" : v.toString(); }
    public static String qty(Object v)     { return v == null ? "0" : v instanceof Number ? QTY.format(v) : v.toString(); }
    public static String percent(Object v) { return v == null ? "" : v instanceof Number ? PERCENT.format(v) + "%" : v.toString(); }
    public static String qtyInput(BigDecimal v)   { return v == null ? "" : QTY_IN.format(v); }
    public static String moneyInput(BigDecimal v) { return v == null ? "" : MONEY.format(v); }

    /** Giá trị từ ResultSet / ô bảng -> BigDecimal (null -> 0). */
    public static BigDecimal dec(Object o) {
        if (o == null)               return BigDecimal.ZERO;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        return new BigDecimal(o.toString().trim());
    }

    // =================================================================
    //  ĐỌC Ô NHẬP -- sai thì báo và trả null
    // =================================================================

    /** Ô tiền: dấu . tách nghìn (đủ 3 số), dấu , thập phân (tối đa 2 số). Đúng: 500.000 | 1.234,50 | 500000 */
    private static final Pattern MONEY_IN = Pattern.compile("^-?(\\d{1,3}(\\.\\d{3})*|\\d+)(,\\d{1,2})?$");

    private static String normalizeMoney(String input) {
        String s = input.trim().replace(" ", "").replace(" ", "");
        while (!s.isEmpty() && "đĐdD".indexOf(s.charAt(s.length() - 1)) >= 0) {
            s = s.substring(0, s.length() - 1);           // cho phép gõ đuôi "đ"
        }
        return s;
    }

    /** Đọc im lặng (cho dòng "= 135.100đ" cạnh ô tiền); null = không hợp lệ hoặc <= 0. */
    public static BigDecimal parseMoney(String input) {
        if (input == null) return null;
        String s = normalizeMoney(input);
        if (!MONEY_IN.matcher(s).matches()) return null;
        BigDecimal v = new BigDecimal(s.replace(".", "").replace(',', '.'));
        return v.signum() <= 0 ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal readMoney(Component parent, String input, String field)          { return readMoney(parent, input, field, false); }
    public static BigDecimal readMoneyAllowZero(Component parent, String input, String field) { return readMoney(parent, input, field, true); }

    private static BigDecimal readMoney(Component parent, String input, String field, boolean allowZero) {
        if (input == null) return null;
        String s = normalizeMoney(input);
        if (s.isEmpty()) {
            warn(parent, "Please enter the " + field.toLowerCase() + "!", "Missing Amount");
            return null;
        }
        if (!MONEY_IN.matcher(s).matches()) {
            warn(parent, "Cannot read the " + field.toLowerCase() + ": \"" + input + "\"\n\n"
                    + ". separates thousands (followed by 3 digits),\n"
                    + ", is the decimal point (up to 2 digits).\n\n"
                    + "Valid:    500.000   |   1.234,50   |   500000\n"
                    + "Invalid:  500.00    |   1.2.3      |   1,234", "Invalid Amount");
            return null;
        }
        BigDecimal v = new BigDecimal(s.replace(".", "").replace(',', '.'));
        if (allowZero ? v.signum() < 0 : v.signum() <= 0) {
            warn(parent, field + (allowZero ? " mustn't be negative!" : " must be greater than 0!"), "Invalid Amount");
            return null;
        }
        return v.setScale(2, RoundingMode.HALF_UP);       // khớp DECIMAL(12,2)
    }

    /**
     * Ô số lượng: , hoặc . đều là dấu THẬP PHÂN (khác ô tiền!), tối đa 3 số lẻ, phải > 0.
     * @param max không null = trần (số còn trả được khi trả hàng)
     */
    public static BigDecimal readQuantity(Component parent, String input, BigDecimal max, String unit) {
        if (input == null) return null;
        String s = input.trim().replace(',', '.');
        String problem = null;
        BigDecimal q = null;
        if (s.isEmpty())                               problem = "Please enter the quantity!";
        else if (s.indexOf('.') != s.lastIndexOf('.')) problem = "Only one decimal point is allowed in the quantity: " + input;
        else {
            try {
                q = new BigDecimal(s);
            } catch (NumberFormatException ex) {
                problem = "Invalid quantity: " + input + "\n\n, or . is the decimal point, NOT a thousands separator.\n"
                        + "Valid:  2   |   1,5   |   1.5   |   0,25";
            }
        }
        if (problem == null && q.scale() > 3)       problem = "At most 3 decimal places.\nYou typed: " + input;
        if (problem == null && q.signum() <= 0)     problem = "Quantity must be greater than 0!";
        if (problem == null && max != null && q.compareTo(max) > 0) {
            problem = "Only " + qty(max) + " " + unit + " can be returned.";
        }
        if (problem != null) {
            warn(parent, problem, "Invalid Quantity");
            return null;
        }
        return q;
    }

    /** Nhãn "= 135.100đ" cạnh ô tiền, cập nhật khi gõ -- thu ngân thấy ngay app hiểu số gì. */
    public static JLabel attachMoneyEcho(JTextField field) {
        JLabel echo = new JLabel(" ");
        echo.setFont(BOLD);
        Runnable update = () -> {
            String raw = field.getText();
            BigDecimal v = parseMoney(raw);
            echo.setText(raw == null || raw.isBlank() ? " " : v == null ? "= ?" : "= " + money(v));
            echo.setForeground(v == null ? Color.RED.darker() : GREEN);
        };
        field.getDocument().addDocumentListener(onChange(update));
        update.run();
        return echo;
    }

    /** Ô bắt buộc để trống -> báo, đặt con trỏ vào ô, trả true. */
    public static boolean missing(Component parent, JTextField f, String name) {
        if (!f.getText().isBlank()) return false;
        warn(parent, "Please fill in '" + name + "', it cannot be empty!", "Missing Information");
        f.requestFocus();
        return true;
    }

    // =================================================================
    //  BẢNG
    // =================================================================

    public static JTable readOnlyTable() {
        JTable t = new JTable(new DefaultTableModel() {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        });
        t.setRowHeight(24);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        t.getTableHeader().setFont(BOLD);
        return t;
    }

    public static void fill(JTable table, QueryResult qr) { fill(table, qr, null, null, null); }

    /**
     * Đổ kết quả SQL vào bảng. Tên cột SQL (Ma_HD, Tong_tien...) hiện bằng tiếng Anh;
     * các cột theo chỉ số trong moneyCols / qtyCols / pctCols được định dạng số.
     */
    public static void fill(JTable table, QueryResult qr, int[] moneyCols, int[] qtyCols, int[] pctCols) {
        Object[][] data = new Object[qr.rows.size()][];
        for (int r = 0; r < data.length; r++) {
            Object[] src = qr.rows.get(r);
            Object[] row = new Object[src.length];
            for (int c = 0; c < src.length; c++) {
                Object v = src[c];
                row[c] = has(moneyCols, c) ? (v == null ? "" : money(v))
                       : has(qtyCols, c)   ? qty(v)
                       : has(pctCols, c)   ? percent(v)
                       : v instanceof String s ? en(s)
                       : v == null ? "" : v;
            }
            data[r] = row;
        }
        String[] cols = qr.columns.stream().map(Ui::col).toArray(String[]::new);
        ((DefaultTableModel) table.getModel()).setDataVector(data, cols);
        autoSize(table);
    }

    private static boolean has(int[] cols, int c) {
        if (cols != null) for (int x : cols) if (x == c) return true;
        return false;
    }

    public static void clear(JTable table) {
        ((DefaultTableModel) table.getModel()).setDataVector(new Object[0][], new Object[0]);
    }

    /** Độ rộng cột theo tiêu đề và 40 dòng đầu. */
    public static void autoSize(JTable table) {
        for (int c = 0; c < table.getColumnCount(); c++) {
            int width = Math.max(80, String.valueOf(table.getColumnModel().getColumn(c).getHeaderValue()).length() * 9 + 20);
            for (int r = 0; r < Math.min(table.getRowCount(), 40); r++) {
                width = Math.max(width, String.valueOf(table.getValueAt(r, c)).length() * 8 + 20);
            }
            table.getColumnModel().getColumn(c).setPreferredWidth(Math.min(width, 400));
        }
    }

    // =================================================================
    //  TỪ ĐIỂN: SQL trả tên cột / vài chuỗi cố định bằng tiếng Việt không dấu
    // =================================================================

    private static final Map<String, String> COLS = new HashMap<>(), VALUES = new HashMap<>();
    static {
        String[] cols = {
            "Ma_HD", "Invoice", "Ngay", "Date", "Gio", "Time", "Thu_ngan", "Cashier", "Khach", "Customer",
            "Khach_hang", "Customer", "Trang_thai", "Status", "Tong_tien", "Total", "Quay", "Counter",
            "Dia_chi", "Address", "Tong_truoc_giam", "Subtotal", "Tong_giam", "Total discount",
            "Tong_phai_tra", "Total due", "Tien_hoan", "Refund", "Tong_sau_dieu_chinh", "Total after returns",
            "Dong", "Line", "San_pham", "Product", "SL", "Qty", "DVT", "Unit", "Don_gia", "Unit price",
            "Giam_gia", "Discount", "Thanh_tien", "Line total", "VAT_pct", "VAT %", "Ma_TT", "Payment",
            "So_tien", "Amount", "Phuong_thuc", "Method", "Chi_tiet", "Details", "Tien_khach_dua", "Tendered",
            "SL_mua", "Bought", "SL_tra", "Returned", "SL_da_tra", "Returned", "SL_con_lai", "Remaining",
            "Thanh_tien_goc", "Original total", "Thanh_tien_moi", "New total", "Ngay_tra", "Return date",
            "Cua_hang", "Shop", "So_hoa_don", "Invoices", "Doanh_thu", "Revenue", "Ma_vach", "Barcode",
            "Gia_cu", "Old price", "Gia_moi", "New price", "Ap_dung_tu", "Effective from", "Tang_phan_tram", "Increase %",
            "Ma_TN", "Cashier ID", "Ten_thu_ngan", "Cashier", "Tong_doanh_thu", "Total revenue", "Gio_hang_TB", "Avg basket",
            "Tong_hoa_don", "Invoice total", "Tien_tra_lai", "Refunded", "Phan_tram_tra", "Returned %",
            "Gia_ghi_tren_HD", "Price on invoice", "Gia_hom_nay", "Today's price", "So_dong_hang", "Item lines",
            "Ban_tu_ngay", "Sold from", "Ban_den_ngay", "Sold until", "KM_phan_tram", "Promo %",
            "Thanh_tien_SL1", "Price (qty 1)", "Chenh_lech", "Change vs. previous day"};
        String[] values = {
            "Tien mat", "Cash", "The ngan hang", "Bank card", "Vi dien tu", "E-wallet", "(khong ro)", "(unknown)",
            "(vang lai)", "(walk-in)", "(khach vang lai)", "(walk-in customer)", "Thu ngan", "Cashier", "Quan ly", "Manager"};
        for (int i = 0; i < cols.length; i += 2)   COLS.put(cols[i], cols[i + 1]);
        for (int i = 0; i < values.length; i += 2) VALUES.put(values[i], values[i + 1]);
    }

    /** Tên cột SQL -> tiêu đề tiếng Anh (không có trong từ điển thì giữ nguyên). */
    public static String col(String sqlColumn) { return COLS.getOrDefault(sqlColumn, sqlColumn); }

    /** Chuỗi cố định do SQL / config trả về ("Tien mat", "(vang lai)"...) -> tiếng Anh. */
    public static String en(String value) { return value == null ? "" : VALUES.getOrDefault(value, value); }

    // =================================================================
    //  HỘP THOẠI, BỐ CỤC
    // =================================================================

    public static void warn(Component parent, String message, String title) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.WARNING_MESSAGE);
    }

    /**
     * Lỗi nghiệp vụ (SIGNAL '45000' trong thủ tục SQL, IllegalArgument...) -> hiện đúng lý do.
     * Lỗi kỹ thuật -> câu chung cho thu ngân, chi tiết in ra console.
     */
    public static void error(Component parent, Throwable e) {
        e.printStackTrace();
        for (Throwable t = e; t != null; t = t.getCause()) {
            boolean business = (t instanceof SQLException sql && "45000".equals(sql.getSQLState()))
                            || t instanceof IllegalArgumentException || t instanceof IllegalStateException;
            if (business) {
                JOptionPane.showMessageDialog(parent, "The action could not be completed:\n" + t.getMessage(),
                        "Not Allowed", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        JOptionPane.showMessageDialog(parent, "Something went wrong while working with the data.\n"
                + "Please try again. If it keeps failing, please tell your manager!", "Error", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Chạy việc gọi DB NGOÀI luồng giao diện (EDT) để cửa sổ không bị đơ; xong thì quay lại EDT:
     * chạy `after` trước (vd. mở lại nút), rồi `onSuccess` -- hoặc hộp thoại lỗi.
     */
    public static <T> void async(Component parent, Supplier<T> work, Runnable after, Consumer<T> onSuccess) {
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() { return work.get(); }
            @Override protected void done() {
                if (after != null) after.run();
                T result;
                try {
                    result = get();
                } catch (Exception ex) {
                    error(parent, ex);
                    return;
                }
                onSuccess.accept(result);
            }
        }.execute();
    }

    /** Hộp thoại OK / Cancel có ô nhập, con trỏ đặt sẵn trong `focus`; Enter = OK. */
    public static boolean ask(Component parent, String title, JComponent focus, Object... message) {
        focus.addAncestorListener(new AncestorListener() {
            @Override public void ancestorAdded(AncestorEvent e)   { SwingUtilities.invokeLater(focus::requestFocusInWindow); }
            @Override public void ancestorRemoved(AncestorEvent e) { }
            @Override public void ancestorMoved(AncestorEvent e)   { }
        });
        return JOptionPane.showConfirmDialog(parent, message, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
    }

    public static DocumentListener onChange(Runnable r) {
        return new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { r.run(); }
            @Override public void removeUpdate(DocumentEvent e)  { r.run(); }
            @Override public void changedUpdate(DocumentEvent e) { r.run(); }
        };
    }

    /** Một hàng component xếp trái -> phải. */
    public static JPanel row(Component... cs) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        for (Component c : cs) p.add(c);
        return p;
    }

    /** Form 2 cột: form("Label:", component, "Label 2:", component2, ...). */
    public static JPanel form(Object... pairs) {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;
        for (int i = 0; i < pairs.length; i += 2) {
            g.gridy = i / 2;
            g.gridx = 0; g.weightx = 0;
            p.add(new JLabel(String.valueOf(pairs[i])), g);
            g.gridx = 1; g.weightx = 1;
            p.add((Component) pairs[i + 1], g);
        }
        return p;
    }

    /** Hàng cuối của hộp thoại sửa: ghi chú "* required" bên trái, các nút bên phải. */
    public static JPanel requiredButtons(JButton... buttons) {
        JLabel note = new JLabel("* required ");                 // dấu cách cuối: chữ nghiêng không bị cắt
        note.setFont(note.getFont().deriveFont(Font.ITALIC));
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        for (JButton b : buttons) right.add(b);
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createEmptyBorder(0, 16, 10, 10));
        p.add(note, BorderLayout.WEST);
        p.add(right, BorderLayout.EAST);
        return p;
    }

    public static JPanel titled(String title, JComponent inner) {
        JPanel p = new JPanel(new BorderLayout(6, 6));
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(inner, BorderLayout.CENTER);
        return p;
    }
}
