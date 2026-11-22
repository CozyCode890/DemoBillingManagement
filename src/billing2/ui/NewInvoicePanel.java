package billing2.ui;

import billing2.dao.InvoiceDao;
import billing2.dao.LookupDao;
import billing2.dao.ProductDao;
import billing2.model.CartLine;
import billing2.model.CustomerRef;
import billing2.model.IdName;
import billing2.model.PayMethod;
import billing2.model.PaymentEntry;
import billing2.model.Product;
import billing2.util.CashRules;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Tab 1 -- LẬP HÓA ĐƠN, bốn khung:
 *   1. Thông tin hóa đơn: quầy, thu ngân, ngày bán, khách (tìm theo số điện thoại)
 *   2. Thêm sản phẩm: giá theo NGÀY BÁN (fn_get_product_price) + khuyến mãi (sp_get_active_promo)
 *   3. Các dòng hàng -- giỏ chỉ nằm trong RAM cho tới khi lưu
 *   4. Thanh toán -- một hóa đơn có thể trả bằng NHIỀU phương thức
 * Bấm CREATE INVOICE -> InvoiceDao.createInvoice() ghi tất cả trong MỘT transaction.
 */
public class NewInvoicePanel extends JPanel {

    private static final String SAVE = "CREATE INVOICE";

    private final ProductDao productDao = new ProductDao();
    private final LookupDao  lookupDao  = new LookupDao();
    private final InvoiceDao invoiceDao = new InvoiceDao();
    private final Runnable   onSaved;                       // lưu xong -> tab Invoices tải lại

    // Khung 1
    private final JComboBox<IdName> cbCounter = new JComboBox<>();
    private final JComboBox<IdName> cbCashier = new JComboBox<>();
    private final CustomerPicker    customer  = new CustomerPicker(lookupDao);
    private final JSpinner spDate = new JSpinner(new SpinnerDateModel(new Date(), null, new Date(), Calendar.DAY_OF_MONTH));
    private final JButton  btReload = new JButton("Reload");
    // Khung 2 + 3
    private final JComboBox<Product> cbProduct = new JComboBox<>();
    private final JTextField tfQty     = new JTextField("1", 6);
    private final JLabel     lbHint    = new JLabel(" ");
    private final JLabel     lbQtyWarn = new JLabel(" ");
    private final JButton    btAdd     = new JButton("Add to invoice");
    private final JButton    btEditQty = new JButton("Edit quantity");
    private final JButton    btDelLine = new JButton("Remove line");
    private final JTable     tbCart    = Ui.readOnlyTable();
    // Khung 4
    private final JComboBox<PayMethod> cbMethod = new JComboBox<>(PayMethod.values());
    private final JLabel     lbAmount = new JLabel();
    private final JTextField tfAmount = new JTextField(12);
    private final JTable     tbPay    = Ui.readOnlyTable();
    private final JButton    btAddPay = new JButton("Add payment");
    private final JButton    btPayAll = new JButton("Customer pays the rest in cash");
    private final JButton    btDelPay = new JButton("Remove payment");
    private final JButton    btClrPay = new JButton("Clear all");
    private final JButton    btChange = new JButton("Change");
    private final JLabel lbTotal = new JLabel(), lbPaid = new JLabel(), lbLeft = new JLabel(), lbChange = new JLabel();
    private final JButton    btReset  = new JButton("Reset tab");
    private final JButton    btSave   = new JButton(SAVE);

    private final List<CartLine>     cart     = new ArrayList<>();
    private final List<PaymentEntry> payments = new ArrayList<>();
    private final Border qtyBorder = tfQty.getBorder();
    private boolean busy;                                   // đang chờ SQL -> khóa các nút ghi

    public NewInvoicePanel(Runnable onSaved) {
        this.onSaved = onSaved;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
        wireUp();
        loadLookups();
        onMethodChanged();
        refreshCartTable();
        refreshPayTable();
    }

    // =================================================================
    //  BỐ CỤC
    // =================================================================

    private JComponent buildHeader() {
        spDate.setEditor(new JSpinner.DateEditor(spDate, "dd/MM/yyyy"));
        cbCounter.setPreferredSize(new Dimension(260, 26));
        cbCashier.setPreferredSize(new Dimension(220, 26));
        JPanel p = new JPanel(new GridLayout(0, 1));
        p.setBorder(BorderFactory.createTitledBorder("1. Invoice details"));
        p.add(Ui.row(new JLabel("Counter:"), cbCounter, new JLabel("Cashier:"), cbCashier, btReload));
        p.add(Ui.row(new JLabel("Sale date:"), spDate, new JLabel("Customer (phone):"), customer));
        p.add(Ui.row(new JLabel("<html><i>Tip: set the sale date to an earlier day, then add a product"
                + " to use THAT DAY'S price.</i></html>")));
        return p;
    }

    private JComponent buildCenter() {
        cbProduct.setPreferredSize(new Dimension(360, 26));
        tfQty.setToolTipText("<html>Use <b>,</b> or <b>.</b> as the decimal point (up to 3 digits) -- NOT a thousands separator.<br>"
                + "Decimals only for products sold by KG / LITER. At most 10,000 per line. Enter = add.</html>");
        lbHint.setForeground(Ui.GREEN);
        lbQtyWarn.setForeground(new Color(220, 38, 38));
        JPanel bar = new JPanel(new GridLayout(0, 1));
        bar.setBorder(BorderFactory.createTitledBorder("2. Add products"));
        bar.add(Ui.row(new JLabel("Product:"), cbProduct, new JLabel("Quantity:"), tfQty, btAdd, btEditQty, btDelLine));
        bar.add(Ui.row(lbHint));
        bar.add(Ui.row(lbQtyWarn));

        JScrollPane sp = new JScrollPane(tbCart);
        sp.setBorder(BorderFactory.createTitledBorder("3. Items"));
        tbCart.setToolTipText("VAT % is the product tax rate; unit prices already include VAT.");
        JPanel center = new JPanel(new BorderLayout(6, 6));
        center.add(bar, BorderLayout.NORTH);
        center.add(sp, BorderLayout.CENTER);
        return center;
    }

    private JComponent buildFooter() {
        tfAmount.setToolTipText("<html><b>.</b> separates THOUSANDS, <b>,</b> is the decimal separator: "
                + "<code>500.000</code> | <code>1.234,50</code> | <code>500000</code>. Enter = add the payment.</html>");
        JPanel pay = new JPanel(new GridLayout(0, 1));
        pay.setBorder(BorderFactory.createTitledBorder("4. Payment (one invoice can have SEVERAL payment lines)"));
        pay.add(Ui.row(new JLabel("Method:"), cbMethod, lbAmount, tfAmount, Ui.attachMoneyEcho(tfAmount)));
        pay.add(Ui.row(btAddPay, btPayAll, btDelPay, btClrPay, btChange));

        JScrollPane spPay = new JScrollPane(tbPay);
        spPay.setPreferredSize(new Dimension(100, 130));

        lbTotal.setFont(Ui.BIG);
        for (JLabel l : List.of(lbPaid, lbLeft, lbChange)) l.setFont(Ui.BOLD);
        lbChange.setForeground(Ui.GREEN);
        btSave.setFont(Ui.BOLD);
        btSave.setBackground(new Color(25, 118, 210));
        btSave.setForeground(Color.WHITE);
        btSave.setOpaque(true);
        btSave.setBorderPainted(false);
        JPanel totals = new JPanel(new FlowLayout(FlowLayout.RIGHT, 18, 8));
        for (JComponent c : List.of(lbPaid, lbLeft, lbChange, lbTotal, btReset, btSave)) totals.add(c);

        JPanel south = new JPanel(new BorderLayout());
        south.add(pay, BorderLayout.NORTH);
        south.add(spPay, BorderLayout.CENTER);
        south.add(totals, BorderLayout.SOUTH);
        return south;
    }

    private void wireUp() {
        btReload.addActionListener(e -> loadLookups());
        ((AbstractDocument) tfQty.getDocument()).setDocumentFilter(QTY_FILTER);
        tfQty.getDocument().addDocumentListener(Ui.onChange(this::validateQtyField));
        cbProduct.addActionListener(e -> validateQtyField());     // đổi sản phẩm = đổi đơn vị tính
        btAdd.addActionListener(e -> addToCart());
        tfQty.addActionListener(e -> addToCart());                // Enter = thêm
        btEditQty.addActionListener(e -> editQuantity());
        btDelLine.addActionListener(e -> removeSelectedLine());
        tbCart.getSelectionModel().addListSelectionListener(e -> updateButtons());

        cbMethod.addActionListener(e -> onMethodChanged());
        btAddPay.addActionListener(e -> addPaymentFromField());
        tfAmount.addActionListener(e -> addPaymentFromField());   // Enter = thêm thanh toán
        btPayAll.addActionListener(e -> payRestInCash());
        btDelPay.addActionListener(e -> removeSelectedPayment());
        btClrPay.addActionListener(e -> { payments.clear(); refreshPayTable(); });
        btChange.addActionListener(e -> showChangeBreakdown());
        tbPay.getSelectionModel().addListSelectionListener(e -> updateButtons());

        btReset.addActionListener(e -> onResetTab());
        btSave.addActionListener(e -> save());
        updateButtons();
    }

    // =================================================================
    //  KHUNG 1: danh mục (chạy NGOÀI EDT)
    // =================================================================

    private record Lookups(List<IdName> counters, List<IdName> cashiers, List<Product> products) { }

    private void loadLookups() {
        btReload.setEnabled(false);
        Ui.async(this, () -> new Lookups(lookupDao.counters(), lookupDao.cashiers(), productDao.findAll()),
                () -> btReload.setEnabled(true), l -> {
                    refill(cbCounter, l.counters());
                    refill(cbCashier, l.cashiers());
                    refill(cbProduct, l.products());
                });
    }

    private static <T> void refill(JComboBox<T> cb, List<T> items) {
        cb.removeAllItems();
        items.forEach(cb::addItem);
    }

    private LocalDate selectedDate() {
        return ((Date) spDate.getValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    // =================================================================
    //  KHUNG 2: số lượng
    // =================================================================

    /** Chặn ngay khi gõ: chỉ chữ số và MỘT dấu , hoặc . -- gõ sai thì kêu bíp. */
    private static final DocumentFilter QTY_FILTER = new DocumentFilter() {
        @Override public void insertString(FilterBypass fb, int offset, String text, AttributeSet a) throws BadLocationException {
            replace(fb, offset, 0, text, a);
        }
        @Override public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet a) throws BadLocationException {
            String cur = fb.getDocument().getText(0, fb.getDocument().getLength());
            String next = cur.substring(0, offset) + (text == null ? "" : text) + cur.substring(offset + length);
            if (CashRules.QTY_TYPING.matcher(next).matches()) super.replace(fb, offset, length, text, a);
            else Toolkit.getDefaultToolkit().beep();
        }
    };

    private String selectedUnit() {
        Product p = (Product) cbProduct.getSelectedItem();
        return p == null ? null : p.unit;
    }

    /** Kiểm khi đang gõ: sai đơn vị / quá 10.000 -> viền đỏ + dòng cảnh báo, chưa chặn gì. */
    private void validateQtyField() {
        String t = tfQty.getText().trim().replace(',', '.');
        if (t.isEmpty()) { showQtyError(null); return; }
        BigDecimal q;
        try {
            q = new BigDecimal(t);
        } catch (NumberFormatException ex) {
            showQtyError("The quantity cannot be read.");
            return;
        }
        showQtyError(CashRules.quantityProblem(q, selectedUnit()));
    }

    private void showQtyError(String message) {
        tfQty.setBorder(message == null ? qtyBorder : BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 38, 38), 2), BorderFactory.createEmptyBorder(2, 5, 2, 5)));
        lbQtyWarn.setText(message == null ? " " : message);
    }

    /**
     * Đọc + kiểm số lượng trước khi thêm / sửa: định dạng (Ui.readQuantity), rồi luật đơn vị
     * (CashRules). existingQty != null: cộng dồn vào dòng có sẵn -> tổng vẫn phải <= 10.000.
     */
    private BigDecimal readQty(String text, String unit, BigDecimal existingQty, boolean isEdit) {
        BigDecimal q = Ui.readQuantity(this, text, null, unit);
        if (q == null) {
            tfQty.requestFocusInWindow();
            return null;
        }
        String problem = CashRules.quantityProblem(q, unit);
        if (problem == null && existingQty != null && existingQty.add(q).compareTo(CashRules.MAX_LINE_QTY) > 0) {
            problem = "Total quantity (already in cart " + Ui.qty(existingQty) + " + new " + Ui.qty(q)
                    + ") exceeds 10,000 per line.";
        }
        if (problem != null) {
            if (!isEdit) showQtyError(problem);
            Ui.warn(this, problem, "Invalid Quantity");
            tfQty.requestFocusInWindow();
            return null;
        }
        showQtyError(null);
        return q;
    }

    // =================================================================
    //  KHUNG 2: giá theo ngày + khuyến mãi -> dòng hàng
    // =================================================================

    /** Giá + khuyến mãi của sản phẩm TẠI ngày bán (2 câu SQL). price == null: chưa có giá trước ngày đó. */
    private record Priced(BigDecimal price, BigDecimal pct, String note) { }

    private Priced priceAt(Product p, LocalDate date) {
        BigDecimal price = productDao.priceOn(p.barcode, date);
        if (price == null) return new Priced(null, BigDecimal.ZERO, "");
        Object[] promo = productDao.activePromoOn(p.barcode, date);
        if (promo == null || promo[1] == null) return new Priced(price, BigDecimal.ZERO, "");
        BigDecimal pct = (BigDecimal) promo[1];
        return new Priced(price, pct, promo[0] + " (-" + pct.stripTrailingZeros().toPlainString() + "%)");
    }

    /** Giảm giá của dòng = SL x đơn giá x % / 100, làm tròn 2 số lẻ. */
    private static CartLine priceLine(Product p, BigDecimal qty, Priced pr) {
        BigDecimal discount = pr.pct().signum() == 0 ? BigDecimal.ZERO
                : qty.multiply(pr.price()).multiply(pr.pct()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        return new CartLine(p, qty, pr.price(), discount, pr.note());
    }

    private CartLine findLine(String barcode) {
        for (CartLine l : cart) if (l.product.barcode.equals(barcode)) return l;
        return null;
    }

    /**
     * Thêm vào giỏ. Cùng sản phẩm + CÙNG đơn giá -> cộng dồn vào dòng cũ;
     * khác đơn giá (đổi ngày bán sang ngày có giá khác) -> thêm dòng mới.
     */
    private void addToCart() {
        Product p = (Product) cbProduct.getSelectedItem();
        if (p == null) {
            Ui.warn(this, "Please select a product!\nIf the list is empty, click \"Reload\" in section 1.", "No Product Selected");
            return;
        }
        CartLine existing = findLine(p.barcode);
        BigDecimal qty = readQty(tfQty.getText(), p.unit, existing == null ? null : existing.quantity, false);
        if (qty == null) return;

        LocalDate date = selectedDate();
        setBusy(true);
        Ui.async(this, () -> priceAt(p, date), () -> setBusy(false), pr -> {
            if (pr.price() == null) {
                Ui.warn(this, "This product has no price in effect before " + date + ".\nPlease pick a later date!", "No Price on Sale Date");
                return;
            }
            CartLine same = findLine(p.barcode);           // đọc lại: giỏ có thể đổi trong lúc chờ SQL
            String extra = "";
            if (same != null && pr.price().compareTo(same.unitPrice) == 0) {
                int i = cart.indexOf(same);
                CartLine merged = priceLine(p, same.quantity.add(qty), pr);
                if (exceedsMoneyCap(merged, i)) return;
                cart.set(i, merged);
            } else {
                CartLine added = priceLine(p, qty, pr);
                if (exceedsMoneyCap(added, -1)) return;
                cart.add(added);
                if (same != null) {
                    extra = "  The price on " + date + " (" + Ui.money(pr.price()) + ") differs from the existing line ("
                          + Ui.money(same.unitPrice) + ") -> added a new line.";
                }
            }
            hint(date, pr, extra);
            refreshCartTable();
            tfQty.setText("1");
            tfQty.requestFocusInWindow();
        });
    }

    /** Sửa số lượng dòng đang chọn -- giá và khuyến mãi được tính lại theo ngày bán đang chọn. */
    private void editQuantity() {
        int row = tbCart.getSelectedRow();
        if (row < 0 || row >= cart.size()) {
            Ui.warn(this, "Please select a line to edit!", "No Line Selected");
            return;
        }
        CartLine line = cart.get(row);
        JTextField field = new JTextField(Ui.qtyInput(line.quantity), 6);
        ((AbstractDocument) field.getDocument()).setDocumentFilter(QTY_FILTER);
        String rule = CashRules.allowsDecimal(line.product.unit)
                ? "Use , or . as the decimal separator, up to 3 digits." : "Whole numbers only (not sold by weight / volume).";
        if (!Ui.ask(this, "Edit Quantity", field, "New quantity for \"" + line.product.name + "\" (" + line.product.unit + "):",
                rule + " At most 10,000.", field)) return;
        BigDecimal newQty = readQty(field.getText(), line.product.unit, null, true);
        if (newQty == null) return;

        LocalDate date = selectedDate();
        setBusy(true);
        Ui.async(this, () -> priceAt(line.product, date), () -> setBusy(false), pr -> {
            if (pr.price() == null) {
                Ui.warn(this, "This product has no price in effect before " + date + ".", "No Price on Sale Date");
                return;
            }
            int i = cart.indexOf(line);
            if (i < 0) return;                              // dòng đã bị xóa trong lúc chờ
            CartLine edited = priceLine(line.product, newQty, pr);
            if (exceedsMoneyCap(edited, i)) return;
            cart.set(i, edited);
            hint(date, pr, "");
            refreshCartTable();
            tbCart.setRowSelectionInterval(i, i);
            recalculatePaymentsIfNeeded();
        });
    }

    private void removeSelectedLine() {
        int r = tbCart.getSelectedRow();
        if (r < 0 || r >= cart.size()) return;
        cart.remove(r);
        refreshCartTable();
        recalculatePaymentsIfNeeded();
    }

    /** Tổng hóa đơn không được vượt trần DECIMAL(12,2). replaceIndex = dòng sẽ bị thay (-1: thêm mới). */
    private boolean exceedsMoneyCap(CartLine line, int replaceIndex) {
        BigDecimal t = line.lineTotal();
        for (int i = 0; i < cart.size(); i++) if (i != replaceIndex) t = t.add(cart.get(i).lineTotal());
        if (t.compareTo(CashRules.MAX_MONEY) <= 0) return false;
        Ui.warn(this, "The invoice total would be " + Ui.money(t) + ", over the limit for one invoice\n("
                + Ui.money(CashRules.MAX_MONEY) + "). Please split it into several invoices!", "Total Too Large");
        return true;
    }

    private void hint(LocalDate date, Priced pr, String extra) {
        lbHint.setText("Price on " + date + " = " + Ui.money(pr.price())
                + (pr.note().isEmpty() ? "  (no promotion)" : "  " + pr.note()) + extra);
    }

    // =================================================================
    //  KHUNG 4: thanh toán
    // =================================================================

    private PayMethod method() {
        PayMethod m = (PayMethod) cbMethod.getSelectedItem();
        return m == null ? PayMethod.CASH : m;
    }

    /** Tiền mặt: ô trống để gõ tiền khách ĐƯA. Thẻ / ví: điền sẵn số còn thiếu. */
    private void onMethodChanged() {
        lbAmount.setText(method().amountFieldLabel() + ":");
        tfAmount.setText(!method().hasChange() && remaining().signum() > 0 ? Ui.moneyInput(remaining()) : "");
    }

    private BigDecimal totalDue() {
        BigDecimal t = BigDecimal.ZERO;
        for (CartLine c : cart) t = t.add(c.lineTotal());
        return t;
    }

    private BigDecimal totalRecorded() {
        BigDecimal t = BigDecimal.ZERO;
        for (PaymentEntry p : payments) t = t.add(p.amount);
        return t;
    }

    private BigDecimal totalChange() {
        BigDecimal t = BigDecimal.ZERO;
        for (PaymentEntry p : payments) t = t.add(p.change());
        return t;
    }

    private BigDecimal remaining() {
        BigDecimal r = totalDue().subtract(totalRecorded());
        return r.signum() < 0 ? BigDecimal.ZERO : r;
    }

    private void addPaymentFromField() {
        BigDecimal typed = Ui.readMoney(this, tfAmount.getText(), method().amountFieldLabel());
        if (typed != null && addPayment(method(), typed)) {
            tfAmount.setText("");
            tfAmount.requestFocusInWindow();
        }
    }

    /** Một lần thanh toán đã tách: tiền GHI NHẬN và tiền khách ĐƯA (null với thẻ / ví). */
    record Split(BigDecimal recorded, BigDecimal tendered) { }

    /**
     * HỢP ĐỒNG TIỀN MẶT:
     *   Tiền mặt:  recorded = min(số gõ, còn thiếu) ; tendered = số gõ   (thừa thì thối)
     *   Thẻ / ví:  recorded = số gõ ; tendered = null ; vượt số còn thiếu -> null = TỪ CHỐI
     */
    static Split splitPayment(PayMethod m, BigDecimal typed, BigDecimal remaining) {
        if (m.hasChange()) return new Split(typed.min(remaining), typed);
        return typed.compareTo(remaining) > 0 ? null : new Split(typed, null);
    }

    private boolean addPayment(PayMethod m, BigDecimal typed) {
        if (cart.isEmpty()) {
            Ui.warn(this, "The invoice has no items yet, so there is nothing to collect!", "No Items Yet");
            return false;
        }
        BigDecimal left = remaining();
        if (left.signum() <= 0) {
            Ui.warn(this, "This invoice is fully paid (" + Ui.money(totalDue()) + ").\nNo more payments are needed.", "Fully Paid");
            return false;
        }
        Split split = splitPayment(m, typed, left);
        if (split == null) {
            Ui.warn(this, m.label() + " " + Ui.money(typed) + " is more than the remaining " + Ui.money(left) + ".\n\n"
                    + m.label() + " cannot give change.\n\nPlease enter exactly " + Ui.money(left)
                    + ", or choose \"Cash\" if the customer pays more!", "Amount Too High");
            return false;
        }
        if (m.hasChange()) {                                    // khách đưa dư có hợp lý không?
            CashRules.TenderCheck tc = CashRules.checkTendered(typed, left);
            if (tc.verdict() == CashRules.Verdict.REJECT) {
                Ui.warn(this, tc.message(), "Unreasonable Amount");
                return false;
            }
            if (tc.verdict() == CashRules.Verdict.SUSPICIOUS) {
                Object[] opts = {"Re-enter amount", "Record anyway"};
                if (JOptionPane.showOptionDialog(this, tc.message() + "\nDid the cashier mistype?", "Extra Banknote?",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, opts, opts[0]) != 1) return false;
            }
        }
        String d1 = null, d2 = null;
        if (m == PayMethod.CARD) {
            String[] card = askCardDetails(split.recorded());
            if (card == null) return false;                     // người dùng hủy
            d1 = card[0];
            d2 = card[1];
        } else if (m == PayMethod.EWALLET) {
            d1 = "MoMo";
            d2 = "TXN-" + System.currentTimeMillis();
        }
        payments.add(new PaymentEntry(m.code(), split.recorded().setScale(2, RoundingMode.HALF_UP),
                split.tendered() == null ? null : split.tendered().setScale(2, RoundingMode.HALF_UP), d1, d2));
        refreshPayTable();
        return true;
    }

    /** Hỏi 4 số cuối + mã chuẩn chi in trên phiếu máy POS; sai thì hỏi lại. @return null = hủy */
    private String[] askCardDetails(BigDecimal amount) {
        JTextField last4 = new JTextField(6), auth = new JTextField(8);
        while (Ui.ask(this, "Bank Card Details", last4,
                "Swipe " + Ui.money(amount) + " on the POS terminal, then copy from its slip:",
                "Last 4 card digits:", last4, "Authorization code (6 characters, 'APPR CODE' on the slip):", auth)) {
            String l4 = last4.getText().trim(), code = auth.getText().trim().toUpperCase();
            String problem = CashRules.cardDetailProblem(l4, code);
            if (problem == null) return new String[]{CashRules.maskedCard(l4), code};
            Ui.warn(this, problem, "Invalid Card Details");
        }
        return null;
    }

    private void payRestInCash() {
        if (cart.isEmpty()) {
            Ui.warn(this, "The invoice has no items yet!", "No Items Yet");
        } else if (remaining().signum() <= 0) {
            Ui.warn(this, "This invoice is fully paid.", "Fully Paid");
        } else {
            addPayment(PayMethod.CASH, remaining());
        }
    }

    private void removeSelectedPayment() {
        int r = tbPay.getSelectedRow();
        if (r < 0 || r >= payments.size()) return;
        payments.remove(r);
        refreshPayTable();
    }

    /** Nút "Change": tách tiền thối thành các tờ. */
    private void showChangeBreakdown() {
        BigDecimal change = totalChange();
        if (change.signum() <= 0) {
            Ui.warn(this, "There is no change to give.", "Change");
            return;
        }
        StringBuilder sb = new StringBuilder("Change for the customer: " + Ui.money(change) + "\n\n");
        for (long[] part : CashRules.breakdown(change)) {
            sb.append(String.format("  %10s  x %d%n", Ui.money(BigDecimal.valueOf(part[0])), part[1]));
        }
        BigDecimal rest = CashRules.breakdownRemainder(change);
        if (rest.signum() > 0) {
            sb.append("\n  ").append(Ui.money(rest)).append(" is below the smallest note (100 VND) -- round it with the customer.");
        }
        JTextArea ta = new JTextArea(sb.toString());
        ta.setEditable(false);
        ta.setFont(Ui.MONO);
        JOptionPane.showMessageDialog(this, ta, "Change", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Tổng hóa đơn GIẢM sau khi sửa / xóa dòng mà đã thu nhiều hơn: bớt phần thừa trên các dòng
     * TIỀN MẶT từ dưới lên (tiền khách đưa giữ nguyên -> tiền thối tăng). Chỉ có thẻ / ví thì
     * không có đường thối -> báo để thu ngân "Clear all" rồi nhập lại.
     */
    private void recalculatePaymentsIfNeeded() {
        BigDecimal excess = totalRecorded().subtract(totalDue());
        if (excess.signum() <= 0) return;
        if (!hasCashPayment()) {
            Ui.warn(this, "The new invoice total is less than the amount already paid by card/e-wallet.\n\n"
                    + "Please click \"Clear all\" in the payment section and enter the payments again!", "Cannot Adjust Change");
            return;
        }
        for (int i = payments.size() - 1; i >= 0 && excess.signum() > 0; i--) {
            PaymentEntry p = payments.get(i);
            if (!PayMethod.CASH.code().equals(p.method)) continue;
            BigDecimal cut = p.amount.min(excess);
            p.amount = p.amount.subtract(cut).setScale(2, RoundingMode.HALF_UP);
            excess = excess.subtract(cut);
        }
        refreshPayTable();
    }

    private boolean hasCashPayment() {
        for (PaymentEntry p : payments) if (PayMethod.CASH.code().equals(p.method)) return true;
        return false;
    }

    // =================================================================
    //  LƯU
    // =================================================================

    /** Kiểm ở giao diện (đủ dòng, đủ tiền) rồi gọi createInvoice NGOÀI EDT; xong thì in hóa đơn. */
    private void save() {
        if (cart.isEmpty())     { Ui.warn(this, "The invoice has no items yet!", "No Items Yet"); return; }
        if (payments.isEmpty()) { Ui.warn(this, "Please add a payment first!", "No Payments"); return; }
        IdName counter = (IdName) cbCounter.getSelectedItem();
        IdName cashier = (IdName) cbCashier.getSelectedItem();
        if (counter == null || cashier == null) {
            Ui.warn(this, "The counter / cashier lists have not loaded.\n"
                    + "Please check the connection, then click \"Reload\" in section 1!", "Missing Information");
            return;
        }
        BigDecimal due = totalDue(), paid = totalRecorded();
        if (paid.compareTo(due) < 0) {
            Ui.warn(this, "The invoice total is " + Ui.money(due) + " but only " + Ui.money(paid) + " has been paid ("
                    + Ui.money(due.subtract(paid)) + " remaining).\nPlease collect the full amount before creating the invoice!",
                    "Payment Incomplete");
            return;
        }
        if (paid.compareTo(due) > 0) {
            if (!hasCashPayment()) {
                Ui.warn(this, "Total payments (" + Ui.money(paid) + ") exceed the invoice total (" + Ui.money(due) + ").\n\n"
                        + "Card/e-wallet payments give no change, so please click \"Clear all\"\n"
                        + "in the payment section and enter the payments again!", "Overpayment");
                return;
            }
            recalculatePaymentsIfNeeded();
            if (totalRecorded().compareTo(due) > 0) {
                Ui.warn(this, "Cannot adjust the change automatically. Please check the payment lines!", "Cannot Adjust Change");
                return;
            }
        }

        CustomerRef cus = customer.getSelected();               // null = khách vãng lai
        LocalDate date = selectedDate();
        LocalTime time = LocalTime.now().withNano(0);
        List<CartLine> lines = new ArrayList<>(cart);
        List<PaymentEntry> pays = new ArrayList<>(payments);
        setBusy(true);
        btSave.setText("Saving...");
        Ui.async(this,
                () -> invoiceDao.createInvoice(null, date, time, counter.id, cashier.id, cus == null ? null : cus.id, lines, pays),
                () -> { setBusy(false); btSave.setText(SAVE); },
                id -> {
                    ReceiptDialog.show(this, invoiceDao, id);  // đọc LẠI từ database rồi in
                    resetForm();
                    if (onSaved != null) onSaved.run();
                });
    }

    private void onResetTab() {
        if (hasPendingWork() && JOptionPane.showConfirmDialog(this,
                "Clear the New invoice tab?\nUnsaved " + pendingWorkSummary() + " will be lost.", "Reset Tab",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        resetForm();
        spDate.setValue(new Date());
        cbMethod.setSelectedItem(PayMethod.CASH);
        showQtyError(null);
    }

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

    /** Cho nút Log out hỏi trước khi bỏ giỏ hàng chưa lưu. */
    public boolean hasPendingWork() { return !cart.isEmpty() || !payments.isEmpty(); }

    public String pendingWorkSummary() {
        List<String> parts = new ArrayList<>();
        if (!cart.isEmpty())     parts.add(cart.size() + " item line(s)");
        if (!payments.isEmpty()) parts.add(payments.size() + " payment(s)");
        return String.join(", ", parts);
    }

    // =================================================================
    //  VẼ LẠI
    // =================================================================

    private void setBusy(boolean b) {
        busy = b;
        setCursor(Cursor.getPredefinedCursor(b ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
        updateButtons();
    }

    private void updateButtons() {
        boolean line = tbCart.getSelectedRow() >= 0 && tbCart.getSelectedRow() < cart.size();
        boolean pay  = tbPay.getSelectedRow() >= 0 && tbPay.getSelectedRow() < payments.size();
        btAdd.setEnabled(!busy);
        btEditQty.setEnabled(!busy && line);
        btDelLine.setEnabled(!busy && line);
        btAddPay.setEnabled(!busy);
        btPayAll.setEnabled(!busy);
        btDelPay.setEnabled(!busy && pay);
        btSave.setEnabled(!busy);
    }

    private void refreshCartTable() {
        String[] cols = {"Line", "Product", "Qty", "Unit", "Unit price", "Discount", "Line total", "VAT %"};
        Object[][] data = new Object[cart.size()][];
        for (int i = 0; i < cart.size(); i++) {
            CartLine c = cart.get(i);
            data[i] = new Object[]{i + 1, c.product.name, Ui.qty(c.quantity), c.product.unit, Ui.money(c.unitPrice),
                                   Ui.money(c.discount), Ui.money(c.lineTotal()), Ui.percent(c.product.taxRate)};
        }
        ((DefaultTableModel) tbCart.getModel()).setDataVector(data, cols);
        Ui.autoSize(tbCart);
        refreshTotals();
    }

    private void refreshPayTable() {
        String[] cols = {"Method", "Amount", "Tendered", "Change", "Details"};
        Object[][] data = new Object[payments.size()][];
        for (int i = 0; i < payments.size(); i++) {
            PaymentEntry p = payments.get(i);
            boolean cash = p.tendered != null;
            data[i] = new Object[]{p.methodLabel(), Ui.money(p.amount), cash ? Ui.money(p.tendered) : "",
                                   cash ? Ui.money(p.change()) : "",
                                   (p.detail1 == null ? "" : p.detail1) + (p.detail2 == null ? "" : " / " + p.detail2)};
        }
        ((DefaultTableModel) tbPay.getModel()).setDataVector(data, cols);
        Ui.autoSize(tbPay);
        refreshTotals();
    }

    private void refreshTotals() {
        BigDecimal left = remaining(), change = totalChange();
        lbTotal.setText("Total: " + Ui.money(totalDue()));
        lbPaid.setText("Paid: " + Ui.money(totalRecorded()));
        lbLeft.setText("Remaining: " + Ui.money(left));
        lbChange.setText("Change: " + Ui.money(change));
        lbLeft.setForeground(left.signum() > 0 ? Color.RED.darker() : Ui.GREEN);
        btChange.setEnabled(change.signum() > 0);
        if (!method().hasChange()) tfAmount.setText(left.signum() > 0 ? Ui.moneyInput(left) : "");
        updateButtons();
    }
}
