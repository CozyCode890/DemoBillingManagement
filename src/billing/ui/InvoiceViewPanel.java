package billing.ui;

import billing.dao.InvoiceDao;
import billing.db.QueryResult;
import billing.model.Account;
import billing.model.UserSession;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import billing.dao.InvoiceDao.ReturnItem;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * ====================================================================
 *  MÀN HÌNH 2 -- XEM HÓA ĐƠN & THAO TÁC TRỰC TIẾP DATABASE
 * ====================================================================
 *  Bên trái: danh sách hóa đơn + nút "Sửa hóa đơn" + nút "Xóa hóa đơn".
 *  Bên phải:
 *    1. Thông tin chung + tổng tiền
 *    2. Các dòng hàng (Invoice_Line) + nút "Sửa dòng", "Xóa dòng", "Thử xóa (Set NULL)"
 *    3. Thanh toán (Payment + bảng con)
 *    4. Trả hàng (Return)
 *
 *  PHÂN QUYỀN TRÊN UI (UI LOCKING):
 *  - Thu ngân (cashier): Các nút sửa/xóa bị khóa (disable).
 *  - Quản lý (manager): Các nút được kích hoạt (enable).
 *  (Lưu ý: Tầng DAO không khóa để phục vụ các mục đích nghiệp vụ phía sau).
 */
public class InvoiceViewPanel extends JPanel {

    private final InvoiceDao dao = new InvoiceDao();

    private final JTable tbInvoices = Ui.readOnlyTable();
    private final JTable tbLines    = Ui.readOnlyTable();
    private final JTable tbPayments = Ui.readOnlyTable();
    private final JTable tbReturns  = Ui.readOnlyTable();
    private final JTextArea taHeader = new JTextArea(8, 40);

    /** Lớp nội bộ để lưu trữ dữ liệu thô của một dòng hàng hoá đơn */
    private static class InvoiceLineData {
        private final String lineNumber;      // STT (cột 0)
        private final String productName;     // Tên sản phẩm (cột 1)
        private final double quantity;        // Số lượng (cột 2) - dạng số
        private final String unit;            // Đơn vị tính (cột 3)
        private final double unitPrice;       // Đơn giá (cột 4) - dạng số
        private final double discount;        // Giảm giá (cột 5) - dạng số

        public InvoiceLineData(String lineNumber, String productName, double quantity,
                              String unit, double unitPrice, double discount) {
            this.lineNumber = lineNumber;
            this.productName = productName;
            this.quantity = quantity;
            this.unit = unit;
            this.unitPrice = unitPrice;
            this.discount = discount;
        }

        public String getLineNumber() { return lineNumber; }
        public String getProductName() { return productName; }
        public double getQuantity() { return quantity; }
        public String getUnit() { return unit; }
        public double getUnitPrice() { return unitPrice; }
        public double getDiscount() { return discount; }

        public double getUnitPriceAfterDiscount() {
            return unitPrice - discount;
        }
    }

    /** Custom TableModel để hiển thị dữ liệu hàng hoá đơn với định dạng tiền tệ */
    private static class InvoiceLineTableModel extends AbstractTableModel {
        private final List<InvoiceLineData> data;
        private final String[] columnNames;

        public InvoiceLineTableModel(List<InvoiceLineData> data, String[] columnNames) {
            this.data = data != null ? data : new ArrayList<>();
            this.columnNames = columnNames;
        }

        @Override
        public int getRowCount() {
            return data.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            InvoiceLineData line = data.get(rowIndex);
            switch (columnIndex) {
                case 0: return line.getLineNumber(); // STT
                case 1: return line.getProductName(); // Tên sản phẩm
                case 2: return String.valueOf((long) Math.round(line.getQuantity())); // Số lượng (hiển thị như số nguyên)
                case 3: return line.getUnit(); // Đơn vị tính
                case 4: return line.getUnitPrice(); // Đơn giá (số)
                case 5: return line.getDiscount(); // Giảm giá (số)
                case 6: return line.getUnitPrice() - line.getDiscount(); // Thành tiền (số)
                default: return "";
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false; // Tất cả các ô không thể chỉnh sửa
        }

        public void addRow(InvoiceLineData lineData) {
            data.add(lineData);
            fireTableRowsInserted(data.size() - 1, data.size() - 1);
        }

        public void removeRow(int rowIndex) {
            if (rowIndex >= 0 && rowIndex < data.size()) {
                data.remove(rowIndex);
                fireTableRowsDeleted(rowIndex, rowIndex);
            }
        }

        public InvoiceLineData getRowData(int rowIndex) {
            if (rowIndex >= 0 && rowIndex < data.size()) {
                return data.get(rowIndex);
            }
            return null;
        }

        public List<InvoiceLineData> getDataList() {
            return new ArrayList<>(data);
        }
    }

    // Các button thao tác hóa đơn (bên trái)
    private final JButton btEditInvoice   = new JButton("Sua hoa don");
    private final JButton btDeleteInvoice = new JButton("Xoa hoa don");

    // Các button thao tác dòng hàng trong hóa đơn (bên phải)
    private final JButton btEditLine       = new JButton("Sua dong hang");
    private final JButton btDeleteLine     = new JButton("Xoa dong hang");
    private final JButton btClearLineValue = new JButton("Thu xoa gia tri (Set NULL)");
    private final JButton btReturnLine     = new JButton("Tra hang");

    // Formatters for numbers
    private static final DecimalFormat CURRENCY_FORMAT;
    private static final DecimalFormat INTEGER_FORMAT;
    static {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.US);
        sym.setGroupingSeparator('.');
        sym.setDecimalSeparator(',');
        CURRENCY_FORMAT = new DecimalFormat("#,##0.00", sym);
        INTEGER_FORMAT = new DecimalFormat("#,##0", sym);
    }

    private String formatCurrency(Object value) {
        if (value == null) {
            return "0,00 đ";
        }
        if (value instanceof Number) {
            return CURRENCY_FORMAT.format(((Number) value).doubleValue()) + " đ";
        }
        return value.toString();
    }

    private String formatInteger(Object value) {
        if (value == null) {
            return "0";
        }
        if (value instanceof Number) {
            return INTEGER_FORMAT.format(((Number) value).longValue());
        }
        return value.toString();
    }

    /**
     * Parse Vietnamese currency string to double.
     * Handles formats like: "19.000", "19.000,00", "19000,00", "19000"
     * Where . is thousands separator and , is decimal separator
     */
    private double parseVNCurrency(Object obj) {
        if (obj == null) {
            return 0.0;
        }
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        String str = obj.toString().trim();
        // Remove currency symbols and whitespace
        str = str.replaceAll("[đdĐD\\s]", "");
        if (str.isEmpty()) {
            return 0.0;
        }
        try {
            // Case 1: Both . and , present (e.g., "19.000,00")
            if (str.contains(".") && str.contains(",")) {
                // Remove all dots (thousands separators), replace comma with dot
                str = str.replace(".", "").replace(",", ".");
                return Double.parseDouble(str);
            }
            // Case 2: Only dot present (could be thousands separator or decimal)
            else if (str.contains(".")) {
                // Check if dot is thousands separator (followed by exactly 3 digits at end)
                int lastDotIndex = str.lastIndexOf('.');
                if (lastDotIndex != -1 &&
                    (str.length() - lastDotIndex - 1) == 3) {
                    // Dot is thousands separator, remove it
                    str = str.replace(".", "");
                    return Double.parseDouble(str);
                }
                // Otherwise treat as decimal separator
                return Double.parseDouble(str);
            }
            // Case 3: Only comma present (decimal separator)
            else if (str.contains(",")) {
                // Replace comma with dot for parsing
                str = str.replace(",", ".");
                return Double.parseDouble(str);
            }
            // Case 4: Just digits
            else {
                return Double.parseDouble(str);
            }
        } catch (Exception e) {
            return 0.0;
        }
    }

    /** Đổ dữ liệu vào JTable với định dạng số tiền/số lượng phù hợp */
    private void fillFormatted(JTable table, QueryResult qr, int[] currencyCols, int[] integerCols) {
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        // Clear existing data
        model.setRowCount(0);
        model.setColumnCount(0);
        // Set column identifiers
        for (String col : qr.columns) {
            model.addColumn(col);
        }
        // Add rows with formatting
        for (Object[] row : qr.rows) {
            Object[] formattedRow = new Object[row.length];
            for (int i = 0; i < row.length; i++) {
                Object val = row[i];
                if (contains(currencyCols, i)) {
                    formattedRow[i] = formatCurrency(val);
                } else if (contains(integerCols, i)) {
                    formattedRow[i] = formatInteger(val);
                } else {
                    formattedRow[i] = (val == null) ? "" : val;
                }
            }
            model.addRow(formattedRow);
        }
        Ui.autoSize(table);
    }

    private boolean contains(int[] arr, int idx) {
        if (arr == null) return false;
        for (int v : arr) {
            if (v == idx) return true;
        }
        return false;
    }

    public InvoiceViewPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        taHeader.setEditable(false);
        taHeader.setFont(Ui.MONO);

        // --- Cột trái: Danh sách hóa đơn và nút thao tác ---
        JButton btReload = new JButton("Tai lai danh sach");
        btReload.addActionListener(e -> reload());

        JPanel pnlInvoiceActions = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
        btEditInvoice.setFont(Ui.BOLD);
        btDeleteInvoice.setFont(Ui.BOLD);
        btDeleteInvoice.setForeground(new Color(180, 40, 40));

        btEditInvoice.addActionListener(e -> onEditInvoice());
        btDeleteInvoice.addActionListener(e -> onDeleteInvoice());

        pnlInvoiceActions.add(btEditInvoice);
        pnlInvoiceActions.add(btDeleteInvoice);

        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.add(btReload, BorderLayout.NORTH);
        left.add(new JScrollPane(tbInvoices), BorderLayout.CENTER);
        left.add(pnlInvoiceActions, BorderLayout.SOUTH);
        left.setBorder(BorderFactory.createTitledBorder("Danh sach hoa don (bam de xem chi tiet)"));

        // --- Cột phải: Chi tiết hóa đơn ---
        // Panel riêng cho dòng hàng kèm các nút thao tác dòng
        JPanel pnlLineActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        btEditLine.setFont(Ui.BOLD);
        btDeleteLine.setForeground(new Color(180, 40, 40));
        btReturnLine.setFont(Ui.BOLD);

        btEditLine.addActionListener(e -> onEditLine());
        btDeleteLine.addActionListener(e -> onDeleteLine());
        btClearLineValue.addActionListener(e -> onTryClearLineValue());
        btReturnLine.addActionListener(e -> onReturnLine());

        pnlLineActions.add(btEditLine);
        pnlLineActions.add(btDeleteLine);
        pnlLineActions.add(btClearLineValue);
        pnlLineActions.add(btReturnLine);

        JPanel pnlLinesContainer = new JPanel(new BorderLayout(2, 2));
        pnlLinesContainer.add(new JScrollPane(tbLines), BorderLayout.CENTER);
        pnlLinesContainer.add(pnlLineActions, BorderLayout.SOUTH);

        JPanel right = new JPanel(new GridLayout(4, 1, 6, 6));
        right.add(Ui.titled("Thong tin chung + tong tien", new JScrollPane(taHeader)));
        right.add(Ui.titled("Cac dong hang (Invoice_Line)", pnlLinesContainer));
        right.add(Ui.titled("Thanh toan (Payment + bang con)", new JScrollPane(tbPayments)));
        right.add(Ui.titled("Tra hang (Return)", new JScrollPane(tbReturns)));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.42);
        add(split, BorderLayout.CENTER);

        tbInvoices.getSelectionModel().addListSelectionListener(this::onSelect);
        reload();

        // Enable column sorting with direct toggle between ASC and DESC (skip UNSORTED state)
        tbInvoices.setAutoCreateRowSorter(true);
        // Custom mouse listener to make sorting more responsive (direct ASC<->DESC toggle)
        tbInvoices.getTableHeader().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int colIndex = tbInvoices.columnAtPoint(e.getPoint());
                if (colIndex < 0) return;

                javax.swing.RowSorter<?> sorter = tbInvoices.getRowSorter();
                if (sorter == null) return;

                javax.swing.RowSorter.SortKey current = null;
                for (javax.swing.RowSorter.SortKey key : sorter.getSortKeys()) {
                    if (key.getColumn() == colIndex) {
                        current = key;
                        break;
                    }
                }

                // Determine next sort order: toggle between ASC and DESC
                javax.swing.SortOrder nextOrder;
                if (current == null) {
                    // No current sort for this column -> start with ASC
                    nextOrder = javax.swing.SortOrder.ASCENDING;
                } else if (current.getSortOrder() == javax.swing.SortOrder.ASCENDING) {
                    // Currently ASC -> switch to DESC
                    nextOrder = javax.swing.SortOrder.DESCENDING;
                } else {
                    // Currently DESC -> switch to ASC
                    nextOrder = javax.swing.SortOrder.ASCENDING;
                }

                // Set new sort keys (only this column sorted)
                java.util.List<javax.swing.RowSorter.SortKey> keys = new java.util.ArrayList<>(1);
                keys.add(new javax.swing.RowSorter.SortKey(colIndex, nextOrder));
                sorter.setSortKeys(keys);
                e.consume();

                // Consume event to prevent default handling (optional)
                // e.consume();
            }
        });

        // Áp dụng phân quyền giao diện theo phiên làm việc hiện tại
        applyRolePermissions();
    }

    /**
     * ================================================================
     *  PHÂN QUYỀN TRÊN GIAO DIỆN (UI ROLE LOCKING)
     * ================================================================
     *  Kiểm tra tài khoản hiện tại từ UserSession:
     *  - Nếu là 'cashier' -> Toàn bộ nút Sửa/Xóa bị khóa (disabled)
     *  - Nếu là 'manager' -> Toàn bộ nút Sửa/Xóa được mở khóa (enabled)
     *  (Tuyệt đối không chặn ở DAO/Service theo yêu cầu của bạn)
     */
    public void applyRolePermissions() {
        Account user = UserSession.getCurrentUser();
        boolean isManager = (user != null && user.isManager());

        btEditInvoice.setEnabled(isManager);
        btDeleteInvoice.setEnabled(isManager);
        btEditLine.setEnabled(isManager);
        btDeleteLine.setEnabled(isManager);
        btClearLineValue.setEnabled(isManager);

        if (!isManager) {
            String lockHint = "Chuc nang nay bi khoa voi Thu ngan (Cashier). Chi Quan ly (Manager) moi co quyen.";
            btEditInvoice.setToolTipText(lockHint);
            btDeleteInvoice.setToolTipText(lockHint);
            btEditLine.setToolTipText(lockHint);
            btDeleteLine.setToolTipText(lockHint);
            btClearLineValue.setToolTipText(lockHint);
        } else {
            btEditInvoice.setToolTipText("Chinh sua thong tin hoa don truc tiep trong DB");
            btDeleteInvoice.setToolTipText("Xoa hoa don va toan bo du lieu lien quan khoi DB");
            btEditLine.setToolTipText("Chinh sua so luong, don gia dong hang trong DB");
            btDeleteLine.setToolTipText("Xoa dong hang khoi DB");
            btClearLineValue.setToolTipText("Thu nghiem co che canh bao xoa gia tri NOT NULL");
        }
    }

    public void reload() {
        try {
            QueryResult qr = dao.recentInvoices();
            // Column index for Tong_tien (total) is last column
            fillFormatted(tbInvoices, qr, new int[]{qr.columns.size() - 1}, null);
            tbInvoices.setAutoCreateRowSorter(true); // Enable column sorting
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    private String getSelectedInvoiceId() {
        int r = tbInvoices.getSelectedRow();
        if (r < 0) return null;
        return String.valueOf(tbInvoices.getValueAt(r, 0));
    }

    private void onSelect(ListSelectionEvent e) {
        if (e != null && e.getValueIsAdjusting()) return;
        String invoiceId = getSelectedInvoiceId();
        if (invoiceId == null) return;

        try {
            // --- 1. Phần đầu hóa đơn -------------------------------
            QueryResult h = dao.invoiceHeader(invoiceId);
            StringBuilder sb = new StringBuilder();
            if (!h.isEmpty()) {
                Object[] row = h.rows.get(0);
                for (int i = 0; i < h.columns.size(); i++) {
                    Object val = row[i];
                    String formatted;
                    if (val instanceof Number) {
                        formatted = formatCurrency(val);
                    } else {
                        formatted = val == null ? "" : val.toString();
                    }
                    sb.append(String.format("%-18s : %s%n", h.columns.get(i), formatted));
                }
            }
            taHeader.setText(sb.toString());
            taHeader.setCaretPosition(0);

            // --- 2, 3, 4 -------------------------------------------
            // tbLines: columns: Dong, San_pham, SL, DVT, Don_gia, Giam_gia, Thanh_tien, VAT_pct
            // SL (index 2) integer; Don_gia (4), Giam_gia (5), Thanh_tien (6) currency; VAT_pct (7) maybe currency? treat as currency.
            QueryResult lines = dao.invoiceLines(invoiceId);
            fillFormatted(tbLines, lines, new int[]{4,5,6,7}, new int[]{2}); // currency cols 4,5,6,7; integer col 2

            // tbPayments: columns: Ma_TT, So_tien, Phuong_thuc, Chi_tiet
            // So_tien (index 1) currency
            QueryResult payments = dao.invoicePayments(invoiceId);
            fillFormatted(tbPayments, payments, new int[]{1}, null);

            // tbReturns: columns: Dong, San_pham, Ngay_tra, SL_tra, Tien_hoan, Nguoi_duyet
            // SL_tra (index 3) integer; Tien_hoan (index 4) currency
            QueryResult returns = dao.invoiceReturns(invoiceId);
            fillFormatted(tbReturns, returns, new int[]{4}, new int[]{3});

        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    // =================================================================
    //  CÁC THAO TÁC CHỈNH SỬA & XÓA TRỰC TIẾP TRONG DATABASE
    // =================================================================

    /** Chỉnh sửa thông tin Header hóa đơn */
    private void onEditInvoice() {
        String invoiceId = getSelectedInvoiceId();
        if (invoiceId == null) {
            JOptionPane.showMessageDialog(this, "Vui long chon mot hoa don de chinh sua!", "Chua chon hoa don", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            // Tra cứu thông tin gốc của hóa đơn
            String sql = "SELECT Invoice_ID, `Date`, `Time`, Status, Counter_ID, Cashier_ID, Customer_ID FROM Invoice WHERE Invoice_ID = ?";
            QueryResult qr = QueryResult.run(sql, invoiceId);
            if (qr.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Khong tim thay hoa don " + invoiceId, "Loi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Object[] r = qr.rows.get(0);
            String date = String.valueOf(r[1]);
            String time = String.valueOf(r[2]);
            String status = String.valueOf(r[3]);
            String counterId = String.valueOf(r[4]);
            String cashierId = String.valueOf(r[5]);
            String customerId = r[6] != null ? String.valueOf(r[6]) : "";

            Window window = SwingUtilities.getWindowAncestor(this);
            EditInvoiceDialog dialog = new EditInvoiceDialog(window, dao, invoiceId, date, time, status, counterId, cashierId, customerId);
            dialog.setVisible(true);

            if (dialog.isSaved()) {
                reload();
                onSelect(null);
            }

        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    /** Xóa hóa đơn trực tiếp trong database */
    private void onDeleteInvoice() {
        String invoiceId = getSelectedInvoiceId();
        if (invoiceId == null) {
            JOptionPane.showMessageDialog(this, "Vui long chon mot hoa don de xoa!", "Chua chon hoa don", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int choice = JOptionPane.showConfirmDialog(this,
                "Ban co chac chan muon XOA VINH VIEN hoa don " + invoiceId + " khoi Database?\n\n"
              + "Thao tac nay se xoa sach:\n"
              + "  1. Cac luot tra hang lien quan (Return)\n"
              + "  2. Cac chi tiet thanh toan (Cash/Card/EWallet_Payment)\n"
              + "  3. Cac luot thanh toan (Payment)\n"
              + "  4. Toan bo dong hang (Invoice_Line)\n"
              + "  5. Phan dau hoa don (Invoice)\n\n"
              + "Hanh dong khong the hoan tac!",
                "Xac nhan xoa hoa don khoi Database",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (choice == JOptionPane.YES_OPTION) {
            try {
                dao.deleteInvoice(invoiceId);
                JOptionPane.showMessageDialog(this, "Da xoa thanh cong hoa don " + invoiceId + " khoi Database!", "Thanh cong", JOptionPane.INFORMATION_MESSAGE);

                // Xóa trắng khung chi tiết
                taHeader.setText("");
                Ui.fill(tbLines, QueryResult.run("SELECT '' WHERE 1=0"));
                Ui.fill(tbPayments, QueryResult.run("SELECT '' WHERE 1=0"));
                Ui.fill(tbReturns, QueryResult.run("SELECT '' WHERE 1=0"));

                reload();
            } catch (Exception ex) {
                Ui.error(this, ex);
            }
        }
    }

    /** Chỉnh sửa một dòng hàng */
    private void onEditLine() {
        String invoiceId = getSelectedInvoiceId();
        int row = tbLines.getSelectedRow();
        if (invoiceId == null || row < 0) {
            JOptionPane.showMessageDialog(this, "Vui long chon mot dong hang trong bang 'Cac dong hang' de chinh sua!", "Chua chon dong hang", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            int lineNo = Integer.parseInt(String.valueOf(tbLines.getValueAt(row, 0)));

            // Lấy thông tin gốc của dòng hàng
            String sql = "SELECT Barcode, Quantity, Unit_Price, Discount FROM Invoice_Line WHERE Invoice_ID = ? AND Line_Number = ?";
            QueryResult qr = QueryResult.run(sql, invoiceId, lineNo);
            if (qr.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Khong tim thay dong hang so " + lineNo, "Loi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Object[] r = qr.rows.get(0);
            String barcode   = String.valueOf(r[0]);
            String quantity  = String.valueOf(r[1]);
            String unitPrice = String.valueOf(r[2]);
            String discount  = String.valueOf(r[3]);

            Window window = SwingUtilities.getWindowAncestor(this);
            EditLineDialog dialog = new EditLineDialog(window, dao, invoiceId, lineNo, barcode, quantity, unitPrice, discount);
            dialog.setVisible(true);

            if (dialog.isSaved()) {
                reload();
                onSelect(null);
            }

        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    /** Xóa một dòng hàng */
    private void onDeleteLine() {
        String invoiceId = getSelectedInvoiceId();
        int row = tbLines.getSelectedRow();
        if (invoiceId == null || row < 0) {
            JOptionPane.showMessageDialog(this, "Vui long chon mot dong hang de xoa!", "Chua chon dong hang", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int lineNo = Integer.parseInt(String.valueOf(tbLines.getValueAt(row, 0)));
        int choice = JOptionPane.showConfirmDialog(this,
                "Ban co chac chan muon xoa dong hang so " + lineNo + " khoi hoa don " + invoiceId + "?",
                "Xac nhan xoa dong hang", JOptionPane.YES_NO_OPTION);

        if (choice == JOptionPane.YES_OPTION) {
            try {
                dao.deleteInvoiceLine(invoiceId, lineNo);
                JOptionPane.showMessageDialog(this, "Da xoa thanh cong dong hang " + lineNo + "!", "Thanh cong", JOptionPane.INFORMATION_MESSAGE);
                reload();
                onSelect(null);
            } catch (Exception ex) {
                Ui.error(this, ex);
            }
        }
    }

    /** Nút thử xóa giá trị (Set NULL) để minh họa cơ chế cảnh báo của Database */
    private void onTryClearLineValue() {
        JOptionPane.showMessageDialog(this,
                "CANH BAO DATABASE:\n\n"
              + "Toan bo cac truong trong bang Invoice_Line (Quantity, Unit_Price, Barcode, Line_Number)\n"
              + "deu duoc dinh nghia rang buoc [NOT NULL] trong Schema!\n\n"
              + "Ban KHONG THE xoa bat ky gia tri nao ve NULL.\n"
              + "Hay su dung nut 'Sua dong hang' va bam 'Thu xoa (Set NULL)' de thay app ngan chan hanh vi nay!",
                "Canh bao rang buoc NOT NULL",
                JOptionPane.WARNING_MESSAGE);
    }

    /** Xử lý trả hàng - cho phép khách hàng hoàn trả sản phẩm từ hoá đơn */
    private void onReturnLine() {
        String invoiceId = getSelectedInvoiceId();
        if (invoiceId == null) {
            JOptionPane.showMessageDialog(this, "Vui long chon mot hoa don de tra hang!", "Chua chon hoa don", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            // Lấy thông tin hoá đơn để hiển thị
            QueryResult headerQr = dao.invoiceHeader(invoiceId);
            String headerInfo = "";
            if (!headerQr.isEmpty()) {
                Object[] row = headerQr.rows.get(0);
                String date = String.valueOf(row[1]);
                String time = String.valueOf(row[2]);
                headerInfo = "Hoa don: " + invoiceId + " | Ngay: " + date + " | Gio: " + time;
            }

            // Lấy danh sách các dòng hàng trong hoá đơn
            QueryResult linesQueryResult = dao.invoiceLines(invoiceId);
            if (linesQueryResult.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Hoa don khong co dong hang nao de tra!", "Thong bao", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            // Chuyển đổi dữ liệu thô thành InvoiceLineData objects
            List<InvoiceLineData> availableLines = new ArrayList<>();
            for (Object[] row : linesQueryResult.rows) {
                String lineNumber = String.valueOf(row[0]);      // Dong (STT)
                String productName = String.valueOf(row[1]);     // San_pham
                double quantity = parseVNCurrency(row[2]); // SL
                String unit = String.valueOf(row[3]);            // DVT
                double unitPrice = parseVNCurrency(row[4]); // Don_gia
                double discount = parseVNCurrency(row[5]); // Giam_gia

                availableLines.add(new InvoiceLineData(lineNumber, productName, quantity, unit, unitPrice, discount));
            }

            // Tạo dialog trả hàng
            JDialog returnDialog = new JDialog((Frame) null, "Tra hang hoa don", true);
            returnDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            returnDialog.setLayout(new BorderLayout(10, 10));
            ((JPanel) returnDialog.getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            // Panel header
            JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            JLabel headerLabel = new JLabel(headerInfo);
            headerLabel.setFont(Ui.BOLD);
            headerPanel.add(headerLabel);
            returnDialog.add(headerPanel, BorderLayout.NORTH);

            // Panel chính chứa hai bảng
            JPanel mainPanel = new JPanel(new GridLayout(1, 2, 10, 10));

            // Bảng trái: Danh sách sản phẩm có thể trả
            JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
            leftPanel.setBorder(BorderFactory.createTitledBorder("Danh sach san pham trong hoa don"));

            String[] lineColumns = {"Dong", "San_pham", "SL", "DVT", "Don_gia", "Giam_gia", "Thanh_tien"};
            InvoiceLineTableModel availableModel = new InvoiceLineTableModel(availableLines, lineColumns);
            JTable availableTable = new JTable(availableModel);
            availableTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

            // Tùy chỉnh hiển thị số tiền cho bảng sản phẩm có sẵn
            availableTable.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                protected void setValue(Object value) {
                    if (value instanceof Number) {
                        setText(formatCurrency((Number) value));
                    } else {
                        setText(value == null ? "" : value.toString());
                    }
                }
            });
            availableTable.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                protected void setValue(Object value) {
                    if (value instanceof Number) {
                        setText(formatCurrency((Number) value));
                    } else {
                        setText(value == null ? "" : value.toString());
                    }
                }
            });
            availableTable.getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                protected void setValue(Object value) {
                    if (value instanceof Number) {
                        setText(formatCurrency((Number) value));
                    } else {
                        setText(value == null ? "" : value.toString());
                    }
                }
            });

            JScrollPane availableScroll = new JScrollPane(availableTable);
            leftPanel.add(availableScroll, BorderLayout.CENTER);

            // Bảng phải: Danh sách sản phẩm đã chọn để trả
            JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
            rightPanel.setBorder(BorderFactory.createTitledBorder("Danh sach san pham se duoc tra"));

            String[] returnColumns = {"Dong", "San_pham", "SL_tra", "DVT", "Don_gia", "Giam_gia", "Thanh_tien_tra"};
            // Sử dụng DefaultTableModel cho bảng trả hàng vì chúng ta cần lưu trữ số lượng trả cụ thể
            DefaultTableModel returnModel = new DefaultTableModel(returnColumns, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            JTable returnTable = new JTable(returnModel);
            returnTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

            // Tùy chỉnh hiển thị số tiền cho bảng sản phẩm trả
            returnTable.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                protected void setValue(Object value) {
                    if (value instanceof Number) {
                        setText(formatCurrency((Number) value));
                    } else {
                        setText(value == null ? "" : value.toString());
                    }
                }
            });
            returnTable.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                protected void setValue(Object value) {
                    if (value instanceof Number) {
                        setText(formatCurrency((Number) value));
                    } else {
                        setText(value == null ? "" : value.toString());
                    }
                }
            });
            returnTable.getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                protected void setValue(Object value) {
                    if (value instanceof Number) {
                        setText(formatCurrency((Number) value));
                    } else {
                        setText(value == null ? "" : value.toString());
                    }
                }
            });

            JScrollPane returnScroll = new JScrollPane(returnTable);
            rightPanel.add(returnScroll, BorderLayout.CENTER);

            // Panel dưới: Tổng tiền phải trả và nút hành động
            JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));

            // Panel hiển thị tổng tiền
            JPanel totalPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JLabel totalLabel = new JLabel("Tong tien phai tra lai: ");
            totalLabel.setFont(Ui.BOLD);
            JLabel totalAmountLabel = new JLabel("0 d");
            totalAmountLabel.setFont(Ui.BOLD);
            totalAmountLabel.setForeground(Color.RED);
            totalPanel.add(totalLabel);
            totalPanel.add(totalAmountLabel);
            bottomPanel.add(totalPanel, BorderLayout.WEST);

            // Panel nút hành động
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            JButton btnConfirm = new JButton("Xac nhan tra hang");
            JButton btnCancel = new JButton("Huy");

            // Nút Thêm
            JButton btnAdd = new JButton("Them >>");
            btnAdd.addActionListener(e -> {
                int selectedRow = availableTable.getSelectedRow();
                if (selectedRow >= 0) {
                    // Lấy thông tin sản phẩm từ bảng bên trái
                    InvoiceLineData lineData = availableModel.getRowData(selectedRow);
                    if (lineData == null) {
                        return;
                    }

                    String productName = lineData.getProductName();

                    // Kiểm tra xem sản phẩm này đã có trong bảng trả hàng chưa
                    boolean alreadyExists = false;
                    for (int i = 0; i < returnModel.getRowCount(); i++) {
                        if (returnModel.getValueAt(i, 1).equals(productName)) {
                            alreadyExists = true;
                            break;
                        }
                    }

                    if (!alreadyExists) {
                        // Yêu cầu nhập số lượng trả
                        int originalQuantity = (int) Math.round(lineData.getQuantity());

                        String input = JOptionPane.showInputDialog(returnDialog,
                                "Nhap so luong tra cho san pham: " + productName + "\n"
                                + "So luong mua ban dau: " + originalQuantity,
                                "Nhap so luong tra",
                                JOptionPane.QUESTION_MESSAGE);

                        if (input != null && !input.trim().isEmpty()) {
                            try {
                                int returnQuantity = Integer.parseInt(input.trim());
                                // Kiểm tra hợp lệ
                                if (returnQuantity <= 0) {
                                    JOptionPane.showMessageDialog(returnDialog,
                                            "So luong tra phai la so duong lon hon 0!",
                                            "Loi nhap lieu", JOptionPane.ERROR_MESSAGE);
                                    return;
                                }
                                if (returnQuantity > originalQuantity) {
                                    JOptionPane.showMessageDialog(returnDialog,
                                            "So luong tra khong duoc vuot qua so luong mua ban dau ("
                                            + originalQuantity + ")!",
                                            "Loi nhap lieu", JOptionPane.ERROR_MESSAGE);
                                    return;
                                }

                                // Tính thành tiền trả
                                double unitPriceAfterDiscount = lineData.getUnitPriceAfterDiscount();
                                double returnAmount = returnQuantity * unitPriceAfterDiscount;

                                // Định dạng kết quả
                                String formattedUnitPrice = formatCurrency(lineData.getUnitPrice());
                                String formattedDiscount = formatCurrency(lineData.getDiscount());
                                String formattedReturnAmount = formatCurrency(returnAmount);

                                // Thêm vào bảng trả hàng
                                Object[] returnRow = {
                                        lineData.getLineNumber(), // Dong
                                        lineData.getProductName(), // San_pham
                                        String.valueOf(returnQuantity), // SL_tra
                                        lineData.getUnit(), // DVT
                                        formattedUnitPrice, // Don_gia
                                        formattedDiscount, // Giam_gia
                                        formattedReturnAmount // Thanh_tien_tra
                                };
                                returnModel.addRow(returnRow);

                                // Cập nhật lạiSTT và tổng tiền
                                updateReturnTableSTTAndTotal(returnTable, returnModel);
                                // Cập nhật nhãn tổng tiền hoàn lại
                                updateTotalRefundLabel(returnModel, totalAmountLabel);

                            } catch (NumberFormatException ex) {
                                JOptionPane.showMessageDialog(returnDialog,
                                        "Vui long nhap so luong la so nguyen hop le!",
                                        "Loi nhap lieu", JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    } else {
                        JOptionPane.showMessageDialog(returnDialog,
                                "San pham da co trong danh sach tra hang!",
                                "Thong bao", JOptionPane.WARNING_MESSAGE);
                    }
                } else {
                    JOptionPane.showMessageDialog(returnDialog,
                            "Vui long chon mot san pham de them!",
                            "Thong bao", JOptionPane.WARNING_MESSAGE);
                }
            });

            leftPanel.add(btnAdd, BorderLayout.SOUTH);
            mainPanel.add(leftPanel);

            // Nút Xoá
            JButton btnRemove = new JButton("<< Xoa");
            btnRemove.addActionListener(e -> {
                int selectedRow = returnTable.getSelectedRow();
                if (selectedRow >= 0) {
                    returnModel.removeRow(selectedRow);
                    // Cập nhật lạiSTT và tổng tiền sau khi xóa
                    updateReturnTableSTTAndTotal(returnTable, returnModel);
                    // Cập nhật nhãn tổng tiền hoàn lại
                    updateTotalRefundLabel(returnModel, totalAmountLabel);
                } else {
                    JOptionPane.showMessageDialog(returnDialog, "Vui long chon mot san pham de xoa!", "Thong bao", JOptionPane.WARNING_MESSAGE);
                }
            });

            rightPanel.add(btnRemove, BorderLayout.SOUTH);
            mainPanel.add(rightPanel);

            returnDialog.add(mainPanel, BorderLayout.CENTER);

            btnConfirm.addActionListener(e -> {
                // Xác nhận trả hàng - lưu vào database
                if (returnModel.getRowCount() == 0) {
                    JOptionPane.showMessageDialog(returnDialog, "Khong co san pham nao de tra!", "Thong bao", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                // Tính lại tổng tiền trả lại để đảm bảo chính xác
                BigDecimal totalRefund = BigDecimal.ZERO;
                for (int i = 0; i < returnModel.getRowCount(); i++) {
                    String amountStr = returnModel.getValueAt(i, 6).toString(); // Cột Thanh_tien_tra
                    try {
                        // Chuyển đổi từ định dạng "1,234,567 d" thành số
                        String cleanAmount = amountStr.replaceAll("[^\\d.]", "");
                        if (!cleanAmount.isEmpty()) {
                            totalRefund = totalRefund.add(new BigDecimal(cleanAmount));
                        }
                    } catch (Exception ex) {
                        // Bỏ qua nếu không parse được
                    }
                }

                double totalRefundDouble = 0;
        // Tính lại tổng tiền trả lại để đảm bảo chính xác (sử dụng dữ liệu thô)
        for (int i = 0; i < returnModel.getRowCount(); i++) {
            String productName = returnModel.getValueAt(i, 1).toString();
            int returnQuantity = Integer.parseInt(returnModel.getValueAt(i, 2).toString());

            // Tìm thông tin sản phẩm trong danh sách gốc để lấy đơn giá và giảm giá
            InvoiceLineData originalLineData = null;
            for (InvoiceLineData line : availableLines) {
                if (line.getProductName().equals(productName)) {
                    originalLineData = line;
                    break;
                }
            }

            if (originalLineData != null) {
                double unitPrice = originalLineData.getUnitPrice();
                double discount = originalLineData.getDiscount();
                double unitPriceAfterDiscount = unitPrice - discount;
                totalRefundDouble += returnQuantity * unitPriceAfterDiscount;
            }
        }

        int choice = JOptionPane.showConfirmDialog(returnDialog,
                            "Ban co chac chan muon xu ly tra hang voi tong so tien hoan lai la "
                                    + formatCurrency(totalRefund) + " khong?",
                            "Xac nhan xu ly tra hang", JOptionPane.YES_NO_OPTION);

                if (choice == JOptionPane.YES_OPTION) {
                    try {
                        // Chuẩn bị danh sách trả hàng để lưu
                        List<ReturnItem> returnList = new ArrayList<>();

                        // Lấy danh sách các dòng hàng trong hoá đơn để có thể mapping Line_Number
                        QueryResult linesDataResult = dao.invoiceLines(invoiceId);
                        Map<String, Integer> lineNumberMap = new HashMap<>(); // productName -> Line_Number
                        for (Object[] row : linesDataResult.rows) {
                            String productName = String.valueOf(row[1]); // San_pham
                            int lineNumber = Integer.parseInt(String.valueOf(row[0])); // Dong
                            lineNumberMap.put(productName, lineNumber);
                        }

                        // Duyệt qua các sản phẩm trong bảng trả hàng
                        for (int i = 0; i < returnModel.getRowCount(); i++) {
                            String productName = returnModel.getValueAt(i, 1).toString();
                            int returnQuantity = Integer.parseInt(returnModel.getValueAt(i, 2).toString());

                            // Lấy Line_Number từ mapping
                            Integer lineNumber = lineNumberMap.get(productName);
                            if (lineNumber == null) {
                                // Nếu không tìm thấy, thử tìm bằng cách khác
                                continue;
                            }

                            // Tìm thông tin sản phẩm trong danh sách gốc để lấy đơn giá và giảm giá
                            InvoiceLineData originalLineData = null;
                            for (InvoiceLineData line : availableLines) {
                                if (line.getProductName().equals(productName)) {
                                    originalLineData = line;
                                    break;
                                }
                            }

                            if (originalLineData == null) {
                                continue; // Không tìm thấy thông tin sản phẩm gốc
                            }

                            // Lấy đơn giá và giảm giá từ dữ liệu gốc
                            double unitPrice = originalLineData.getUnitPrice();
                            double discount = originalLineData.getDiscount();
                            double unitPriceAfterDiscount = unitPrice - discount;
                            double amountRefunded = returnQuantity * unitPriceAfterDiscount;

                            ReturnItem item = new ReturnItem(
                                    productName,
                                    lineNumber,
                                    returnQuantity,
                                    amountRefunded
                            );
                            returnList.add(item);
                        }

                        // Lấy ID của người quản lý hiện tại (giả sử là người đang thao tác)
                        String supervisorId = "";
                        Account currentUser = UserSession.getCurrentUser();
                        if (currentUser != null) {
                            supervisorId = currentUser.getUsername();
                        }

                        // Gọi DAO để lưu trả hàng
                        boolean success = dao.processReturn(invoiceId, returnList, supervisorId);

                        if (success) {
                            JOptionPane.showMessageDialog(returnDialog,
                                    "Xu ly tra hang thanh cong!\n"
                                            + "Tong tien hoan lai: " + formatCurrency(totalRefund),
                                    "Thanh cong", JOptionPane.INFORMATION_MESSAGE);
                            returnDialog.dispose();
                            reload(); // Lam moi danh sach hoa don
                            onSelect(null); // Lam moi chi tiết hoa don
                        } else {
                            JOptionPane.showMessageDialog(returnDialog,
                                    "Xu ly tra hang that bai! Vui long thu lai sau.",
                                    "Loi", JOptionPane.ERROR_MESSAGE);
                        }
                    } catch (Exception ex) {
                        Ui.error(returnDialog, ex);
                    }
                }
            });

            btnCancel.addActionListener(e -> {
                returnDialog.dispose();
            });

            buttonPanel.add(btnConfirm);
            buttonPanel.add(btnCancel);
            bottomPanel.add(buttonPanel, BorderLayout.EAST);

            returnDialog.add(bottomPanel, BorderLayout.SOUTH);

            // Cập nhật hiển thị tổng tiền khi thay đổi bảng trả hàng
            // Đây là một cách đơn giản để cập nhật - trong thực tế chúng ta nên sử dụng TableModelListener
            // nhưng để đơn giản, chúng ta sẽ cập nhật sau mỗi thao tác Thêm/Xoá
            // Cập nhật nhãn tổng tiền hoàn lại khi mở dialog
            updateTotalRefundLabel(returnModel, totalAmountLabel);

            returnDialog.pack();
            returnDialog.setLocationRelativeTo(this);
            returnDialog.setVisible(true);

        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    
    /**
     * Cập nhật lạiSTT và tính tổng tiền cho bảng trả hàng
     */
    private void updateReturnTableSTTAndTotal(JTable table, DefaultTableModel model) {
        // Cập nh lạiSTT (cột đầu tiên)
        for (int i = 0; i < model.getRowCount(); i++) {
            model.setValueAt(i + 1, i, 0);
        }
    }

    /**
     * Cập nhật nhãn hiển thị tổng tiền hoàn lại
     * @param returnModel Modelo của bảng trả hàng
     * @param totalAmountLabel Nhãn hiển thị tổng tiền
     */
    private void updateTotalRefundLabel(DefaultTableModel returnModel, JLabel totalAmountLabel) {
        double totalRefund = 0;
        // Tính tổng tiền hoàn từ các mục trong bảng trả hàng
        for (int i = 0; i < returnModel.getRowCount(); i++) {
            String amountStr = returnModel.getValueAt(i, 6).toString(); // Cột Thanh_tien_tra
            try {
                // Chuyển đổi từ định dạng "1,234,567 đ" thành số
                String cleanAmount = amountStr.replaceAll("[^\\d.]", "");
                if (!cleanAmount.isEmpty()) {
                    totalRefund += Double.parseDouble(cleanAmount);
                }
            } catch (Exception ex) {
                // Bỏ qua nếu không parse được
            }
        }
        // Cập nhật nhãn hiển thị
        totalAmountLabel.setText(formatCurrency(totalRefund));
    }
}