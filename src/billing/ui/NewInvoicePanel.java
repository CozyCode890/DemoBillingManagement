package billing.ui;

import billing.dao.InvoiceDao;
import billing.dao.LookupDao;
import billing.dao.ProductDao;
import billing.model.CartLine;
import billing.model.IdName;
import billing.model.PaymentEntry;

import javax.swing.*;
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
 */
public class NewInvoicePanel extends JPanel {

    private final ProductDao productDao = new ProductDao();
    private final LookupDao  lookupDao  = new LookupDao();
    private final InvoiceDao invoiceDao = new InvoiceDao();

    private final JComboBox<IdName>  cbCounter  = new JComboBox<>();
    private final JComboBox<IdName>  cbCashier  = new JComboBox<>();
    private final JComboBox<IdName>  cbCustomer = new JComboBox<>();
    private final JSpinner           spDate     =
            new JSpinner(new SpinnerDateModel(new Date(), null, null, java.util.Calendar.DAY_OF_MONTH));

    private final JComboBox<billing.model.Product> cbProduct = new JComboBox<>();
    private final JTextField tfQty   = new JTextField("1", 6);
    private final JLabel     lbHint  = new JLabel(" ");

    private final List<CartLine>     cart     = new ArrayList<>();
    private final List<PaymentEntry> payments = new ArrayList<>();

    private final JTable tbCart = Ui.readOnlyTable();
    private final JTable tbPay  = Ui.readOnlyTable();

    private final JLabel lbTotal = new JLabel("Tong: 0 d");
    private final JLabel lbPaid  = new JLabel("Da thanh toan: 0 d");
    private final JLabel lbLeft  = new JLabel("Con thieu: 0 d");

    private final Runnable onSaved;

    public NewInvoicePanel(Runnable onSaved) {
        this.onSaved = onSaved;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(buildHeader(),  BorderLayout.NORTH);
        add(buildCenter(),  BorderLayout.CENTER);
        add(buildFooter(),  BorderLayout.SOUTH);

        loadLookups();
    }

    // -----------------------------------------------------------------
    //  Khu vực trên: thông tin chung của hóa đơn
    // -----------------------------------------------------------------
    private JComponent buildHeader() {
        JSpinner.DateEditor ed = new JSpinner.DateEditor(spDate, "dd/MM/yyyy");
        spDate.setEditor(ed);

        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder("1. Thong tin hoa don"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.anchor = GridBagConstraints.WEST;

        int col = 0;
        addPair(p, g, col++, "Quay:",       cbCounter);
        addPair(p, g, col++, "Thu ngan:",   cbCashier);
        addPair(p, g, col++, "Khach hang:", cbCustomer);
        addPair(p, g, col++, "Ngay ban:",   spDate);

        JLabel note = new JLabel("<html><i>Meo: doi 'Ngay ban' ve nam ngoai "
                + "roi them san pham -> don gia se lay theo GIA CU trong Price_History.</i></html>");
        g.gridx = 0; g.gridy = 2; g.gridwidth = 8;
        p.add(note, g);
        return p;
    }

    private void addPair(JPanel p, GridBagConstraints g, int col, String label, JComponent field) {
        g.gridx = col; g.gridy = 0; g.gridwidth = 1;
        p.add(new JLabel(label), g);
        g.gridx = col; g.gridy = 1;
        field.setPreferredSize(new Dimension(220, 26));
        p.add(field, g);
    }

    // -----------------------------------------------------------------
    //  Khu vực giữa: thêm sản phẩm + bảng giỏ hàng
    // -----------------------------------------------------------------
    private JComponent buildCenter() {
        JPanel addBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        addBar.setBorder(BorderFactory.createTitledBorder("2. Them san pham"));
        cbProduct.setPreferredSize(new Dimension(380, 26));
        addBar.add(new JLabel("San pham:"));
        addBar.add(cbProduct);
        addBar.add(new JLabel("So luong:"));
        addBar.add(tfQty);

        JButton btAdd = new JButton("Them vao hoa don");
        btAdd.addActionListener(e -> addToCart());
        addBar.add(btAdd);

        JButton btDel = new JButton("Xoa dong dang chon");
        btDel.addActionListener(e -> removeSelectedLine());
        addBar.add(btDel);

        lbHint.setForeground(new Color(0, 110, 0));
        addBar.add(lbHint);

        JPanel top = new JPanel(new BorderLayout());
        top.add(addBar, BorderLayout.NORTH);

        JScrollPane sp = new JScrollPane(tbCart);
        sp.setBorder(BorderFactory.createTitledBorder("3. Cac dong hang (se thanh bang Invoice_Line)"));
        top.add(sp, BorderLayout.CENTER);
        return top;
    }

    // -----------------------------------------------------------------
    //  Khu vực dưới: thanh toán + nút lưu
    // -----------------------------------------------------------------
    private JComponent buildFooter() {
        JPanel pay = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        pay.setBorder(BorderFactory.createTitledBorder(
                "4. Thanh toan (mot hoa don co the co NHIEU dong thanh toan)"));

        JComboBox<String> cbMethod = new JComboBox<>(new String[]{
                "Tien mat", "The ngan hang", "Vi dien tu"});
        JTextField tfAmount = new JTextField(12);

        pay.add(new JLabel("Phuong thuc:"));
        pay.add(cbMethod);
        pay.add(new JLabel("So tien:"));
        pay.add(tfAmount);

        JButton btAddPay = new JButton("Them thanh toan");
        btAddPay.addActionListener(e -> {
            try {
                BigDecimal amt = new BigDecimal(tfAmount.getText().trim());
                addPayment(cbMethod.getSelectedIndex(), amt);
                tfAmount.setText("");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "So tien khong hop le.");
            }
        });
        pay.add(btAddPay);

        JButton btRest = new JButton("Tra het bang tien mat");
        btRest.addActionListener(e -> {
            BigDecimal left = totalDue().subtract(totalPaid());
            if (left.compareTo(BigDecimal.ZERO) > 0) addPayment(0, left);
        });
        pay.add(btRest);

        JButton btClearPay = new JButton("Xoa het thanh toan");
        btClearPay.addActionListener(e -> { payments.clear(); refreshPayTable(); });
        pay.add(btClearPay);

        JScrollPane spPay = new JScrollPane(tbPay);
        spPay.setPreferredSize(new Dimension(100, 90));

        JPanel totals = new JPanel(new FlowLayout(FlowLayout.RIGHT, 18, 8));
        lbTotal.setFont(Ui.BIG);
        lbPaid.setFont(Ui.BOLD);
        lbLeft.setFont(Ui.BOLD);
        lbLeft.setForeground(Color.RED.darker());
        totals.add(lbPaid);
        totals.add(lbLeft);
        totals.add(lbTotal);

        JButton btSave = new JButton("LUU HOA DON  (INSERT + COMMIT)");
        btSave.setFont(Ui.BOLD);
        btSave.setBackground(new Color(25, 118, 210));
        btSave.setForeground(Color.WHITE);
        btSave.setOpaque(true);
        btSave.setBorderPainted(false);
        btSave.addActionListener(e -> save());
        totals.add(btSave);

        JPanel south = new JPanel(new BorderLayout());
        south.add(pay,    BorderLayout.NORTH);
        south.add(spPay,  BorderLayout.CENTER);
        south.add(totals, BorderLayout.SOUTH);
        return south;
    }

    // -----------------------------------------------------------------
    //  Nạp dữ liệu cho các ô chọn (đây là các câu SELECT đầu tiên chạy)
    // -----------------------------------------------------------------
    private void loadLookups() {
        try {
            for (IdName x : lookupDao.counters())  cbCounter.addItem(x);
            for (IdName x : lookupDao.cashiers())  cbCashier.addItem(x);
            for (IdName x : lookupDao.customers()) cbCustomer.addItem(x);
            for (billing.model.Product p : productDao.findAll()) cbProduct.addItem(p);
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    private LocalDate selectedDate() {
        Date d = (Date) spDate.getValue();
        return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    // -----------------------------------------------------------------
    //  *** ĐOẠN QUAN TRỌNG NHẤT CỦA CẢ APP ***
    //  Thêm một sản phẩm vào giỏ: hỏi database giá hợp lệ tại ngày bán,
    //  rồi hỏi tiếp xem có khuyến mãi nào đang chạy không.
    // -----------------------------------------------------------------
    private void addToCart() {
        try {
            billing.model.Product p = (billing.model.Product) cbProduct.getSelectedItem();
            if (p == null) return;

            BigDecimal qty = new BigDecimal(tfQty.getText().trim());
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                JOptionPane.showMessageDialog(this, "So luong phai lon hon 0.");
                return;
            }

            LocalDate date = selectedDate();

            // (1) Tra giá theo NGÀY BÁN -- không phải giá hôm nay
            BigDecimal price = productDao.priceOn(p.barcode, date);
            if (price == null) {
                JOptionPane.showMessageDialog(this,
                        "San pham nay chua co gia nao co hieu luc truoc ngay "
                        + date + ".\nHay chon ngay muon hon.");
                return;
            }

            // (2) Tra khuyến mãi đang hiệu lực tại ngày đó
            BigDecimal discount = BigDecimal.ZERO;
            String     note     = "";
            Object[] promo = productDao.activePromoOn(p.barcode, date);
            if (promo != null) {
                BigDecimal pct = (BigDecimal) promo[1];
                discount = qty.multiply(price)
                              .multiply(pct)
                              .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                note = promo[0] + " (-" + pct.stripTrailingZeros().toPlainString() + "%)";
            }

            cart.add(new CartLine(p, qty, price, discount, note));
            refreshCartTable();

            lbHint.setText("Gia ngay " + date + " = " + Ui.money(price)
                         + (note.isEmpty() ? "  (khong co KM)" : "  " + note));

        } catch (NumberFormatException nfe) {
            JOptionPane.showMessageDialog(this, "So luong khong hop le.");
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    private void removeSelectedLine() {
        int r = tbCart.getSelectedRow();
        if (r >= 0 && r < cart.size()) {
            cart.remove(r);
            refreshCartTable();
        }
    }

    private void addPayment(int methodIndex, BigDecimal amount) {
        String method;
        String d1, d2;
        if (methodIndex == 0) {
            method = PaymentEntry.CASH;
            d1 = amount.setScale(2, RoundingMode.HALF_UP).toPlainString(); // tiền khách đưa
            d2 = null;
        } else if (methodIndex == 1) {
            method = PaymentEntry.CARD;
            d1 = "4111-****-****-" + (1000 + (int) (Math.random() * 9000));
            d2 = "AUTH" + (10000 + (int) (Math.random() * 89999));
        } else {
            method = PaymentEntry.EWALLET;
            d1 = "MoMo";
            d2 = "TXN-" + System.currentTimeMillis();
        }
        payments.add(new PaymentEntry(method, amount.setScale(2, RoundingMode.HALF_UP), d1, d2));
        refreshPayTable();
    }

    // -----------------------------------------------------------------
    //  Lưu xuống database
    // -----------------------------------------------------------------
    private void save() {
        try {
            if (cart.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Hoa don chua co dong hang nao.");
                return;
            }
            BigDecimal due  = totalDue();
            BigDecimal paid = totalPaid();
            if (due.compareTo(paid) != 0) {
                int ok = JOptionPane.showConfirmDialog(this,
                        "Tong hoa don " + Ui.money(due) + " nhung da thanh toan "
                        + Ui.money(paid) + ".\nVan luu chu?",
                        "Lech tien", JOptionPane.YES_NO_OPTION);
                if (ok != JOptionPane.YES_OPTION) return;
            }
            if (payments.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Chua co dong thanh toan nao.");
                return;
            }

            String invoiceId = invoiceDao.nextInvoiceId();
            IdName counter   = (IdName) cbCounter.getSelectedItem();
            IdName cashier   = (IdName) cbCashier.getSelectedItem();
            IdName customer  = (IdName) cbCustomer.getSelectedItem();

            invoiceDao.createInvoice(
                    invoiceId,
                    selectedDate(),
                    LocalTime.now().withNano(0),
                    counter.id,
                    cashier.id,
                    customer == null ? null : customer.id,   // null = khách vãng lai
                    cart,
                    payments);

            JOptionPane.showMessageDialog(this,
                    "Da luu hoa don " + invoiceId + " voi " + cart.size() + " dong hang.\n"
                  + "Mo tab 'Nhat ky SQL' de xem cac cau lenh vua chay.",
                    "Thanh cong", JOptionPane.INFORMATION_MESSAGE);

            cart.clear();
            payments.clear();
            refreshCartTable();
            refreshPayTable();
            if (onSaved != null) onSaved.run();

        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    // -----------------------------------------------------------------
    //  Cập nhật bảng hiển thị
    // -----------------------------------------------------------------
    private void refreshCartTable() {
        String[] cols = {"Dong", "San pham", "SL", "DVT", "Don gia", "Giam gia", "Thanh tien", "Khuyen mai"};
        Object[][] data = new Object[cart.size()][cols.length];
        for (int i = 0; i < cart.size(); i++) {
            CartLine c = cart.get(i);
            data[i] = new Object[]{
                    i + 1,
                    c.product.name,
                    c.quantity.stripTrailingZeros().toPlainString(),
                    c.product.unit,
                    Ui.money(c.unitPrice),
                    Ui.money(c.discount),
                    Ui.money(c.lineTotal()),
                    c.promoNote};
        }
        ((DefaultTableModel) tbCart.getModel()).setDataVector(data, cols);
        Ui.autoSize(tbCart);
        refreshTotals();
    }

    private void refreshPayTable() {
        String[] cols = {"Phuong thuc", "So tien", "Chi tiet"};
        Object[][] data = new Object[payments.size()][cols.length];
        for (int i = 0; i < payments.size(); i++) {
            PaymentEntry p = payments.get(i);
            data[i] = new Object[]{p.methodLabel(), Ui.money(p.amount),
                    (p.detail1 == null ? "" : p.detail1) + (p.detail2 == null ? "" : " / " + p.detail2)};
        }
        ((DefaultTableModel) tbPay.getModel()).setDataVector(data, cols);
        Ui.autoSize(tbPay);
        refreshTotals();
    }

    private BigDecimal totalDue() {
        BigDecimal t = BigDecimal.ZERO;
        for (CartLine c : cart) t = t.add(c.lineTotal());
        return t;
    }

    private BigDecimal totalPaid() {
        BigDecimal t = BigDecimal.ZERO;
        for (PaymentEntry p : payments) t = t.add(p.amount);
        return t;
    }

    private void refreshTotals() {
        BigDecimal due  = totalDue();
        BigDecimal paid = totalPaid();
        lbTotal.setText("Tong: " + Ui.money(due));
        lbPaid.setText("Da thanh toan: " + Ui.money(paid));
        lbLeft.setText("Con thieu: " + Ui.money(due.subtract(paid)));
    }
}
