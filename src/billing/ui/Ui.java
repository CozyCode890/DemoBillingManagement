package billing.ui;

import billing.db.QueryResult;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Vài hàm dùng chung cho giao diện, để các màn hình khác đỡ lặp code. */
public class Ui {

    public static final Font MONO  = new Font("Consolas", Font.PLAIN, 13);
    public static final Font BOLD  = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font BIG   = new Font("Segoe UI", Font.BOLD, 20);

    private static final DecimalFormat MONEY;
    static {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.US);
        sym.setGroupingSeparator('.');
        MONEY = new DecimalFormat("#,##0", sym);
    }

    /** 135100 -> "135.100 d" */
    public static String money(BigDecimal v) {
        if (v == null) return "0 d";
        return MONEY.format(v) + " d";
    }

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
