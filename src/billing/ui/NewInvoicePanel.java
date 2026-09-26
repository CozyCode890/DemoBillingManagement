package billing.ui;

import billing.dao.InvoiceDao;
import billing.dao.LookupDao;
import billing.dao.ProductDao;
import billing.model.CartLine;
import billing.model.IdName;
import billing.model.PaymentEntry;
import billing.util.VietQRHelper;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.nio.charset.StandardCharsets;

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
    private final JTextField           tfCustomerPhone = new JTextField(16);
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

    // Autocomplete components
    private JList<String> customerList;
    private DefaultListModel<String> listModel;
    private JWindow popupWindow;
    private IdName selectedCustomer; // Currently selected customer (null for walk-in)

    // Edit quantity button
    private final JButton btEditQty = new JButton("Chỉnh số lượng");

    // Refund button
    private final JButton btRefund = new JButton("Thối tiền");

    // Track selected payment method for refund button state
    private int selectedPaymentMethodIndex = 0; // Default to Cash

    // Reference to the amount text field for refund operations
    private JTextField tfAmountRef;

    // QR code button
    private final JButton btQr = new JButton("Tạo mã QR");

    // QR code configuration (loaded from config.properties with defaults)
    private String bankId = "MB";
    private String accountNo = "90902241107";
    private String accountName = "CHUNG HOC HAO";

    private final Runnable onSaved;

    public NewInvoicePanel(Runnable onSaved) {
        this.onSaved = onSaved;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        loadBankConfig(); // Load QR config from properties

        add(buildHeader(),  BorderLayout.NORTH);
        add(buildCenter(),  BorderLayout.CENTER);
        add(buildFooter(),  BorderLayout.SOUTH);

        loadLookups();
        setupCustomerAutocomplete();
        setupCartSelectionListener();
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
        addPair(p, g, col++, "Khach hang:", tfCustomerPhone);
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

        // Edit quantity button
        btEditQty.setEnabled(false); // Initially disabled
        btEditQty.addActionListener(e -> editQuantity());
        addBar.add(btEditQty);

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
        // Store reference to tfAmount for use in handleRefund
        tfAmountRef = tfAmount;

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

        // QR code button - initially disabled
        btQr.setEnabled(false);
        btQr.addActionListener(e -> handleQrPayment(cbMethod.getSelectedIndex()));
        pay.add(btQr);

        JButton btRest = new JButton("Tra het bang tien mat");
        btRest.addActionListener(e -> {
            BigDecimal left = totalDue().subtract(totalPaid());
            if (left.compareTo(BigDecimal.ZERO) > 0) addPayment(0, left);
        });
        pay.add(btRest);

        JButton btClearPay = new JButton("Xoa het thanh toan");
        btClearPay.addActionListener(e -> { payments.clear(); refreshPayTable(); });
        pay.add(btClearPay);

        // Refund button
        btRefund.setEnabled(false); // Initially disabled
        btRefund.addActionListener(e -> handleRefund());
        pay.add(btRefund);

        // Add item listener to payment method combo box to update refund button state and QR button state
        cbMethod.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                selectedPaymentMethodIndex = cbMethod.getSelectedIndex();
                updateRefundButtonState();
                // Enable QR button only for "Vi dien tu" (index 2)
                btQr.setEnabled(selectedPaymentMethodIndex == 2);
            }
        });

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
            for (billing.model.Product p : productDao.findAll()) cbProduct.addItem(p);
            // Customer lookup is handled dynamically via autocomplete
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    // -----------------------------------------------------------------
    //  Load QR configuration from config.properties
    // -----------------------------------------------------------------
    private void loadBankConfig() {
        try {
            Properties props = new Properties();
            props.load(getClass().getClassLoader().getResourceAsStream("config.properties"));
            String id = props.getProperty("qr.bank.id");
            String accNo = props.getProperty("qr.account.no");
            String accName = props.getProperty("qr.account.name");
            if (id != null && !id.trim().isEmpty()) bankId = id.trim();
            if (accNo != null && !accNo.trim().isEmpty()) accountNo = accNo.trim();
            if (accName != null && !accName.trim().isEmpty()) accountName = accName.trim();
        } catch (Exception ex) {
            // If any error, keep defaults
            System.err.println("Could not load QR config, using defaults: " + ex.getMessage());
        }
    }

    // -----------------------------------------------------------------
    //  Setup autocomplete for customer phone input
    // -----------------------------------------------------------------
    private void setupCustomerAutocomplete() {
        // Create list model and JList for popup
        listModel = new DefaultListModel<>();
        customerList = new JList<>(listModel);
        customerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        customerList.setFixedCellHeight(22);
        customerList.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        // Create popup window
        popupWindow = new JWindow(SwingUtilities.getWindowAncestor(this));
        popupWindow.getContentPane().add(new JScrollPane(customerList));
        popupWindow.setAlwaysOnTop(true);

        // Add mouse listener to list for selection
        customerList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {
                    selectCustomerFromList();
                }
            }
        });

        // Add key listener to list for Enter key
        customerList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    selectCustomerFromList();
                }
            }
        });

        // Add document listener to text field for autocomplete
        tfCustomerPhone.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateCustomerPopup();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateCustomerPopup();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateCustomerPopup();
            }
        });

        // Add focus listener to hide popup when losing focus
        tfCustomerPhone.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                // Show popup on focus if there's text
                if (!tfCustomerPhone.getText().trim().isEmpty()) {
                    updateCustomerPopup();
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                // Hide popup when losing focus (with small delay to allow list selection)
                SwingUtilities.invokeLater(() -> {
                    if (!popupWindow.isFocusOwner() &&
                            !customerList.isFocusOwner()) {
                        hideCustomerPopup();
                    }
                });
            }
        });
    }

    private void updateCustomerPopup() {
        String phonePrefix = tfCustomerPhone.getText().trim();

        if (phonePrefix.isEmpty()) {
            // Clear selection and hide popup when empty
            selectedCustomer = null;
            hideCustomerPopup();
            return;
        }

        // Search for matching customers
        List<IdName> matches = lookupDao.searchCustomersByPhonePrefix(phonePrefix);

        // Update list model
        listModel.clear();
        for (IdName customer : matches) {
            listModel.addElement(customer.name);
        }

        // Show popup if there are matches
        if (!matches.isEmpty()) {
            showCustomerPopup();
        } else {
            hideCustomerPopup();
            // Optionally: keep selectedCustomer as is (last valid selection)
            // or set to null if we want to require valid selection
            // For now, we'll keep last selection but allow clearing via empty field
        }
    }

    private void showCustomerPopup() {
        if (listModel.isEmpty()) return;

        // Position popup below the text field
        Point location = tfCustomerPhone.getLocationOnScreen();
        popupWindow.setLocation(
                location.x,
                location.y + tfCustomerPhone.getHeight()
        );

        // Calculate width needed for popup content
        int textFieldWidth = tfCustomerPhone.getWidth();
        int contentWidth = calculatePopupContentWidth();
        int popupWidth = Math.max(textFieldWidth, contentWidth) + 20; // Add 20px padding

        // Size popup with calculated width, with reasonable height
        popupWindow.setSize(
                popupWidth,
                Math.min(listModel.size() * 22 + 4, 200) // 22px per item + 4px padding, max 200px
        );

        popupWindow.setVisible(true);
        customerList.requestFocusInWindow();
    }

    /**
     * Calculates the width needed to display the longest item in the customer list
     * @return width in pixels
     */
    private int calculatePopupContentWidth() {
        if (listModel.isEmpty()) return 0;

        FontMetrics fm = customerList.getFontMetrics(customerList.getFont());
        int maxWidth = 0;

        for (int i = 0; i < listModel.size(); i++) {
            String text = listModel.getElementAt(i);
            int textWidth = fm.stringWidth(text);
            if (textWidth > maxWidth) {
                maxWidth = textWidth;
            }
        }

        return maxWidth;
    }

    private void hideCustomerPopup() {
        if (popupWindow != null) {
            popupWindow.setVisible(false);
        }
    }

    private void selectCustomerFromList() {
        int selectedIndex = customerList.getSelectedIndex();
        if (selectedIndex >= 0 && selectedIndex < listModel.size()) {
            // Get the selected customer from our search results
            // Note: We need to re-search to get the actual IdName objects
            // This is not optimal but keeps the code simple for this demo
            String phonePrefix = tfCustomerPhone.getText().trim();
            List<IdName> matches = lookupDao.searchCustomersByPhonePrefix(phonePrefix);
            if (selectedIndex < matches.size()) {
                selectedCustomer = matches.get(selectedIndex);
                tfCustomerPhone.setText(selectedCustomer.name);
                hideCustomerPopup();
            }
        }
    }

    // -----------------------------------------------------------------
    //  Setup selection listener for cart table to manage edit button state
    // -----------------------------------------------------------------
    private void setupCartSelectionListener() {
        tbCart.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            if (e.getValueIsAdjusting()) return;

            int selectedRow = tbCart.getSelectedRow();
            boolean hasSelection = selectedRow >= 0 && selectedRow < cart.size();
            btEditQty.setEnabled(hasSelection);
        });
    }

    // -----------------------------------------------------------------
    //  HELPER: Tìm CartLine trong giỏ hàng theo mã vạch sản phẩm
    // -----------------------------------------------------------------
    /**
     * Tìm CartLine trong giỏ hàng theo mã vạch sản phẩm.
     * @param barcode mã vạch sản phẩm cần tìm
     * @return CartLine nếu tìm thấy, null nếu không tìm thấy
     */
    private CartLine findCartLineByBarcode(String barcode) {
        for (CartLine line : cart) {
            if (line.product.barcode.equals(barcode)) {
                return line;
            }
        }
        return null;
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

            // Kiểm tra xem sản phẩm đã có trong giỏ hàng chưa
            CartLine existingLine = findCartLineByBarcode(p.barcode);
            if (existingLine != null) {
                // Nếu có rồi, cập nhật số lượng và giảm giá
                existingLine.quantity = existingLine.quantity.add(qty);
                existingLine.discount = existingLine.discount.add(discount);
                // promoNote giữ nguyên (nên là samaеня vì cùng sản phẩm và cùng ngày)
            } else {
                // Nếu chưa có, thêm dòng mới
                cart.add(new CartLine(p, qty, price, discount, note));
            }
            refreshCartTable();

            lbHint.setText("Gia ngay " + date + " = " + Ui.money(price)
                    + (note.isEmpty() ? "  (khong co KM)" : "  " + note));

        } catch (NumberFormatException nfe) {
            JOptionPane.showMessageDialog(this, "So luong khong hop le.");
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    // -----------------------------------------------------------------
    //  Edit quantity of selected cart line
    // -----------------------------------------------------------------
    private void editQuantity() {
        int selectedRow = tbCart.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= cart.size()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một dòng sản phẩm để chỉnh sửa.");
            return;
        }

        try {
            BigDecimal newQty = new BigDecimal(tfQty.getText().trim());
            if (newQty.compareTo(BigDecimal.ZERO) <= 0) {
                JOptionPane.showMessageDialog(this, "Số lượng phải lớn hơn 0.");
                return;
            }

            // Update the quantity of the selected cart line
            CartLine cartLine = cart.get(selectedRow);
            cartLine.quantity = newQty;

            // Refresh the display
            refreshCartTable();
            refreshTotals();

            // Clear the quantity field after update (optional)
            tfQty.setText("1");
            tfQty.requestFocus();

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Số lượng không hợp lệ. Vui lòng nhập một số.");
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
        updateRefundButtonState(); // Update refund button state after adding payment
    }

    /**
     * Thêm thanh toán với chi tiết cụ thể (dùng cho VietQR)
     * @param methodIndex chỉ số phương thức thanh toán
     * @param amount số tiền
     * @param detail1 chi tiết 1 (ví dụ: nhà cung cấp ví điện tử)
     * @param detail2 chi tiết 2 (ví dụ: mã giao dịch)
     */
    private void addPayment(int methodIndex, BigDecimal amount, String detail1, String detail2) {
        String method;
        if (methodIndex == 0) {
            method = PaymentEntry.CASH;
        } else if (methodIndex == 1) {
            method = PaymentEntry.CARD;
        } else {
            method = PaymentEntry.EWALLET;
        }
        payments.add(new PaymentEntry(method, amount.setScale(2, RoundingMode.HALF_UP), detail1, detail2));
        refreshPayTable();
        updateRefundButtonState(); // Update refund button state after adding payment
    }

    private void handleRefund() {
        // Only process if refund button is enabled (should be checked by caller, but double-check)
        if (!btRefund.isEnabled()) {
            return;
        }

        try {
            BigDecimal refundAmt = new BigDecimal(tfAmountRef.getText().trim());
            if (refundAmt.compareTo(BigDecimal.ZERO) <= 0) {
                JOptionPane.showMessageDialog(this, "Số tiền thối phải lớn hơn 0.");
                return;
            }

            // Find the last cash payment to refund from
            int lastCashIndex = -1;
            for (int i = payments.size() - 1; i >= 0; i--) {
                PaymentEntry payment = payments.get(i);
                if (payment.method.equals(PaymentEntry.CASH)) {
                    lastCashIndex = i;
                    break;
                }
            }

            if (lastCashIndex == -1) {
                JOptionPane.showMessageDialog(this, "Không tìm thấy thanh toán tiền mặt để thối.");
                return;
            }

            PaymentEntry cashPayment = payments.get(lastCashIndex);
            if (cashPayment.amount.compareTo(refundAmt) < 0) {
                JOptionPane.showMessageDialog(this, "Số tiền thối vượt quá số tiền của giao dịch tiền mặt cuối cùng.");
                return;
            }

            // Refund the amount: reduce the payment amount
            BigDecimal newAmount = cashPayment.amount.subtract(refundAmt);
            payments.set(lastCashIndex, new PaymentEntry(
                    cashPayment.method,
                    newAmount.setScale(2, RoundingMode.HALF_UP),
                    cashPayment.detail1,
                    cashPayment.detail2
            ));

            // Clear the amount field and set focus
            tfAmountRef.setText("");
            tfAmountRef.requestFocus();

            // Refresh displays
            refreshPayTable();
            refreshTotals();

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Số tiền thối không hợp lệ. Vui lòng nhập một số.");
        }
    }

    /**
     * Xử lý thanh toán qua QR code (VietQR)
     * @param methodIndex chỉ số của phương thức thanh toán trong cbMethod (phải là 2 cho "Vi dien tu")
     */
    private void handleQrPayment(int methodIndex) {
        if (methodIndex != 2) {
            // Not EWALLET, should not happen due to button enabling
            return;
        }

        try {
            String amountStr = tfAmountRef.getText().trim();
            BigDecimal amount;
            if (amountStr.isEmpty()) {
                // If amount empty, use the remaining amount due
                amount = totalDue().subtract(totalPaid());
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    JOptionPane.showMessageDialog(this, "Không còn số tiền cần thanh toán.");
                    return;
                }
                // Auto-fill the amount field
                tfAmountRef.setText(amount.setScale(0, RoundingMode.HALF_UP).toPlainString());
            } else {
                amount = new BigDecimal(amountStr);
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    JOptionPane.showMessageDialog(this, "Vui lòng nhập số tiền hợp lệ!");
                    return;
                }
            }

            // Generate transaction reference
            String transactionRef = "TXN" + (System.currentTimeMillis() % 10000000);

            // Generate QR code image using VietQRHelper
            BufferedImage qrImg = VietQRHelper.generateQRCodeImage(
                    amount.longValue(),
                    transactionRef,
                    320); // Size 320x320 as requested

        // Show QR dialog
        showQrDialog(qrImg, amount, transactionRef);

        // Clear amount field after generating QR (optional)
        tfAmountRef.setText("");
        tfAmountRef.requestFocus();

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Số tiền không hợp lệ!");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi khi tạo mã QR: " + ex.getMessage());
        }
    }

    /**
     * Hiển thị dialog chứa mã QR và thông tin thanh toán
     * @param qrUrl URL của ảnh QR code
     * @param amount Số tiền thanh toán
     * @param transactionRef Mã giao dịch
     */
    private void showQrDialog(BufferedImage qrImg, BigDecimal amount, String transactionRef) {
        // Create dialog
        JDialog dialog = new JDialog((Frame) null, "Thanh toán qua QR code", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(10, 10));
        ((JPanel) dialog.getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Display QR image directly from BufferedImage
        // Resize image to 300x300 for display
        Image scaled = qrImg.getScaledInstance(300, 300, Image.SCALE_SMOOTH);
        ImageIcon qrIcon = new ImageIcon(scaled);

        JLabel qrLabel = new JLabel(qrIcon);
        qrLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // Info panel
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JLabel amountLabel = new JLabel(
                String.format("Số tiền cần thanh toán: %,.0f đ", amount));
        amountLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        infoPanel.add(amountLabel);

        JLabel refLabel = new JLabel(
                String.format("Nội dung CK (Transaction_ref): %s", transactionRef));
        refLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        infoPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        infoPanel.add(refLabel);

        JLabel statusLabel = new JLabel("Trạng thái: Đang chờ khách quét mã...");
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusLabel.setForeground(new Color(0, 100, 0));
        infoPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        infoPanel.add(statusLabel);

        // Buttons panel
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton btnConfirm = new JButton("Xác nhận đã nhận tiền (Thành công)");
        JButton btnCancel = new JButton("Hủy bỏ");

        btnConfirm.addActionListener(e -> {
            // Confirm payment: add to payment list as EWALLET with VietQR info
            addPayment(2, amount.setScale(2, RoundingMode.HALF_UP),
                    "VietQR", transactionRef);
            dialog.dispose();
        });

        btnCancel.addActionListener(e -> {
            dialog.dispose();
        });

        btnPanel.add(btnConfirm);
        btnPanel.add(btnCancel);

        // Assemble dialog
        dialog.add(qrLabel, BorderLayout.CENTER);
        dialog.add(infoPanel, BorderLayout.SOUTH);
        dialog.add(btnPanel, BorderLayout.PAGE_END);

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

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

            // Determine customer: use selectedCustomer if not null, else null for walk-in
            IdName customer = selectedCustomer;
            // If text field is empty, definitely walk-in (null)
            if (tfCustomerPhone.getText().trim().isEmpty()) {
                customer = null;
            }

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

        // Update the lbLeft label based on whether we're overpaid or underpaid
        if (paid.compareTo(due) > 0) {
            // Overpaid: show refund amount
            BigDecimal refundAmount = paid.subtract(due);
            lbLeft.setText("Số tiền cần thối: " + Ui.money(refundAmount));
        } else {
            // Underpaid or exact: show amount due
            BigDecimal amountDue = due.subtract(paid);
            lbLeft.setText("Con thieu: " + Ui.money(amountDue));
        }

        lbTotal.setText("Tong: " + Ui.money(due));
        lbPaid.setText("Da thanh toan: " + Ui.money(paid));

        // Update refund button state based on current conditions
        updateRefundButtonState();
    }

    private void updateRefundButtonState() {
        // Enable refund button only when:
        // 1. Selected payment method is Cash (index 0)
        // 2. There is at least one payment
        // 3. Total paid > Total due (there is overpayment to refund)
        // 4. There is at least one cash payment with positive amount

        boolean enable = false;

        // Check if selected payment method is Cash
        boolean isCashSelected = (selectedPaymentMethodIndex == 0);

        // Check if there is at least one payment
        boolean hasPayments = !payments.isEmpty();

        // Check if we're overpaid (total paid > total due)
        boolean isOverpaid = totalPaid().compareTo(totalDue()) > 0;

        // Check if there is at least one cash payment with positive amount
        boolean hasCashPayment = false;
        for (PaymentEntry payment : payments) {
            if (payment.method.equals(PaymentEntry.CASH) &&
                payment.amount.compareTo(BigDecimal.ZERO) > 0) {
                hasCashPayment = true;
                break;
            }
        }

        // Enable if all conditions are met
        enable = isCashSelected && hasPayments && isOverpaid && hasCashPayment;

        btRefund.setEnabled(enable);
    }
}