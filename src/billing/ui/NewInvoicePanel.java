package billing.ui;

import billing.dao.InvoiceDao;
import billing.dao.LookupDao;
import billing.dao.ProductDao;
import billing.model.CartLine;
import billing.model.CustomerRef;
import billing.model.IdName;
import billing.model.PayMethod;
import billing.model.PaymentEntry;
import billing.model.Product;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * ====================================================================
 *  MÀN HÌNH 1 -- LẬP HÓA ĐƠN
 * ====================================================================
 *  Đây là nơi thể hiện rõ nhất yêu cầu khó nhất của đề bài:
 *
 *    "Đơn giá trên dòng hóa đơn phải là giá có hiệu lực vào NGÀY LẬP
 *     hóa đơn, không phải giá hôm nay."
 *
 *  Hãy thử: đổi ô "Ngày bán" về khoảng 400 ngày trước rồi thêm sản phẩm
 *  "Thit ba chi". Bạn sẽ thấy đơn giá nhảy về 130.000 (giá cũ) thay vì
 *  158.000 (giá hiện tại). App không hề hardcode -- nó hỏi database.
 *
 *  --------------------------------------------------------------------
 *  BỐN HỢP ĐỒNG mà màn hình này phải giữ (plan/14 mục 3):
 *
 *  1. TIỀN MẶT (mục 3.1) -- ba con số, ba chỗ ở:
 *         tendered = Cash_Payment.Tendered_Amount   tiền khách ĐƯA  (thu ngân gõ)
 *         recorded = Payment.Amount                 tiền GHI NHẬN  (APP TÍNH)
 *         change   = tendered - recorded            tiền THỐI      (không lưu)
 *     Tiền mặt:  recorded = min(tendered, còn thiếu)  -> paid > due KHÔNG BAO GIỜ
 *                xảy ra từ tab 1, nên nút "Thối tiền" hết lý do tồn tại.
 *     Thẻ / ví:  recorded <= còn thiếu, vượt thì TỪ CHỐI (không có đường trả lại).
 *
 *  2. GIẢM GIÁ (mục 3.2) -- discount là hàm của (sản phẩm, ngày, SỐ LƯỢNG):
 *         discount = ROUND(qty * unitPrice * pct / 100, 2)
 *     Nên MỌI chỗ đổi số lượng phải tính lại giảm giá, và chỉ có MỘT hàm dựng
 *     một dòng giỏ hàng ({@link #priceLine}).
 *
 *  3. ĐỌC / HIỆN SỐ (mục 3.3, 3.4) -- qua {@code Ui.readMoney} /
 *     {@code Ui.readQuantity} / {@code Ui.money} / {@code Ui.qty}, không bao
 *     giờ {@code new BigDecimal(textField.getText())}.
 *
 *  4. KHÁCH HÀNG (mục 3.5) -- {@link CustomerPicker} giữ bất biến
 *     "khách trên hoá đơn đúng là khách đang hiện trên màn hình".
 */
public class NewInvoicePanel extends JPanel {

    private final ProductDao productDao = new ProductDao();
    private final LookupDao  lookupDao  = new LookupDao();
    private final InvoiceDao invoiceDao = new InvoiceDao();

    // --- Khung 1: thông tin hoá đơn ---------------------------------
    private final JComboBox<IdName> cbCounter = new JComboBox<>();
    private final JComboBox<IdName> cbCashier = new JComboBox<>();
    private final CustomerPicker    customer  = new CustomerPicker(lookupDao);
    private final JSpinner          spDate;
    private final JButton           btReloadLookups = new JButton("Tai lai");

    // --- Khung 2: thêm sản phẩm -------------------------------------
    private final JComboBox<Product> cbProduct = new JComboBox<>();
    private final JTextField         tfQty     = new JTextField("1", 6);
    private final JLabel             lbHint    = new JLabel(" ");
    private final JButton            btAdd     = new JButton("Them vao hoa don");
    private final JButton            btEditQty = new JButton("Sua so luong");
    private final JButton            btDelLine = new JButton("Xoa dong dang chon");

    // --- Khung 3: các dòng hàng -------------------------------------
    private final JTable tbCart = Ui.readOnlyTable();

    // --- Khung 4: thanh toán ----------------------------------------
    private final JComboBox<PayMethod> cbMethod = new JComboBox<>(PayMethod.values());
    private final JLabel     lbAmount  = new JLabel();
    private final JTextField tfAmount  = new JTextField(12);
    private final JLabel     lbEcho;
    private final JTable     tbPay     = Ui.readOnlyTable();
    private final JButton    btAddPay  = new JButton("Them thanh toan");
    private final JButton    btPayAll  = new JButton("Khach tra du bang tien mat");
    private final JButton    btQr      = new JButton("Tao ma QR");
    private final JButton    btDelPay  = new JButton("Xoa dong dang chon");
    private final JButton    btClrPay  = new JButton("Xoa het");

    // --- Hàng tổng + nút lưu ----------------------------------------
    private final JLabel  lbTotal  = new JLabel();
    private final JLabel  lbPaid   = new JLabel();
    private final JLabel  lbLeft   = new JLabel();
    private final JLabel  lbChange = new JLabel();
    private final JButton btSave   = new JButton("XUAT HOA DON  (INSERT + COMMIT)");

    private final List<CartLine>     cart     = new ArrayList<>();
    private final List<PaymentEntry> payments = new ArrayList<>();

    private final Runnable onSaved;

    public NewInvoicePanel(Runnable onSaved) {
        this.onSaved = onSaved;

        // Tran cua o ngay la HOM NAY: ban hang o tuong lai khong phai nghiep vu
        // nao (plan/14 kich ban #24).
        Date now = new Date();
        spDate = new JSpinner(new SpinnerDateModel(
                now, null, now, java.util.Calendar.DAY_OF_MONTH));

        lbEcho = Ui.attachMoneyEcho(tfAmount);

        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        wireUp();
        loadLookups();              // NGOAI EDT (plan/14 loi #19)
        onMethodChanged();
        refreshCartTable();
        refreshPayTable();
    }

    // -----------------------------------------------------------------
    //  Dữ liệu chưa lưu (dùng để cảnh báo trước khi đăng xuất)
    // -----------------------------------------------------------------

    /**
     * Có dữ liệu chưa lưu trong tab này hay không.
     *
     * <p>Dùng để cảnh báo trước khi đăng xuất ({@code MainWindow.doLogout}) --
     * {@code dispose()} cửa sổ sẽ xoá sạch giỏ hàng mà không hỏi gì.
     */
    public boolean hasPendingWork() {
        return !cart.isEmpty() || !payments.isEmpty();
    }

    /**
     * Mô tả ngắn phần chưa lưu, ví dụ {@code "3 dong hang, 1 lan thanh toan"};
     * chuỗi rỗng nếu không có gì.
     *
     * <p>Hộp thoại đăng xuất cần nói rõ <i>sẽ mất bao nhiêu</i> -- "bạn có dữ
     * liệu chưa lưu" chung chung thì người dùng không biết có đáng quan tâm không.
     */
    public String pendingWorkSummary() {
        if (!hasPendingWork()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (!cart.isEmpty()) {
            sb.append(cart.size()).append(" dong hang");
        }
        if (!payments.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(payments.size()).append(" lan thanh toan");
        }
        return sb.toString();
    }

    // =================================================================
    //  BỐ CỤC
    // =================================================================

    private JComponent buildHeader() {
        spDate.setEditor(new JSpinner.DateEditor(spDate, "dd/MM/yyyy"));

        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("1. Thong tin hoa don"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 6, 3, 6);
        g.anchor = GridBagConstraints.WEST;

        cbCounter.setPreferredSize(new Dimension(260, 26));
        cbCashier.setPreferredSize(new Dimension(220, 26));
        spDate.setPreferredSize(new Dimension(130, 26));

        // Hàng 1
        g.gridy = 0;
        g.gridx = 0; p.add(new JLabel("Quay:"), g);
        g.gridx = 1; p.add(cbCounter, g);
        g.gridx = 2; p.add(new JLabel("Thu ngan:"), g);
        g.gridx = 3; p.add(cbCashier, g);
        g.gridx = 4; p.add(btReloadLookups, g);

        // Hàng 2
        g.gridy = 1;
        g.gridx = 0; p.add(new JLabel("Ngay ban:"), g);
        g.gridx = 1; p.add(spDate, g);
        g.gridx = 2; p.add(new JLabel("Khach (SDT):"), g);
        g.gridx = 3; g.gridwidth = 2; p.add(customer, g);
        g.gridwidth = 1;

        // Hàng 3: dòng mẹo -- thứ cả app sinh ra để minh hoạ, GIỮ NGUYÊN
        JLabel note = new JLabel("<html><i>Meo: doi 'Ngay ban' ve nam ngoai "
                + "roi them san pham -> don gia se lay theo GIA CU trong Price_History.</i></html>");
        g.gridy = 2; g.gridx = 0; g.gridwidth = 5;
        p.add(note, g);
        return p;
    }

    private JComponent buildCenter() {
        JPanel bar = new JPanel(new GridBagLayout());
        bar.setBorder(BorderFactory.createTitledBorder("2. Them san pham"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 6, 3, 6);
        g.anchor = GridBagConstraints.WEST;

        cbProduct.setPreferredSize(new Dimension(360, 26));
        tfQty.setToolTipText("<html>Dau <b>,</b> hoac <b>.</b> la dau THAP PHAN"
                + " (toi da 3 chu so) -- KHONG phai dau tach nghin.<br>"
                + "Dung: <code>2</code> | <code>1,5</code> | <code>1.5</code> | <code>0,25</code><br>"
                + "<i>Khac hẳn o tien ben duoi, noi dau <b>.</b> la dau tach nghin.</i><br>"
                + "Enter = Them vao hoa don.</html>");

        g.gridy = 0;
        g.gridx = 0; bar.add(new JLabel("San pham:"), g);
        g.gridx = 1; bar.add(cbProduct, g);
        g.gridx = 2; bar.add(new JLabel("So luong:"), g);
        g.gridx = 3; bar.add(tfQty, g);
        g.gridx = 4; bar.add(btAdd, g);
        g.gridx = 5; bar.add(btEditQty, g);
        g.gridx = 6; bar.add(btDelLine, g);

        lbHint.setForeground(new Color(0, 110, 0));
        g.gridy = 1; g.gridx = 0; g.gridwidth = 7;
        bar.add(lbHint, g);

        JScrollPane sp = new JScrollPane(tbCart);
        sp.setBorder(BorderFactory.createTitledBorder(
                "3. Cac dong hang (se thanh bang Invoice_Line)"));

        JPanel center = new JPanel(new BorderLayout(6, 6));
        center.add(bar, BorderLayout.NORTH);
        center.add(sp,  BorderLayout.CENTER);
        return center;
    }

    /**
     * Khung 4 dùng {@code GridBagLayout}, KHÔNG {@code FlowLayout}.
     *
     * <p>Bản cũ là một {@code FlowLayout} với 9 thành phần: thu nhỏ cửa sổ là
     * hàng nút xuống dòng và khung cao gấp đôi, ăn hết chỗ của bảng giỏ hàng
     * -- đúng cái bẫy mà {@code MainWindow} đã gặp, đã sửa, và đã ghi lại bằng
     * comment (plan/14 lỗi #24). Với GridBag, hàng nút xuống dòng <b>theo đúng
     * chỗ ta đặt</b>, không theo bề rộng cửa sổ.
     */
    private JComponent buildFooter() {
        JPanel pay = new JPanel(new GridBagLayout());
        pay.setBorder(BorderFactory.createTitledBorder(
                "4. Thanh toan (mot hoa don co the co NHIEU dong thanh toan)"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 6, 3, 6);
        g.anchor = GridBagConstraints.WEST;

        tfAmount.setToolTipText("<html>Dau <b>.</b> la dau TACH NGHIN (phai du 3 chu so),"
                + " dau <b>,</b> la dau thap phan.<br>"
                + "Dung: <code>500.000</code> | <code>1.234,50</code> | <code>500000</code><br>"
                + "Sai : <code>500.00</code> | <code>1.2.3</code> | <code>1,234</code><br>"
                + "<i>Khac hẳn o so luong o tren, noi dau <b>.</b> la dau thap phan.</i><br>"
                + "Enter = Them thanh toan.</html>");

        // Hàng 1: phương thức + ô tiền + nhãn vọng lại
        g.gridy = 0;
        g.gridx = 0; pay.add(new JLabel("Phuong thuc:"), g);
        g.gridx = 1; pay.add(cbMethod, g);
        g.gridx = 2; pay.add(lbAmount, g);
        g.gridx = 3; pay.add(tfAmount, g);
        g.gridx = 4; pay.add(lbEcho, g);

        // Hàng 2: hàng nút -- xuống dòng ở ĐÂY, không theo bề rộng cửa sổ
        JPanel buttons = new JPanel(new GridLayout(1, 0, 6, 0));
        buttons.add(btAddPay);
        buttons.add(btPayAll);
        buttons.add(btQr);
        buttons.add(btDelPay);
        buttons.add(btClrPay);
        g.gridy = 1; g.gridx = 0; g.gridwidth = 5; g.fill = GridBagConstraints.HORIZONTAL;
        pay.add(buttons, g);
        g.gridwidth = 1; g.fill = GridBagConstraints.NONE;

        // Bảng thanh toán cao ~4 dòng: khung mang tên "NHIEU dong thanh toan"
        // thì không thể chỉ xem được 2 dòng (plan/14 lỗi #25).
        JScrollPane spPay = new JScrollPane(tbPay);
        spPay.setPreferredSize(new Dimension(100, 130));

        JPanel totals = new JPanel(new FlowLayout(FlowLayout.RIGHT, 18, 8));
        lbTotal.setFont(Ui.BIG);
        lbPaid.setFont(Ui.BOLD);
        lbLeft.setFont(Ui.BOLD);
        lbChange.setFont(Ui.BOLD);
        lbLeft.setForeground(Color.RED.darker());
        lbChange.setForeground(new Color(0, 110, 0));
        totals.add(lbPaid);
        totals.add(lbLeft);
        totals.add(lbChange);
        totals.add(lbTotal);

        btSave.setFont(Ui.BOLD);
        btSave.setBackground(new Color(25, 118, 210));
        btSave.setForeground(Color.WHITE);
        btSave.setOpaque(true);
        btSave.setBorderPainted(false);
        totals.add(btSave);

        JPanel south = new JPanel(new BorderLayout());
        south.add(pay,    BorderLayout.NORTH);
        south.add(spPay,  BorderLayout.CENTER);
        south.add(totals, BorderLayout.SOUTH);
        return south;
    }

    private void wireUp() {
        btReloadLookups.addActionListener(e -> loadLookups());

        btAdd.addActionListener(e -> addToCart());
        tfQty.addActionListener(e -> addToCart());          // Enter = them (muc 5.6a)
        btEditQty.addActionListener(e -> editQuantity());
        btDelLine.addActionListener(e -> removeSelectedLine());
        btEditQty.setEnabled(false);
        btDelLine.setEnabled(false);
        tbCart.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            if (e.getValueIsAdjusting()) return;
            boolean has = tbCart.getSelectedRow() >= 0 && tbCart.getSelectedRow() < cart.size();
            btEditQty.setEnabled(has);
            btDelLine.setEnabled(has);
        });

        cbMethod.addActionListener(e -> onMethodChanged());
        btAddPay.addActionListener(e -> addPaymentFromField());
        tfAmount.addActionListener(e -> addPaymentFromField());   // Enter = them thanh toan
        btPayAll.addActionListener(e -> payRestInCash());
        btQr.addActionListener(e -> showQr());
        btDelPay.addActionListener(e -> removeSelectedPayment());
        btClrPay.addActionListener(e -> { payments.clear(); refreshPayTable(); });
        btDelPay.setEnabled(false);
        tbPay.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            if (e.getValueIsAdjusting()) return;
            btDelPay.setEnabled(tbPay.getSelectedRow() >= 0
                             && tbPay.getSelectedRow() < payments.size());
        });

        // qr.enabled=false -> nut AN HAN, khong mo: mot nut bi khoa khong giai
        // thich duoc gi (plan/14 muc 5.6f, dung cach khoi 2 xu ly nut Doi vai tro).
        btQr.setVisible(QrPaymentDialog.isEnabled());

        btSave.addActionListener(e -> save());
    }

    // =================================================================
    //  NẠP DANH MỤC -- NGOÀI EDT
    // =================================================================

    /** Ba danh mục đọc được, gói lại để truyền từ luồng nền về EDT. */
    private static class Lookups {
        List<IdName>  counters;
        List<IdName>  cashiers;
        List<Product> products;
    }

    /**
     * Nạp quầy / thu ngân / sản phẩm.
     *
     * <p>NGOÀI EDT: bản cũ gọi ba câu {@code SELECT} ngay trong hàm dựng, nên
     * MySQL chậm hoặc tắt là cửa sổ chưa kịp vẽ đã đứng, và {@code Ui.error}
     * hiện trên một panel chưa hiển thị (plan/14 lỗi #19).
     *
     * <p>Thất bại thì cửa sổ <b>vẫn vẽ ra</b>, chỉ là ba ô chọn rỗng -- và
     * {@link #save()} tự biết nói "chưa nạp được danh mục" thay vì ném NPE
     * (lỗi #20).
     */
    private void loadLookups() {
        btReloadLookups.setEnabled(false);
        new SwingWorker<Lookups, Void>() {
            @Override
            protected Lookups doInBackground() {
                Lookups l = new Lookups();
                l.counters = lookupDao.counters();
                l.cashiers = lookupDao.cashiers();
                l.products = productDao.findAll();
                return l;
            }

            @Override
            protected void done() {
                btReloadLookups.setEnabled(true);
                Lookups l;
                try {
                    l = get();
                } catch (Exception ex) {
                    Ui.error(NewInvoicePanel.this, ex);
                    return;
                }
                cbCounter.removeAllItems();
                cbCashier.removeAllItems();
                cbProduct.removeAllItems();
                for (IdName x : l.counters)  cbCounter.addItem(x);
                for (IdName x : l.cashiers)  cbCashier.addItem(x);
                for (Product p : l.products) cbProduct.addItem(p);
            }
        }.execute();
    }

    private LocalDate selectedDate() {
        Date d = (Date) spDate.getValue();
        return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    // =================================================================
    //  GIỎ HÀNG -- hợp đồng giảm giá, mục 3.2
    // =================================================================

    /** Giá tại ngày bán + khuyến mãi tốt nhất tại ngày đó. */
    private static class Priced {
        BigDecimal price;      // null = san pham chua co gia nao truoc ngay do
        BigDecimal pct = BigDecimal.ZERO;
        String     note = "";
    }

    /**
     * Hỏi database giá tại NGÀY BÁN và khuyến mãi tại ngày đó.
     * CHẠY NGOÀI EDT (2 lần gọi database).
     */
    private Priced priceAt(Product p, LocalDate date) {
        Priced r = new Priced();
        r.price = productDao.priceOn(p.barcode, date);
        if (r.price == null) return r;

        Object[] promo = productDao.activePromoOn(p.barcode, date);
        if (promo != null) {
            r.pct  = (BigDecimal) promo[1];
            r.note = promo[0] + " (-" + r.pct.stripTrailingZeros().toPlainString() + "%)";
        }
        return r;
    }

    /**
     * Dựng một dòng giỏ hàng. ĐÂY LÀ CHỖ DUY NHẤT tính {@code Discount}.
     *
     * <p>{@code addToCart()} và {@code editQuantity()} <b>đều</b> đi qua đây,
     * nên không có đường nào đổi số lượng mà quên tính lại tiền giảm
     * (plan/14 lỗi #2: bản cũ chỉ gán {@code cartLine.quantity = newQty}, nên
     * một sản phẩm 20.000 có KM 10% đổi số lượng từ 1 lên 10 cho ra
     * {@code 198.000} thay vì {@code 180.000} -- lệch 18.000, và con số sai đó
     * được ghi thẳng vào cột {@code Invoice_Line.Discount}).
     */
    private CartLine priceLine(Product p, BigDecimal qty, Priced pr) {
        return new CartLine(p, qty, pr.price,
                            lineDiscount(qty, pr.price, pr.pct), pr.note);
    }

    /**
     * {@code discount = ROUND(qty * price * pct / 100, 2)} -- số tiền giảm của
     * CẢ DÒNG. plan/14 mục 3.2.
     *
     * <p>Công thức này nằm trong Java và đó KHÔNG phải ngoại lệ của nguyên tắc
     * khối 3 ("công thức tiền chỉ ở một chỗ, và chỗ đó là SQL"): nguyên tắc là
     * <i>chỉ một bản chép</i>. {@code fn_return_refund} phải ở SQL vì <b>SQL
     * cũng tính tiền hoàn</b> (hai thủ tục đọc gọi vào nó).
     * {@code Invoice_Line.Discount} thì khác: nó là <b>giá trị đầu vào được
     * lưu</b>, không chỗ nào trong SQL tính lại nó. Đưa công thức xuống SQL sẽ
     * tạo ra bản chép thứ hai của quy tắc <i>chọn khuyến mãi tốt nhất</i>
     * (vì {@code sp_get_active_promo} vẫn phải tự chọn để lấy
     * {@code Rule_Description} cho dòng gợi ý) -- tức là đổi một bản chép thành
     * hai. Đã xét và loại.
     */
    private static BigDecimal lineDiscount(BigDecimal qty, BigDecimal price, BigDecimal pct) {
        if (pct == null || pct.signum() == 0) return BigDecimal.ZERO;
        return qty.multiply(price)
                  .multiply(pct)
                  .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    private CartLine findLineByBarcode(String barcode) {
        for (CartLine l : cart) {
            if (l.product.barcode.equals(barcode)) return l;
        }
        return null;
    }

    /**
     * ================================================================
     *  *** ĐOẠN QUAN TRỌNG NHẤT CỦA CẢ APP ***
     * ================================================================
     *  Thêm một sản phẩm vào giỏ: hỏi database giá hợp lệ tại ngày bán,
     *  rồi hỏi tiếp xem có khuyến mãi nào đang chạy không.
     *
     *  GỘP DÒNG CHỈ KHI ĐƠN GIÁ BẰNG NHAU (plan/14 lỗi #3): bản cũ cộng số
     *  lượng vào dòng cũ nhưng giữ nguyên {@code unitPrice} của lần thêm đầu,
     *  nên đổi ô "Ngày bán" giữa hai lần thêm cùng một sản phẩm -- chính là
     *  thao tác mà dòng mẹo ở khung 1 dạy người dùng làm -- là toàn bộ số lượng
     *  bị tính theo giá cũ. Hai đơn giá khác nhau <b>là</b> hai dòng hoá đơn
     *  khác nhau: khoá chính {@code Invoice_Line} là
     *  {@code (Invoice_ID, Line_Number)}, không cấm trùng {@code Barcode}.
     */
    private void addToCart() {
        final Product p = (Product) cbProduct.getSelectedItem();
        if (p == null) {
            Ui.warn(this, "Chua chon san pham nao.\n"
                        + "Neu o chon trong: bam \"Tai lai\" o khung 1 de nap lai danh muc.",
                    "Chua chon san pham");
            return;
        }
        final BigDecimal qty = Ui.readQuantity(this, tfQty.getText(), null, p.unit);
        if (qty == null) return;                 // Ui da hien thong bao
        final LocalDate date = selectedDate();

        setAddBusy(true);
        new SwingWorker<Priced, Void>() {
            @Override
            protected Priced doInBackground() {
                return priceAt(p, date);
            }

            @Override
            protected void done() {
                setAddBusy(false);
                Priced pr;
                try {
                    pr = get();
                } catch (Exception ex) {
                    Ui.error(NewInvoicePanel.this, ex);
                    return;
                }
                if (pr.price == null) {
                    Ui.warn(NewInvoicePanel.this,
                            "San pham nay chua co gia nao co hieu luc truoc ngay "
                          + date + ".\nHay chon ngay muon hon.",
                            "Khong co gia tai ngay ban");
                    return;
                }

                CartLine existing = findLineByBarcode(p.barcode);
                if (existing != null && pr.price.compareTo(existing.unitPrice) == 0) {
                    // Gop: tinh lai giam gia theo TONG so luong (kich ban #21)
                    int i = cart.indexOf(existing);
                    cart.set(i, priceLine(p, existing.quantity.add(qty), pr));
                    hint(date, pr, "");
                } else if (existing != null) {
                    cart.add(priceLine(p, qty, pr));
                    hint(date, pr, "  Gia ngay " + date + " (" + Ui.money(pr.price)
                            + ") khac don gia cua dong da co (" + Ui.money(existing.unitPrice)
                            + ") -> them dong moi.");
                } else {
                    cart.add(priceLine(p, qty, pr));
                    hint(date, pr, "");
                }
                refreshCartTable();
                tfQty.setText("1");
                tfQty.requestFocusInWindow();
            }
        }.execute();
    }

    private void hint(LocalDate date, Priced pr, String extra) {
        lbHint.setText("Gia ngay " + date + " = " + Ui.money(pr.price)
                + (pr.note.isEmpty() ? "  (khong co KM)" : "  " + pr.note) + extra);
    }

    private void setAddBusy(boolean busy) {
        btAdd.setEnabled(!busy);
        btEditQty.setEnabled(!busy && tbCart.getSelectedRow() >= 0);
    }

    /**
     * Sửa số lượng của dòng đang chọn.
     *
     * <p>KHÔNG mượn ô "So luong" của khung 2 nữa -- một nửa nguyên nhân của lỗi
     * #2 là chỗ đó: ô kia dùng cho việc thêm, nên người dùng không trông đợi nó
     * cũng là ô sửa. Hộp thoại điền sẵn {@code Ui.qtyInput(...)} (không dấu
     * nghìn, vì ô nhập hiểu {@code '.'} là dấu thập phân) và đọc bằng
     * {@code Ui.readQuantity}.
     */
    private void editQuantity() {
        int row = tbCart.getSelectedRow();
        if (row < 0 || row >= cart.size()) {
            Ui.warn(this, "Chua chon dong hang nao de sua.", "Chua chon dong hang");
            return;
        }
        final CartLine line = cart.get(row);
        String input = JOptionPane.showInputDialog(this,
                "So luong moi cho \"" + line.product.name + "\" (" + line.product.unit + "):\n\n"
              + "Dau , hoac . la dau thap phan, toi da 3 chu so.",
                Ui.qtyInput(line.quantity));
        final BigDecimal newQty = Ui.readQuantity(this, input, null, line.product.unit);
        if (newQty == null) return;

        final Product   p    = line.product;
        final LocalDate date = selectedDate();
        setAddBusy(true);
        new SwingWorker<Priced, Void>() {
            @Override
            protected Priced doInBackground() {
                return priceAt(p, date);
            }

            @Override
            protected void done() {
                setAddBusy(false);
                Priced pr;
                try {
                    pr = get();
                } catch (Exception ex) {
                    Ui.error(NewInvoicePanel.this, ex);
                    return;
                }
                if (pr.price == null) {
                    Ui.warn(NewInvoicePanel.this,
                            "San pham nay khong co gia nao co hieu luc truoc ngay " + date + ".",
                            "Khong co gia tai ngay ban");
                    return;
                }
                int i = cart.indexOf(line);
                if (i < 0) return;               // dong da bi xoa trong luc cho
                cart.set(i, priceLine(p, newQty, pr));
                hint(date, pr, "");
                refreshCartTable();
                tbCart.setRowSelectionInterval(i, i);
            }
        }.execute();
    }

    private void removeSelectedLine() {
        int r = tbCart.getSelectedRow();
        if (r >= 0 && r < cart.size()) {
            cart.remove(r);
            refreshCartTable();
        }
    }

    // =================================================================
    //  THANH TOÁN -- hợp đồng tiền mặt, mục 3.1
    // =================================================================

    private PayMethod method() {
        PayMethod m = (PayMethod) cbMethod.getSelectedItem();
        return m == null ? PayMethod.CASH : m;
    }

    /**
     * Nhãn ô tiền đổi theo phương thức: {@code "Khach dua:"} cho tiền mặt,
     * {@code "So tien:"} cho thẻ / ví.
     *
     * <p>Hợp đồng tiền mặt (mục 3.1) nhìn thấy được ngay trên màn hình, không
     * phải đọc tài liệu mới biết ô đó đang hỏi con số nào.
     */
    private void onMethodChanged() {
        PayMethod m = method();
        lbAmount.setText(m.amountFieldLabel() + ":");
        btQr.setEnabled(m == PayMethod.EWALLET);
    }

    private BigDecimal totalDue() {
        BigDecimal t = BigDecimal.ZERO;
        for (CartLine c : cart) t = t.add(c.lineTotal());
        return t;
    }

    /** Tổng tiền ĐÃ GHI NHẬN = Σ {@code Payment.Amount}. */
    private BigDecimal totalRecorded() {
        BigDecimal t = BigDecimal.ZERO;
        for (PaymentEntry p : payments) t = t.add(p.amount);
        return t;
    }

    /** Tổng tiền THỐI = Σ (khách đưa − ghi nhận). Không lưu ở cột nào. */
    private BigDecimal totalChange() {
        BigDecimal t = BigDecimal.ZERO;
        for (PaymentEntry p : payments) t = t.add(p.change());
        return t;
    }

    /** Còn thiếu. Nhờ hợp đồng mục 3.1, con số này KHÔNG BAO GIỜ âm. */
    private BigDecimal remaining() {
        BigDecimal r = totalDue().subtract(totalRecorded());
        return r.signum() < 0 ? BigDecimal.ZERO : r;
    }

    private void addPaymentFromField() {
        PayMethod m = method();
        BigDecimal typed = Ui.readMoney(this, tfAmount.getText(), m.amountFieldLabel());
        if (typed == null) return;              // Ui da hien thong bao
        if (addPayment(m, typed, null, null)) {
            tfAmount.setText("");
            tfAmount.requestFocusInWindow();
        }
    }

    /** Kết quả áp dụng hợp đồng tiền mặt cho một số tiền vừa gõ. */
    static final class Split {
        /** Tiền GHI NHẬN -> {@code Payment.Amount}. */
        final BigDecimal recorded;
        /** Tiền khách ĐƯA -> {@code Cash_Payment.Tendered_Amount}; null cho thẻ/ví. */
        final BigDecimal tendered;

        Split(BigDecimal recorded, BigDecimal tendered) {
            this.recorded = recorded;
            this.tendered = tendered;
        }

        BigDecimal change() {
            if (tendered == null) return BigDecimal.ZERO;
            BigDecimal c = tendered.subtract(recorded);
            return c.signum() < 0 ? BigDecimal.ZERO : c;
        }
    }

    /**
     * HỢP ĐỒNG TIỀN MẶT (mục 3.1) ở dạng THUẦN: không Swing, không database,
     * không thông báo.
     *
     * <pre>
     * Tien mat:  recorded = min(tendered, con thieu) ; tendered = so vua go
     * The / vi:  recorded = so vua go (tendered = NULL) ; vuot con thieu -> null
     * </pre>
     *
     * <p>Tách ra vì đây là chỗ dễ <b>hiểu ngược</b> nhất của cả khối (plan/14
     * rủi ro #2: ai đó đọc "Khach dua" thành "số tiền ghi nhận" rồi đặt
     * {@code recorded = tendered} trở lại). Một hàm thuần thì kiểm được trực
     * tiếp, không cần dựng cả màn hình.
     *
     * @return {@code null} nghĩa là TỪ CHỐI -- chỉ xảy ra với thẻ / ví khi số
     *         tiền vượt số còn thiếu, vì hai phương thức đó không có đường
     *         trả lại
     */
    static Split splitPayment(PayMethod m, BigDecimal typed, BigDecimal remaining) {
        if (m.hasChange()) {
            // Thua bao nhieu cung duoc: thoi duoc. Nhung GHI NHAN khong bao gio
            // vuot so con thieu -> paid > due khong the xay ra tu tab 1.
            return new Split(typed.min(remaining), typed);
        }
        if (typed.compareTo(remaining) > 0) return null;
        return new Split(typed, null);
    }

    /**
     * Ghi một dòng thanh toán theo HỢP ĐỒNG TIỀN MẶT (mục 3.1).
     *
     * <pre>
     * Tien mat:  thu ngan go TENDERED, app tu tinh
     *                recorded = min(tendered, con thieu)
     *                change   = tendered - recorded
     * The / vi:  thu ngan go RECORDED, va tendered khong ton tai (NULL)
     *                recorded &lt;= con thieu   -- vuot thi TU CHOI
     * </pre>
     *
     * <p>Vì sao tiền mặt thì cho thừa mà thẻ thì từ chối: tiền mặt <b>thối
     * được</b>, còn quẹt thẻ nhiều hơn hoá đơn thì <b>không có đường trả lại</b>
     * (không có cột tiền thối cho thẻ, và phiếu hoàn tiền nằm ngoài phạm vi
     * khối này).
     *
     * <p>Nhờ {@code recorded = min(...)}, {@code paid > due} không bao giờ xảy
     * ra từ tab 1 -- nên nút <i>Thối tiền</i> của bản cũ hết lý do tồn tại, và
     * cùng với nó là lỗi #8 (thối được xuống dưới mức phải trả, làm hoá đơn
     * thành {@code OPEN} và bị loại khỏi báo cáo doanh thu). Phép kiểm
     * {@code >=} trong {@code sp_settle_invoice} GIỮ NGUYÊN -- nó thuộc khối 4
     * và vẫn đúng; chỉ là tab 1 không còn sinh ra trường hợp {@code >}.
     *
     * @return {@code true} nếu đã thêm được dòng
     */
    private boolean addPayment(PayMethod m, BigDecimal typed, String detail1, String detail2) {
        if (cart.isEmpty()) {
            Ui.warn(this, "Hoa don chua co dong hang nao, chua biet phai thu bao nhieu.",
                    "Chua co dong hang");
            return false;
        }
        BigDecimal left = remaining();
        if (left.signum() <= 0) {
            Ui.warn(this, "Hoa don nay da thu du tien (" + Ui.money(totalDue()) + ").\n"
                        + "Khong can them dong thanh toan nao nua.",
                    "Da thu du tien");
            return false;
        }

        Split split = splitPayment(m, typed, left);
        if (split == null) {
            Ui.warn(this,
                    m.label() + " " + Ui.money(typed) + " nhieu hon so con thieu "
                  + Ui.money(left) + ".\n\n"
                  + "Khong co duong tra lai cho " + m.label().toLowerCase()
                  + ": bang Payment khong co cot tien thoi cho phuong thuc nay,\n"
                  + "va phieu hoan tien nam ngoai pham vi ban demo.\n\n"
                  + "Hay go dung " + Ui.money(left)
                  + ", hoac chon \"Tien mat\" neu khach dua thua.",
                    "So tien vuot qua so con thieu");
            return false;
        }

        String d1 = detail1;
        String d2 = detail2;
        if (d1 == null && d2 == null) {
            if (m == PayMethod.CARD) {
                d1 = "4111-****-****-" + (1000 + (int) (Math.random() * 9000));
                d2 = "AUTH" + (10000 + (int) (Math.random() * 89999));
            } else if (m == PayMethod.EWALLET) {
                d1 = "MoMo";
                d2 = "TXN-" + System.currentTimeMillis();
            }
        }

        payments.add(new PaymentEntry(m.code(),
                split.recorded.setScale(2, RoundingMode.HALF_UP),
                split.tendered == null ? null : split.tendered.setScale(2, RoundingMode.HALF_UP),
                d1, d2));
        refreshPayTable();
        return true;
    }

    /** Một dòng tiền mặt đúng số còn thiếu -> {@code Tien thoi = 0}. */
    private void payRestInCash() {
        BigDecimal left = remaining();
        if (cart.isEmpty()) {
            Ui.warn(this, "Hoa don chua co dong hang nao.", "Chua co dong hang");
            return;
        }
        if (left.signum() <= 0) {
            Ui.warn(this, "Hoa don nay da thu du tien.", "Da thu du tien");
            return;
        }
        addPayment(PayMethod.CASH, left, null, null);
    }

    /**
     * Mã QR cho số còn thiếu (hoặc cho số đang gõ trong ô).
     *
     * <p>Số tiền được kiểm theo đúng luật của ví điện tử TRƯỚC khi sinh mã: ví
     * không có tiền thối, nên một mã QR mang số tiền lớn hơn số còn thiếu là
     * một mã không ghi nhận được.
     */
    private void showQr() {
        if (cart.isEmpty()) {
            Ui.warn(this, "Hoa don chua co dong hang nao.", "Chua co dong hang");
            return;
        }
        BigDecimal left = remaining();
        if (left.signum() <= 0) {
            Ui.warn(this, "Hoa don nay da thu du tien.", "Da thu du tien");
            return;
        }

        BigDecimal amount;
        String typed = tfAmount.getText().trim();
        if (typed.isEmpty()) {
            amount = left;
        } else {
            amount = Ui.readMoney(this, typed, "So tien");
            if (amount == null) return;
            if (amount.compareTo(left) > 0) {
                Ui.warn(this, "So tien " + Ui.money(amount) + " nhieu hon so con thieu "
                            + Ui.money(left) + ".\n"
                            + "Vi dien tu khong co tien thoi, hay go dung " + Ui.money(left) + ".",
                        "So tien vuot qua so con thieu");
                return;
            }
        }

        QrPaymentDialog.Result r = QrPaymentDialog.show(this, amount);
        if (r == null) return;                   // nguoi dung huy
        if (addPayment(PayMethod.EWALLET, r.amount, "VietQR", r.transactionRef)) {
            tfAmount.setText("");
        }
    }

    private void removeSelectedPayment() {
        int r = tbPay.getSelectedRow();
        if (r >= 0 && r < payments.size()) {
            payments.remove(r);
            refreshPayTable();
        }
    }

    // =================================================================
    //  LƯU
    // =================================================================

    /**
     * Lưu hoá đơn. NGOÀI EDT, và nút bị khoá trong lúc lưu.
     *
     * <p>Ba lỗi được sửa cùng một chỗ:
     * <ul>
     *   <li>#16 {@code createInvoice} là một transaction gồm {@code 1+N+M+1}
     *       lệnh gọi thủ tục; chạy trên EDT thì MySQL chậm hoặc tắt là cả cửa
     *       sổ đứng hình. Giỏ hàng <b>còn nguyên</b> sau khi lỗi, để lưu lại.</li>
     *   <li>#17 bấm hai lần nhanh cho ra hai hoá đơn cho cùng một giỏ hàng.
     *       Nút đổi nhãn thành {@code "Dang luu..."}, không chỉ mờ đi: một nút
     *       mờ không giải thích gì thì người dùng bấm tiếp.</li>
     *   <li>#20 {@code counter.id} khi {@code loadLookups()} thất bại là một
     *       {@code NullPointerException} trần.</li>
     * </ul>
     */
    private void save() {
        if (cart.isEmpty()) {
            Ui.warn(this, "Hoa don chua co dong hang nao.", "Chua co dong hang");
            return;
        }
        // Phép kiểm "chưa có thanh toán nào" phải đi TRƯỚC: giỏ có hàng mà
        // chưa thanh toán gì thì bản cũ hỏi "lệch tiền, vẫn lưu chứ?", người
        // dùng bấm Yes, rồi mới bị từ chối vì "Chua co dong thanh toan nao"
        // -- một câu hỏi vô ích (plan/13 mục 5.7).
        if (payments.isEmpty()) {
            Ui.warn(this, "Chua co dong thanh toan nao.", "Chua co thanh toan");
            return;
        }

        // Đọc hết các ô NHẬP trên EDT trước khi vào SwingWorker: đọc Swing
        // component từ luồng khác là sai, và là loại sai chạy đúng 99 lần.
        final IdName counter = (IdName) cbCounter.getSelectedItem();
        final IdName cashier = (IdName) cbCashier.getSelectedItem();
        if (counter == null || cashier == null) {
            Ui.warn(this, "Chua nap duoc danh sach quay / thu ngan.\n"
                        + "Kiem tra ket noi MySQL roi bam \"Tai lai\" o khung 1.",
                    "Thieu du lieu danh muc");
            return;                                  // thay cho NPE (loi #20)
        }

        BigDecimal due  = totalDue();
        BigDecimal paid = totalRecorded();
        if (paid.compareTo(due) < 0) {
            // Đây là chỗ DUY NHẤT người dùng gặp trạng thái OPEN lần đầu.
            // Không nói rõ hậu quả thì họ sẽ tưởng app vừa đánh mất hóa đơn.
            int ok = JOptionPane.showConfirmDialog(this,
                    "Tong hoa don " + Ui.money(due) + " nhung chi thanh toan "
                  + Ui.money(paid) + " (thieu " + Ui.money(due.subtract(paid)) + ").\n\n"
                  + "Van luu, nhung hoa don se o trang thai DANG LAP (OPEN):\n"
                  + "  - Khong duoc tinh vao bao cao doanh thu\n"
                  + "  - Khong tra hang duoc\n"
                  + "  - Tu chuyen sang DA THANH TOAN khi thu du tien\n\n"
                  + "Luu chu?",
                    "Thieu tien -- hoa don se la OPEN", JOptionPane.YES_NO_OPTION);
            if (ok != JOptionPane.YES_OPTION) return;
        }

        final CustomerRef cus  = customer.getSelected();   // bat bien muc 3.5
        final LocalDate   date = selectedDate();
        final LocalTime   time = LocalTime.now().withNano(0);   // MOT lan (loi #29)
        final List<CartLine>     lines = new ArrayList<>(cart);
        final List<PaymentEntry> pays  = new ArrayList<>(payments);

        setSaving(true);
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                // invoiceId = null: thu tuc tu sinh ma TRONG transaction (loi #18)
                return invoiceDao.createInvoice(null, date, time,
                        counter.id, cashier.id,
                        cus == null ? null : cus.id,       // null = khach vang lai
                        lines, pays);
            }

            @Override
            protected void done() {
                setSaving(false);
                String id;
                try {
                    id = get();
                } catch (Exception ex) {
                    // Gio hang CON NGUYEN -- luu lai duoc ngay sau khi sua loi.
                    Ui.error(NewInvoicePanel.this, ex);
                    return;
                }
                ReceiptDialog.show(NewInvoicePanel.this, invoiceDao, id);  // doc lai tu DB
                resetForm();
                if (onSaved != null) onSaved.run();
            }
        }.execute();
    }

    private void setSaving(boolean saving) {
        btSave.setEnabled(!saving);
        btSave.setText(saving ? "Dang luu..." : "XUAT HOA DON  (INSERT + COMMIT)");
        btAddPay.setEnabled(!saving);
        btPayAll.setEnabled(!saving);
        btAdd.setEnabled(!saving);
        setCursor(Cursor.getPredefinedCursor(
                saving ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    /**
     * Xoá giỏ hàng, thanh toán, <b>và KHÁCH HÀNG</b>.
     *
     * <p>Bản cũ không xoá {@code selectedCustomer} lẫn ô text, nên hoá đơn TIẾP
     * THEO tự động gắn cho khách của hoá đơn trước -- với khách vãng lai (đa
     * số) thì đây là gán sai dữ liệu một cách im lặng, mỗi lần lập hoá đơn
     * (plan/14 lỗi #11).
     */
    private void resetForm() {
        cart.clear();
        payments.clear();
        customer.clear();
        tfQty.setText("1");
        tfAmount.setText("");
        lbHint.setText(" ");
        refreshCartTable();
        refreshPayTable();
    }

    // =================================================================
    //  VẼ LẠI BẢNG
    // =================================================================

    /**
     * Tám cột, KHỚP TỪNG CỘT với tám cột của tab 3
     * ({@code sp_get_invoice_lines}).
     *
     * <p>{@code Thanh tien = SL * Don gia - Giam gia} -- đúng công thức của thủ
     * tục. Bản cũ ghi rõ trong comment là <i>"khong tru giam gia"</i>, nên cùng
     * một dòng hàng cho hai con số trên hai màn hình, và tổng cột
     * {@code Thanh tien} của tab 1 không bằng nhãn {@code Tong:} ngay bên dưới
     * nó (plan/14 lỗi #7).
     *
     * <p>Cột {@code VAT %} dùng {@code Product.taxRate} -- field được nạp từ
     * {@code sp_get_all_products} rồi không dùng ở bất kỳ đâu (lỗi #29). Giá
     * trong {@code Price_History} <b>đã gồm VAT</b>, không công thức tính tổng
     * nào dùng tới thuế suất, nên cột này kèm tooltip nói rõ điều đó.
     */
    private void refreshCartTable() {
        String[] cols = {"Dong", "San pham", "SL", "DVT",
                         "Don gia", "Giam gia", "Thanh tien", "VAT %"};
        Object[][] data = new Object[cart.size()][cols.length];
        for (int i = 0; i < cart.size(); i++) {
            CartLine c = cart.get(i);
            data[i] = new Object[]{
                    i + 1,
                    c.product.name,
                    Ui.qty(c.quantity),
                    c.product.unit,
                    Ui.money(c.unitPrice),
                    Ui.money(c.discount),
                    Ui.money(c.lineTotal()),
                    Ui.percent(c.product.taxRate)};
        }
        ((DefaultTableModel) tbCart.getModel()).setDataVector(data, cols);
        Ui.autoSize(tbCart);
        tbCart.setToolTipText("Cot VAT % la thue suat cua san pham;"
                + " don gia trong Price_History DA GOM VAT.");
        refreshTotals();
    }

    /**
     * Năm cột: {@code Khach dua} và {@code Tien thoi} là hai cột mới, trống với
     * thẻ / ví -- ba con số của hợp đồng mục 3.1 nhìn thấy được cùng lúc.
     */
    private void refreshPayTable() {
        String[] cols = {"Phuong thuc", "So tien", "Khach dua", "Tien thoi", "Chi tiet"};
        Object[][] data = new Object[payments.size()][cols.length];
        for (int i = 0; i < payments.size(); i++) {
            PaymentEntry p = payments.get(i);
            boolean hasTendered = p.tendered != null;
            data[i] = new Object[]{
                    p.methodLabel(),
                    Ui.money(p.amount),
                    hasTendered ? Ui.money(p.tendered) : "",
                    hasTendered ? Ui.money(p.change())  : "",
                    (p.detail1 == null ? "" : p.detail1)
                            + (p.detail2 == null ? "" : " / " + p.detail2)};
        }
        ((DefaultTableModel) tbPay.getModel()).setDataVector(data, cols);
        Ui.autoSize(tbPay);
        refreshTotals();
    }

    /**
     * Hàng tổng.
     *
     * <p>{@code Con thieu} KHÔNG BAO GIỜ âm nữa (mục 3.1), nên nhánh
     * <i>"Số tiền cần thối"</i> của bản cũ -- vốn mượn chính cái nhãn
     * {@code Con thieu} để nói một chuyện khác -- biến mất: tiền thối có nhãn
     * riêng.
     */
    private void refreshTotals() {
        BigDecimal due    = totalDue();
        BigDecimal paid   = totalRecorded();
        BigDecimal left   = remaining();
        BigDecimal change = totalChange();

        lbTotal.setText("Tong: " + Ui.money(due));
        lbPaid.setText("Da thu: " + Ui.money(paid));
        lbLeft.setText("Con thieu: " + Ui.money(left));
        lbChange.setText("Tien thoi: " + Ui.money(change));
        lbLeft.setForeground(left.signum() > 0 ? Color.RED.darker() : new Color(0, 110, 0));
    }
}
