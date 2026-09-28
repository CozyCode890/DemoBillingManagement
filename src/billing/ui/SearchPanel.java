package billing.ui;

import billing.db.Db;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tab 5: Tra cứu dữ liệu (Search Form)
 * Chọn bảng, xem dữ liệu hoặc tìm kiếm theo từ khóa.
 * Giao diện giống tab 2: bảng read-only, sắp xếp cột, định dạng số.
 */
public class SearchPanel extends JPanel {

    // UI components
    private final JComboBox<String> cbTables;
    private final JTextField tfSearch;
    private final JButton btSearch;
    private final JButton btReload;
    private final JTable table;
    private final JLabel lbRowCount;

    // Formatters for Vietnamese locale (matching tab 2)
    private static final DecimalFormat CURRENCY_FORMAT;
    private static final DecimalFormat INTEGER_FORMAT;
    static {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(new Locale("vi", "VN"));
        sym.setGroupingSeparator('.');
        sym.setDecimalSeparator(',');
        CURRENCY_FORMAT = new DecimalFormat("#,##0.00", sym);
        INTEGER_FORMAT = new DecimalFormat("#,##0", sym);
    }

    public SearchPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // === Toolbar ===
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        cbTables = new JComboBox<>(new String[]{
                "Product", "Invoice", "Invoice_Line", "Price_History",
                "Promotion", "Product_Promotion", "Customer", "Cashier",
                "Shop", "Counter", "Payment", "Cash_Payment", "Card_Payment", "EWallet_Payment"
        });
        cbTables.setPreferredSize(new Dimension(180, 28));
        cbTables.addActionListener(e -> loadTableData((String) cbTables.getSelectedItem()));

        tfSearch = new JTextField(20);
        tfSearch.addActionListener(e -> performSearch());

        btSearch = new JButton("Tìm kiếm");
        btSearch.addActionListener(e -> performSearch());

        btReload = new JButton("Tải lại / Xem tất cả");
        btReload.addActionListener(e -> reloadCurrentTable());

        toolbar.add(new JLabel("Chọn bảng: "));
        toolbar.add(cbTables);
        toolbar.add(new JLabel("Từ khóa: "));
        toolbar.add(tfSearch);
        toolbar.add(btSearch);
        toolbar.add(btReload);

        add(toolbar, BorderLayout.NORTH);

        // === Table ===
        table = Ui.readOnlyTable(); // Same look as tab 2
        table.setAutoCreateRowSorter(true); // Enable column sorting (3-state: ASC, DESC, UNSORTED)
        // Note: Default behavior clicks: ASC -> DESC -> UNSORTED -> ASC ...

        add(new JScrollPane(table), BorderLayout.CENTER);

        // === Footer ===
        JPanel footer = new JPanel(new BorderLayout(5, 0));
        lbRowCount = new JLabel("Tổng số hàng: 0 dòng");
        lbRowCount.setFont(new Font("Consolas", Font.PLAIN, 12));
        footer.add(lbRowCount, BorderLayout.WEST);
        footer.add(Box.createHorizontalGlue(), BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);

        // Load first table by default
        loadTableData((String) cbTables.getSelectedItem());
    }

    /** Load all data from the selected table and display formatted */
    private void loadTableData(String tableName) {
        if (tableName == null || tableName.isEmpty()) return;

        try (Connection conn = Db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM `" + tableName + "`")) {

            populateTable(rs);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Lỗi khi tải dữ liệu từ bảng '" + tableName + "':\n" + ex.getMessage(),
                    "Lỗi cơ sở dữ liệu", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    /** Perform search based on keyword in text fields */
    private void performSearch() {
        String keyword = tfSearch.getText().trim();
        if (keyword == null || keyword.isEmpty()) {
            reloadCurrentTable();
            return;
        }

        if (cbTables.getSelectedItem() == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một bảng trước khi tìm kiếm.", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String tableName = (String) cbTables.getSelectedItem();

        try (Connection conn = Db.getConnection()) {
            // Determine searchable (string-like) columns for LIKE search
            List<String> searchableCols = new ArrayList<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM `" + tableName + "` LIMIT 1")) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                for (int i = 1; i <= colCount; i++) {
                    int type = meta.getColumnType(i);
                    // Consider types that are character-based
                    if (type == Types.CHAR || type == Types.VARCHAR ||
                            type == Types.LONGVARCHAR || type == Types.NCHAR ||
                            type == Types.NVARCHAR || type == Types.LONGNVARCHAR) {
                        searchableCols.add(meta.getColumnLabel(i));
                    }
                }
            }

            if (searchableCols.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Bảng '" + tableName + "' không có cột chuỗi để tìm kiếm.",
                        "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            // Build WHERE clause: OR across all searchable columns
            StringBuilder where = new StringBuilder();
            for (int i = 0; i < searchableCols.size(); i++) {
                if (i > 0) where.append(" OR ");
                where.append("`").append(searchableCols.get(i)).append("` LIKE ?");
            }

            String sql = "SELECT * FROM `" + tableName + "` WHERE " + where.toString();

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                // Set same keyword for each placeholder
                for (int i = 0; i < searchableCols.size(); i++) {
                    ps.setString(i + 1, "%" + keyword + "%");
                }

                try (ResultSet rs = ps.executeQuery()) {
                    populateTable(rs);
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Lỗi khi thực hiện tìm kiếm:\n" + ex.getMessage(),
                    "Lỗi cơ sở dữ liệu", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    /** Reload current table (show all data) */
    private void reloadCurrentTable() {
        String tableName = (String) cbTables.getSelectedItem();
        if (tableName != null && !tableName.isEmpty()) {
            loadTableData(tableName);
            tfSearch.setText("");
        }
    }

    /** Fill table with formatted data from ResultSet */
    private void populateTable(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();

        // Prepare column names
        String[] columnNames = new String[colCount];
        for (int i = 1; i <= colCount; i++) {
            columnNames[i - 1] = meta.getColumnLabel(i);
        }

        // Prepare data rows with formatting
        DefaultTableModel model = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // read-only
            }
        };

        while (rs.next()) {
            Object[] row = new Object[colCount];
            for (int i = 1; i <= colCount; i++) {
                Object val = rs.getObject(i);
                int sqlType = meta.getColumnType(i);
                row[i - 1] = formatValue(val, sqlType);
            }
            model.addRow(row);
        }

        table.setModel(model);
        Ui.autoSize(table); // Adjust column widths to content (like tab 2)
        updateRowCount();
    }

    /** Format a value according to its SQL type for display */
    private String formatValue(Object value, int sqlType) {
        if (value == null) {
            return "";
        }

        // Numeric types: treat as currency if fractional, else integer
        switch (sqlType) {
            case Types.NUMERIC:
            case Types.DECIMAL:
            case Types.DOUBLE:
            case Types.FLOAT:
            case Types.REAL:
                return formatCurrency(value);
            case Types.INTEGER:
            case Types.SMALLINT:
            case Types.TINYINT:
            case Types.BIGINT:
                return formatInteger(value);
            // Date/time types: show as string (default formatting)
            case Types.DATE:
            case Types.TIME:
            case Types.TIMESTAMP:
                return value.toString();
            // Other types: default string representation
            default:
                return value.toString();
        }
    }

    /** Format as currency (with thousand separator dot, comma decimal, and " đ" suffix) */
    private String formatCurrency(Object value) {
        if (value instanceof Number) {
            return CURRENCY_FORMAT.format(((Number) value).doubleValue()) + " đ";
        }
        return value.toString();
    }

    /** Format as integer (with thousand separator dot, no decimal) */
    private String formatInteger(Object value) {
        if (value instanceof Number) {
            return INTEGER_FORMAT.format(((Number) value).longValue());
        }
        return value.toString();
    }

    /** Update row count label */
    private void updateRowCount() {
        int rows = table.getModel().getRowCount();
        lbRowCount.setText("Tổng số hàng: " + rows + " dòng");
    }
}