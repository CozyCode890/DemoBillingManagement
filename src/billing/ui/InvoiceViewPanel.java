package billing.ui;

import billing.dao.InvoiceDao;
import billing.dao.InvoiceDao.ReturnItem;
import billing.dao.LookupDao;
import billing.db.QueryResult;
import billing.model.Account;
import billing.model.IdName;
import billing.model.InvoiceStatus;
import billing.model.UserSession;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * ====================================================================
 *  MÀN HÌNH 2 -- XEM HÓA ĐƠN & THAO TÁC TRỰC TIẾP DATABASE
 * ====================================================================
 *  Bên trái: danh sách hóa đơn + nút "Sửa hóa đơn", "Hủy hóa đơn", "Xóa hóa đơn".
 *  ("Hủy" đổi Status sang VOIDED và giữ nguyên dữ liệu; "Xóa" xóa sạch 7 bảng.)
 *  Bên phải:
 *    1. Thông tin chung + tổng tiền
 *    2. Các dòng hàng (Invoice_Line) + nút "Sửa dòng", "Xóa dòng", "Thử xóa (Set NULL)"
 *    3. Thanh toán (Payment + bảng con)
 *    4. Trả hàng (Return) + nút "Trả hàng", "Hủy lượt trả"
 *
 *  PHÂN QUYỀN TRÊN UI (UI LOCKING):
 *  - Thu ngân (cashier): Các nút sửa/xóa/trả hàng bị khóa (disable).
 *  - Quản lý (manager): Các nút được kích hoạt (enable).
 *  (Lưu ý: Tầng DAO không khóa để phục vụ các mục đích nghiệp vụ phía sau).
 *
 *  TIỀN HOÀN KHI TRẢ HÀNG: lớp này KHÔNG tự tính, dù chỉ một phép nhân.
 *  Công thức chỉ tồn tại ở một chỗ trong cả dự án và chỗ đó là SQL
 *  (fn_return_refund) -- xem plan/12 mục 2.2.
 */
public class InvoiceViewPanel extends JPanel {

    private final InvoiceDao dao = new InvoiceDao();
    private final LookupDao lookupDao = new LookupDao();

    private final JTable tbInvoices = Ui.readOnlyTable();
    private final JTable tbLines    = Ui.readOnlyTable();
    private final JTable tbPayments = Ui.readOnlyTable();
    private final JTable tbReturns  = Ui.readOnlyTable();
    private final JTextArea taHeader = new JTextArea(8, 40);

    // Các button thao tác hóa đơn (bên trái)
    private final JButton btEditInvoice   = new JButton("Sua hoa don");
    // Huỷ KHÁC xoá: nút này đổi Status sang VOIDED và để nguyên dữ liệu;
    // btDeleteInvoice xoá sạch khỏi 7 bảng (plan/13 mục 3.1).
    private final JButton btVoidInvoice   = new JButton("Huy hoa don");
    private final JButton btDeleteInvoice = new JButton("Xoa hoa don");

    // Các button thao tác dòng hàng trong hóa đơn (bên phải)
    private final JButton btEditLine       = new JButton("Sua dong hang");
    private final JButton btDeleteLine     = new JButton("Xoa dong hang");
    private final JButton btClearLineValue = new JButton("Thu xoa gia tri (Set NULL)");
    private final JButton btReturnLine     = new JButton("Tra hang");

    // Button thao tác trên bảng "Tra hang (Return)"
    private final JButton btDeleteReturn   = new JButton("Huy luot tra");

    // =================================================================
    //  Ô CHỌN KHOẢNG NGÀY + NHÃN SỐ DÒNG (khối 6)
    // =================================================================
    //  Seed 91 ngày có ~550 hóa đơn và bảng này không có ô lọc nào, nên nạp
    //  cả bảng là vừa chậm vừa không đọc được (plan/15 lỗi #18).
    //
    //  Nhãn số dòng KHÔNG phải trang trí: một bảng bị cắt mà không nói là
    //  đang nói sai. plan/14 mục 5.6f đã đặt quy ước đó cho nút "Tạo mã QR"
    //  ("một nút bị khoá không giải thích được gì"), và nó áp dụng y nguyên
    //  cho một bảng bị cắt.
    //
    //  DAY_VALUES song song từng phần tử với các nhãn của cbDays; 0 = tất cả,
    //  đúng hợp đồng của sp_get_recent_invoices(p_days).
    private static final int[] DAY_VALUES = { 7, 30, 90, 0 };
    private final JComboBox<String> cbDays = new JComboBox<>(new String[] {
            "7 ngay gan nhat", "30 ngay gan nhat", "90 ngay gan nhat", "Tat ca"
    });
    private final JLabel lbInvoiceCount = new JLabel(" ");

    // =================================================================
    //  ĐỊNH DẠNG SỐ -- KHÔNG CÒN BẢN CHÉP Ở ĐÂY
    // =================================================================
    //  Trước khối 5 lớp này giữ bộ DecimalFormat RIÊNG của nó: bản chép thứ
    //  hai trong bốn bản của cả dự án (Ui, lớp này, và hai lần trong
    //  SearchPanel). Thêm một kiểu cột là phải sửa bốn chỗ, mà không chỗ nào
    //  báo nếu quên -- đúng loại lỗi mà khối 3 trị bằng fn_return_refund và
    //  khối 4 trị bằng InvoiceStatus (plan/14 lỗi #22).
    //
    //  Giờ mọi phép định dạng đi qua Ui.money / Ui.qty / Ui.percent / Ui.dec,
    //  và nhờ vậy VAT_pct tự đúng là "8%" khi fillFormatted xếp nó vào nhóm
    //  phần trăm (lỗi #21).

    private static int intOf(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        return Integer.parseInt(String.valueOf(o).trim());
    }

    /**
     * Đổ dữ liệu vào JTable với định dạng số tiền / số nguyên / số lượng /
     * phần trăm phù hợp.
     *
     * Chỉ số cột ở đây là HỢP ĐỒNG với thứ tự cột của các thủ tục SQL: đổi cột
     * trả về của thủ tục là phải sửa cả 4 chỗ gọi hàm này (plan/12 mục 3.5).
     * Vì vậy mỗi chỗ gọi có comment liệt kê tên cột theo chỉ số.
     *
     * percentCols là tham số thứ sáu, thêm ở khối 5: VAT_pct bị xếp vào nhóm
     * tiền tệ nên 8.00 hiện ra là "8đ" (plan/14 lỗi #21) -- việc khối 3 và
     * khối 4 đều đã giao lại cho khối này.
     */
    private void fillFormatted(JTable table, QueryResult qr,
                               int[] currencyCols, int[] integerCols,
                               int[] quantityCols, int[] percentCols) {
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        model.setRowCount(0);
        model.setColumnCount(0);
        for (String col : qr.columns) {
            model.addColumn(col);
        }
        for (Object[] row : qr.rows) {
            Object[] formattedRow = new Object[row.length];
            for (int i = 0; i < row.length; i++) {
                Object val = row[i];
                if (contains(currencyCols, i)) {
                    // null -> "" chu khong phai "0đ": cot Tien_khach_dua la NULL
                    // voi the / vi dien tu, va "0đ" o do la noi rang khach dua 0
                    // dong, trong khi su that la "phuong thuc nay khong co tien
                    // khach dua" (plan/14 muc 3.1).
                    formattedRow[i] = (val == null) ? "" : Ui.money(val);
                } else if (contains(integerCols, i)) {
                    formattedRow[i] = Ui.integer(val);
                } else if (contains(quantityCols, i)) {
                    formattedRow[i] = Ui.qty(val);
                } else if (contains(percentCols, i)) {
                    formattedRow[i] = Ui.percent(val);
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

        // Mặc định 30 ngày: đủ dày để thấy cả ba trạng thái và vài lượt trả
        // hàng, mà không nạp cả 548 dòng ngay khi mở tab.
        cbDays.setSelectedIndex(1);
        cbDays.addActionListener(e -> reload());
        cbDays.setToolTipText("Loc theo ngay ban. 'Tat ca' nap moi hoa don, "
                + "ke ca hai hoa don INV-OLD-* tu nam truoc.");

        JPanel pnlFilter = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        pnlFilter.add(new JLabel("Khoang ngay:"));
        pnlFilter.add(cbDays);
        pnlFilter.add(btReload);

        lbInvoiceCount.setFont(lbInvoiceCount.getFont().deriveFont(Font.PLAIN, 11f));
        lbInvoiceCount.setForeground(new Color(90, 90, 90));
        lbInvoiceCount.setBorder(BorderFactory.createEmptyBorder(2, 4, 0, 4));

        JPanel pnlInvoiceActions = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
        btEditInvoice.setFont(Ui.BOLD);
        btVoidInvoice.setFont(Ui.BOLD);
        // Cam (không phải đỏ) để mắt phân biệt được ngay với nút xoá vĩnh viễn.
        btVoidInvoice.setForeground(new Color(190, 110, 20));
        btDeleteInvoice.setFont(Ui.BOLD);
        btDeleteInvoice.setForeground(new Color(180, 40, 40));

        btEditInvoice.addActionListener(e -> onEditInvoice());
        btVoidInvoice.addActionListener(e -> onVoidInvoice());
        btDeleteInvoice.addActionListener(e -> onDeleteInvoice());

        pnlInvoiceActions.add(btEditInvoice);
        pnlInvoiceActions.add(btVoidInvoice);
        pnlInvoiceActions.add(btDeleteInvoice);

        JPanel pnlInvoiceSouth = new JPanel(new BorderLayout());
        pnlInvoiceSouth.add(lbInvoiceCount, BorderLayout.NORTH);
        pnlInvoiceSouth.add(pnlInvoiceActions, BorderLayout.CENTER);

        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.add(pnlFilter, BorderLayout.NORTH);
        left.add(new JScrollPane(tbInvoices), BorderLayout.CENTER);
        left.add(pnlInvoiceSouth, BorderLayout.SOUTH);
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

        // Panel riêng cho bảng trả hàng kèm nút hủy một lượt trả
        JPanel pnlReturnActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        btDeleteReturn.setForeground(new Color(180, 40, 40));
        btDeleteReturn.addActionListener(e -> onDeleteReturn());
        pnlReturnActions.add(btDeleteReturn);

        JPanel pnlReturnsContainer = new JPanel(new BorderLayout(2, 2));
        pnlReturnsContainer.add(new JScrollPane(tbReturns), BorderLayout.CENTER);
        pnlReturnsContainer.add(pnlReturnActions, BorderLayout.SOUTH);

        JPanel right = new JPanel(new GridLayout(4, 1, 6, 6));
        right.add(Ui.titled("Thong tin chung + tong tien", new JScrollPane(taHeader)));
        right.add(Ui.titled("Cac dong hang (Invoice_Line)", pnlLinesContainer));
        right.add(Ui.titled("Thanh toan (Payment + bang con)", new JScrollPane(tbPayments)));
        right.add(Ui.titled("Tra hang (Return)", pnlReturnsContainer));

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
     *  - Nếu là 'cashier' -> Toàn bộ nút Sửa/Xóa/Trả hàng bị khóa (disabled)
     *  - Nếu là 'manager' -> Toàn bộ nút được mở khóa (enabled)
     *  (Tuyệt đối không chặn ở DAO/Service theo yêu cầu của bạn)
     *
     *  MainWindow.switchRole() gọi lại đúng hàm này, nên đổi vai trò tại chỗ
     *  cũng tự đúng (plan/11 mục 4.4).
     *
     *  Nút "Tra hang" nằm trong danh sách này từ khối 3: cột được ghi tên là
     *  Approved_By -- NGƯỜI DUYỆT. Một nút ghi vào ô "người duyệt" mà ai cũng
     *  bấm được thì ô đó không duyệt gì cả.
     *
     *  Nút "Huy hoa don" vào danh sách từ khối 4 -- thành 8 nút. Huỷ hoá đơn
     *  là hành động nghiệp vụ của quản lý, không phải một lần sửa trường.
     */
    public void applyRolePermissions() {
        Account user = UserSession.getCurrentUser();
        boolean isManager = (user != null && user.isManager());

        btEditInvoice.setEnabled(isManager);
        btVoidInvoice.setEnabled(isManager);
        btDeleteInvoice.setEnabled(isManager);
        btEditLine.setEnabled(isManager);
        btDeleteLine.setEnabled(isManager);
        btClearLineValue.setEnabled(isManager);
        btReturnLine.setEnabled(isManager);
        btDeleteReturn.setEnabled(isManager);

        if (!isManager) {
            String lockHint = "Chuc nang nay bi khoa voi Thu ngan (Cashier). Chi Quan ly (Manager) moi co quyen.";
            btEditInvoice.setToolTipText(lockHint);
            btVoidInvoice.setToolTipText(lockHint);
            btDeleteInvoice.setToolTipText(lockHint);
            btEditLine.setToolTipText(lockHint);
            btDeleteLine.setToolTipText(lockHint);
            btClearLineValue.setToolTipText(lockHint);
            btReturnLine.setToolTipText(lockHint);
            btDeleteReturn.setToolTipText(lockHint);
        } else {
            btEditInvoice.setToolTipText("Chinh sua thong tin hoa don truc tiep trong DB");
            btVoidInvoice.setToolTipText("Huy hoa don: du lieu van con, chi khong tinh vao bao cao");
            btDeleteInvoice.setToolTipText("Xoa hoa don va toan bo du lieu lien quan khoi DB");
            btEditLine.setToolTipText("Chinh sua so luong, don gia dong hang trong DB");
            btDeleteLine.setToolTipText("Xoa dong hang khoi DB");
            btClearLineValue.setToolTipText("Thu nghiem co che canh bao xoa gia tri NOT NULL");
            btReturnLine.setToolTipText("Ghi mot luot tra hang cho cac dong cua hoa don dang chon");
            btDeleteReturn.setToolTipText("Huy mot luot tra hang da ghi (chon dong trong bang Tra hang)");
        }
    }

    public void reload() {
        try {
            int sel  = cbDays.getSelectedIndex();
            int days = DAY_VALUES[sel < 0 ? 1 : sel];

            QueryResult qr = dao.recentInvoices(days);
            // tbInvoices: Ma_HD, Ngay, Gio, Shop, Thu_ngan, Khach, Trang_thai, Tong_tien
            // Tong_tien la cot CUOI va la cot tien duy nhat.
            fillFormatted(tbInvoices, qr, new int[]{qr.columns.size() - 1}, null, null, null);
            tbInvoices.setAutoCreateRowSorter(true); // Enable column sorting

            // Nói ra số dòng VÀ phạm vi: một bảng bị cắt phải nói là đang bị
            // cắt, nếu không nó đang nói sai (plan/15 mục 5.2).
            lbInvoiceCount.setText("Dang hien " + Ui.integer(qr.rows.size()) + " hoa don ("
                    + (days == 0 ? "tat ca" : days + " ngay gan nhat") + ")");
        } catch (Exception ex) {
            lbInvoiceCount.setText(" ");
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
        showDetail(invoiceId);
    }

    /**
     * Vẽ lại 4 khung chi tiết cho một hóa đơn.
     *
     * Tách ra từ onSelect để gọi được từ chỗ khác mà KHÔNG phụ thuộc vào dòng
     * đang được chọn trong tbInvoices: reload() gọi model.setRowCount(0) nên nó
     * xóa luôn selection, và đó chính là lý do trước đây lưu trả hàng xong mà
     * bảng "Tra hang (Return)" vẫn giữ nội dung cũ (plan/12 lỗi #12).
     */
    private void showDetail(String invoiceId) {
        try {
            // --- 1. Phần đầu hóa đơn -------------------------------
            QueryResult h = dao.invoiceHeader(invoiceId);
            StringBuilder sb = new StringBuilder();
            if (!h.isEmpty()) {
                Object[] row = h.rows.get(0);
                for (int i = 0; i < h.columns.size(); i++) {
                    Object val = row[i];
                    String formatted = (val instanceof Number)
                            ? Ui.money(val)
                            : (val == null ? "" : val.toString());
                    sb.append(String.format("%-18s : %s%n", h.columns.get(i), formatted));
                }
            }
            taHeader.setText(sb.toString());
            taHeader.setCaretPosition(0);

            // --- 2, 3, 4 -------------------------------------------
            // tbLines: Dong, San_pham, SL, DVT, Don_gia, Giam_gia, Thanh_tien, VAT_pct
            // SL (2) la so luong DECIMAL(10,3); Don_gia (4), Giam_gia (5),
            // Thanh_tien (6) di theo nhom tien te.
            // VAT_pct (7) la PHAN TRAM, KHONG phai tien: ban cu xep no vao nhom
            // tien te nen 8.00 hien ra la "8đ" (plan/14 loi #21).
            QueryResult lines = dao.invoiceLines(invoiceId);
            fillFormatted(tbLines, lines, new int[]{4, 5, 6}, null, new int[]{2}, new int[]{7});

            // tbPayments: Ma_TT, So_tien, Phuong_thuc, Chi_tiet, Tien_khach_dua
            // So_tien (1) va Tien_khach_dua (4) deu la tien. Cot thu 5 moi co tu
            // khoi 5: sp_get_invoice_payments khong CONCAT so tien vao Chi_tiet
            // nua, nen tab 3 het hien "Khach dua: 500000.00" (plan/14 loi #22).
            QueryResult payments = dao.invoicePayments(invoiceId);
            fillFormatted(tbPayments, payments, new int[]{1, 4}, null, null, null);

            // tbReturns: Dong, Lan_tra, San_pham, Ngay_tra, SL_tra, Tien_hoan, Nguoi_duyet
            // SL_tra (4) la so luong; Tien_hoan (5) la tien. Chi so da dich mot cot
            // so voi truoc khoi 3 vi sp_get_invoice_returns them cot Lan_tra.
            QueryResult returns = dao.invoiceReturns(invoiceId);
            fillFormatted(tbReturns, returns, new int[]{5}, null, new int[]{4}, null);

        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    /** Chọn lại đúng hàng của hóa đơn này trong tbInvoices sau khi reload() xóa selection. */
    private void selectInvoiceRow(String invoiceId) {
        for (int viewRow = 0; viewRow < tbInvoices.getRowCount(); viewRow++) {
            if (invoiceId.equals(String.valueOf(tbInvoices.getValueAt(viewRow, 0)))) {
                tbInvoices.setRowSelectionInterval(viewRow, viewRow);
                tbInvoices.scrollRectToVisible(tbInvoices.getCellRect(viewRow, 0, true));
                return;
            }
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
            // Tra cứu thông tin gốc của hóa đơn.
            // Thủ tục, KHÔNG phải SQL thô: câu SELECT cũ nằm ngoài tầm với của
            // mọi thay đổi trong sql/ -- đổi tên cột là nó sai thầm lặng
            // (plan/13 lỗi #8). sp_get_invoice_edit_fields giữ đúng 7 cột theo
            // thứ tự cũ nên các chỉ số r[1]..r[6] bên dưới không phải sửa.
            QueryResult qr = dao.invoiceEditFields(invoiceId);
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
                // reload() xoá selection -> chọn lại rồi vẽ lại chi tiết, thay
                // cho onSelect(null) vốn không còn hàng nào để đọc (lỗi #30).
                reload();
                selectInvoiceRow(invoiceId);
                showDetail(invoiceId);
            }

        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    /**
     * Hủy một hóa đơn (Status -> VOIDED).
     *
     * ĐÂY KHÔNG PHẢI XÓA. Hộp thoại xác nhận phải dạy được sự khác nhau, vì
     * hai nút nằm cạnh nhau và "huỷ" là từ dễ bị hiểu thành "xoá" (plan/13
     * rủi ro #7).
     *
     * Chạy NGOÀI EDT: sp_void_invoice có SELECT ... FOR UPDATE, MySQL chậm
     * hoặc dòng đang bị khóa bởi cửa sổ khác là cả cửa sổ đứng hình.
     */
    private void onVoidInvoice() {
        final String invoiceId = getSelectedInvoiceId();
        if (invoiceId == null) {
            JOptionPane.showMessageDialog(this, "Vui long chon mot hoa don de huy!",
                    "Chua chon hoa don", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int choice = JOptionPane.showConfirmDialog(this,
                "Huy hoa don " + invoiceId + "?\n\n"
              + "Huy KHAC Xoa:\n"
              + "  - Du lieu van con nguyen: dong hang, thanh toan, tra hang\n"
              + "  - Hoa don khong con duoc tinh vao bao cao doanh thu\n"
              + "  - Khong tra hang duoc nua\n"
              + "  - KHONG mo lai duoc: VOIDED la trang thai cuoi\n\n"
              + "Muon xoa sach khoi database thi dung nut \"Xoa hoa don\".",
                "Xac nhan huy hoa don",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (choice != JOptionPane.YES_OPTION) return;

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                dao.voidInvoice(invoiceId);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception ex) {
                    showSqlFailure(InvoiceViewPanel.this, ex, "Khong huy duoc hoa don");
                    return;
                }

                JOptionPane.showMessageDialog(InvoiceViewPanel.this,
                        "Da huy hoa don " + invoiceId + ".\n"
                      + "Du lieu van con trong database, chi khong tinh vao bao cao nua.",
                        "Da huy hoa don", JOptionPane.INFORMATION_MESSAGE);

                // PHẢI THẤY KẾT QUẢ: reload() xóa selection nên chọn lại đúng
                // hàng rồi vẽ lại chi tiết (plan/12 lỗi #12).
                reload();
                selectInvoiceRow(invoiceId);
                showDetail(invoiceId);
            }
        }.execute();
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

                // Xóa trắng khung chi tiết.
                // Bản cũ chạy ba câu QueryResult.run("SELECT '' WHERE 1=0") chỉ
                // để vẽ ba bảng rỗng -- ba lượt đi database không cần thiết
                // (plan/14 mục 5.9f).
                taHeader.setText("");
                Ui.clear(tbLines);
                Ui.clear(tbPayments);
                Ui.clear(tbReturns);

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

            // Lấy thông tin gốc của dòng hàng QUA THỦ TỤC, không phải SQL thô:
            // câu SELECT cũ ở đây là câu SQL thô cuối cùng còn lại trong tầng
            // UI/DAO của app billing (khối 4 đã xoá câu còn lại). SQL thô trong
            // UI nằm ngoài tầm với của mọi thay đổi trong sql/ -- đổi tên cột là
            // nó sai thầm lặng. Thứ tự 4 cột là hợp đồng: r[0]..r[3].
            QueryResult qr = dao.invoiceLineEditFields(invoiceId, lineNo);
            if (qr.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Khong tim thay dong hang so " + lineNo, "Loi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // BigDecimal, KHONG phai String.valueOf(...): cot DECIMAL cua MySQL
            // ve Java von la BigDecimal roi, va EditLineDialog can so co kieu de
            // dien san bang Ui.qtyInput / Ui.moneyInput (plan/14 muc 5.9e).
            Object[] r = qr.rows.get(0);
            String     barcode   = String.valueOf(r[0]);
            BigDecimal quantity  = Ui.dec(r[1]);
            BigDecimal unitPrice = Ui.dec(r[2]);
            BigDecimal discount  = Ui.dec(r[3]);

            Window window = SwingUtilities.getWindowAncestor(this);
            EditLineDialog dialog = new EditLineDialog(window, dao, invoiceId, lineNo, barcode, quantity, unitPrice, discount);
            dialog.setVisible(true);

            if (dialog.isSaved()) {
                // PHẢI THẤY KẾT QUẢ: reload() gọi model.setRowCount(0) nên nó xoá
                // luôn selection, và onSelect(null) sau đó không còn hàng nào để
                // đọc -> bảng dòng hàng giữ nguyên nội dung cũ (plan/14 lỗi #30).
                // Khối 3 đã dựng sẵn hai hàm này và ghi rõ là để lại cho khối 5.
                reload();
                selectInvoiceRow(invoiceId);
                showDetail(invoiceId);
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
                // reload() xoá selection -> chọn lại rồi vẽ lại chi tiết, nên
                // bảng dòng hàng VÀ tổng tiền ở khung trên cập nhật ngay (lỗi #30).
                reload();
                selectInvoiceRow(invoiceId);
                showDetail(invoiceId);
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

    // =================================================================
    //  TRẢ HÀNG (RETURN)
    // =================================================================

    /**
     * Một dòng hóa đơn kèm số lượng còn trả được -- đọc từ sp_get_returnable_lines.
     * Tên trường tương ứng 1-1 với tên cột của thủ tục (plan/12 mục 4.5).
     */
    private static class ReturnableLine {
        final int        lineNumber;
        final String     productName;
        final String     unit;
        final BigDecimal bought;      // SL_mua
        final BigDecimal returned;    // SL_da_tra
        final BigDecimal remaining;   // SL_con_lai
        final BigDecimal unitPrice;   // Don_gia
        final BigDecimal discount;    // Giam_gia
        final BigDecimal lineTotal;   // Thanh_tien = SL_mua * Don_gia - Giam_gia

        ReturnableLine(Object[] row) {
            this.lineNumber  = intOf(row[0]);
            this.productName = String.valueOf(row[1]);
            this.unit        = row[2] == null ? "" : String.valueOf(row[2]);
            this.bought      = Ui.dec(row[3]);
            this.returned    = Ui.dec(row[4]);
            this.remaining   = Ui.dec(row[5]);
            this.unitPrice   = Ui.dec(row[6]);
            this.discount    = Ui.dec(row[7]);
            this.lineTotal   = Ui.dec(row[8]);
        }
    }

    /** Bảng bên trái hộp thoại: các dòng của hóa đơn kèm số còn trả được. */
    private static class ReturnableLinesModel extends AbstractTableModel {
        private static final String[] COLS = {"Dong", "San_pham", "DVT", "SL_mua",
                "SL_da_tra", "SL_con_lai", "Don_gia", "Giam_gia", "Thanh_tien"};

        private final List<ReturnableLine> data;

        ReturnableLinesModel(List<ReturnableLine> data) {
            this.data = data;
        }

        @Override public int getRowCount()               { return data.size(); }
        @Override public int getColumnCount()            { return COLS.length; }
        @Override public String getColumnName(int c)     { return COLS[c]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }

        ReturnableLine get(int row) { return data.get(row); }

        /**
         * Model giữ SỐ, không giữ chuỗi đã định dạng -- renderer lo việc hiển thị.
         * Nhờ vậy cột Thanh_tien là thành tiền thật của cả dòng (SL * Don_gia -
         * Giam_gia), không phải Don_gia - Giam_gia như trước khối 3 (lỗi #6).
         */
        @Override
        public Object getValueAt(int row, int col) {
            ReturnableLine l = data.get(row);
            switch (col) {
                case 0:  return l.lineNumber;
                case 1:  return l.productName;
                case 2:  return l.unit;
                case 3:  return l.bought;
                case 4:  return l.returned;
                case 5:  return l.remaining;
                case 6:  return l.unitPrice;
                case 7:  return l.discount;
                case 8:  return l.lineTotal;
                default: return "";
            }
        }
    }

    /** Một dòng trong bảng "se tra lai". */
    private static class ReturnRow {
        final int        lineNumber;
        final String     productName;
        final String     unit;
        final BigDecimal quantity;
        final BigDecimal refund;   // do fn_return_refund trả về, Java không tính

        ReturnRow(int lineNumber, String productName, String unit,
                  BigDecimal quantity, BigDecimal refund) {
            this.lineNumber  = lineNumber;
            this.productName = productName;
            this.unit        = unit;
            this.quantity    = quantity;
            this.refund      = refund;
        }
    }

    /** Bảng bên phải hộp thoại: các mục sẽ được ghi vào bảng `Return`. */
    private static class ReturnRowsModel extends AbstractTableModel {
        private static final String[] COLS = {"Dong", "San_pham", "SL_tra", "DVT", "Tien_hoan"};

        private final List<ReturnRow> data = new ArrayList<>();

        @Override public int getRowCount()               { return data.size(); }
        @Override public int getColumnCount()            { return COLS.length; }
        @Override public String getColumnName(int c)     { return COLS[c]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }

        @Override
        public Object getValueAt(int row, int col) {
            ReturnRow r = data.get(row);
            switch (col) {
                case 0:  return r.lineNumber;    // SỐ DÒNG THẬT, không ai ghi đè
                case 1:  return r.productName;
                case 2:  return r.quantity;
                case 3:  return r.unit;
                case 4:  return r.refund;
                default: return "";
            }
        }

        void add(ReturnRow r) {
            data.add(r);
            fireTableRowsInserted(data.size() - 1, data.size() - 1);
        }

        void removeAt(int row) {
            if (row >= 0 && row < data.size()) {
                data.remove(row);
                fireTableRowsDeleted(row, row);
            }
        }

        /** Vị trí của mục đang trả cho dòng này, hoặc -1. Khóa là SỐ DÒNG, không phải tên sản phẩm. */
        int indexOfLine(int lineNumber) {
            for (int i = 0; i < data.size(); i++) {
                if (data.get(i).lineNumber == lineNumber) return i;
            }
            return -1;
        }

        /** Tổng số lượng đang chọn cho một dòng (để trừ tiếp vào SL_con_lai). */
        BigDecimal selectedFor(int lineNumber) {
            BigDecimal sum = BigDecimal.ZERO;
            for (ReturnRow r : data) {
                if (r.lineNumber == lineNumber) sum = sum.add(r.quantity);
            }
            return sum;
        }

        /**
         * Cộng các số tiền ĐÃ LÀM TRÒN của từng dòng, không làm tròn tổng
         * (plan/12 mục 3.1) -- nhờ vậy tổng ở đây trùng khít với tổng cột
         * Tien_hoan ở tab 3 sau khi lưu.
         */
        BigDecimal totalRefund() {
            BigDecimal sum = BigDecimal.ZERO;
            for (ReturnRow r : data) sum = sum.add(r.refund);
            return sum;
        }

        List<ReturnItem> toReturnItems() {
            List<ReturnItem> items = new ArrayList<>(data.size());
            for (ReturnRow r : data) {
                items.add(new ReturnItem(r.productName, r.lineNumber, r.quantity));
            }
            return items;
        }
    }

    /** Renderer tiền tệ: model giữ số, chỗ này lo định dạng. */
    private static class CurrencyRenderer extends DefaultTableCellRenderer {
        CurrencyRenderer() { setHorizontalAlignment(SwingConstants.RIGHT); }
        @Override protected void setValue(Object value) {
            setText(value == null ? "" : Ui.money(value));
        }
    }

    /** Renderer số lượng DECIMAL(10,3): 1.500 -> "1,5". */
    private static class QuantityRenderer extends DefaultTableCellRenderer {
        QuantityRenderer() { setHorizontalAlignment(SwingConstants.RIGHT); }
        @Override protected void setValue(Object value) {
            setText(value == null ? "" : Ui.qty(value));
        }
    }

    private void applyRenderers(JTable table, int[] quantityCols, int[] currencyCols) {
        for (int c : quantityCols) {
            table.getColumnModel().getColumn(c).setCellRenderer(new QuantityRenderer());
        }
        for (int c : currencyCols) {
            table.getColumnModel().getColumn(c).setCellRenderer(new CurrencyRenderer());
        }
    }

    /**
     * Đặt bề rộng cột bằng SỐ rồi giới hạn bề rộng khung nhìn.
     *
     * AUTO_RESIZE_ALL_COLUMNS: các bề rộng ở trên là TỶ LỆ, không phải kích thước
     * cứng -- bảng luôn vừa khít khung nhìn nên không bao giờ tràn box, và nới
     * rộng hộp thoại thì mọi cột rộng ra theo. Bảng bên trái có 9 cột nên đây là
     * chỗ dễ sai nhất của khối này (plan/12 rủi ro #7).
     */
    private static void sizeColumns(JTable table, int[] widths, int maxViewportWidth, int visibleRows) {
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setRowHeight(22);
        int total = 0;
        for (int c = 0; c < widths.length && c < table.getColumnCount(); c++) {
            table.getColumnModel().getColumn(c).setPreferredWidth(widths[c]);
            total += widths[c];
        }
        table.setPreferredScrollableViewportSize(
                new Dimension(Math.min(total, maxViewportWidth), visibleRows * 22));
    }

    /**
     * Hộp thoại trả hàng.
     *
     * CHỖ NÀO CHẠY NGOÀI EDT (ghi vào javadoc để khối sau không "dọn dẹp" theo
     * hai hướng ngược nhau -- plan/12 mục 5.6):
     *   - Mở hộp thoại (invoiceHeader + returnableLines): EDT. Hai câu SELECT
     *     nhỏ, và cả app đang đọc DB trên EDT; đổi riêng chỗ này chỉ tạo ra sự
     *     không nhất quán.
     *   - "Them >>" (một lần gọi fn_return_refund): EDT. Một hàm vô hướng.
     *   - "Xac nhan" (processReturn, transaction N lệnh INSERT): SwingWorker.
     *     Đây là thao tác GHI, lâu nhất, và nếu MySQL treo thì hộp thoại MODAL
     *     sẽ đứng hình không vẽ lại được (đúng vấn đề plan/11 mục 4.2).
     *   - Làm mới tab 3 sau khi lưu: EDT, trong done().
     */
    private void onReturnLine() {
        final String invoiceId = getSelectedInvoiceId();
        if (invoiceId == null) {
            JOptionPane.showMessageDialog(this, "Vui long chon mot hoa don de tra hang!",
                    "Chua chon hoa don", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // --- 1. Người duyệt phải là một nhân viên thật ----------------
        Account user = UserSession.getCurrentUser();
        if (user == null || user.getCashierId() == null || user.getCashierId().isBlank()) {
            JOptionPane.showMessageDialog(this,
                "Tai khoan '" + (user == null ? "?" : user.getUsername()) + "' chua duoc gan voi nhan vien\n"
              + "nao trong bang Cashier, nen khong the dung lam nguoi duyet tra hang.\n\n"
              + "Cach sua: chay lai .\\scripts\\ps1\\db-setup.ps1 (xoa sach du lieu),\n"
              + "hoac chay doan ALTER/UPDATE o plan/12 muc 4.1 de giu du lieu dang co.",
                "Chua xac dinh duoc nguoi duyet", JOptionPane.WARNING_MESSAGE);
            return;
        }
        final String approverId = user.getCashierId();
        final String approverLabel = approverLabel(approverId);

        try {
            // --- 2. Phần đầu hóa đơn: ngày + trạng thái --------------
            // sp_get_invoice_header: Ma_HD(0) Ngay(1) Gio(2) Trang_thai(3) ...
            QueryResult headerQr = dao.invoiceHeader(invoiceId);
            String invoiceDate = "";
            String status = "";
            if (!headerQr.isEmpty()) {
                Object[] row = headerQr.rows.get(0);
                invoiceDate = String.valueOf(row[1]);
                status = row[3] == null ? "" : String.valueOf(row[3]);
            }

            // Khối 4 sở hữu cột Status và đã định nghĩa: OPEN = chưa thu đủ tiền
            // -> hoàn tiền cho một hóa đơn chưa thu tiền là vô nghĩa. SQL cũng
            // chặn (sp_add_return), đây chỉ là lớp thông báo sớm.
            InvoiceStatus st = InvoiceStatus.of(status);
            if (st != InvoiceStatus.PAID) {
                JOptionPane.showMessageDialog(this,
                        "Hoa don " + invoiceId + " dang o trang thai "
                      + (st == null ? status : st.display())
                      + ".\nChi hoa don da thanh toan (PAID) moi tra hang duoc.",
                        "Khong tra hang duoc", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // --- 3. Các dòng còn trả được ----------------------------
            QueryResult linesQr = dao.returnableLines(invoiceId);
            if (linesQr.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Hoa don khong co dong hang nao de tra!",
                        "Thong bao", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            List<ReturnableLine> availableLines = new ArrayList<>();
            for (Object[] row : linesQr.rows) {
                availableLines.add(new ReturnableLine(row));
            }

            // --- 4. Dựng hộp thoại -----------------------------------
            final JDialog returnDialog = new JDialog(SwingUtilities.getWindowAncestor(this),
                    "Tra hang -- hoa don " + invoiceId, Dialog.ModalityType.APPLICATION_MODAL);
            returnDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            returnDialog.setLayout(new BorderLayout(10, 10));
            ((JPanel) returnDialog.getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JLabel headerLabel = new JLabel("Hoa don: " + invoiceId
                    + "  |  Ngay: " + invoiceDate + "  |  Trang thai: " + status);
            headerLabel.setFont(Ui.BOLD);
            JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            headerPanel.add(headerLabel);
            returnDialog.add(headerPanel, BorderLayout.NORTH);

            // Bảng trái: các dòng của hóa đơn kèm số còn trả được
            final ReturnableLinesModel availableModel = new ReturnableLinesModel(availableLines);
            final JTable availableTable = new JTable(availableModel);
            availableTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            availableTable.getTableHeader().setFont(Ui.BOLD);
            applyRenderers(availableTable, new int[]{3, 4, 5}, new int[]{6, 7, 8});
            sizeColumns(availableTable,
                    new int[]{50, 175, 55, 75, 85, 90, 95, 95, 105}, 640, 8);

            JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
            leftPanel.setBorder(BorderFactory.createTitledBorder("Cac dong cua hoa don"));
            leftPanel.add(new JScrollPane(availableTable), BorderLayout.CENTER);

            // Bảng phải: các mục sẽ được trả
            final ReturnRowsModel rowsModel = new ReturnRowsModel();
            final JTable returnTable = new JTable(rowsModel);
            returnTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            returnTable.getTableHeader().setFont(Ui.BOLD);
            applyRenderers(returnTable, new int[]{2}, new int[]{4});
            sizeColumns(returnTable, new int[]{50, 175, 80, 55, 105}, 430, 8);

            JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
            rightPanel.setBorder(BorderFactory.createTitledBorder("Se tra lai"));
            rightPanel.add(new JScrollPane(returnTable), BorderLayout.CENTER);

            final JButton btnAdd     = new JButton("Them >>");
            final JButton btnRemove  = new JButton("<< Xoa");
            final JButton btnConfirm = new JButton("Xac nhan tra hang");
            final JButton btnCancel  = new JButton("Huy");
            btnConfirm.setFont(Ui.BOLD);

            leftPanel.add(btnAdd, BorderLayout.SOUTH);
            rightPanel.add(btnRemove, BorderLayout.SOUTH);

            JPanel mainPanel = new JPanel(new GridLayout(1, 2, 10, 10));
            mainPanel.add(leftPanel);
            mainPanel.add(rightPanel);
            returnDialog.add(mainPanel, BorderLayout.CENTER);

            // Panel dưới: tổng tiền + người duyệt + nút hành động
            final JLabel totalAmountLabel = new JLabel(Ui.money(BigDecimal.ZERO));
            totalAmountLabel.setFont(Ui.BOLD);
            totalAmountLabel.setForeground(new Color(180, 40, 40));
            JPanel totalPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JLabel totalCaption = new JLabel("Tong tien phai tra lai: ");
            totalCaption.setFont(Ui.BOLD);
            totalPanel.add(totalCaption);
            totalPanel.add(totalAmountLabel);

            // Ai duyệt phải NHÌN THẤY ĐƯỢC trước khi bấm, không chỉ sau khi lưu.
            JPanel approverPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            approverPanel.add(new JLabel("Nguoi duyet: " + approverLabel));

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPanel.add(btnConfirm);
            buttonPanel.add(btnCancel);

            JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
            bottomPanel.add(totalPanel, BorderLayout.WEST);
            bottomPanel.add(approverPanel, BorderLayout.CENTER);
            bottomPanel.add(buttonPanel, BorderLayout.EAST);
            returnDialog.add(bottomPanel, BorderLayout.SOUTH);

            final Runnable refreshTotal =
                    () -> totalAmountLabel.setText(Ui.money(rowsModel.totalRefund()));

            // Khóa các nút trong lúc transaction đang chạy: bấm 5 lần không được
            // xếp hàng 5 transaction (cùng lý do với setBusy của plan/11 mục 4.2).
            final Consumer<Boolean> setBusy = busy -> {
                btnAdd.setEnabled(!busy);
                btnRemove.setEnabled(!busy);
                btnConfirm.setEnabled(!busy);
                returnDialog.setCursor(Cursor.getPredefinedCursor(
                        busy ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
            };

            // --- "Them >>" ------------------------------------------
            btnAdd.addActionListener(e -> {
                int row = availableTable.getSelectedRow();
                if (row < 0) {
                    JOptionPane.showMessageDialog(returnDialog,
                            "Vui long chon mot dong hang o bang ben trai de them!",
                            "Chua chon dong hang", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                ReturnableLine line = availableModel.get(row);

                // Chặn trùng theo SỐ DÒNG, không theo tên sản phẩm: một hóa đơn
                // được phép có cùng một sản phẩm ở hai dòng (plan/12 lỗi #9).
                if (rowsModel.indexOfLine(line.lineNumber) >= 0) {
                    JOptionPane.showMessageDialog(returnDialog,
                            "Dong " + line.lineNumber + " (" + line.productName + ") da co trong danh sach se tra.\n\n"
                          + "Muon doi so luong: chon muc do o bang ben phai, bam '<< Xoa',\n"
                          + "roi 'Them >>' lai voi so luong khac.",
                            "Dong nay da duoc chon", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                BigDecimal remaining = line.remaining.subtract(rowsModel.selectedFor(line.lineNumber));
                if (remaining.signum() <= 0) {
                    JOptionPane.showMessageDialog(returnDialog,
                            "Dong " + line.lineNumber + " (" + line.productName + ") da tra het "
                          + Ui.qty(line.bought) + " " + line.unit + " da mua,\n"
                          + "khong con gi de tra.",
                            "Da tra het", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                // Điền sẵn số còn lại -> trường hợp phổ biến nhất (trả hết phần
                // còn lại) chỉ còn một lần Enter.
                Object answer = JOptionPane.showInputDialog(returnDialog,
                        "San pham: " + line.productName + "\n"
                      + "Da mua: " + Ui.qty(line.bought) + " " + line.unit
                      + "   |   Da tra: " + Ui.qty(line.returned)
                      + "   |   Con lai: " + Ui.qty(remaining) + "\n\n"
                      + "Nhap so luong tra (toi da 3 chu so thap phan, dung ',' hoac '.'):",
                        "Nhap so luong tra cho dong " + line.lineNumber,
                        JOptionPane.QUESTION_MESSAGE, null, null,
                        Ui.qtyInput(remaining));
                if (answer == null) return;           // bấm Cancel / đóng hộp thoại

                BigDecimal qty = Ui.readQuantity(returnDialog, answer.toString(),
                                                 remaining, line.unit);
                if (qty == null) return;

                try {
                    // Tiền hoàn do SQL tính -- cùng một hàm mà tab 3 và báo cáo dùng.
                    BigDecimal refund = dao.returnRefund(invoiceId, line.lineNumber, qty);
                    rowsModel.add(new ReturnRow(line.lineNumber, line.productName,
                            line.unit, qty, refund));
                    refreshTotal.run();
                } catch (Exception ex) {
                    Ui.error(returnDialog, ex);
                }
            });

            // --- "<< Xoa" -------------------------------------------
            btnRemove.addActionListener(e -> {
                int row = returnTable.getSelectedRow();
                if (row < 0) {
                    JOptionPane.showMessageDialog(returnDialog,
                            "Vui long chon mot muc o bang ben phai de xoa!",
                            "Chua chon muc nao", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                rowsModel.removeAt(row);
                refreshTotal.run();
            });

            // --- "Xac nhan tra hang" --------------------------------
            btnConfirm.addActionListener(e -> {
                if (rowsModel.getRowCount() == 0) {
                    JOptionPane.showMessageDialog(returnDialog,
                            "Khong co san pham nao de tra!",
                            "Danh sach trong", JOptionPane.WARNING_MESSAGE);
                    return;      // không gọi DB
                }

                final BigDecimal total = rowsModel.totalRefund();
                final List<ReturnItem> items = rowsModel.toReturnItems();

                int choice = JOptionPane.showConfirmDialog(returnDialog,
                        "Ghi " + items.size() + " luot tra hang cho hoa don " + invoiceId + "?\n\n"
                      + "Tong tien phai tra lai: " + Ui.money(total) + "\n"
                      + "Nguoi duyet: " + approverLabel,
                        "Xac nhan xu ly tra hang",
                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (choice != JOptionPane.YES_OPTION) return;

                setBusy.accept(true);
                new SwingWorker<Void, Void>() {
                    @Override
                    protected Void doInBackground() {
                        dao.processReturn(invoiceId, items, approverId);
                        return null;
                    }

                    @Override
                    protected void done() {
                        setBusy.accept(false);
                        Component parent = returnDialog.isShowing() ? returnDialog : InvoiceViewPanel.this;
                        try {
                            get();
                        } catch (Exception ex) {
                            showSqlFailure(parent, ex, "Khong tra hang duoc");
                            return;
                        }

                        returnDialog.dispose();
                        JOptionPane.showMessageDialog(InvoiceViewPanel.this,
                                "Da ghi " + items.size() + " luot tra hang cho hoa don " + invoiceId + ".\n"
                              + "Tong tien tra lai: " + Ui.money(total),
                                "Thanh cong", JOptionPane.INFORMATION_MESSAGE);

                        // PHẢI THẤY KẾT QUẢ: reload() xóa selection nên chọn lại
                        // đúng hàng rồi vẽ lại chi tiết (plan/12 lỗi #12).
                        reload();
                        selectInvoiceRow(invoiceId);
                        showDetail(invoiceId);
                    }
                }.execute();
            });

            btnCancel.addActionListener(e -> returnDialog.dispose());

            // --- 5. Kích thước: đo BẰNG SỐ, không bằng mắt -----------
            // Bảng bên trái có 9 cột; plan/09 mục 7.1 ghi 6/8 lỗi của tab Git là
            // lỗi kích thước, và "nhìn thấy ổn" đã sai một lần rồi (plan/11 rủi ro #4).
            returnDialog.pack();
            Dimension pref = returnDialog.getPreferredSize();
            Rectangle work = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            int w = Math.min(pref.width,  work.width  - 40);
            int h = Math.min(pref.height, work.height - 40);
            returnDialog.setSize(w, h);
            returnDialog.setMinimumSize(new Dimension(Math.min(720, w), Math.min(420, h)));
            if (w < pref.width || h < pref.height) {
                System.out.println("[InvoiceViewPanel] Hop thoai tra hang bi thu nho: " + w + "x" + h
                        + " < can " + pref.width + "x" + pref.height
                        + " (vung lam viec " + work.width + "x" + work.height
                        + ") -- hai bang deu co thanh cuon nen khong mat noi dung.");
            }
            returnDialog.setLocationRelativeTo(this);
            returnDialog.setVisible(true);

        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    // readQuantity DA DOI CHO: no la hop dong kieu so cua khoi 3 (plan/12 muc
    // 3.2) nhung lai bi khoa trong lop nay bang `private`, nen tab 1 va
    // EditLineDialog khong dung duoc va ca hai dang nhan 1.2345 de MySQL am
    // tham lam tron thanh 1.235 -- dung dieu quy tac 1 cam (plan/14 loi #23).
    // Gio no la Ui.readQuantity, nguyen van, chi khac mot diem: tham so `max`
    // nhan null khi khong co tran (tab 1 khong co tran so luong).

    /**
     * Lỗi do SQL chủ động SIGNAL (SQLSTATE 45000) -> cảnh báo gọn, đọc được.
     * Mọi lỗi khác (mất kết nối, thiếu thủ tục, sai khóa ngoại) -> Ui.error.
     *
     * Nhờ vậy câu "Dong 1: chi con 0.000 co the tra (da mua 2.000, da tra 2.000)"
     * hiện ra như một lời giải thích, chứ không như một tai nạn. Và lỗi thật vẫn
     * giữ nguyên đường cũ với thông điệp gốc của MySQL (plan/12 mục 5.7c).
     *
     * BA NÚT DÙNG CHUNG MỘT CÁCH HIỆN LỖI: trả hàng, hủy hóa đơn, và lưu hộp
     * thoại sửa hóa đơn (EditInvoiceDialog). Cả ba đều gặp SIGNAL '45000' từ
     * các thủ tục của khối 4, nên tiêu đề hộp thoại là tham số.
     */
    static void showSqlFailure(Component parent, Throwable ex, String title) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException && "45000".equals(((SQLException) t).getSQLState())) {
                JOptionPane.showMessageDialog(parent, t.getMessage(),
                        title, JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        Ui.error(parent, ex);
    }

    /** "Tran Van An (E01)" -- tên nhân viên duyệt, tra từ sp_lookup_cashiers. */
    private String approverLabel(String cashierId) {
        try {
            for (IdName c : lookupDao.cashiers()) {
                if (cashierId.equals(c.id)) {
                    return c.name + " (" + cashierId + ")";
                }
            }
        } catch (Exception ignore) {
            // Không tra được tên thì vẫn hiện mã -- không đáng để chặn trả hàng.
        }
        return cashierId;
    }

    /**
     * Hủy một lượt trả hàng đã ghi.
     *
     * Lấy Dong và Lan_tra từ cột 0 và cột 1 của tbReturns -- hai cột này tồn tại
     * được là nhờ cột Lan_tra mới của sp_get_invoice_returns (plan/12 mục 3.5).
     * Không gọi reload() nên selection của tbInvoices còn nguyên.
     */
    private void onDeleteReturn() {
        String invoiceId = getSelectedInvoiceId();
        int row = tbReturns.getSelectedRow();
        if (invoiceId == null || row < 0) {
            JOptionPane.showMessageDialog(this,
                    "Vui long chon mot luot tra trong bang 'Tra hang (Return)' de huy!",
                    "Chua chon luot tra", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int lineNo;
        int returnId;
        try {
            lineNo   = Integer.parseInt(String.valueOf(tbReturns.getValueAt(row, 0)).trim());
            returnId = Integer.parseInt(String.valueOf(tbReturns.getValueAt(row, 1)).trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    "Khong doc duoc so dong / lan tra cua hang dang chon.",
                    "Loi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String product = String.valueOf(tbReturns.getValueAt(row, 2));
        String quantity = String.valueOf(tbReturns.getValueAt(row, 4));
        String refund = String.valueOf(tbReturns.getValueAt(row, 5));

        int choice = JOptionPane.showConfirmDialog(this,
                "Huy luot tra nay khoi Database?\n\n"
              + "  Hoa don : " + invoiceId + "\n"
              + "  Dong    : " + lineNo + " (" + product + ")\n"
              + "  Lan tra : " + returnId + "\n"
              + "  SL tra  : " + quantity + "\n"
              + "  Tien hoan da ghi nhan: " + refund + "\n\n"
              + "Sau khi huy, so luong nay duoc tra lai vao phan con tra duoc cua dong.\n"
              + "Hanh dong khong the hoan tac!",
                "Xac nhan huy luot tra",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;

        try {
            int affected = dao.deleteReturn(invoiceId, lineNo, returnId);
            if (affected == 0) {
                JOptionPane.showMessageDialog(this,
                        "Khong tim thay luot tra lan " + returnId + " cua dong " + lineNo
                      + " (co the ai khac da huy truoc).",
                        "Khong co gi bi huy", JOptionPane.WARNING_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Da huy luot tra lan " + returnId + " cua dong " + lineNo + ".",
                        "Thanh cong", JOptionPane.INFORMATION_MESSAGE);
            }
            showDetail(invoiceId);
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }
}
