package billing.ui;

import billing.db.QueryResult;

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

public class Ui {
    public static final Font  MONO  = new Font("Consolas", Font.PLAIN, 13);
    public static final Font  BOLD  = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font  BIG   = new Font("Segoe UI", Font.BOLD, 20);
    public static final Color GREEN = new Color(0, 110, 0);
    public static final Color RED   = new Color(180, 40, 40);

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
        QTY_IN = new DecimalFormat("0.###", dot);
        QTY_IN.setGroupingUsed(false);
    }

    public static String money(Object v)   { return v == null ? "0đ" : v instanceof Number ? MONEY.format(v) + "đ" : v.toString(); }
    public static String qty(Object v)     { return v == null ? "0" : v instanceof Number ? QTY.format(v) : v.toString(); }
    public static String percent(Object v) { return v == null ? "" : v instanceof Number ? PERCENT.format(v) + "%" : v.toString(); }
    public static String integer(Object v) { return v == null ? "0" : v instanceof Number n ? MONEY.format(n.longValue()) : v.toString(); }

    public static String status(Object raw) { return raw == null ? "" : val(String.valueOf(raw)); }
    public static String qtyInput(BigDecimal v)   { return v == null ? "" : QTY_IN.format(v); }
    public static String moneyInput(BigDecimal v) { return v == null ? "" : MONEY.format(v); }

    public static BigDecimal dec(Object o) {
        if (o == null)               return BigDecimal.ZERO;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        return new BigDecimal(o.toString().trim());
    }

    private static final Pattern MONEY_IN = Pattern.compile("^-?(\\d{1,3}(\\.\\d{3})*|\\d+)(,\\d{1,2})?$");

    private static String normalizeMoney(String input) {
        String s = input.trim().replace(" ", "").replace(" ", "");
        while (!s.isEmpty() && "đĐdD".indexOf(s.charAt(s.length() - 1)) >= 0) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

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
            warn(parent, I18n.msg("Vui lòng nhập " + field.toLowerCase() + "!", "Please enter the " + field.toLowerCase() + "!"),
                    I18n.msg("Chưa nhập số tiền", "Missing Amount"));
            return null;
        }
        if (!MONEY_IN.matcher(s).matches()) {
            warn(parent, I18n.msg(
                    "Không đọc được " + field.toLowerCase() + ": \"" + input + "\"\n\n"
                  + "Dấu . tách hàng nghìn (theo sau là đủ 3 chữ số),\n"
                  + "dấu , là dấu thập phân (tối đa 2 chữ số).\n\n"
                  + "Đúng:  500.000   |   1.234,50   |   500000\n"
                  + "Sai:   500.00    |   1.2.3      |   1,234",
                    "Cannot read the " + field.toLowerCase() + ": \"" + input + "\"\n\n"
                  + ". separates thousands (followed by 3 digits),\n"
                  + ", is the decimal point (up to 2 digits).\n\n"
                  + "Valid:    500.000   |   1.234,50   |   500000\n"
                  + "Invalid:  500.00    |   1.2.3      |   1,234"),
                    I18n.msg("Số tiền không hợp lệ", "Invalid Amount"));
            return null;
        }
        BigDecimal v = new BigDecimal(s.replace(".", "").replace(',', '.'));
        if (allowZero ? v.signum() < 0 : v.signum() <= 0) {
            warn(parent, allowZero ? I18n.msg(field + " không được âm!", field + " mustn't be negative!")
                                   : I18n.msg(field + " phải lớn hơn 0!", field + " must be greater than 0!"),
                    I18n.msg("Số tiền không hợp lệ", "Invalid Amount"));
            return null;
        }
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal readQuantity(Component parent, String input, BigDecimal max, String unit) {
        if (input == null) return null;
        String s = input.trim().replace(',', '.');
        String problem = null;
        BigDecimal q = null;
        if (s.isEmpty()) {
            problem = I18n.msg("Vui lòng nhập số lượng!", "Please enter the quantity!");
        } else if (s.indexOf('.') != s.lastIndexOf('.')) {
            problem = I18n.msg("Số lượng chỉ được có MỘT dấu thập phân: ", "Only one decimal point is allowed in the quantity: ") + input;
        } else {
            try {
                q = new BigDecimal(s);
            } catch (NumberFormatException ex) {
                problem = I18n.msg(
                        "Số lượng không hợp lệ: " + input + "\n\nDấu , hoặc . là dấu THẬP PHÂN, không phải dấu tách nghìn.\n"
                      + "Đúng:  2   |   1,5   |   1.5   |   0,25",
                        "Invalid quantity: " + input + "\n\n, or . is the decimal point, NOT a thousands separator.\n"
                      + "Valid:  2   |   1,5   |   1.5   |   0,25");
            }
        }
        if (problem == null && q.scale() > 3) {
            problem = I18n.msg("Tối đa 3 chữ số thập phân.\nBạn đã gõ: ", "At most 3 decimal places.\nYou typed: ") + input;
        }
        if (problem == null && q.signum() <= 0) {
            problem = I18n.msg("Số lượng phải lớn hơn 0!", "Quantity must be greater than 0!");
        }
        if (problem == null && max != null && q.compareTo(max) > 0) {
            problem = I18n.msg("Chỉ còn trả được " + qty(max) + " " + unit + ".",
                               "Only " + qty(max) + " " + unit + " can be returned.");
        }
        if (problem != null) {
            warn(parent, problem, I18n.msg("Số lượng không hợp lệ", "Invalid Quantity"));
            return null;
        }
        return q;
    }

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
                       : v instanceof String s ? val(s)
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

    public static void autoSize(JTable table) {
        for (int c = 0; c < table.getColumnCount(); c++) {
            int width = Math.max(80, String.valueOf(table.getColumnModel().getColumn(c).getHeaderValue()).length() * 9 + 20);
            for (int r = 0; r < Math.min(table.getRowCount(), 40); r++) {
                width = Math.max(width, String.valueOf(table.getValueAt(r, c)).length() * 8 + 20);
            }
            table.getColumnModel().getColumn(c).setPreferredWidth(Math.min(width, 400));
        }
    }

    private static final Map<String, String[]> COLS = new HashMap<>(), VALUES = new HashMap<>();
    static {
        String[] cols = {
            "Ma_HD", "Mã HĐ", "Invoice", "Ngay", "Ngày", "Date", "Gio", "Giờ", "Time",
            "Shop", "Cửa hàng", "Shop", "Thu_ngan", "Thu ngân", "Cashier", "Khach", "Khách", "Customer",
            "Khach_hang", "Khách hàng", "Customer", "Trang_thai", "Trạng thái", "Status",
            "Tong_tien", "Tổng tiền", "Total", "Quay", "Quầy", "Counter", "Dia_chi", "Địa chỉ", "Address",
            "Tong_truoc_giam", "Tổng trước giảm", "Subtotal", "Tong_giam", "Tổng giảm", "Total discount",
            "Tong_phai_tra", "Tổng phải trả", "Total due", "Tien_hoan", "Tiền hoàn", "Refund",
            "Tong_sau_dieu_chinh", "Tổng sau điều chỉnh", "Total after returns",
            "Dong", "Dòng", "Line", "San_pham", "Sản phẩm", "Product", "SL", "SL", "Qty", "DVT", "ĐVT", "Unit",
            "Don_gia", "Đơn giá", "Unit price", "Giam_gia", "Giảm giá", "Discount",
            "Thanh_tien", "Thành tiền", "Line total", "VAT_pct", "VAT %", "VAT %",
            "Ma_TT", "Mã TT", "Payment", "So_tien", "Số tiền", "Amount", "Phuong_thuc", "Phương thức", "Method",
            "Chi_tiet", "Chi tiết", "Details", "Tien_khach_dua", "Khách đưa", "Tendered",
            "SL_mua", "SL mua", "Bought", "SL_tra", "SL trả", "Returned", "SL_da_tra", "SL đã trả", "Returned",
            "SL_con_lai", "SL còn lại", "Remaining", "Thanh_tien_goc", "Thành tiền gốc", "Original total",
            "Thanh_tien_moi", "Thành tiền mới", "New total", "Ngay_tra", "Ngày trả", "Return date",
            "Cua_hang", "Cửa hàng", "Shop", "So_hoa_don", "Số hóa đơn", "Invoices", "Doanh_thu", "Doanh thu", "Revenue",
            "Ma_vach", "Mã vạch", "Barcode", "Gia_cu", "Giá cũ", "Old price", "Gia_moi", "Giá mới", "New price",
            "Ap_dung_tu", "Áp dụng từ", "Effective from", "Tang_phan_tram", "Tăng %", "Increase %",
            "Ma_TN", "Mã TN", "Cashier ID", "Ten_thu_ngan", "Tên thu ngân", "Cashier",
            "Tong_doanh_thu", "Tổng doanh thu", "Total revenue", "Gio_hang_TB", "Giỏ hàng TB", "Avg basket",
            "Tong_hoa_don", "Tổng hóa đơn", "Invoice total", "Tien_tra_lai", "Tiền trả lại", "Refunded",
            "Phan_tram_tra", "% trả lại", "Returned %", "Gia_ghi_tren_HD", "Giá ghi trên HĐ", "Price on invoice",
            "Gia_hom_nay", "Giá hôm nay", "Today's price", "So_dong_hang", "Số dòng hàng", "Item lines",
            "Ban_tu_ngay", "Bán từ ngày", "Sold from", "Ban_den_ngay", "Bán đến ngày", "Sold until",
            "KM_phan_tram", "KM %", "Promo %", "Thanh_tien_SL1", "Giá (SL 1)", "Price (qty 1)",
            "Chenh_lech", "Chênh lệch so với hôm trước", "Change vs. previous day"};
        String[] values = {
            "Tien mat", "Tiền mặt", "Cash", "The ngan hang", "Thẻ ngân hàng", "Bank card",
            "Vi dien tu", "Ví điện tử", "E-wallet", "(khong ro)", "(không rõ)", "(unknown)",
            "(vang lai)", "(vãng lai)", "(walk-in)", "(khach vang lai)", "(khách vãng lai)", "(walk-in customer)",
            "Thu ngan", "Thu ngân", "Cashier", "Quan ly", "Quản lý", "Manager",
            "PAID", "Đã thanh toán", "Paid", "VOIDED", "Đã hủy", "Voided"};
        for (int i = 0; i < cols.length; i += 3)   COLS.put(cols[i], new String[]{cols[i + 1], cols[i + 2]});
        for (int i = 0; i < values.length; i += 3) VALUES.put(values[i], new String[]{values[i + 1], values[i + 2]});
    }

    public static String col(String sqlColumn) {
        String[] t = COLS.get(sqlColumn);
        return t == null ? sqlColumn : I18n.text(t[0], t[1]);
    }

    public static String val(String value) {
        if (value == null) return "";
        String[] t = VALUES.get(value);
        return t == null ? value : I18n.msg(t[0], t[1]);
    }

    public static void warn(Component parent, String message, String title) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.WARNING_MESSAGE);
    }

    public static void error(Component parent, Throwable e) {
        e.printStackTrace();
        for (Throwable t = e; t != null; t = t.getCause()) {
            boolean business = (t instanceof SQLException sql && "45000".equals(sql.getSQLState()))
                            || t instanceof IllegalArgumentException || t instanceof IllegalStateException;
            if (business) {
                JOptionPane.showMessageDialog(parent,
                        I18n.msg("Không thực hiện được thao tác này:\n", "The action could not be completed:\n") + t.getMessage(),
                        I18n.msg("Không được phép", "Not Allowed"), JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        JOptionPane.showMessageDialog(parent, I18n.msg(
                "Có lỗi khi làm việc với dữ liệu.\nVui lòng thử lại. Nếu vẫn lỗi, hãy báo quản lý!",
                "Something went wrong while working with the data.\n"
              + "Please try again. If it keeps failing, please tell your manager!"),
                I18n.msg("Lỗi", "Error"), JOptionPane.ERROR_MESSAGE);
    }

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

    public static JPanel row(Component... cs) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        for (Component c : cs) p.add(c);
        return p;
    }

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

    public static JPanel titled(String title, JComponent inner) {
        JPanel p = new JPanel(new BorderLayout(6, 6));
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(inner, BorderLayout.CENTER);
        return p;
    }
}
