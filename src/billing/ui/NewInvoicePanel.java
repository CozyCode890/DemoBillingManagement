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
import billing.util.CashRules;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.AbstractDocument;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.event.ActionListener;
import javax.swing.SwingUtilities;
import java.awt.Dialog;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
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
 *                xảy ra từ tab 1. Nút "Thoi tien" hiện nay CHỈ tách tiền thối
 *                thành các tờ VND để thu ngân đưa đúng -- không ghi gì.
 *                Khách đưa dư phải hợp lý: CashRules.checkTendered.
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
    private final JTextField tfAmount  = new JTextField(8);
    private final JLabel     lbEcho;
    private final JTable     tbPay     = Ui.readOnlyTable();
    private final JButton    btAddPay  = new JButton("Them thanh toan");
    private final JButton    btPayAll  = new JButton("Khach tra du bang tien mat");
    private final JButton    btQr      = new JButton("Tao ma QR");
    private final JButton    btDelPay  = new JButton("Xoa dong dang chon");
    private final JButton    btClrPay  = new JButton("Xoa het");
    /** Tách tiền thối thành các tờ VND (note: "nút thối tiền"). */
    private final JButton    btChange  = new JButton(I18n.text("Thối tiền", "Change"));

    // --- Hàng tổng + nút lưu ----------------------------------------
    private final JLabel  lbTotal  = new JLabel();
    private final JLabel  lbPaid   = new JLabel();
    private final JLabel  lbLeft   = new JLabel();
    private final JLabel  lbChange = new JLabel();
    private final JButton btSave   = new JButton(saveLabel());
    /** Xóa trắng cả tab để lập hóa đơn khác (note: "nút reset tab"). */
    private final JButton btReset  = new JButton(I18n.text("Làm mới tab", "Reset Tab"));

    private final List<CartLine>     cart     = new ArrayList<>();
    private final List<PaymentEntry> payments = new ArrayList<>();

    /** Ngăn vòng lặp khi setText() tự động cập nhật tfAmount trong onMethodChanged/refreshTotals. */
    private boolean autoUpdatingAmount = false;

    /** Viền mặc định của ô Số lượng, dùng để khôi phục khi hợp lệ. */
    private Border defaultQtyBorder;

    /** Nhãn cảnh báo cho ô Số lượng (hiển thị khi vượt giới hạn). */
    private final JLabel lbQtyWarn = new JLabel(" ");

    private final Runnable onSaved;

    public NewInvoicePanel(Runnable onSaved) {
        this.onSaved = onSaved;

        // Ngay ban luon theo dong ho server (BR2) -> khoa chi doc.
        Date now = new Date();
        spDate = new JSpinner(new SpinnerDateModel(
                now, null, null, java.util.Calendar.DAY_OF_MONTH));
        spDate.setEnabled(false);
        spDate.setToolTipText(I18n.text(
                "Ngày lập hóa đơn luôn lấy theo đồng hồ của máy chủ.",
                "The invoice date always follows the server clock."));

        lbEcho = Ui.attachMoneyEcho(tfAmount);

        // Lưu viền mặc định của ô Số lượng
        defaultQtyBorder = tfQty.getBorder();
        // DocumentFilter: chữ số + MỘT dấu , hoặc . (tối đa 5 số nguyên, 3 số lẻ).
        // Bản trước chỉ nhận số nguyên nên KHÔNG bán được 1,5 kg thịt -- trong
        // khi tooltip, seed và cột DECIMAL(10,3) đều dùng số thập phân.
        ((AbstractDocument) tfQty.getDocument()).setDocumentFilter(new QuantityDocumentFilter());

        // Thiết lập nhãn cảnh báo
        lbQtyWarn.setForeground(new Color(220, 38, 38));
        lbQtyWarn.setFont(lbQtyWarn.getFont().deriveFont(11f));
        lbQtyWarn.setVisible(false);

        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        wireUp();
        // Nhan ghep so lieu (tong tien, nhan o tien, bang) khong co trong tu
        // dien -- doi ngon ngu thi tu ve lai bang du lieu dang co.
        I18n.onLanguageChange(this, () -> {
            lbAmount.setText(method().amountFieldLabel() + ":");
            if (btSave.isEnabled()) btSave.setText(saveLabel());
            refreshCartTable();
            refreshPayTable();
        });
        loadLookups();              // NGOAI EDT (plan/14 loi #19)
        onMethodChanged();
        refreshCartTable();
        refreshPayTable();
    }

    // -----------------------------------------------------------------
    //  Dữ liệu chưa lưu (dùng để cảnh báo trước khi thoát ứng dụng)
    // -----------------------------------------------------------------

    /**
     * Có dữ liệu chưa lưu trong tab này hay không.
     *
     * <p>Dùng để cảnh báo trước khi thoát ({@code MainWindow.confirmExit}) --
     * đóng cửa sổ sẽ xoá sạch giỏ hàng chưa lưu.
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
            sb.append(cart.size()).append(I18n.msg(" dòng hàng", " item line(s)"));
        }
        if (!payments.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(payments.size()).append(I18n.msg(" lần thanh toán", " payment(s)"));
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

        // Hàng 3: giải thích quy tắc ngày lập hóa đơn & giá lịch sử (BR2/BR8)
        JLabel note = new JLabel(I18n.text(
                "<html><i>Lưu ý: ngày giờ hóa đơn và đơn giá tự động lấy theo ngày hiện tại của hệ thống. "
                        + "Để xem giá lịch sử và hóa đơn cũ giữ giá cũ, xem tab Xem hóa đơn &amp; Báo cáo.</i></html>",
                "<html><i>Note: invoice date/time and unit prices follow today's server clock. "
                        + "To inspect historical prices and old invoices, see the Invoices &amp; Reports tabs.</i></html>"));
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
        tfQty.setToolTipText(I18n.text(
                "<html>Dấu <b>,</b> hoặc <b>.</b> là dấu THẬP PHÂN"
                + " (tối đa 3 chữ số) -- KHÔNG phải dấu tách nghìn.<br>"
                + "Số thập phân CHỈ cho sản phẩm bán theo KG / LITER;"
                + " cái / gói / hộp chỉ số nguyên. Tối đa 10.000 mỗi dòng.<br>"
                + "Đúng: <code>2</code> | <code>1,5</code> | <code>1.5</code> | <code>0,25</code><br>"
                + "<i>Khác hẳn ô tiền bên dưới, nơi dấu <b>.</b> là dấu tách nghìn.</i><br>"
                + "Enter = Thêm vào hóa đơn.</html>",
                "<html>Use <b>,</b> or <b>.</b> as the decimal point"
                + " (up to 3 digits) -- NOT a thousands separator.<br>"
                + "Decimals are allowed ONLY for products sold by KG / LITER;"
                + " pieces / packs / boxes take whole numbers. At most 10,000 per line.<br>"
                + "Valid: <code>2</code> | <code>1,5</code> | <code>1.5</code> | <code>0,25</code><br>"
                + "<i>Unlike the amount field below, where <b>.</b> separates thousands.</i><br>"
                + "Enter = add to the invoice.</html>"));

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

        // Hàng cảnh báo Số lượng (hàng 2)
        g.gridy = 2; g.gridx = 0; g.gridwidth = 7;
        bar.add(lbQtyWarn, g);

        JScrollPane sp = new JScrollPane(tbCart);
        sp.setBorder(BorderFactory.createTitledBorder(
                I18n.text("3. Các dòng hàng", "3. Items")));

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

        tfAmount.setToolTipText(I18n.text(
                "<html>Dấu <b>.</b> là dấu TÁCH NGHÌN (phải đủ 3 chữ số),"
                + " dấu <b>,</b> là dấu thập phân.<br>"
                + "Đúng: <code>500.000</code> | <code>1.234,50</code> | <code>500000</code><br>"
                + "Sai: <code>500.00</code> | <code>1.2.3</code> | <code>1,234</code><br>"
                + "<i>Khác hẳn ô số lượng bên trên, nơi dấu <b>.</b> là dấu thập phân.</i><br>"
                + "Enter = Thêm thanh toán.</html>",
                "<html><b>.</b> separates THOUSANDS (must be followed by 3 digits),"
                + " <b>,</b> is the decimal separator.<br>"
                + "Valid: <code>500.000</code> | <code>1.234,50</code> | <code>500000</code><br>"
                + "Invalid: <code>500.00</code> | <code>1.2.3</code> | <code>1,234</code><br>"
                + "<i>Unlike the quantity field above, where <b>.</b> is the decimal separator.</i><br>"
                + "Enter = add the payment.</html>"));

        // Đảm bảo ô nhập tiền không bị GridBagLayout co nhỏ khi cửa sổ hẹp.
        Dimension amtPref = tfAmount.getPreferredSize();
        int amtW = 80;
        tfAmount.setMinimumSize(new Dimension(amtW, amtPref.height));
        tfAmount.setPreferredSize(new Dimension(amtW, amtPref.height));

        // Hàng 1: phương thức + ô tiền + nhãn vọng lại
        g.gridy = 0;
        g.gridx = 0; pay.add(new JLabel("Phuong thuc:"), g);
        g.gridx = 1; pay.add(cbMethod, g);
        g.gridx = 2; pay.add(lbAmount, g);
        g.gridx = 3; pay.add(tfAmount, g);
        g.gridx = 4; g.weightx = 1.0; pay.add(lbEcho, g);
        g.weightx = 0;

        // Hàng 2: hàng nút -- xuống dòng ở ĐÂY, không theo bề rộng cửa sổ
        JPanel buttons = new JPanel(new GridLayout(1, 0, 6, 0));
        buttons.add(btAddPay);
        buttons.add(btPayAll);
        buttons.add(btQr);
        buttons.add(btDelPay);
        buttons.add(btClrPay);
        buttons.add(btChange);
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
        totals.add(btReset);
        totals.add(btSave);

        JPanel south = new JPanel(new BorderLayout());
        south.add(pay,    BorderLayout.NORTH);
        south.add(spPay,  BorderLayout.CENTER);
        south.add(totals, BorderLayout.SOUTH);
        return south;
    }

    private void wireUp() {
        btReloadLookups.addActionListener(e -> loadLookups());

        // DocumentListener cho ô Số lượng: kiểm tra ngay khi gõ (trần 10.000,
        // số nguyên / thập phân theo đơn vị của sản phẩm đang chọn)
        tfQty.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { validateQtyField(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { validateQtyField(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { validateQtyField(); }
        });

        cbProduct.addActionListener(e -> validateQtyField());   // doi san pham = doi don vi
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
        btChange.addActionListener(e -> showChangeBreakdown());
        btChange.setEnabled(false);
        btReset.addActionListener(e -> onResetTab());
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
    //  KIỂM TRA SỐ LƯỢNG -- luật nằm ở CashRules.quantityProblem
    // =================================================================
    //  Trần 10.000 mỗi dòng (note: "hàng vô hạn / mua số lượng vượt loài
    //  người"), số thập phân CHỈ cho KG / LITER. Bản trước đổi ô này sang số
    //  nguyên cho mọi sản phẩm, nên 1,5 kg thịt không bán được nữa.

    private String selectedUnit() {
        Product p = (Product) cbProduct.getSelectedItem();
        return p == null ? null : p.unit;
    }

    /** Đọc im lặng (không hộp thoại) để tô viền ô khi đang gõ. */
    private static BigDecimal parseQtySilently(String text) {
        String t = text.trim().replace(',', '.');
        if (t.isEmpty() || t.equals(".")) return null;
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Kiểm tra giá trị ô Số lượng real-time (từ DocumentListener).
     * Hiển thị viền đỏ + cảnh báo nếu vượt giới hạn hoặc không hợp lệ.
     */
    private void validateQtyField() {
        String text = tfQty.getText().trim();
        if (text.isEmpty()) {
            restoreQtyBorder();          // de trong -> kiem khi bam nut
            return;
        }
        BigDecimal q = parseQtySilently(text);
        String problem = (q == null) ? I18n.msg("Số lượng không đọc được.", "The quantity cannot be read.")
                                     : CashRules.quantityProblem(q, selectedUnit());
        if (problem != null) {
            showQtyError(problem);
        } else {
            restoreQtyBorder();
        }
    }

    /**
     * Hiển thị viền đỏ và dòng cảnh báo cho ô Số lượng.
     */
    private void showQtyError(String message) {
        tfQty.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 38, 38), 2),
                BorderFactory.createEmptyBorder(2, 5, 2, 5)));
        tfQty.putClientProperty("JComponent.outline", "error");
        lbQtyWarn.setText(message);
        lbQtyWarn.setVisible(true);
    }

    /**
     * Khôi phục viền mặc định và ẩn cảnh báo.
     */
    private void restoreQtyBorder() {
        tfQty.setBorder(defaultQtyBorder);
        tfQty.putClientProperty("JComponent.outline", null);
        lbQtyWarn.setText(" ");
        lbQtyWarn.setVisible(false);
    }

    /**
     * Kiểm tra số lượng khi bấm "Thêm vào hóa đơn" hoặc "Sửa số lượng".
     * Trả về số lượng nếu hợp lệ, null nếu không (đã hiển thị cảnh báo).
     *
     * @param qtyText     chuỗi nhập từ ô Số lượng (hoặc hộp thoại sửa)
     * @param existingQty số lượng đã có trong giỏ (cho cùng sản phẩm), null nếu không có
     * @param productUnit đơn vị sản phẩm -- quyết định có nhận số thập phân không
     * @param isEdit      true nếu là sửa (số mới THAY số cũ, không cộng dồn)
     */
    private BigDecimal validateQuantityForAction(String qtyText, BigDecimal existingQty,
                                                 String productUnit, boolean isEdit) {
        // Ui.readQuantity lo định dạng (dấu , / ., <= 3 số lẻ, > 0) và tự báo lỗi.
        BigDecimal q = Ui.readQuantity(this, qtyText, null, productUnit);
        if (q == null) {
            tfQty.requestFocusInWindow();
            return null;
        }
        String problem = CashRules.quantityProblem(q, productUnit);
        if (problem != null) {
            if (!isEdit) showQtyError(problem);
            Ui.warn(this, problem, I18n.msg("Số lượng không hợp lệ", "Invalid Quantity"));
            tfQty.requestFocusInWindow();
            return null;
        }
        // Cộng dồn với dòng đã có trong giỏ (chỉ khi thêm mới)
        if (existingQty != null && !isEdit
                && existingQty.add(q).compareTo(CashRules.MAX_LINE_QTY) > 0) {
            String msg = I18n.msg(
                    "Tổng số lượng (đã có trong giỏ " + Ui.qty(existingQty)
                  + " + nhập mới " + Ui.qty(q) + ") vượt quá 10.000 mỗi dòng.",
                    "Total quantity (already in cart " + Ui.qty(existingQty)
                  + " + new " + Ui.qty(q) + ") exceeds 10,000 per line.");
            showQtyError(msg);
            Ui.warn(this, msg, I18n.msg("Số lượng không hợp lệ", "Invalid Quantity"));
            tfQty.requestFocusInWindow();
            return null;
        }
        restoreQtyBorder();
        return q;
    }

    /**
     * Tổng hóa đơn sau khi THAY dòng thứ {@code replaceIndex} (hoặc thêm mới
     * nếu {@code -1}) bằng {@code line} có vượt cột DECIMAL(12,2) không.
     * Trần số lượng 10.000 chưa đủ: 20 dòng x 10.000 x 390.000 đã vượt.
     */
    private boolean exceedsMoneyCap(CartLine line, int replaceIndex) {
        BigDecimal t = BigDecimal.ZERO;
        for (int i = 0; i < cart.size(); i++) {
            if (i != replaceIndex) t = t.add(cart.get(i).lineTotal());
        }
        t = t.add(line.lineTotal());
        if (t.compareTo(CashRules.MAX_MONEY) <= 0) return false;
        Ui.warn(this, I18n.msg(
                    "Tổng hóa đơn sẽ là " + Ui.money(t) + ", vượt mức tối đa của một hóa đơn\n"
                  + "(" + Ui.money(CashRules.MAX_MONEY) + "). Hãy tách thành nhiều hóa đơn.",
                    "The invoice total would be " + Ui.money(t) + ", over the limit for one invoice\n"
                  + "(" + Ui.money(CashRules.MAX_MONEY) + "). Please split it into several invoices!"),
                I18n.msg("Tổng tiền quá lớn", "Total Too Large"));
        return true;
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
        return LocalDate.now();
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
            Ui.warn(this, I18n.msg(
                        "Chưa chọn sản phẩm nào.\n"
                      + "Nếu ô chọn trống: bấm \"Tải lại\" ở khung 1 để nạp lại danh mục.",
                        "Please select a product!\n"
                      + "If the list is empty, click \"Reload\" in section 1 to reload the catalog."),
                    I18n.msg("Chưa chọn sản phẩm", "No Product Selected"));
            return;
        }

        // Kiểm tra giới hạn số lượng (MySQL INT)
        CartLine existing = findLineByBarcode(p.barcode);
        BigDecimal existingQty = (existing != null) ? existing.quantity : null;
        final BigDecimal qty = validateQuantityForAction(tfQty.getText(), existingQty, p.unit, false);
        if (qty == null) return;  // Đã hiển thị cảnh báo

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
                            I18n.msg("Sản phẩm này chưa có giá nào có hiệu lực trước ngày "
                                   + date + ".\nHãy chọn ngày muộn hơn.",
                                     "This product has no price in effect before "
                                   + date + ".\nPlease pick a later date!"),
                            I18n.msg("Không có giá tại ngày bán", "No Price on Sale Date"));
                    return;
                }

                CartLine existing = findLineByBarcode(p.barcode);
                if (existing != null) {
                    // BR7: moi san pham toi da 1 dong tren 1 hoa don -> cong don SL va tinh lai giam gia
                    int i = cart.indexOf(existing);
                    CartLine merged = priceLine(p, existing.quantity.add(qty), pr);
                    if (exceedsMoneyCap(merged, i)) return;
                    cart.set(i, merged);
                    hint(date, pr, "");
                } else {
                    CartLine added = priceLine(p, qty, pr);
                    if (exceedsMoneyCap(added, -1)) return;
                    cart.add(added);
                    hint(date, pr, "");
                }
                refreshCartTable();
                tfQty.setText("1");
                tfQty.requestFocusInWindow();
            }
        }.execute();
    }

    private void hint(LocalDate date, Priced pr, String extra) {
        lbHint.setText(I18n.msg("Giá ngày ", "Price on ") + date + " = " + Ui.money(pr.price)
                + (pr.note.isEmpty() ? I18n.msg("  (không có KM)", "  (no promotion)") : "  " + pr.note)
                + extra);
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
            Ui.warn(this, I18n.msg("Chưa chọn dòng hàng nào để sửa.", "Please select a line to edit!"),
                    I18n.msg("Chưa chọn dòng hàng", "No Line Selected"));
            return;
        }
        final CartLine line = cart.get(row);

        // Cùng bộ lọc với ô Số lượng: chữ số + một dấu thập phân
        final JTextField editQtyField = new JTextField(Ui.qtyInput(line.quantity), 6);
        ((AbstractDocument) editQtyField.getDocument()).setDocumentFilter(new QuantityDocumentFilter());

        // Tạo dialog tùy chỉnh
        Window parent = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(parent, I18n.msg("Sửa số lượng", "Edit Quantity"),
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout(8, 8));
        ((JComponent) dialog.getContentPane()).setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel msgLabel = new JLabel("<html>"
                + I18n.msg("Số lượng mới cho \"", "New quantity for \"")
                + line.product.name + "\" (" + line.product.unit + "):<br>"
                + (CashRules.allowsDecimal(line.product.unit)
                        ? I18n.msg("Dấu , hoặc . là dấu thập phân, tối đa 3 chữ số.",
                                   "Use , or . as the decimal separator, up to 3 digits.")
                        : I18n.msg("Chỉ số nguyên (sản phẩm không bán lẻ theo cân / đong).",
                                   "Whole numbers only (this product is not sold by weight / volume)."))
                + I18n.msg(" Tối đa 10.000.", " At most 10,000.") + "</html>");
        msgLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton okBtn = new JButton("OK");
        JButton cancelBtn = new JButton(I18n.msg("Hủy", "Cancel"));
        btnPanel.add(okBtn);
        btnPanel.add(cancelBtn);

        dialog.add(msgLabel, BorderLayout.NORTH);
        dialog.add(editQtyField, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        final String[] result = {null};

        okBtn.addActionListener(e -> {
            result[0] = editQtyField.getText();
            dialog.dispose();
        });
        cancelBtn.addActionListener(e -> dialog.dispose());
        editQtyField.addActionListener(e -> {
            result[0] = editQtyField.getText();
            dialog.dispose();
        });

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);

        String input = result[0];
        if (input == null) return; // User cancelled

        // Kiểm tra giới hạn số lượng - khi sửa, số lượng mới THAY THẾ số lượng cũ
        // nên không cộng dồn, chỉ kiểm tra giá trị mới
        final BigDecimal newQty = validateQuantityForAction(input, null, line.product.unit, true);
        if (newQty == null) return;  // Đã hiển thị cảnh báo

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
                            I18n.msg("Sản phẩm này không có giá nào có hiệu lực trước ngày " + date + ".",
                                     "This product has no price in effect before " + date + "."),
                            I18n.msg("Không có giá tại ngày bán", "No Price on Sale Date"));
                    return;
                }
                int i = cart.indexOf(line);
                if (i < 0) return;               // dong da bi xoa trong luc cho
                CartLine edited = priceLine(p, newQty, pr);
                if (exceedsMoneyCap(edited, i)) return;
                cart.set(i, edited);
                hint(date, pr, "");
                refreshCartTable();
                tbCart.setRowSelectionInterval(i, i);
                recalculatePaymentsIfNeeded();
            }
        }.execute();
    }

    private void removeSelectedLine() {
        int r = tbCart.getSelectedRow();
        if (r >= 0 && r < cart.size()) {
            cart.remove(r);
            refreshCartTable();
            recalculatePaymentsIfNeeded();
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
     *
     * <p>Tự động điền số tiền còn thiếu khi chọn Thẻ / Ví điện tử:
     * - The ngan hang / Vi dien tu: điền số còn thiếu (nếu > 0), để trống nếu = 0
     * - Tien mat: xóa trắng ô So tien (nhập số khách đưa)
     */
    private void onMethodChanged() {
        PayMethod m = method();
        lbAmount.setText(m.amountFieldLabel() + ":");
        btQr.setEnabled(m == PayMethod.EWALLET);

        // Tự động cập nhật ô Số tiền theo phương thức thanh toán
        autoUpdatingAmount = true;
        try {
            BigDecimal left = remaining();
            if (m.hasChange()) {
                // Tiền mặt: xóa trắng ô So tien để thu ngân nhập số khách đưa
                tfAmount.setText("");
            } else {
                // Thẻ / Ví điện tử: điền số còn thiếu (nếu > 0)
                if (left.signum() > 0) {
                    tfAmount.setText(Ui.moneyInput(left));
                } else {
                    tfAmount.setText("");
                }
            }
        } finally {
            autoUpdatingAmount = false;
        }
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
     * ra từ tab 1 -- nên nút <i>Thối tiền</i> của bản cũ (sửa số ghi nhận, gây
     * lỗi #8: thối xuống dưới mức phải trả) không quay lại. Nút "Thoi tien"
     * mới chỉ ĐỌC tổng tiền thối và tách thành các tờ ({@link #showChangeBreakdown}).
     * Phép kiểm {@code >=} trong {@code sp_settle_invoice} GIỮ NGUYÊN.
     *
     * @return {@code true} nếu đã thêm được dòng
     */
    private boolean addPayment(PayMethod m, BigDecimal typed, String detail1, String detail2) {
        if (cart.isEmpty()) {
            Ui.warn(this, I18n.msg("Hóa đơn chưa có dòng hàng nào, chưa biết phải thu bao nhiêu.",
                                   "The invoice has no items yet, so there is nothing to collect!"),
                    I18n.msg("Chưa có dòng hàng", "No Items Yet"));
            return false;
        }
        BigDecimal left = remaining();
        if (left.signum() <= 0) {
            Ui.warn(this, I18n.msg(
                        "Hóa đơn này đã thu đủ tiền (" + Ui.money(totalDue()) + ").\n"
                      + "Không cần thêm dòng thanh toán nào nữa.",
                        "This invoice is fully paid (" + Ui.money(totalDue()) + ").\n"
                      + "No more payments are needed."),
                    I18n.msg("Đã thu đủ tiền", "Fully Paid"));
            return false;
        }

        Split split = splitPayment(m, typed, left);
        if (split == null) {
            Ui.warn(this, I18n.msg(
                    m.displayLabel() + " " + Ui.money(typed) + " nhiều hơn số còn thiếu "
                  + Ui.money(left) + ".\n\n"
                  + m.displayLabel() + " không thối lại tiền được.\n\n"
                  + "Hãy gõ đúng " + Ui.money(left)
                  + ", hoặc chọn \"Tiền mặt\" nếu khách đưa thừa.",
                    m.displayLabel() + " " + Ui.money(typed) + " is more than the remaining "
                  + Ui.money(left) + ".\n\n"
                  + m.displayLabel() + " cannot give change.\n\n"
                  + "Please enter exactly " + Ui.money(left)
                  + ", or choose \"Cash\" if the customer pays more!"),
                    I18n.msg("Số tiền vượt quá số còn thiếu", "Amount Too High"));
            return false;
        }

        // KHÁCH ĐƯA DƯ CÓ HỢP LÝ KHÔNG (note: "38k đưa 45k", "48k đưa 480k").
        // Luật ở CashRules.checkTendered: hợp lý khi khách không đưa thừa tờ
        // nào. Sai hẳn (thối >= 500.000, có xu, vượt DECIMAL(12,2)) -> chặn;
        // có tờ thừa -> HỎI LẠI, vì "đưa lẻ để nhận chẵn" là có thật.
        if (m.hasChange()) {
            CashRules.TenderCheck tc = CashRules.checkTendered(typed, left);
            if (tc.verdict == CashRules.Verdict.REJECT) {
                Ui.warn(this, tc.message, I18n.msg("Số tiền khách đưa không hợp lý",
                                                   "Unreasonable Amount"));
                return false;
            }
            if (tc.verdict == CashRules.Verdict.SUSPICIOUS) {
                Object[] opts = {I18n.msg("Gõ lại số tiền", "Re-enter amount"),
                                 I18n.msg("Vẫn ghi", "Record anyway")};
                int c = JOptionPane.showOptionDialog(this,
                        tc.message + I18n.msg("\nCó phải thu ngân gõ nhầm không?",
                                              "\nDid the cashier mistype?"),
                        I18n.msg("Khách đưa thừa tờ tiền?", "Extra Banknote?"),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE, null, opts, opts[0]);
                if (c != 1) return false;
            }
        }

        String d1 = detail1;
        String d2 = detail2;
        if (d1 == null && d2 == null) {
            if (m == PayMethod.CARD) {
                // Chi tiết thẻ THẬT, đọc từ phiếu máy POS -- bản trước sinh số
                // thẻ và mã chuẩn chi NGẪU NHIÊN, nên cột "Chi tiet" không nói
                // gì về giao dịch thật (note: "phần chi tiết là gì").
                String[] card = askCardDetails(split.recorded);
                if (card == null) return false;              // nguoi dung huy
                d1 = card[0];
                d2 = card[1];
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

    /**
     * Hỏi 4 số cuối thẻ + mã chuẩn chi in trên phiếu máy POS.
     * App chỉ lưu 4 số cuối ({@code ****-****-****-1234}), không bao giờ số
     * thẻ đầy đủ.
     *
     * @return {Card_number, Auth_code}, hoặc null nếu người dùng hủy
     */
    private String[] askCardDetails(BigDecimal amount) {
        JTextField tfLast4 = new JTextField(6);
        JTextField tfAuth  = new JTextField(8);
        JPanel form = new JPanel(new GridLayout(0, 1, 4, 4));
        form.add(new JLabel(I18n.msg(
                "Quẹt thẻ " + Ui.money(amount) + " trên máy POS, rồi nhập theo phiếu in ra:",
                "Swipe " + Ui.money(amount) + " on the POS terminal, then copy from its slip:")));
        form.add(new JLabel(I18n.msg("4 số cuối của thẻ:", "Last 4 card digits:")));
        form.add(tfLast4);
        form.add(new JLabel(I18n.msg("Mã chuẩn chi (6 ký tự, 'APPR CODE' trên phiếu):",
                                     "Authorization code (6 characters, 'APPR CODE' on the slip):")));
        form.add(tfAuth);
        while (true) {
            int c = JOptionPane.showConfirmDialog(this, form,
                    I18n.msg("Chi tiết thanh toán thẻ ngân hàng", "Bank Card Details"),
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (c != JOptionPane.OK_OPTION) return null;
            String last4 = tfLast4.getText().trim();
            String auth  = tfAuth.getText().trim().toUpperCase();
            String problem = CashRules.cardDetailProblem(last4, auth);
            if (problem == null) {
                return new String[]{CashRules.maskedCard(last4), auth};
            }
            Ui.warn(this, problem, I18n.msg("Chi tiết thẻ chưa đúng", "Invalid Card Details"));
        }
    }

    /** Một dòng tiền mặt đúng số còn thiếu -> {@code Tien thoi = 0}. */
    private void payRestInCash() {
        BigDecimal left = remaining();
        if (cart.isEmpty()) {
            Ui.warn(this, I18n.msg("Hóa đơn chưa có dòng hàng nào.", "The invoice has no items yet!"),
                    I18n.msg("Chưa có dòng hàng", "No Items Yet"));
            return;
        }
        if (left.signum() <= 0) {
            Ui.warn(this, I18n.msg("Hóa đơn này đã thu đủ tiền.", "This invoice is fully paid."),
                    I18n.msg("Đã thu đủ tiền", "Fully Paid"));
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
            Ui.warn(this, I18n.msg("Hóa đơn chưa có dòng hàng nào.", "The invoice has no items yet!"),
                    I18n.msg("Chưa có dòng hàng", "No Items Yet"));
            return;
        }
        BigDecimal left = remaining();
        if (left.signum() <= 0) {
            Ui.warn(this, I18n.msg("Hóa đơn này đã thu đủ tiền.", "This invoice is fully paid."),
                    I18n.msg("Đã thu đủ tiền", "Fully Paid"));
            return;
        }

        // Ô trống thì KHÔNG tự lấy số còn thiếu nữa (note: "chưa nhập số tiền
        // mà vẫn tạo được mã QR"). Chọn "Vi dien tu" là ô đã tự điền số còn
        // thiếu rồi, nên ô trống nghĩa là người dùng đã cố ý xóa nó.
        BigDecimal amount;
        String typed = tfAmount.getText().trim();
        if (typed.isEmpty()) {
            Ui.warn(this, I18n.msg(
                        "Chưa nhập số tiền cần thu bằng mã QR.\n"
                      + "Chọn phương thức \"" + PayMethod.EWALLET.displayLabel()
                      + "\" để ô Số tiền tự điền số còn thiếu.",
                        "Please enter the amount to collect with the QR code!\n"
                      + "Choosing \"" + PayMethod.EWALLET.displayLabel()
                      + "\" fills the Amount field with the remaining balance."),
                    I18n.msg("Chưa nhập số tiền", "Missing Amount"));
            tfAmount.requestFocusInWindow();
            return;
        } else {
            amount = Ui.readMoney(this, typed, I18n.msg("Số tiền", "Amount"));
            if (amount == null) return;
            if (amount.compareTo(left) > 0) {
                Ui.warn(this, I18n.msg(
                            "Số tiền " + Ui.money(amount) + " nhiều hơn số còn thiếu "
                          + Ui.money(left) + ".\n"
                          + "Ví điện tử không có tiền thối, hãy gõ đúng " + Ui.money(left) + ".",
                            "The amount " + Ui.money(amount) + " is more than the remaining "
                          + Ui.money(left) + ".\n"
                          + "E-wallets give no change. Please enter exactly " + Ui.money(left) + "."),
                        I18n.msg("Số tiền vượt quá số còn thiếu", "Amount Too High"));
                return;
            }
        }

        QrPaymentDialog.Result r = QrPaymentDialog.show(this, amount);
        if (r == null) return;                   // nguoi dung huy
        if (addPayment(PayMethod.EWALLET, r.amount, "VietQR", r.transactionRef)) {
            tfAmount.setText("");
        }
    }

    /**
     * Nút "Thoi tien": tách tổng tiền thối thành các tờ VND, tờ lớn trước.
     * Chỉ là gợi ý cho thu ngân -- không đổi dữ liệu nào (tiền thối không
     * lưu ở cột nào: thối = khách đưa − ghi nhận, plan/14 mục 3.1).
     */
    private void showChangeBreakdown() {
        BigDecimal change = totalChange();
        if (change.signum() <= 0) {
            Ui.warn(this, I18n.msg("Không có tiền thối.", "There is no change to give."),
                    I18n.msg("Tiền thối", "Change"));
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(I18n.msg("Tiền thối cho khách: ", "Change for the customer: "))
          .append(Ui.money(change)).append("\n\n");
        for (long[] part : CashRules.breakdown(change)) {
            sb.append(String.format("  %10s  x %d%n", Ui.money(BigDecimal.valueOf(part[0])), part[1]));
        }
        BigDecimal rest = CashRules.breakdownRemainder(change);
        if (rest.signum() > 0) {
            sb.append(I18n.msg("\n  Lẻ ", "\n  ")).append(Ui.money(rest))
              .append(I18n.msg(" dưới tờ nhỏ nhất (100đ) -- làm tròn với khách.",
                               " is below the smallest note (100 VND) -- round it with the customer."));
        }
        JTextArea ta = new JTextArea(sb.toString());
        ta.setEditable(false);
        ta.setFont(Ui.MONO);
        JOptionPane.showMessageDialog(this, ta, I18n.msg("Thối tiền", "Change"),
                JOptionPane.INFORMATION_MESSAGE);
    }

    /** Nút "Lam moi tab": hỏi trước nếu đang có dữ liệu chưa lưu. */
    private void onResetTab() {
        if (hasPendingWork()) {
            int c = JOptionPane.showConfirmDialog(this,
                    I18n.msg("Xóa trắng tab Lập hóa đơn?\nSẽ mất " + pendingWorkSummary() + " chưa lưu.",
                             "Clear the New invoice tab?\nUnsaved " + pendingWorkSummary() + " will be lost."),
                    I18n.msg("Làm mới tab", "Reset Tab"), JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (c != JOptionPane.YES_OPTION) return;
        }
        resetForm();
        spDate.setValue(new Date());
        cbMethod.setSelectedItem(PayMethod.CASH);
        restoreQtyBorder();
    }

    private void removeSelectedPayment() {
        int r = tbPay.getSelectedRow();
        if (r >= 0 && r < payments.size()) {
            payments.remove(r);
            refreshPayTable();
        }
    }

    // =================================================================
    //  TỰ ĐỘNG TIÊN THỐI KHI TỔNG HÓA ĐƠN GIẢM -- mục 3.1 mở rộng
    // =================================================================
    /**
     * Kiểm tra và tự động điều chỉnh thanh toán khi tổng hóa đơn mới nhỏ hơn
     * tổng tiền đã thanh toán.
     *
     * <p>Hai trường hợp:
     * <ol>
     *   <li>Có ít nhất một dòng Tiền mặt: Giữ nguyên {@code tendered} (Khach dua),
     *       giảm {@code amount} (So tien) trên các dòng Tiền mặt từ cuối lên đầu
     *       cho đến khi {@code totalRecorded == totalDue}. Phần chênh lệch được
     *       cộng vào {@code change} (Tien thoi) sao cho {@code amount + change = tendered}.
     *   <li>Chỉ có Thẻ / Ví điện tử: Hiển thị cảnh báo, không tự điều chỉnh được
     *       vì không có cơ chế tiền thối. {@link #save()} sẽ chặn lưu nếu vẫn
     *       còn {@code totalRecorded > totalDue}.
     * </ol>
     */
    private void recalculatePaymentsIfNeeded() {
        BigDecimal due = totalDue();
        BigDecimal paid = totalRecorded();

        // Nếu tổng thanh toán <= tổng hóa đơn thì không cần làm gì
        if (paid.compareTo(due) <= 0) {
            return;
        }

        // Tính số tiền dư thừa cần xử lý
        BigDecimal excess = paid.subtract(due);

        // Kiểm tra có dòng Tiền mặt nào không
        boolean hasCashPayment = false;
        for (PaymentEntry p : payments) {
            if (PayMethod.CASH.code().equals(p.method)) {
                hasCashPayment = true;
                break;
            }
        }

        if (hasCashPayment) {
            // Trường hợp 1: Có Tiền mặt -> tự động điều chỉnh từ dòng Tiền mặt cuối cùng lên đầu
            // Duyệt ngược để trừ từ dòng Tiền mặt cuối cùng
            for (int i = payments.size() - 1; i >= 0 && excess.signum() > 0; i--) {
                PaymentEntry p = payments.get(i);
                if (!PayMethod.CASH.code().equals(p.method)) {
                    continue; // Chỉ xử lý dòng Tiền mặt
                }

                // Số tiền có thể giảm trên dòng này = min(amount hiện tại, excess)
                BigDecimal canReduce = p.amount.min(excess);

                // Giảm amount, giữ nguyên tendered -> change tự tăng
                BigDecimal newAmount = p.amount.subtract(canReduce);
                p.amount = newAmount.setScale(2, RoundingMode.HALF_UP);
                // tendered giữ nguyên, change() sẽ tự tính = tendered - amount mới

                excess = excess.subtract(canReduce);
            }

            // Cập nhật lại giao diện
            refreshPayTable();
        } else {
            // Trường hợp 2: Chỉ có Thẻ / Ví -> cảnh báo, không tự điều chỉnh được
            Ui.warn(this, I18n.msg(
                    "Tổng tiền hóa đơn mới nhỏ hơn số tiền đã thanh toán qua thẻ/ví điện tử.\n\n"
                  + "Vui lòng bấm nút \"Xóa hết\" ở mục Thanh toán để nhập lại số tiền phù hợp.",
                    "The new invoice total is less than the amount already paid by card/e-wallet.\n\n"
                  + "Please click \"Clear all\" in the payment section and enter the payments again!"),
                    I18n.msg("Tiền thối không thể tự động điều chỉnh", "Cannot Adjust Change"));
            // Không refreshPayTable() vì không thay đổi dữ liệu
            // save() sẽ kiểm tra và chặn nếu vẫn còn paid > due
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
            Ui.warn(this, I18n.msg("Hóa đơn chưa có dòng hàng nào.", "The invoice has no items yet!"),
                    I18n.msg("Chưa có dòng hàng", "No Items Yet"));
            return;
        }
        // Phép kiểm "chưa có thanh toán nào" phải đi TRƯỚC: giỏ có hàng mà
        // chưa thanh toán gì thì bản cũ hỏi "lệch tiền, vẫn lưu chứ?", người
        // dùng bấm Yes, rồi mới bị từ chối vì "Chua co dong thanh toan nao"
        // -- một câu hỏi vô ích (plan/13 mục 5.7).
        if (payments.isEmpty()) {
            Ui.warn(this, I18n.msg("Chưa có dòng thanh toán nào.", "Please add a payment first!"),
                    I18n.msg("Chưa có thanh toán", "No Payments"));
            return;
        }

        // Đọc hết các ô NHẬP trên EDT trước khi vào SwingWorker: đọc Swing
        // component từ luồng khác là sai, và là loại sai chạy đúng 99 lần.
        final IdName counter = (IdName) cbCounter.getSelectedItem();
        final IdName cashier = (IdName) cbCashier.getSelectedItem();
        if (counter == null || cashier == null) {
            Ui.warn(this, I18n.msg(
                        "Chưa nạp được danh sách quầy / thu ngân.\n"
                      + "Kiểm tra kết nối rồi bấm \"Tải lại\" ở khung 1.",
                        "The counter / cashier lists have not loaded.\n"
                      + "Please check the connection, then click \"Reload\" in section 1!"),
                    I18n.msg("Thiếu dữ liệu danh mục", "Missing Information"));
            return;                                  // thay cho NPE (loi #20)
        }

        BigDecimal due  = totalDue();
        BigDecimal paid = totalRecorded();
        if (paid.compareTo(due) < 0) {
            // Chặn lưu khi thanh toán chưa đủ: không cho lưu trạng thái OPEN,
            // yêu cầu thu ngân thu đủ tiền trước khi xuất hóa đơn.
            BigDecimal remaining = due.subtract(paid);
            Ui.warn(this,
                    I18n.msg(
                    "Tổng hóa đơn là " + Ui.money(due) + " nhưng mới thanh toán " + Ui.money(paid)
                  + " (còn thiếu " + Ui.money(remaining) + ").\n"
                  + "Vui lòng thu đủ tiền trước khi xuất hóa đơn!",
                    "The invoice total is " + Ui.money(due) + " but only " + Ui.money(paid)
                  + " has been paid (" + Ui.money(remaining) + " remaining).\n"
                  + "Please collect the full amount before creating the invoice!"),
                    I18n.msg("Thanh toán chưa đủ", "Payment Incomplete"));
            return;
        } else if (paid.compareTo(due) > 0) {
            // Trường hợp paid > due: kiểm tra có Tiền mặt để tự điều chỉnh không
            boolean hasCashPayment = false;
            for (PaymentEntry p : payments) {
                if (PayMethod.CASH.code().equals(p.method)) {
                    hasCashPayment = true;
                    break;
                }
            }
            if (!hasCashPayment) {
                // Chỉ có Thẻ / Ví -> không thể tự điều chỉnh tiền thối, chặn lưu
                Ui.warn(this,
                        I18n.msg(
                        "Tổng tiền thanh toán (" + Ui.money(paid) + ") lớn hơn tổng hóa đơn (" + Ui.money(due) + ").\n\n"
                      + "Vì chỉ có thanh toán bằng Thẻ/Ví điện tử (không có tiền thối),\n"
                      + "vui lòng bấm nút \"Xóa hết\" ở mục Thanh toán để nhập lại số tiền phù hợp.",
                        "Total payments (" + Ui.money(paid) + ") exceed the invoice total (" + Ui.money(due) + ").\n\n"
                      + "Card/e-wallet payments give no change, so please click \"Clear all\"\n"
                      + "in the payment section and enter the payments again!"),
                        I18n.msg("Thanh toán vượt tổng hóa đơn", "Overpayment"));
                return;
            }
            // Có Tiền mặt -> recalculatePaymentsIfNeeded() đã được gọi khi sửa/xóa hàng,
            // nhưng kiểm tra lại lần nữa để an toàn (nếu người dùng bấm lưu ngay mà không sửa hàng)
            recalculatePaymentsIfNeeded();
            // Sau khi điều chỉnh, paid phải == due
            paid = totalRecorded();
            if (paid.compareTo(due) > 0) {
                // Vẫn còn dư (hiếm xảy ra) -> chặn lưu
                Ui.warn(this,
                        I18n.msg("Không thể tự điều chỉnh đủ tiền thối. Vui lòng kiểm tra lại các dòng thanh toán.",
                                 "Cannot adjust the change automatically. Please check the payment lines!"),
                        I18n.msg("Lỗi điều chỉnh tiền thối", "Cannot Adjust Change"));
                return;
            }
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

    /** Nhan nut luu -- doc lai moi lan vi doi ngon ngu thi ve lai. */
    private static String saveLabel() {
        return I18n.text("XUẤT HÓA ĐƠN", "CREATE INVOICE");
    }

    private void setSaving(boolean saving) {
        btSave.setEnabled(!saving);
        btSave.setText(saving ? I18n.msg("Đang lưu...", "Saving...") : saveLabel());
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
        spDate.setValue(new Date());
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
        String[] cols = {"Dong", I18n.text("Sản phẩm", "Product"), "SL", "DVT",
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
        tbCart.setToolTipText(I18n.text(
                "Cột VAT % là thuế suất của sản phẩm; đơn giá ĐÃ GỒM VAT.",
                "VAT % is the product tax rate; unit prices already include VAT."));
        refreshTotals();
    }

    /**
     * Năm cột: {@code Khach dua} và {@code Tien thoi} là hai cột mới, trống với
     * thẻ / ví -- ba con số của hợp đồng mục 3.1 nhìn thấy được cùng lúc.
     */
    private void refreshPayTable() {
        String[] cols = {"Phuong thuc", I18n.text("Số tiền", "Amount"), "Khach dua", "Tien thoi", "Chi tiet"};
        Object[][] data = new Object[payments.size()][cols.length];
        for (int i = 0; i < payments.size(); i++) {
            PaymentEntry p = payments.get(i);
            boolean hasTendered = p.tendered != null;
            data[i] = new Object[]{
                    p.methodDisplayLabel(),
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
     *
     * <p>Cũng tự động cập nhật ô Số tiền khi còn thiếu thay đổi:
     * - Nếu đang chọn Thẻ / Ví điện tử: điền số còn thiếu mới (nếu > 0)
     * - Nếu đang chọn Tiền mặt: không tự điền (giữ trống sau reset/thêm thanh toán)
     */
    private void refreshTotals() {
        BigDecimal due    = totalDue();
        BigDecimal paid   = totalRecorded();
        BigDecimal left   = remaining();
        BigDecimal change = totalChange();

        lbTotal.setText(I18n.msg("Tổng: ", "Total: ") + Ui.money(due));
        lbPaid.setText(I18n.msg("Đã thu: ", "Paid: ") + Ui.money(paid));
        lbLeft.setText(I18n.msg("Còn thiếu: ", "Remaining: ") + Ui.money(left));
        lbChange.setText(I18n.msg("Tiền thối: ", "Change: ") + Ui.money(change));
        lbLeft.setForeground(left.signum() > 0 ? Color.RED.darker() : new Color(0, 110, 0));
        btChange.setEnabled(change.signum() > 0);

        // Tự động cập nhật ô Số tiền khi còn thiếu thay đổi (chỉ cho Thẻ / Ví)
        PayMethod m = method();
        if (!m.hasChange()) {
            autoUpdatingAmount = true;
            try {
                if (left.signum() > 0) {
                    tfAmount.setText(Ui.moneyInput(left));
                } else {
                    tfAmount.setText("");
                }
            } finally {
                autoUpdatingAmount = false;
            }
        }
    }
    // =================================================================
    //  DOCUMENT FILTER: chữ số + MỘT dấu , hoặc . (CashRules.QTY_TYPING)
    // =================================================================

    /**
     * Lọc ô số lượng ngay tại tầng Document (gõ, dán, kéo-thả đều qua đây):
     * tối đa 5 chữ số phần nguyên và 3 chữ số phần lẻ. Có cho dấu thập phân
     * ngay cả với sản phẩm tính theo cái -- lúc gõ chưa chắc đã chọn xong
     * sản phẩm; luật "cái thì số nguyên" kiểm ở validateQtyField / khi bấm nút.
     */
    private static final class QuantityDocumentFilter extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String text, javax.swing.text.AttributeSet attr)
                throws javax.swing.text.BadLocationException {
            replace(fb, offset, 0, text, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, javax.swing.text.AttributeSet attrs)
                throws javax.swing.text.BadLocationException {
            javax.swing.text.Document d = fb.getDocument();
            String cur  = d.getText(0, d.getLength());
            String next = cur.substring(0, offset) + (text == null ? "" : text)
                        + cur.substring(offset + length);
            if (CashRules.QTY_TYPING.matcher(next).matches()) {
                super.replace(fb, offset, length, text, attrs);
            } else {
                Toolkit.getDefaultToolkit().beep();
            }
        }
    }
}
