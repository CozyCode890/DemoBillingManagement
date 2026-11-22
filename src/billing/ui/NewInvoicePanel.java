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
import javax.swing.table.DefaultTableModel;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class NewInvoicePanel extends JPanel {
    private static String saveLabel() { return I18n.text("LƯU HÓA ĐƠN", "CREATE INVOICE"); }

    private final ProductDao productDao = new ProductDao();
    private final LookupDao  lookupDao  = new LookupDao();
    private final InvoiceDao invoiceDao = new InvoiceDao();
    private final Runnable   onSaved;

    private final JComboBox<IdName> cbCounter = new JComboBox<>();
    private final JComboBox<IdName> cbCashier = new JComboBox<>();
    private final CustomerPicker    customer  = new CustomerPicker(lookupDao);
    private final JLabel   lbDate   = new JLabel(I18n.msg("(đang tải...)", "(loading...)"));
    private final JButton  btReload = new JButton(I18n.text("Tải lại", "Reload"));
    private final JComboBox<Product> cbProduct = new JComboBox<>();
    private final JTextField tfQty     = new JTextField("1", 6);
    private final JLabel     lbHint    = new JLabel(" ");
    private final JLabel     lbQtyWarn = new JLabel(" ");
    private final JButton    btAdd     = new JButton(I18n.text("Thêm vào hóa đơn", "Add to invoice"));
    private final JButton    btEditQty = new JButton(I18n.text("Sửa số lượng", "Edit quantity"));
    private final JButton    btDelLine = new JButton(I18n.text("Xóa dòng", "Remove line"));
    private final JTable     tbCart    = Ui.readOnlyTable();
    private final JComboBox<PayMethod> cbMethod = new JComboBox<>(PayMethod.values());
    private final JLabel     lbAmount = new JLabel();
    private final JTextField tfAmount = new JTextField(12);
    private final JTable     tbPay    = Ui.readOnlyTable();
    private final JButton    btAddPay = new JButton(I18n.text("Thêm thanh toán", "Add payment"));
    private final JButton    btPayAll = new JButton(I18n.text("Khách trả nốt bằng tiền mặt", "Customer pays the rest in cash"));
    private final JButton    btDelPay = new JButton(I18n.text("Xóa thanh toán", "Remove payment"));
    private final JButton    btClrPay = new JButton(I18n.text("Xóa hết", "Clear all"));
    private final JButton    btChange = new JButton(I18n.text("Thối tiền", "Change"));
    private final JButton    btQr     = new JButton(I18n.text("Tạo mã QR", "Create QR code"));
    private final JLabel lbTotal = new JLabel(), lbPaid = new JLabel(), lbLeft = new JLabel(), lbChange = new JLabel();
    private final JButton    btReset  = new JButton(I18n.text("Làm mới tab", "Reset tab"));
    private final JButton    btSave   = new JButton(saveLabel());

    private final List<CartLine>     cart     = new ArrayList<>();
    private final List<PaymentEntry> payments = new ArrayList<>();
    private final Border qtyBorder = tfQty.getBorder();
    private boolean busy;

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

        for (JLabel l : List.of(lbDate, lbHint, lbQtyWarn, lbAmount, lbTotal, lbPaid, lbLeft, lbChange)) {
            I18n.markDynamicText(l);
        }
        I18n.onLanguageChange(this, () -> {
            if (lastDate != null) showDate(lastDate);
            lbHint.setText(" ");
            validateQtyField();
            lbAmount.setText(method().amountFieldLabel() + ":");
            cbMethod.repaint();
            refreshCartTable();
            refreshPayTable();
        });
    }

    private JComponent buildHeader() {
        cbCounter.setPreferredSize(new Dimension(260, 26));
        cbCashier.setPreferredSize(new Dimension(220, 26));
        lbDate.setFont(Ui.BOLD);
        lbDate.setToolTipText(I18n.text("Ngày giờ hóa đơn luôn lấy theo đồng hồ của máy chủ, không theo máy này.",
                "The invoice date and time always come from the server clock, not this computer."));
        JPanel p = new JPanel(new GridLayout(0, 1));
        p.setBorder(BorderFactory.createTitledBorder(I18n.text("1. Thông tin hóa đơn", "1. Invoice details")));
        p.add(Ui.row(new JLabel(I18n.text("Quầy:", "Counter:")), cbCounter,
                     new JLabel(I18n.text("Thu ngân:", "Cashier:")), cbCashier, btReload));
        p.add(Ui.row(new JLabel(I18n.text("Ngày bán:", "Sale date:")), lbDate,
                     new JLabel(I18n.text("   Khách hàng (SĐT):", "   Customer (phone):")), customer));
        p.add(Ui.row(new JLabel(I18n.text(
                "<html><i>Ngày giờ, đơn giá và khuyến mãi theo đồng hồ MÁY CHỦ. Hóa đơn cũ giữ giá cũ -- xem tab"
              + " Xem hóa đơn và Báo cáo.</i></html>",
                "<html><i>Date, time, unit prices and promotions follow the SERVER clock."
              + " Old invoices keep their old prices -- see the Invoices and Reports tabs.</i></html>"))));
        return p;
    }

    private JComponent buildCenter() {
        cbProduct.setPreferredSize(new Dimension(360, 26));
        tfQty.setToolTipText(I18n.text(
                "<html>Dấu <b>,</b> hoặc <b>.</b> là dấu thập phân (tối đa 3 chữ số) -- KHÔNG phải dấu tách nghìn.<br>"
              + "Chỉ hàng bán theo KG / LITER mới có số lẻ. Tối đa 10.000 mỗi dòng. Enter = thêm.</html>",
                "<html>Use <b>,</b> or <b>.</b> as the decimal point (up to 3 digits) -- NOT a thousands separator.<br>"
              + "Decimals only for products sold by KG / LITER. At most 10,000 per line. Enter = add.</html>"));
        lbHint.setForeground(Ui.GREEN);
        lbQtyWarn.setForeground(new Color(220, 38, 38));
        JPanel bar = new JPanel(new GridLayout(0, 1));
        bar.setBorder(BorderFactory.createTitledBorder(I18n.text("2. Thêm sản phẩm", "2. Add products")));
        bar.add(Ui.row(new JLabel(I18n.text("Sản phẩm:", "Product:")), cbProduct,
                       new JLabel(I18n.text("Số lượng:", "Quantity:")), tfQty, btAdd, btEditQty, btDelLine));
        bar.add(Ui.row(lbHint));
        bar.add(Ui.row(lbQtyWarn));

        JScrollPane sp = new JScrollPane(tbCart);
        sp.setBorder(BorderFactory.createTitledBorder(I18n.text("3. Các dòng hàng", "3. Items")));
        tbCart.setToolTipText(I18n.text("VAT % là thuế suất của sản phẩm; đơn giá đã gồm VAT.",
                                        "VAT % is the product tax rate; unit prices already include VAT."));
        JPanel center = new JPanel(new BorderLayout(6, 6));
        center.add(bar, BorderLayout.NORTH);
        center.add(sp, BorderLayout.CENTER);
        return center;
    }

    private JComponent buildFooter() {
        tfAmount.setToolTipText(I18n.text(
                "<html>Dấu <b>.</b> tách HÀNG NGHÌN, dấu <b>,</b> là dấu thập phân: "
              + "<code>500.000</code> | <code>1.234,50</code> | <code>500000</code>. Enter = thêm thanh toán.</html>",
                "<html><b>.</b> separates THOUSANDS, <b>,</b> is the decimal separator: "
              + "<code>500.000</code> | <code>1.234,50</code> | <code>500000</code>. Enter = add the payment.</html>"));
        cbMethod.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                    boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof PayMethod m) setText(m.displayLabel());
                return this;
            }
        });
        btQr.setToolTipText(I18n.text("Mã VietQR cho số tiền trong ô -- chỉ dùng với Ví điện tử",
                                      "VietQR code for the amount in the field -- e-wallet only"));
        btQr.setVisible(QrPaymentDialog.isEnabled());
        JPanel pay = new JPanel(new GridLayout(0, 1));
        pay.setBorder(BorderFactory.createTitledBorder(I18n.text("4. Thanh toán (một hóa đơn có thể có NHIỀU dòng thanh toán)",
                "4. Payment (one invoice can have SEVERAL payment lines)")));
        pay.add(Ui.row(new JLabel(I18n.text("Phương thức:", "Method:")), cbMethod, lbAmount, tfAmount,
                       Ui.attachMoneyEcho(tfAmount), btQr));
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
        cbProduct.addActionListener(e -> validateQtyField());
        btAdd.addActionListener(e -> addToCart());
        tfQty.addActionListener(e -> addToCart());
        btEditQty.addActionListener(e -> editQuantity());
        btDelLine.addActionListener(e -> removeSelectedLine());
        tbCart.getSelectionModel().addListSelectionListener(e -> updateButtons());

        cbMethod.addActionListener(e -> onMethodChanged());
        btAddPay.addActionListener(e -> addPaymentFromField());
        tfAmount.addActionListener(e -> addPaymentFromField());
        btPayAll.addActionListener(e -> payRestInCash());
        btDelPay.addActionListener(e -> removeSelectedPayment());
        btClrPay.addActionListener(e -> { payments.clear(); refreshPayTable(); });
        btChange.addActionListener(e -> showChangeBreakdown());
        btQr.addActionListener(e -> showQr());
        tbPay.getSelectionModel().addListSelectionListener(e -> updateButtons());

        btReset.addActionListener(e -> onResetTab());
        btSave.addActionListener(e -> save());
        updateButtons();
    }

    private record Lookups(List<IdName> counters, List<IdName> cashiers, List<Product> products, LocalDate today) { }

    private void loadLookups() {
        btReload.setEnabled(false);
        Ui.async(this, () -> new Lookups(lookupDao.counters(), lookupDao.cashiers(), productDao.findAll(),
                                         lookupDao.serverDate()),
                () -> btReload.setEnabled(true), l -> {
                    refill(cbCounter, l.counters());
                    refill(cbCashier, l.cashiers());
                    refill(cbProduct, l.products());
                    showDate(l.today());
                });
    }

    private static <T> void refill(JComboBox<T> cb, List<T> items) {
        cb.removeAllItems();
        items.forEach(cb::addItem);
    }

    private LocalDate lastDate;

    private void showDate(LocalDate d) {
        lastDate = d;
        lbDate.setText(d.toString() + I18n.msg("  (theo máy chủ)", "  (server clock)"));
    }

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

    private void validateQtyField() {
        String t = tfQty.getText().trim().replace(',', '.');
        if (t.isEmpty()) { showQtyError(null); return; }
        BigDecimal q;
        try {
            q = new BigDecimal(t);
        } catch (NumberFormatException ex) {
            showQtyError(I18n.msg("Không đọc được số lượng.", "The quantity cannot be read."));
            return;
        }
        showQtyError(CashRules.quantityProblem(q, selectedUnit()));
    }

    private void showQtyError(String message) {
        tfQty.setBorder(message == null ? qtyBorder : BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 38, 38), 2), BorderFactory.createEmptyBorder(2, 5, 2, 5)));
        lbQtyWarn.setText(message == null ? " " : message);
    }

    private BigDecimal readQty(String text, String unit, BigDecimal existingQty, boolean isEdit) {
        BigDecimal q = Ui.readQuantity(this, text, null, unit);
        if (q == null) {
            tfQty.requestFocusInWindow();
            return null;
        }
        String problem = CashRules.quantityProblem(q, unit);
        if (problem == null && existingQty != null && existingQty.add(q).compareTo(CashRules.MAX_LINE_QTY) > 0) {
            problem = tooMuch(existingQty, q);
        }
        if (problem != null) {
            if (!isEdit) showQtyError(problem);
            Ui.warn(this, problem, I18n.msg("Số lượng không hợp lệ", "Invalid Quantity"));
            tfQty.requestFocusInWindow();
            return null;
        }
        showQtyError(null);
        return q;
    }

    private static String tooMuch(BigDecimal inCart, BigDecimal added) {
        return I18n.msg("Tổng số lượng (đã có trong giỏ " + Ui.qty(inCart) + " + thêm " + Ui.qty(added)
                        + ") vượt 10.000 mỗi dòng.",
                        "Total quantity (already in cart " + Ui.qty(inCart) + " + new " + Ui.qty(added)
                        + ") exceeds 10,000 per line.");
    }

    private record Priced(LocalDate date, BigDecimal price, BigDecimal pct, String note) { }

    private Priced priceAt(Product p, LocalDate date) {
        BigDecimal price = productDao.priceOn(p.barcode, date);
        if (price == null) return new Priced(date, null, BigDecimal.ZERO, "");
        Object[] promo = productDao.activePromoOn(p.barcode, date);
        if (promo == null || promo[1] == null) return new Priced(date, price, BigDecimal.ZERO, "");
        BigDecimal pct = (BigDecimal) promo[1];
        return new Priced(date, price, pct, promo[0] + " (-" + pct.stripTrailingZeros().toPlainString() + "%)");
    }

    private Priced priceToday(Product p) {
        return priceAt(p, lookupDao.serverDate());
    }

    static BigDecimal lineDiscount(BigDecimal qty, BigDecimal price, BigDecimal pct) {
        if (pct == null || pct.signum() == 0) return BigDecimal.ZERO;
        return qty.multiply(price).multiply(pct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    private static CartLine priceLine(Product p, BigDecimal qty, Priced pr) {
        return new CartLine(p, qty, pr.price(), lineDiscount(qty, pr.price(), pr.pct()), pr.note());
    }

    private CartLine findLine(String barcode) {
        for (CartLine l : cart) if (l.product.barcode.equals(barcode)) return l;
        return null;
    }

    private void addToCart() {
        Product p = (Product) cbProduct.getSelectedItem();
        if (p == null) {
            Ui.warn(this, I18n.msg("Vui lòng chọn sản phẩm!\nNếu danh sách trống, bấm \"Tải lại\" ở khung 1.",
                                   "Please select a product!\nIf the list is empty, click \"Reload\" in section 1."),
                    I18n.msg("Chưa chọn sản phẩm", "No Product Selected"));
            return;
        }
        CartLine existing = findLine(p.barcode);
        BigDecimal qty = readQty(tfQty.getText(), p.unit, existing == null ? null : existing.quantity, false);
        if (qty == null) return;

        setBusy(true);
        Ui.async(this, () -> priceToday(p), () -> setBusy(false), pr -> {
            showDate(pr.date());
            if (pr.price() == null) {
                Ui.warn(this, I18n.msg("Sản phẩm này chưa có giá hiệu lực vào ngày " + pr.date() + ".",
                                       "This product has no price in effect on " + pr.date() + "."),
                        I18n.msg("Chưa có giá hôm nay", "No Price Today"));
                return;
            }
            CartLine same = findLine(p.barcode);
            String extra = "";
            if (same != null) {
                int i = cart.indexOf(same);
                if (same.quantity.add(qty).compareTo(CashRules.MAX_LINE_QTY) > 0) {
                    Ui.warn(this, tooMuch(same.quantity, qty), I18n.msg("Số lượng không hợp lệ", "Invalid Quantity"));
                    return;
                }
                CartLine merged = priceLine(p, same.quantity.add(qty), pr);
                if (exceedsMoneyCap(merged, i)) return;
                cart.set(i, merged);
                extra = I18n.msg("  -> cộng vào dòng " + (i + 1) + " (nay " + Ui.qty(merged.quantity) + " " + p.unit + ")",
                                 "  -> added to line " + (i + 1) + " (now " + Ui.qty(merged.quantity) + " " + p.unit + ")");
                if (pr.price().compareTo(same.unitPrice) != 0) {
                    extra += I18n.msg(", tính lại từ giá ", ", re-priced from ") + Ui.money(same.unitPrice);
                }
            } else {
                CartLine added = priceLine(p, qty, pr);
                if (exceedsMoneyCap(added, -1)) return;
                cart.add(added);
            }
            hint(pr, extra);
            refreshCartTable();
            recalculatePaymentsIfNeeded();
            tfQty.setText("1");
            tfQty.requestFocusInWindow();
        });
    }

    private void editQuantity() {
        int row = tbCart.getSelectedRow();
        if (row < 0 || row >= cart.size()) {
            Ui.warn(this, I18n.msg("Vui lòng chọn một dòng để sửa!", "Please select a line to edit!"),
                    I18n.msg("Chưa chọn dòng", "No Line Selected"));
            return;
        }
        CartLine line = cart.get(row);
        JTextField field = new JTextField(Ui.qtyInput(line.quantity), 6);
        ((AbstractDocument) field.getDocument()).setDocumentFilter(QTY_FILTER);
        String rule = CashRules.allowsDecimal(line.product.unit)
                ? I18n.msg("Dùng , hoặc . làm dấu thập phân, tối đa 3 chữ số.", "Use , or . as the decimal separator, up to 3 digits.")
                : I18n.msg("Chỉ số nguyên (hàng không bán theo cân / đong).", "Whole numbers only (not sold by weight / volume).");
        if (!Ui.ask(this, I18n.msg("Sửa số lượng", "Edit Quantity"), field,
                I18n.msg("Số lượng mới cho \"", "New quantity for \"") + line.product.name + "\" (" + line.product.unit + "):",
                rule + I18n.msg(" Tối đa 10.000.", " At most 10,000."), field)) return;
        BigDecimal newQty = readQty(field.getText(), line.product.unit, null, true);
        if (newQty == null) return;

        setBusy(true);
        Ui.async(this, () -> priceToday(line.product), () -> setBusy(false), pr -> {
            showDate(pr.date());
            if (pr.price() == null) {
                Ui.warn(this, I18n.msg("Sản phẩm này chưa có giá hiệu lực vào ngày " + pr.date() + ".",
                                       "This product has no price in effect on " + pr.date() + "."),
                        I18n.msg("Chưa có giá hôm nay", "No Price Today"));
                return;
            }
            int i = cart.indexOf(line);
            if (i < 0) return;
            CartLine edited = priceLine(line.product, newQty, pr);
            if (exceedsMoneyCap(edited, i)) return;
            cart.set(i, edited);
            hint(pr, "");
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

    private boolean exceedsMoneyCap(CartLine line, int replaceIndex) {
        BigDecimal t = line.lineTotal();
        for (int i = 0; i < cart.size(); i++) if (i != replaceIndex) t = t.add(cart.get(i).lineTotal());
        if (t.compareTo(CashRules.MAX_MONEY) <= 0) return false;
        Ui.warn(this, I18n.msg(
                "Tổng hóa đơn sẽ là " + Ui.money(t) + ", vượt mức tối đa của một hóa đơn\n("
              + Ui.money(CashRules.MAX_MONEY) + "). Vui lòng tách thành nhiều hóa đơn!",
                "The invoice total would be " + Ui.money(t) + ", over the limit for one invoice\n("
              + Ui.money(CashRules.MAX_MONEY) + "). Please split it into several invoices!"),
                I18n.msg("Tổng tiền quá lớn", "Total Too Large"));
        return true;
    }

    private void hint(Priced pr, String extra) {
        lbHint.setText(I18n.msg("Giá ngày ", "Price on ") + pr.date() + " = " + Ui.money(pr.price())
                + (pr.note().isEmpty() ? I18n.msg("  (không có khuyến mãi)", "  (no promotion)") : "  " + pr.note()) + extra);
    }

    private PayMethod method() {
        PayMethod m = (PayMethod) cbMethod.getSelectedItem();
        return m == null ? PayMethod.CASH : m;
    }

    private void onMethodChanged() {
        lbAmount.setText(method().amountFieldLabel() + ":");
        tfAmount.setText(!method().hasChange() && remaining().signum() > 0 ? Ui.moneyInput(remaining()) : "");
        updateButtons();
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

    record Split(BigDecimal recorded, BigDecimal tendered) {
        BigDecimal change() {
            return tendered == null ? BigDecimal.ZERO : tendered.subtract(recorded).max(BigDecimal.ZERO);
        }
    }

    static Split splitPayment(PayMethod m, BigDecimal typed, BigDecimal remaining) {
        if (m.hasChange()) return new Split(typed.min(remaining), typed);
        return typed.compareTo(remaining) > 0 ? null : new Split(typed, null);
    }

    private boolean addPayment(PayMethod m, BigDecimal typed) { return addPayment(m, typed, null, null); }

    private boolean addPayment(PayMethod m, BigDecimal typed, String walletProvider, String walletRef) {
        if (cart.isEmpty()) {
            Ui.warn(this, I18n.msg("Hóa đơn chưa có dòng hàng nào nên chưa có gì để thu!",
                                   "The invoice has no items yet, so there is nothing to collect!"),
                    I18n.msg("Chưa có dòng hàng", "No Items Yet"));
            return false;
        }
        BigDecimal left = remaining();
        if (left.signum() <= 0) {
            Ui.warn(this, I18n.msg("Hóa đơn này đã thu đủ tiền (" + Ui.money(totalDue()) + ").\nKhông cần thu thêm.",
                                   "This invoice is fully paid (" + Ui.money(totalDue()) + ").\nNo more payments are needed."),
                    I18n.msg("Đã thu đủ tiền", "Fully Paid"));
            return false;
        }
        Split split = splitPayment(m, typed, left);
        if (split == null) {
            String ml = m.displayLabel();
            Ui.warn(this, I18n.msg(
                    ml + " " + Ui.money(typed) + " nhiều hơn số còn thiếu " + Ui.money(left) + ".\n\n"
                  + ml + " không có tiền thối.\n\nHãy gõ đúng " + Ui.money(left)
                  + ", hoặc chọn \"" + PayMethod.CASH.displayLabel() + "\" nếu khách đưa nhiều hơn!",
                    ml + " " + Ui.money(typed) + " is more than the remaining " + Ui.money(left) + ".\n\n"
                  + ml + " cannot give change.\n\nPlease enter exactly " + Ui.money(left)
                  + ", or choose \"" + PayMethod.CASH.displayLabel() + "\" if the customer pays more!"),
                    I18n.msg("Số tiền quá lớn", "Amount Too High"));
            return false;
        }
        if (m.hasChange()) {
            CashRules.TenderCheck tc = CashRules.checkTendered(typed, left);
            if (tc.verdict == CashRules.Verdict.REJECT) {
                Ui.warn(this, tc.message, I18n.msg("Số tiền không hợp lý", "Unreasonable Amount"));
                return false;
            }
            if (tc.verdict == CashRules.Verdict.SUSPICIOUS) {
                Object[] opts = {I18n.msg("Nhập lại số tiền", "Re-enter amount"), I18n.msg("Vẫn ghi nhận", "Record anyway")};
                if (JOptionPane.showOptionDialog(this, tc.message + I18n.msg("\nThu ngân có gõ nhầm không?", "\nDid the cashier mistype?"),
                        I18n.msg("Thừa tờ tiền?", "Extra Banknote?"),
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, opts, opts[0]) != 1) return false;
            }
        }
        String d1 = null, d2 = null;
        if (m == PayMethod.CARD) {
            String[] card = askCardDetails(split.recorded());
            if (card == null) return false;
            d1 = card[0];
            d2 = card[1];
        } else if (m == PayMethod.EWALLET) {
            d1 = walletProvider != null ? walletProvider : "MoMo";
            d2 = walletRef != null ? walletRef : "TXN-" + System.currentTimeMillis();
        }
        payments.add(new PaymentEntry(m.code(), split.recorded().setScale(2, RoundingMode.HALF_UP),
                split.tendered() == null ? null : split.tendered().setScale(2, RoundingMode.HALF_UP), d1, d2));
        refreshPayTable();
        return true;
    }

    private String[] askCardDetails(BigDecimal amount) {
        JTextField last4 = new JTextField(6), auth = new JTextField(8);
        while (Ui.ask(this, I18n.msg("Thông tin thẻ ngân hàng", "Bank Card Details"), last4,
                I18n.msg("Quẹt " + Ui.money(amount) + " trên máy POS, rồi chép từ phiếu in của máy:",
                         "Swipe " + Ui.money(amount) + " on the POS terminal, then copy from its slip:"),
                I18n.msg("4 số cuối của thẻ:", "Last 4 card digits:"), last4,
                I18n.msg("Mã chuẩn chi (6 ký tự, dòng 'APPR CODE' trên phiếu):",
                         "Authorization code (6 characters, 'APPR CODE' on the slip):"), auth)) {
            String l4 = last4.getText().trim(), code = auth.getText().trim().toUpperCase();
            String problem = CashRules.cardDetailProblem(l4, code);
            if (problem == null) return new String[]{CashRules.maskedCard(l4), code};
            Ui.warn(this, problem, I18n.msg("Thông tin thẻ không hợp lệ", "Invalid Card Details"));
        }
        return null;
    }

    private void payRestInCash() {
        if (cart.isEmpty()) {
            Ui.warn(this, I18n.msg("Hóa đơn chưa có dòng hàng nào!", "The invoice has no items yet!"),
                    I18n.msg("Chưa có dòng hàng", "No Items Yet"));
        } else if (remaining().signum() <= 0) {
            Ui.warn(this, I18n.msg("Hóa đơn này đã thu đủ tiền.", "This invoice is fully paid."),
                    I18n.msg("Đã thu đủ tiền", "Fully Paid"));
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
        String typed = tfAmount.getText().trim();
        if (typed.isEmpty()) {
            Ui.warn(this, I18n.msg(
                        "Chưa nhập số tiền cần thu bằng mã QR.\nChọn phương thức \"" + PayMethod.EWALLET.displayLabel()
                      + "\" để ô Số tiền tự điền số còn thiếu.",
                        "Please enter the amount to collect with the QR code!\nChoosing \"" + PayMethod.EWALLET.displayLabel()
                      + "\" fills the Amount field with the remaining balance."),
                    I18n.msg("Chưa nhập số tiền", "Missing Amount"));
            tfAmount.requestFocusInWindow();
            return;
        }
        BigDecimal amount = Ui.readMoney(this, typed, I18n.msg("Số tiền", "Amount"));
        if (amount == null) return;
        if (amount.compareTo(left) > 0) {
            Ui.warn(this, I18n.msg(
                        "Số tiền " + Ui.money(amount) + " nhiều hơn số còn thiếu " + Ui.money(left) + ".\n"
                      + "Ví điện tử không có tiền thối, hãy gõ đúng " + Ui.money(left) + ".",
                        "The amount " + Ui.money(amount) + " is more than the remaining " + Ui.money(left) + ".\n"
                      + "E-wallets give no change. Please enter exactly " + Ui.money(left) + "."),
                    I18n.msg("Số tiền vượt quá số còn thiếu", "Amount Too High"));
            return;
        }
        QrPaymentDialog.Result res = QrPaymentDialog.show(this, amount);
        if (res == null) return;
        if (addPayment(PayMethod.EWALLET, res.amount, "VietQR", res.transactionRef)) tfAmount.setText("");
    }

    private void showChangeBreakdown() {
        BigDecimal change = totalChange();
        if (change.signum() <= 0) {
            Ui.warn(this, I18n.msg("Không có tiền thối.", "There is no change to give."), I18n.msg("Thối tiền", "Change"));
            return;
        }
        StringBuilder sb = new StringBuilder(I18n.msg("Tiền thối cho khách: ", "Change for the customer: ") + Ui.money(change) + "\n\n");
        for (long[] part : CashRules.breakdown(change)) {
            sb.append(String.format("  %10s  x %d%n", Ui.money(BigDecimal.valueOf(part[0])), part[1]));
        }
        BigDecimal rest = CashRules.breakdownRemainder(change);
        if (rest.signum() > 0) {
            sb.append("\n  ").append(Ui.money(rest)).append(I18n.msg(" nhỏ hơn tờ tiền nhỏ nhất (100đ) -- làm tròn với khách.",
                    " is below the smallest note (100 VND) -- round it with the customer."));
        }
        JTextArea ta = new JTextArea(sb.toString());
        ta.setEditable(false);
        ta.setFont(Ui.MONO);
        JOptionPane.showMessageDialog(this, ta, I18n.msg("Thối tiền", "Change"), JOptionPane.INFORMATION_MESSAGE);
    }

    private void recalculatePaymentsIfNeeded() {
        BigDecimal excess = totalRecorded().subtract(totalDue());
        if (excess.signum() <= 0) return;
        if (!hasCashPayment()) {
            Ui.warn(this, I18n.msg(
                    "Tổng hóa đơn mới nhỏ hơn số đã thu bằng thẻ / ví điện tử.\n\n"
                  + "Vui lòng bấm \"Xóa hết\" ở khung thanh toán rồi nhập lại các dòng thanh toán!",
                    "The new invoice total is less than the amount already paid by card/e-wallet.\n\n"
                  + "Please click \"Clear all\" in the payment section and enter the payments again!"),
                    I18n.msg("Không tự chỉnh được tiền thối", "Cannot Adjust Change"));
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

    private void save() {
        if (cart.isEmpty()) { warnNoItems(); return; }
        List<CartLine> snapshot = new ArrayList<>(cart);
        setBusy(true);
        Ui.async(this, () -> {
            LocalDate today = lookupDao.serverDate();
            List<Priced> prices = new ArrayList<>();
            for (CartLine c : snapshot) prices.add(priceAt(c.product, today));
            return prices;
        }, () -> setBusy(false), prices -> {
            List<String> changed = new ArrayList<>();
            for (int i = 0; i < snapshot.size(); i++) {
                CartLine old = snapshot.get(i);
                Priced pr = prices.get(i);
                int idx = cart.indexOf(old);
                if (idx < 0) continue;
                if (pr.price() == null) {
                    Ui.warn(this, I18n.msg("\"" + old.product.name + "\" chưa có giá hiệu lực vào ngày " + pr.date()
                                           + ".\nVui lòng xóa dòng đó khỏi hóa đơn!",
                                           "\"" + old.product.name + "\" has no price in effect on " + pr.date()
                                           + ".\nPlease remove it from the invoice!"),
                            I18n.msg("Chưa có giá hôm nay", "No Price Today"));
                    return;
                }
                CartLine fresh = priceLine(old.product, old.quantity, pr);
                if (fresh.lineTotal().compareTo(old.lineTotal()) != 0) {
                    cart.set(idx, fresh);
                    changed.add("  - " + old.product.name + ": " + Ui.money(old.lineTotal()) + " -> " + Ui.money(fresh.lineTotal()));
                }
            }
            if (!prices.isEmpty()) showDate(prices.get(0).date());
            if (!changed.isEmpty()) {
                refreshCartTable();
                recalculatePaymentsIfNeeded();
                Ui.warn(this, I18n.msg(
                        "Giá hoặc khuyến mãi trên máy chủ (" + prices.get(0).date() + ") đã đổi kể từ lúc thêm các dòng này:\n"
                      + String.join("\n", changed) + "\n\nTổng mới là " + Ui.money(totalDue())
                      + ". Vui lòng kiểm lại thanh toán rồi bấm " + saveLabel() + " lần nữa.",
                        "Prices or promotions changed on the server (" + prices.get(0).date() + ") since these items were added:\n"
                      + String.join("\n", changed) + "\n\nThe new total is " + Ui.money(totalDue())
                      + ". Please check the payments, then click " + saveLabel() + " again."),
                        I18n.msg("Giá đã cập nhật", "Prices Updated"));
                return;
            }
            doSave();
        });
    }

    private void doSave() {
        if (cart.isEmpty()) { warnNoItems(); return; }
        if (payments.isEmpty()) {
            Ui.warn(this, I18n.msg("Vui lòng thêm thanh toán trước!", "Please add a payment first!"),
                    I18n.msg("Chưa có thanh toán", "No Payments"));
            return;
        }
        IdName counter = (IdName) cbCounter.getSelectedItem();
        IdName cashier = (IdName) cbCashier.getSelectedItem();
        if (counter == null || cashier == null) {
            Ui.warn(this, I18n.msg("Danh sách quầy / thu ngân chưa tải được.\nVui lòng kiểm tra kết nối rồi bấm \"Tải lại\" ở khung 1!",
                                   "The counter / cashier lists have not loaded.\n"
                                 + "Please check the connection, then click \"Reload\" in section 1!"),
                    I18n.msg("Thiếu thông tin", "Missing Information"));
            return;
        }
        BigDecimal due = totalDue(), paid = totalRecorded();
        if (paid.compareTo(due) < 0) {
            Ui.warn(this, I18n.msg(
                    "Tổng hóa đơn là " + Ui.money(due) + " nhưng mới thu " + Ui.money(paid) + " (còn thiếu "
                  + Ui.money(due.subtract(paid)) + ").\nVui lòng thu đủ tiền trước khi lưu hóa đơn!",
                    "The invoice total is " + Ui.money(due) + " but only " + Ui.money(paid) + " has been paid ("
                  + Ui.money(due.subtract(paid)) + " remaining).\nPlease collect the full amount before creating the invoice!"),
                    I18n.msg("Chưa thu đủ tiền", "Payment Incomplete"));
            return;
        }
        if (paid.compareTo(due) > 0) {
            if (!hasCashPayment()) {
                Ui.warn(this, I18n.msg(
                        "Tổng thanh toán (" + Ui.money(paid) + ") vượt tổng hóa đơn (" + Ui.money(due) + ").\n\n"
                      + "Thẻ / ví điện tử không có tiền thối, vui lòng bấm \"Xóa hết\"\n"
                      + "ở khung thanh toán rồi nhập lại!",
                        "Total payments (" + Ui.money(paid) + ") exceed the invoice total (" + Ui.money(due) + ").\n\n"
                      + "Card/e-wallet payments give no change, so please click \"Clear all\"\n"
                      + "in the payment section and enter the payments again!"),
                        I18n.msg("Thu thừa tiền", "Overpayment"));
                return;
            }
            recalculatePaymentsIfNeeded();
            if (totalRecorded().compareTo(due) > 0) {
                Ui.warn(this, I18n.msg("Không tự chỉnh được tiền thối. Vui lòng kiểm lại các dòng thanh toán!",
                                       "Cannot adjust the change automatically. Please check the payment lines!"),
                        I18n.msg("Không tự chỉnh được tiền thối", "Cannot Adjust Change"));
                return;
            }
        }

        CustomerRef cus = customer.getSelected();
        List<CartLine> lines = new ArrayList<>(cart);
        List<PaymentEntry> pays = new ArrayList<>(payments);
        setBusy(true);
        btSave.setText(I18n.msg("Đang lưu...", "Saving..."));
        Ui.async(this,
                () -> invoiceDao.createInvoice(counter.id, cashier.id, cus == null ? null : cus.id, lines, pays),
                () -> { setBusy(false); btSave.setText(saveLabel()); },
                id -> {
                    ReceiptDialog.show(this, invoiceDao, id);
                    resetForm();
                    if (onSaved != null) onSaved.run();
                });
    }

    private void onResetTab() {
        if (hasPendingWork() && JOptionPane.showConfirmDialog(this, I18n.msg(
                "Xóa trắng tab Lập hóa đơn?\n" + pendingWorkSummary() + " chưa lưu sẽ mất.",
                "Clear the New invoice tab?\nUnsaved " + pendingWorkSummary() + " will be lost."),
                I18n.msg("Làm mới tab", "Reset Tab"),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        resetForm();
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

    public boolean hasPendingWork() { return !cart.isEmpty() || !payments.isEmpty(); }

    public String pendingWorkSummary() {
        List<String> parts = new ArrayList<>();
        if (!cart.isEmpty())     parts.add(cart.size() + I18n.msg(" dòng hàng", " item line(s)"));
        if (!payments.isEmpty()) parts.add(payments.size() + I18n.msg(" dòng thanh toán", " payment(s)"));
        return String.join(", ", parts);
    }

    private void warnNoItems() {
        Ui.warn(this, I18n.msg("Hóa đơn chưa có dòng hàng nào!", "The invoice has no items yet!"),
                I18n.msg("Chưa có dòng hàng", "No Items Yet"));
    }

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
        btQr.setEnabled(!busy && method() == PayMethod.EWALLET);
    }

    private void refreshCartTable() {
        String[] cols = {I18n.text("Dòng", "Line"), I18n.text("Sản phẩm", "Product"), I18n.text("SL", "Qty"),
                         I18n.text("ĐVT", "Unit"), I18n.text("Đơn giá", "Unit price"), I18n.text("Giảm giá", "Discount"),
                         I18n.text("Thành tiền", "Line total"), "VAT %"};
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
        String[] cols = {I18n.text("Phương thức", "Method"), I18n.text("Ghi nhận", "Amount"), I18n.text("Khách đưa", "Tendered"),
                         I18n.text("Tiền thối", "Change"), I18n.text("Chi tiết", "Details")};
        Object[][] data = new Object[payments.size()][];
        for (int i = 0; i < payments.size(); i++) {
            PaymentEntry p = payments.get(i);
            boolean cash = p.tendered != null;
            data[i] = new Object[]{p.methodDisplayLabel(), Ui.money(p.amount), cash ? Ui.money(p.tendered) : "",
                                   cash ? Ui.money(p.change()) : "",
                                   (p.detail1 == null ? "" : p.detail1) + (p.detail2 == null ? "" : " / " + p.detail2)};
        }
        ((DefaultTableModel) tbPay.getModel()).setDataVector(data, cols);
        Ui.autoSize(tbPay);
        refreshTotals();
    }

    private void refreshTotals() {
        BigDecimal left = remaining(), change = totalChange();
        lbTotal.setText(I18n.msg("Tổng: ", "Total: ") + Ui.money(totalDue()));
        lbPaid.setText(I18n.msg("Đã thu: ", "Paid: ") + Ui.money(totalRecorded()));
        lbLeft.setText(I18n.msg("Còn thiếu: ", "Remaining: ") + Ui.money(left));
        lbChange.setText(I18n.msg("Tiền thối: ", "Change: ") + Ui.money(change));
        lbLeft.setForeground(left.signum() > 0 ? Color.RED.darker() : Ui.GREEN);
        btChange.setEnabled(change.signum() > 0);
        if (!method().hasChange()) tfAmount.setText(left.signum() > 0 ? Ui.moneyInput(left) : "");
        updateButtons();
    }
}
