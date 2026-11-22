package billing.ui;

import billing.dao.InvoiceDao;
import billing.dao.InvoiceDao.ReturnItem;
import billing.dao.LookupDao;
import billing.db.QueryResult;
import billing.model.IdName;
import billing.model.InvoiceStatus;
import billing.util.CashRules;

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
 *  Bên trái: danh sách hóa đơn + nút "Sửa khách hàng", "Hủy hóa đơn".
 *  ("Hủy" đổi Status sang VOIDED với xác nhận quản lý và giữ nguyên dữ liệu.)
 *  Bên phải:
 *    1. Thông tin chung + tổng tiền
 *    2. Các dòng hàng (Invoice_Line)
 *    3. Thanh toán (Payment + bảng con)
 *    4. Trả hàng (Return) + nút "Trả hàng" (cần quản lý duyệt, hóa đơn vẫn PAID)
 *
 *  TIỀN HOÀN KHI TRẢ HÀNG: lớp này KHÔNG tự tính, dù chỉ một phép nhân.
 *  Công thức chỉ tồn tại ở một chỗ trong cả dự án và chỗ đó là SQL
 *  (fn_return_refund).
 */
public class InvoiceViewPanel extends JPanel {

    private final InvoiceDao dao = new InvoiceDao();
    private final LookupDao lookupDao = new LookupDao();

    private final JTable tbInvoices = Ui.readOnlyTable();
    private final JTable tbLines    = Ui.readOnlyTable();
    private final JTable tbPayments = Ui.readOnlyTable();
    /** Hóa đơn điều chỉnh = Invoice_Line − Return (sp_get_invoice_adjusted). */
    private final JTable tbAdjusted = Ui.readOnlyTable();
    private final javax.swing.border.TitledBorder adjustedBorder =
            BorderFactory.createTitledBorder(adjustedTitleNone());
    /** Tong moi / tien hoan cua hoa don dang xem (null = chua tra hang) -- de ve lai tieu de. */
    private BigDecimal adjNewTotal, adjRefund;
    private final JPanel pnlAdjustedContainer = new JPanel(new BorderLayout(2, 2));
    private final JTextArea taHeader = new JTextArea(8, 40);

    // Các button thao tác hóa đơn (bên trái)
    private final JButton btEditInvoice   = new JButton(I18n.text("Sửa khách hàng", "Edit customer"));
    // Huỷ KHÁC xoá: nút này đổi Status sang VOIDED và để nguyên dữ liệu
    private final JButton btVoidInvoice   = new JButton(I18n.text("Hủy hóa đơn", "Void invoice"));

    // Nút thao tác dòng hàng trong hóa đơn (bên phải)
    private final JButton btReturnLine = new JButton(I18n.text("Trả hàng", "Process return"));

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
        I18n.markKeyValueText(taHeader);
        I18n.onLanguageChange(this, this::updateAdjustedTitle);

        // --- Cột trái: Danh sách hóa đơn và nút thao tác ---
        JButton btReload = new JButton("Tai lai danh sach");
        btReload.addActionListener(e -> reload());

        // Mặc định 30 ngày: đủ dày để thấy cả ba trạng thái và vài lượt trả
        // hàng, mà không nạp cả 548 dòng ngay khi mở tab.
        cbDays.setSelectedIndex(1);
        cbDays.addActionListener(e -> reload());
        cbDays.setToolTipText(I18n.text("Lọc theo ngày bán. 'Tất cả' hiện mọi hóa đơn, kể cả năm trước.",
                                        "Filter by sale date. 'All' shows every invoice, including last year's."));

        JPanel pnlFilter = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        pnlFilter.add(new JLabel("Khoang ngay:"));
        pnlFilter.add(cbDays);
        pnlFilter.add(btReload);

        lbInvoiceCount.setFont(lbInvoiceCount.getFont().deriveFont(Font.PLAIN, 11f));
        lbInvoiceCount.setForeground(new Color(90, 90, 90));
        lbInvoiceCount.setBorder(BorderFactory.createEmptyBorder(2, 4, 0, 4));

        JPanel pnlInvoiceActions = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
        btEditInvoice.setFont(Ui.BOLD);
        btEditInvoice.setToolTipText(I18n.text(
                "Cập nhật khách hàng của hóa đơn (các thông tin khác bất biến)",
                "Update invoice customer (other fields are immutable)"));
        btVoidInvoice.setFont(Ui.BOLD);
        // Cam (không phải đỏ) để mắt phân biệt được nghĩa Hủy hóa đơn.
        btVoidInvoice.setForeground(new Color(190, 110, 20));
        btVoidInvoice.setToolTipText(I18n.text(
                "Hủy hóa đơn (cần chọn Quản lý duyệt): dữ liệu vẫn còn nhưng không tính vào doanh thu",
                "Void invoice (requires Manager approval): data is kept but excluded from revenue"));

        btEditInvoice.addActionListener(e -> onEditInvoice());
        btVoidInvoice.addActionListener(e -> onVoidInvoice());

        pnlInvoiceActions.add(btEditInvoice);
        pnlInvoiceActions.add(btVoidInvoice);

        JPanel pnlInvoiceSouth = new JPanel(new BorderLayout());
        pnlInvoiceSouth.add(lbInvoiceCount, BorderLayout.NORTH);
        pnlInvoiceSouth.add(pnlInvoiceActions, BorderLayout.CENTER);

        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.add(pnlFilter, BorderLayout.NORTH);
        left.add(new JScrollPane(tbInvoices), BorderLayout.CENTER);
        left.add(pnlInvoiceSouth, BorderLayout.SOUTH);
        left.setBorder(BorderFactory.createTitledBorder("Danh sach hoa don (bam de xem chi tiet)"));

        // --- Cột phải: Chi tiết hóa đơn ---
        // Panel riêng cho dòng hàng kèm nút Trả hàng
        JPanel pnlLineActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        btReturnLine.setFont(Ui.BOLD);
        btReturnLine.setToolTipText(I18n.text(
                "Trả hàng cho các dòng của hóa đơn đang chọn (cần chọn Quản lý duyệt)",
                "Return items from the selected invoice (requires Manager approval)"));

        btReturnLine.addActionListener(e -> onReturnLine());

        pnlLineActions.add(btReturnLine);

        JPanel pnlLinesContainer = new JPanel(new BorderLayout(2, 2));
        pnlLinesContainer.add(new JScrollPane(tbLines), BorderLayout.CENTER);
        pnlLinesContainer.add(pnlLineActions, BorderLayout.SOUTH);

        // Hóa đơn điều chỉnh: đặt SONG SONG với hóa đơn gốc -- hai bảng cùng
        // thứ tự dòng nên đọc ngang được: trái là đã mua, phải là sau khi trả.
        pnlAdjustedContainer.add(new JScrollPane(tbAdjusted), BorderLayout.CENTER);
        pnlAdjustedContainer.setBorder(adjustedBorder);

        JSplitPane linesSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                Ui.titled(I18n.text("Hóa đơn gốc", "Original invoice"), pnlLinesContainer),
                pnlAdjustedContainer);
        linesSplit.setResizeWeight(0.5);
        linesSplit.setBorder(null);

        JPanel right = new JPanel(new GridLayout(3, 1, 6, 6));
        right.add(Ui.titled(I18n.text("Thông tin chung + tổng tiền", "Summary + totals"), new JScrollPane(taHeader)));
        right.add(linesSplit);
        right.add(Ui.titled(I18n.text("Thanh toán", "Payments"), new JScrollPane(tbPayments)));

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

            // tbAdjusted (sp_get_invoice_adjusted):
            //   0 Ma_HD | 1 Dong | 2 San_pham | 3 DVT | 4 SL_mua | 5 SL_tra
            //   6 SL_con_lai | 7 Don_gia | 8 Thanh_tien_goc | 9 Tien_hoan
            //   10 Thanh_tien_moi | 11 Ngay_tra
            // Chỉ hiện khi hóa đơn ĐÃ trả hàng (header cột 12 Tien_hoan > 0):
            // hóa đơn chưa trả thì "hóa đơn điều chỉnh" y hệt hóa đơn gốc --
            // hiện ra chỉ gây nhầm là đã có điều chỉnh.
            BigDecimal refund = (!h.isEmpty() && h.columns.size() > 13)
                    ? Ui.dec(h.rows.get(0)[12]) : BigDecimal.ZERO;
            if (refund.signum() > 0) {
                QueryResult adjusted = dao.invoiceAdjusted(invoiceId);
                fillFormatted(tbAdjusted, adjusted, new int[]{7, 8, 9, 10}, null,
                              new int[]{4, 5, 6}, null);
                adjNewTotal = Ui.dec(h.rows.get(0)[13]);
                adjRefund = refund;
                updateAdjustedTitle();
            } else {
                clearAdjusted();
            }
            pnlAdjustedContainer.repaint();

        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    /** Bảng hóa đơn điều chỉnh về trạng thái "chưa trả hàng". */
    private void clearAdjusted() {
        DefaultTableModel m = (DefaultTableModel) tbAdjusted.getModel();
        m.setRowCount(0);
        m.setColumnCount(0);
        adjNewTotal = null;
        adjRefund = null;
        updateAdjustedTitle();
    }

    private static String adjustedTitleNone() {
        return I18n.msg("Hóa đơn điều chỉnh -- (hóa đơn này chưa trả hàng)",
                        "Adjusted invoice -- (no returns on this invoice)");
    }

    /** Tieu de khung hoa don dieu chinh theo ngon ngu hien tai. */
    private void updateAdjustedTitle() {
        adjustedBorder.setTitle(adjNewTotal == null ? adjustedTitleNone()
                : I18n.msg("Hóa đơn điều chỉnh (sau trả hàng) -- tổng mới ",
                           "Adjusted invoice (after returns) -- new total ")
                  + Ui.money(adjNewTotal) + I18n.msg(", đã hoàn ", ", refunded ") + Ui.money(adjRefund));
        pnlAdjustedContainer.repaint();
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
     * Hủy một hóa đơn (Status -> VOIDED) với xác nhận của Quản lý (BR3/BR15).
     *
     * Hóa đơn đã phát sinh trả hàng không được phép hủy (giữ PAID theo BR16 / R9).
     * Chạy NGOÀI EDT vì sp_void_invoice dùng SELECT ... FOR UPDATE.
     */
    private void onVoidInvoice() {
        final String invoiceId = getSelectedInvoiceId();
        if (invoiceId == null) {
            JOptionPane.showMessageDialog(this, "Vui long chon mot hoa don de huy!",
                    "Chua chon hoa don", JOptionPane.WARNING_MESSAGE);
            return;
        }

        final List<IdName> managers;
        try {
            QueryResult headerQr = dao.invoiceHeader(invoiceId);
            if (headerQr.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        I18n.msg("Không tìm thấy hóa đơn " + invoiceId, "Invoice not found: " + invoiceId),
                        I18n.msg("Lỗi", "Error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
            Object[] h = headerQr.rows.get(0);
            String status = h[3] == null ? "" : String.valueOf(h[3]);
            if (InvoiceStatus.of(status) == InvoiceStatus.VOIDED) {
                JOptionPane.showMessageDialog(this,
                        I18n.msg("Hóa đơn " + invoiceId + " đã bị hủy trước đó.",
                                 "Invoice " + invoiceId + " is already voided."),
                        I18n.msg("Không hủy được hóa đơn", "Cannot Void Invoice"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            BigDecimal refund = (headerQr.columns.size() > 12) ? Ui.dec(h[12]) : BigDecimal.ZERO;
            if (refund.signum() > 0) {
                JOptionPane.showMessageDialog(this,
                        I18n.msg("Hóa đơn " + invoiceId + " đã phát sinh trả hàng nên không thể hủy\n"
                               + "(phải giữ trạng thái Đã thanh toán để bảo toàn doanh thu thuần và lịch sử trả hàng).",
                                 "Invoice " + invoiceId + " already has returns and cannot be voided\n"
                               + "(it must stay Paid to preserve net revenue and return history)."),
                        I18n.msg("Không hủy được hóa đơn", "Cannot Void Invoice"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            managers = lookupDao.managers();
        } catch (Exception ex) {
            Ui.error(this, ex);
            return;
        }

        if (managers.isEmpty()) {
            Ui.warn(this,
                    I18n.msg("Không có nhân viên Quản lý (MANAGER) trong bảng Cashier để duyệt hủy.",
                             "No MANAGER found in the Cashier table to approve voiding."),
                    I18n.msg("Thiếu người duyệt", "Missing Approver"));
            return;
        }

        JComboBox<IdName> cbManager = new JComboBox<>(managers.toArray(new IdName[0]));
        JPanel confirmPanel = new JPanel(new BorderLayout(8, 8));
        JLabel lbMsg = new JLabel(toHtmlLines(I18n.msg(
                "Hủy hóa đơn " + invoiceId + "?\n\n"
              + "Hủy KHÁC Xóa:\n"
              + "  - Hóa đơn vẫn còn nguyên: hàng, thanh toán\n"
              + "  - Hóa đơn không còn được tính vào doanh thu\n"
              + "  - Không trả hàng được nữa và KHÔNG mở lại được",
                "Void invoice " + invoiceId + "?\n\n"
              + "Void is NOT Delete:\n"
              + "  - The invoice is kept: items, payments\n"
              + "  - It is no longer counted in revenue\n"
              + "  - No returns are allowed and it CANNOT be reopened")));
        JPanel mgrRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        mgrRow.add(new JLabel(I18n.msg("Quản lý duyệt hủy:", "Approving manager:")));
        mgrRow.add(cbManager);
        confirmPanel.add(lbMsg, BorderLayout.CENTER);
        confirmPanel.add(mgrRow, BorderLayout.SOUTH);

        int choice = JOptionPane.showConfirmDialog(this, confirmPanel,
                I18n.msg("Xác nhận hủy hóa đơn", "Confirm Void Invoice"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (choice != JOptionPane.YES_OPTION) return;
        final IdName selectedManager = (IdName) cbManager.getSelectedItem();
        if (selectedManager == null) return;

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                dao.voidInvoice(invoiceId, selectedManager.id);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception ex) {
                    showSqlFailure(InvoiceViewPanel.this, ex, I18n.msg("Không hủy được hóa đơn", "Cannot Void Invoice"));
                    return;
                }

                JOptionPane.showMessageDialog(InvoiceViewPanel.this,
                        I18n.msg("Đã hủy hóa đơn " + invoiceId + " (duyệt bởi " + selectedManager + ").\n"
                               + "Hóa đơn vẫn còn, chỉ không tính vào doanh thu nữa.",
                                 "Invoice " + invoiceId + " has been voided (approved by " + selectedManager + ").\n"
                               + "It is kept, but no longer counted in revenue."),
                        I18n.msg("Đã hủy hóa đơn", "Invoice Voided"), JOptionPane.INFORMATION_MESSAGE);

                // PHẢI THẤY KẾT QUẢ: reload() xóa selection nên chọn lại đúng
                // hàng rồi vẽ lại chi tiết (plan/12 lỗi #12).
                reload();
                selectInvoiceRow(invoiceId);
                showDetail(invoiceId);
            }
        }.execute();
    }

    private static String toHtmlLines(String plain) {
        return "<html>" + plain.replace("&", "&amp;")
                               .replace("<", "&lt;")
                               .replace(">", "&gt;")
                               .replace("\n", "<br>") + "</html>";
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

        try {
            // --- 1. Phần đầu hóa đơn: ngày + trạng thái --------------
            // sp_get_invoice_header: Ma_HD(0) Ngay(1) Gio(2) Trang_thai(3) ...
            QueryResult headerQr = dao.invoiceHeader(invoiceId);
            String invoiceDate = "";
            String status = "";
            if (!headerQr.isEmpty()) {
                Object[] row = headerQr.rows.get(0);
                invoiceDate = String.valueOf(row[1]);
                status = row[3] == null ? "" : String.valueOf(row[3]);
            }

            // Chỉ hóa đơn PAID mới trả hàng được (BR16: hóa đơn vẫn giữ PAID sau khi trả).
            InvoiceStatus st = InvoiceStatus.of(status);
            if (st != InvoiceStatus.PAID) {
                JOptionPane.showMessageDialog(this,
                        I18n.msg("Hóa đơn " + invoiceId + " " + statusText(st, status)
                               + ".\nChỉ hóa đơn đã thanh toán mới trả hàng được.",
                                 "Invoice " + invoiceId + " " + statusText(st, status)
                               + ".\nOnly paid invoices can be returned."),
                        I18n.msg("Không trả hàng được", "Cannot Return"), JOptionPane.WARNING_MESSAGE);
                return;
            }

            // --- 2. Các dòng còn trả được & danh sách quản lý duyệt ---
            QueryResult linesQr = dao.returnableLines(invoiceId);
            if (linesQr.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Hoa don khong co dong hang nao de tra!",
                        "Thong bao", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            List<IdName> managers = lookupDao.managers();
            if (managers.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        I18n.msg("Không tìm thấy quản lý (Role = MANAGER) để duyệt trả hàng.",
                                 "No manager (Role = MANAGER) found to approve returns."),
                        I18n.msg("Thiếu người duyệt", "Missing Manager"), JOptionPane.WARNING_MESSAGE);
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

            JLabel headerLabel = new JLabel(I18n.msg("Hóa đơn: ", "Invoice: ") + invoiceId
                    + I18n.msg("  |  Ngày: ", "  |  Date: ") + invoiceDate
                    + I18n.msg("  |  Trạng thái: ", "  |  Status: ") + statusText(st, status));
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
            rightPanel.setBorder(BorderFactory.createTitledBorder(I18n.msg("Sẽ trả lại", "To be returned")));
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

            // Quản lý duyệt phải được chọn và nhìn thấy trước khi xác nhận (BR15).
            final JComboBox<IdName> cbApprover = new JComboBox<>(managers.toArray(new IdName[0]));
            JPanel approverPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            approverPanel.add(new JLabel(I18n.msg("Quản lý duyệt: ", "Approved by (Manager): ")));
            approverPanel.add(cbApprover);

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
                cbApprover.setEnabled(!busy);
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
                            I18n.msg("Dòng " + line.lineNumber + " (" + line.productName + ") đã có trong danh sách sẽ trả.\n\n"
                                   + "Muốn đổi số lượng: chọn mục đó ở bảng bên phải, bấm '<< Xóa',\n"
                                   + "rồi 'Thêm >>' lại với số lượng khác.",
                                     "Line " + line.lineNumber + " (" + line.productName + ") is already in the return list.\n\n"
                                   + "To change the quantity, select it on the right, click '<< Remove',\n"
                                   + "then 'Add >>' again with the new quantity."),
                            "Dong nay da duoc chon", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                BigDecimal remaining = line.remaining.subtract(rowsModel.selectedFor(line.lineNumber));
                if (remaining.signum() <= 0) {
                    JOptionPane.showMessageDialog(returnDialog,
                            I18n.msg("Dòng " + line.lineNumber + " (" + line.productName + ") đã trả hết "
                                   + Ui.qty(line.bought) + " " + line.unit + " đã mua,\n"
                                   + "không còn gì để trả.",
                                     "Line " + line.lineNumber + " (" + line.productName + ") has already returned all "
                                   + Ui.qty(line.bought) + " " + line.unit + ",\n"
                                   + "there is nothing left to return."),
                            "Da tra het", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                boolean wholeOnly = !CashRules.allowsDecimal(line.unit);
                String qtyPrompt = wholeOnly
                        ? I18n.msg("Nhập số lượng trả (số nguyên dương, đơn vị " + line.unit + "):",
                                   "Enter the return quantity (positive whole number, unit " + line.unit + "):")
                        : I18n.msg("Nhập số lượng trả (tối đa 3 chữ số thập phân, dùng ',' hoặc '.'):",
                                   "Enter the return quantity (up to 3 decimal places, use ',' or '.'):");

                // Điền sẵn số còn lại -> trường hợp phổ biến nhất (trả hết phần
                // còn lại) chỉ còn một lần Enter.
                Object answer = JOptionPane.showInputDialog(returnDialog,
                        I18n.msg("Sản phẩm: ", "Product: ") + line.productName + "\n"
                      + I18n.msg("Đã mua: ", "Bought: ") + Ui.qty(line.bought) + " " + line.unit
                      + I18n.msg("   |   Đã trả: ", "   |   Returned: ") + Ui.qty(line.returned)
                      + I18n.msg("   |   Còn lại: ", "   |   Remaining: ") + Ui.qty(remaining) + "\n\n"
                      + qtyPrompt,
                        I18n.msg("Nhập số lượng trả cho dòng ", "Return quantity for line ") + line.lineNumber,
                        JOptionPane.QUESTION_MESSAGE, null, null,
                        Ui.qtyInput(remaining));
                if (answer == null) return;           // bấm Cancel / đóng hộp thoại

                BigDecimal qty = Ui.readQuantity(returnDialog, answer.toString(),
                                                 remaining, line.unit);
                if (qty == null) return;
                String unitProblem = CashRules.quantityProblem(qty, line.unit);
                if (unitProblem != null) {
                    JOptionPane.showMessageDialog(returnDialog, unitProblem,
                            I18n.msg("Sai số lượng", "Invalid Quantity"), JOptionPane.WARNING_MESSAGE);
                    return;
                }

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
                IdName approver = (IdName) cbApprover.getSelectedItem();
                if (approver == null) return;

                final BigDecimal total = rowsModel.totalRefund();
                final List<ReturnItem> items = rowsModel.toReturnItems();

                int choice = JOptionPane.showConfirmDialog(returnDialog,
                        I18n.msg(
                        "Trả " + items.size() + " dòng hàng của hóa đơn " + invoiceId + "?\n\n"
                      + "Tổng tiền phải trả lại: " + Ui.money(total) + "\n"
                      + "Quản lý duyệt: " + approver + "\n\n"
                      + "Hóa đơn vẫn giữ trạng thái Đã thanh toán và có thể trả thêm nếu còn số lượng,\n"
                      + "nhưng mỗi lượt trả hàng đã ghi nhận KHÔNG thể hủy bỏ.\n"
                      + "Doanh thu tính theo doanh thu ròng sau hoàn tiền.",
                        "Return " + items.size() + " line(s) of invoice " + invoiceId + "?\n\n"
                      + "Total refund: " + Ui.money(total) + "\n"
                      + "Approved by: " + approver + "\n\n"
                      + "The invoice remains Paid and can accept more returns while quantity remains,\n"
                      + "but recorded returns CANNOT be cancelled.\n"
                      + "Revenue counts net sales after refunds."),
                        I18n.msg("Xác nhận trả hàng", "Confirm Return"),
                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (choice != JOptionPane.YES_OPTION) return;

                setBusy.accept(true);
                new SwingWorker<Void, Void>() {
                    @Override
                    protected Void doInBackground() {
                        dao.processReturn(invoiceId, items, approver.id);
                        return null;
                    }

                    @Override
                    protected void done() {
                        setBusy.accept(false);
                        Component parent = returnDialog.isShowing() ? returnDialog : InvoiceViewPanel.this;
                        try {
                            get();
                        } catch (Exception ex) {
                            showSqlFailure(parent, ex, I18n.msg("Không trả hàng được", "Cannot Return"));
                            return;
                        }

                        returnDialog.dispose();
                        JOptionPane.showMessageDialog(InvoiceViewPanel.this,
                                I18n.msg("Đã ghi " + items.size() + " lượt trả hàng cho hóa đơn " + invoiceId + ".\n"
                                       + "Tổng tiền trả lại: " + Ui.money(total),
                                         "Recorded " + items.size() + " return(s) for invoice " + invoiceId + ".\n"
                                       + "Total refund: " + Ui.money(total)),
                                I18n.msg("Thành công", "Success"), JOptionPane.INFORMATION_MESSAGE);

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
                System.out.println("[InvoiceViewPanel] Return dialog shrunk: " + w + "x" + h
                        + " < needed " + pref.width + "x" + pref.height
                        + " (work area " + work.width + "x" + work.height
                        + ") -- both tables scroll, so nothing is lost.");
            }
            returnDialog.setLocationRelativeTo(this);
            returnDialog.setVisible(true);

        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    // Ui.readQuantity nhận null cho `max` khi không có trần số lượng (tab 1).

    /**
     * Lỗi do SQL chủ động SIGNAL (SQLSTATE 45000) -> cảnh báo gọn, đọc được.
     * Mọi lỗi khác (mất kết nối, thiếu thủ tục, sai khóa ngoại) -> Ui.error.
     *
     * BA NÚT DÙNG CHUNG MỘT CÁCH HIỆN LỖI: trả hàng, hủy hóa đơn, và lưu hộp
     * thoại sửa hóa đơn (EditInvoiceDialog). Cả ba đều gặp SIGNAL '45000' từ
     * các thủ tục, nên tiêu đề hộp thoại là tham số.
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

    /** "đã thanh toán" / "đã bị hủy" -- câu cho nhân viên, không có mã PAID/VOIDED. */
    private static String statusText(InvoiceStatus st, String raw) {
        if (st == InvoiceStatus.PAID) return I18n.msg("đã thanh toán", "is paid");
        if (st == InvoiceStatus.VOIDED) return I18n.msg("đã bị hủy", "is voided");
        return raw;
    }
}
