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
    private final JButton           btReloadLookups = new JButton("Tải lại");

    // --- Khung 2: thêm sản phẩm -------------------------------------
    private final JComboBox<Product> cbProduct = new JComboBox<>();
    private final JTextField         tfQty     = new JTextField("1", 6);
    private final JLabel             lbHint    = new JLabel(" ");
    private final JButton            btAdd     = new JButton("Thêm vào hóa đơn");
    private final JButton            btEditQty = new JButton("Sửa số lượng");
    private final JButton            btDelLine = new JButton("Xóa dòng đang chọn");

    // --- Khung 3: các dòng hàng -------------------------------------
    private final JTable tbCart = Ui.readOnlyTable();

    // --- Khung 4: thanh toán ----------------------------------------
    private final JComboBox<PayMethod> cbMethod = new JComboBox<>(PayMethod.values());
    private final JLabel     lbAmount  = new JLabel();
    private final JTextField tfAmount  = new JTextField(12);
    private final JLabel     lbEcho;
    private final JTable     tbPay     = Ui.readOnlyTable();
    private final JButton    btAddPay  = new JButton("Thêm thanh toán");
    private final JButton    btPayAll  = new JButton("Khách trả đủ bằng tiền mặt");
    private final JButton    btQr      = new JButton("Tạo mã QR");
    private final JButton    btDelPay  = new JButton("Xóa dòng đang chọn");
    private final JButton    btClrPay  = new JButton("Xóa hết");

    // --- Hàng tổng + nút lưu ----------------------------------------
    private final JLabel  lbTotal  = new JLabel();
    private final JLabel  lbPaid   = new JLabel();
    private final JLabel  lbLeft   = new JLabel();
    private final JLabel  lbChange = new JLabel();
    private final JButton btSave   = new JButton("XUẤT HÓA ĐƠN  (INSERT + COMMIT)");

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
     * Mô tả ngắn phần chưa lưu, ví dụ {@code "3 dòng hàng, 1 lần thanh toán"};
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
            sb.append(cart.size()).append(" dòng hàng");
        }
        if (!payments.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(payments.size()).append(" lần thanh toán");
        }
        return sb.toString();
    }

    // =================================================================
    //  BỐ CỤC
    // =================================================================

    private JComponent buildHeader() {
        spDate.setEditor(new JSpinner.DateEditor(spDate, "dd/MM/yyyy"));

        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("1. Thông tin hóa đơn"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 6, 3, 6);
        g.anchor = GridBagConstraints.WEST;

        cbCounter.setPreferredSize(new Dimension(260, 26));
        cbCashier.setPreferredSize(new Dimension(220, 26));
        spDate.setPreferredSize(new Dimension(130, 26));

        // Hàng 1
        g.gridy = 0;
        g.gridx = 0; p.add(new JLabel("Quầy:"), g);
        g.gridx = 1; p.add(cbCounter, g);
        g.gridx = 2; p.add(new JLabel("Thu ngân:"), g);
        g.gridx = 3; p.add(cbCashier, g);
        g.gridx = 4; p.add(btReloadLookups, g);

        // Hàng 2
        g.gridy = 1;
        g.gridx = 0; p.add(new JLabel("Ngày bán:"), g);
        g.gridx = 1; p.add(spDate, g);
        g.gridx = 2; p.add(new JLabel("Khách (SDT):"), g);
        g.gridx = 3; g.gridwidth = 2; p.add(customer, g);
        g.gridwidth = 1;

        // Hàng 3: dòng mẹo -- thứ cả app sinh ra để minh hoạ, GIỮ NGUYÊN
        JLabel note = new JLabel("<html><i>Mẹo: đổi 'Ngày bán' về năm ngoái "
                + "rồi thêm sản phẩm -> đơn giá sẽ lấy theo GIÁ CŨ trong Price_History.</i></html>");
        g.gridy = 2; g.gridx = 0; g.gridwidth = 5;
        p.add(note, g);
        return p;
    }

    private JComponent buildCenter() {
        JPanel bar = new JPanel(new GridBagLayout());
        bar.setBorder(BorderFactory.createTitledBorder("2. Thêm sản phẩm"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 6, 3, 6);
        g.anchor = GridBagConstraints.WEST;

        cbProduct.setPreferredSize(new Dimension(360, 26));
        tfQty.setToolTipText("<html>Dấu <b>,</b> hoặc <b>.</b> là dấu THẬP PHÂN"
                + " (tối đa 3 chữ số) -- KHÔNG phải dấu tách nghìn.<br>"
                + "Đúng: <code>2</code> | <code>1,5</code> | <code>1.5</code> | <code>0,25</code><br>"
                + "<i>Khác hẳn ở tiền bên dưới, nơi dấu <b>.</b> là dấu tách nghìn.</i><br>"
                + "Enter = Thêm vào hóa đơn.</html>");

        g.gridy = 0;
        g.gridx = 0; bar.add(new JLabel("Sản phẩm:"), g);
        g.gridx = 1; bar.add(cbProduct, g);
        g.gridx = 2; bar.add(new JLabel("Số lượng:"), g);
        g.gridx = 3; bar.add(tfQty, g);
        g.gridx = 4; bar.add(btAdd, g);
        g.gridx = 5; bar.add(btEditQty, g);
        g.gridx = 6; bar.add(btDelLine, g);

        lbHint.setForeground(new Color(0, 110, 0));
        g.gridy = 1; g.gridx = 0; g.gridwidth = 7;
        bar.add(lbHint, g);

        JScrollPane sp = new JScrollPane(tbCart);
        sp.setBorder(BorderFactory.createTitledBorder(
                "3. Các dòng hàng (sẽ thành bảng Invoice_Line)"));

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
                "4. Thanh toán (một hóa đơn có thể có NHIỀU dòng thanh toán)"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 6, 3, 6);
        g.anchor = GridBagConstraints.WEST;

        tfAmount.setToolTipText("<html>Dấu <b>.</b> là dấu TÁCH NGHÌN (phải đủ 3 chữ số),"
                + " dấu <b>,</b> là dấu thập phân.<br>"
                + "Dung: <code>500.000</code> | <code>1.234,50</code> | <code>500000</code><br>"
                + "Sai : <code>500.00</code> | <code>1.2.3</code> | <code>1,234</code><br>"
                + "<i>Khác hẳn ở số lượng ở trên, nơi dấu <b>.</b> là dấu thập phân.</i><br>"
                + "Enter = Thêm thanh toán.</html>");

        // Hàng 1: phương thức + ô tiền + nhãn vọng lại
        g.gridy = 0;
        g.gridx = 0; pay.add(new JLabel("Phương thức:"), g);
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
            Ui.warn(this, "Chưa chọn sản phẩm nào.\n"
                        + "Nếu ô chọn trống: bấm \"Tải lại\" ở khung 1 để nạp lại danh mục.",
                    "Chưa chọn sản phẩm");
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
                            "Sản phẩm này chưa có giá nào có hiệu lực trước ngày "
                          + date + ".\nHãy chọn ngày muộn hơn.",
                            "Không có giá tại ngày bán");
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
                    hint(date, pr, "  Giá ngày " + date + " (" + Ui.money(pr.price)
                            + ") khác đơn giá của dòng đã có (" + Ui.money(existing.unitPrice)
                            + ") -> thêm dòng mới.");
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
        lbHint.setText(I18n.richText("Giá ngày " + date + " = " + Ui.money(pr.price)
                + (pr.note.isEmpty() ? "  (không có KM)" : "  " + pr.note) + extra));
    }

    private void setAddBusy(boolean busy) {
        btAdd.setEnabled(!busy);
        btEditQty.setEnabled(!busy && tbCart.getSelectedRow() >= 0);
    }

    /**
     * Sửa số lượng của dòng đang chọn.
     *
     * <p>KHÔNG mượn ô "Số lượng" của khung 2 nữa -- một nửa nguyên nhân của lỗi
     * #2 là chỗ đó: ô kia dùng cho việc thêm, nên người dùng không trông đợi nó
     * cũng là ô sửa. Hộp thoại điền sẵn {@code Ui.qtyInput(...)} (không dấu
     * nghìn, vì ô nhập hiểu {@code '.'} là dấu thập phân) và đọc bằng
     * {@code Ui.readQuantity}.
     */
    private void editQuantity() {
        int row = tbCart.getSelectedRow();
        if (row < 0 || row >= cart.size()) {
            Ui.warn(this, "Chưa chọn dòng hàng nào để sửa.", "Chưa chọn dòng hàng");
            return;
        }
        final CartLine line = cart.get(row);
        String input = I18n.showInputDialog(this,
                "Số lượng mới cho \"" + line.product.name + "\" (" + line.product.unit + "):\n\n"
              + "Dấu , hoặc . là dấu thập phân, tối đa 3 chữ số.",
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
                            "Sản phẩm này không có giá nào có hiệu lực trước ngày " + date + ".",
                            "Không có giá tại ngày bán");
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
        lbAmount.setText(I18n.richText(m.amountFieldLabel() + ":"));
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
     * rủi ro #2: ai đó đọc "Khách đưa" thành "số tiền ghi nhận" rồi đặt
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
            Ui.warn(this, "Hóa đơn chưa có dòng hàng nào, chưa biết phải thu bao nhiêu.",
                    "Chưa có dòng hàng");
            return false;
        }
        BigDecimal left = remaining();
        if (left.signum() <= 0) {
            Ui.warn(this, "Hóa đơn này đã thu đủ tiền (" + Ui.money(totalDue()) + ").\n"
                        + "Không cần thêm dòng thanh toán nào nữa.",
                    "Đã thu đủ tiền");
            return false;
        }

        Split split = splitPayment(m, typed, left);
        if (split == null) {
            Ui.warn(this,
                    m.label() + " " + Ui.money(typed) + " nhiều hơn số còn thiếu "
                  + Ui.money(left) + ".\n\n"
                  + "Không có đường trả lại cho " + m.label().toLowerCase()
                  + ": bảng Payment không có cột tiền thối cho phương thức này,\n"
                  + "và phiếu hoàn tiền nằm ngoài phạm vi bản demo.\n\n"
                  + "Hãy gõ đúng " + Ui.money(left)
                  + ", hoặc chọn \"Tiền mặt\" nếu khách đưa thừa.",
                    "Số tiền vượt quá số còn thiếu");
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
            Ui.warn(this, "Hóa đơn chưa có dòng hàng nào.", "Chưa có dòng hàng");
            return;
        }
        if (left.signum() <= 0) {
            Ui.warn(this, "Hóa đơn này đã thu đủ tiền.", "Đã thu đủ tiền");
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
            Ui.warn(this, "Hóa đơn chưa có dòng hàng nào.", "Chưa có dòng hàng");
            return;
        }
        BigDecimal left = remaining();
        if (left.signum() <= 0) {
            Ui.warn(this, "Hóa đơn này đã thu đủ tiền.", "Đã thu đủ tiền");
            return;
        }

        BigDecimal amount;
        String typed = tfAmount.getText().trim();
        if (typed.isEmpty()) {
            amount = left;
        } else {
            amount = Ui.readMoney(this, typed, "Số tiền");
            if (amount == null) return;
            if (amount.compareTo(left) > 0) {
                Ui.warn(this, "Số tiền " + Ui.money(amount) + " nhiều hơn số còn thiếu "
                            + Ui.money(left) + ".\n"
                            + "Ví điện tử không có tiền thối, hãy gõ đúng " + Ui.money(left) + ".",
                        "Số tiền vượt quá số còn thiếu");
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
     *       Nút đổi nhãn thành {@code "Đang lưu..."}, không chỉ mờ đi: một nút
     *       mờ không giải thích gì thì người dùng bấm tiếp.</li>
     *   <li>#20 {@code counter.id} khi {@code loadLookups()} thất bại là một
     *       {@code NullPointerException} trần.</li>
     * </ul>
     */
    private void save() {
        if (cart.isEmpty()) {
            Ui.warn(this, "Hóa đơn chưa có dòng hàng nào.", "Chưa có dòng hàng");
            return;
        }
        // Phép kiểm "chưa có thanh toán nào" phải đi TRƯỚC: giỏ có hàng mà
        // chưa thanh toán gì thì bản cũ hỏi "lệch tiền, vẫn lưu chứ?", người
        // dùng bấm Yes, rồi mới bị từ chối vì "Chua co dong thanh toan nao"
        // -- một câu hỏi vô ích (plan/13 mục 5.7).
        if (payments.isEmpty()) {
            Ui.warn(this, "Chưa có dòng thanh toán nào.", "Chưa có thanh toán");
            return;
        }

        // Đọc hết các ô NHẬP trên EDT trước khi vào SwingWorker: đọc Swing
        // component từ luồng khác là sai, và là loại sai chạy đúng 99 lần.
        final IdName counter = (IdName) cbCounter.getSelectedItem();
        final IdName cashier = (IdName) cbCashier.getSelectedItem();
        if (counter == null || cashier == null) {
            Ui.warn(this, "Chưa nạp được danh sách quầy / thu ngân.\n"
                        + "Kiểm tra kết nối MySQL rồi bấm \"Tải lại\" ở khung 1.",
                    "Thiếu dữ liệu danh mục");
            return;                                  // thay cho NPE (loi #20)
        }

        BigDecimal due  = totalDue();
        BigDecimal paid = totalRecorded();
        if (paid.compareTo(due) < 0) {
            // Đây là chỗ DUY NHẤT người dùng gặp trạng thái OPEN lần đầu.
            // Không nói rõ hậu quả thì họ sẽ tưởng app vừa đánh mất hóa đơn.
            int ok = I18n.showConfirmDialog(this,
                    "Tổng hóa đơn " + Ui.money(due) + " nhưng chỉ thanh toán "
                  + Ui.money(paid) + " (thiếu " + Ui.money(due.subtract(paid)) + ").\n\n"
                  + "Vẫn lưu, nhưng hóa đơn sẽ ở trạng thái ĐANG LẬP (OPEN):\n"
                  + "  - Không được tính vào báo cáo doanh thu\n"
                  + "  - Không trả hàng được\n"
                  + "  - Tự chuyển sang ĐÃ THANH TOÁN khi thu đủ tiền\n\n"
                  + "Lưu chứ?",
                    "Thiếu tiền -- hóa đơn sẽ là OPEN", JOptionPane.YES_NO_OPTION);
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
        btSave.setText(I18n.text(saving ? "Đang lưu..." : "XUẤT HÓA ĐƠN  (INSERT + COMMIT)"));
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
        String[] cols = {"Dòng", "Sản phẩm", "SL", "DVT",
                         "Đơn giá", "Giảm giá", "Thành tiền", "VAT %"};
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
        tbCart.setToolTipText("Cột VAT % là thuế suất của sản phẩm;"
                + " đơn giá trong Price_History ĐÃ GỒM VAT.");
        refreshTotals();
    }

    /**
     * Năm cột: {@code Khach dua} và {@code Tien thoi} là hai cột mới, trống với
     * thẻ / ví -- ba con số của hợp đồng mục 3.1 nhìn thấy được cùng lúc.
     */
    private void refreshPayTable() {
        String[] cols = {"Phương thức", "Số tiền", "Khách đưa", "Tiền thối", "Chi tiết"};
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

        lbTotal.setText(I18n.richText("Tổng: " + Ui.money(due)));
        lbPaid.setText(I18n.richText("Đã thu: " + Ui.money(paid)));
        lbLeft.setText(I18n.richText("Còn thiếu: " + Ui.money(left)));
        lbChange.setText(I18n.richText("Tiền thối: " + Ui.money(change)));
        lbLeft.setForeground(left.signum() > 0 ? Color.RED.darker() : new Color(0, 110, 0));
    }
}
