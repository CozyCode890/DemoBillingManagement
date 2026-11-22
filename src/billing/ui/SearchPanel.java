package billing.ui;

import billing.db.Db;
import billing.model.InvoiceStatus;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SearchPanel extends JPanel {
    private final JComboBox<String> cbTables;
    private final JTextField tfSearch;
    private final JButton btSearch;
    private final JButton btReload;
    private final JTable table;
    private final JLabel lbRowCount;

    private static final int ROW_LIMIT = 1000;
    private boolean lastResultTruncated = false;

    private final JPanel filterCardPanel;
    private final CardLayout cardLayout;

    private final List<JPanel> filterPanels;

    private PaymentFilterPanel paymentFilterPanel;
    private InvoiceFilterPanel invoiceFilterPanel;
    private ProductFilterPanel productFilterPanel;
    private ReturnFilterPanel returnFilterPanel;

    public SearchPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        filterCardPanel = new JPanel();
        cardLayout = new CardLayout();
        filterCardPanel.setLayout(cardLayout);

        filterPanels = new ArrayList<>();

        String[] tableNames = {
                "Product", "Invoice", "Invoice_Line", "Price_History",
                "Promotion", "Applies_To", "Return", "Customer", "Cashier",
                "Shop", "Counter", "Payment", "Cash_Payment", "Card_Payment", "EWallet_Payment"
        };
        java.util.Arrays.sort(tableNames);
        for (String tableName : tableNames) {
            filterPanels.add(createFilterPanel(tableName));
        }
        for (int i = 0; i < filterPanels.size(); i++) {
            filterCardPanel.add(filterPanels.get(i), String.valueOf(i));
        }

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        cbTables = new JComboBox<>(tableNames);
        cbTables.setPreferredSize(new Dimension(220, 28));
        cbTables.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                setText(tableLabel(String.valueOf(value)));
                return this;
            }
        });
        cbTables.addActionListener(e -> {
            String tableName = (String) cbTables.getSelectedItem();
            int index = cbTables.getSelectedIndex();
            cardLayout.show(filterCardPanel, String.valueOf(index));
            resetFilterPanel(index);
            loadTableData(tableName);
        });

        tfSearch = new JTextField(20);
        tfSearch.addActionListener(e -> performSearch());

        btSearch = new JButton("Tìm kiếm");
        btSearch.addActionListener(e -> performSearch());

        btReload = new JButton("Tải lại / Xem tất cả");
        btReload.addActionListener(e -> reloadCurrentTable());

        toolbar.add(new JLabel(I18n.text("Danh mục: ", "Category: ")));
        toolbar.add(cbTables);
        toolbar.add(new JLabel("Từ khóa: "));
        toolbar.add(tfSearch);
        toolbar.add(btSearch);
        toolbar.add(btReload);

        add(toolbar, BorderLayout.NORTH);

        JPanel filterAndTablePanel = new JPanel(new BorderLayout(5, 5));

        filterAndTablePanel.add(filterCardPanel, BorderLayout.NORTH);

        table = Ui.readOnlyTable();
        table.setAutoCreateRowSorter(true);

        filterAndTablePanel.add(new JScrollPane(table), BorderLayout.CENTER);

        add(filterAndTablePanel, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(5, 0));
        lbRowCount = new JLabel("Tổng số hàng: 0 dòng");
        lbRowCount.setFont(new Font("Consolas", Font.PLAIN, 12));
        footer.add(lbRowCount, BorderLayout.WEST);
        footer.add(Box.createHorizontalGlue(), BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);

        loadTableData((String) cbTables.getSelectedItem());
        resetFilterPanel(0);
    }

    static String tableLabel(String table) {
        switch (table) {
            case "Product":         return I18n.msg("Sản phẩm", "Products");
            case "Invoice":         return I18n.msg("Hóa đơn", "Invoices");
            case "Invoice_Line":    return I18n.msg("Dòng hàng của hóa đơn", "Invoice items");
            case "Price_History":   return I18n.msg("Lịch sử giá", "Price history");
            case "Promotion":       return I18n.msg("Khuyến mãi", "Promotions");
            case "Applies_To":      return I18n.msg("Áp dụng khuyến mãi", "Promotion items");
            case "Return":          return I18n.msg("Trả hàng", "Returns");
            case "Customer":        return I18n.msg("Khách hàng", "Customers");
            case "Cashier":         return I18n.msg("Thu ngân", "Cashiers");
            case "Shop":            return I18n.msg("Cửa hàng", "Shops");
            case "Counter":         return I18n.msg("Quầy", "Counters");
            case "Payment":         return I18n.msg("Thanh toán", "Payments");
            case "Cash_Payment":    return I18n.msg("Thanh toán tiền mặt", "Cash payments");
            case "Card_Payment":    return I18n.msg("Thanh toán thẻ", "Card payments");
            case "EWallet_Payment": return I18n.msg("Thanh toán ví điện tử", "E-wallet payments");
            default:                return table;
        }
    }

    private JPanel createFilterPanel(String tableName) {
        switch (tableName) {
            case "Payment":
                paymentFilterPanel = new PaymentFilterPanel();
                return paymentFilterPanel;
            case "Invoice":
                invoiceFilterPanel = new InvoiceFilterPanel();
                return invoiceFilterPanel;
            case "Product":
                productFilterPanel = new ProductFilterPanel();
                return productFilterPanel;
            case "Return":
                returnFilterPanel = new ReturnFilterPanel();
                return returnFilterPanel;
            default:
                JPanel panel = new JPanel();
                panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
                panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                panel.add(new JLabel("Chưa cấu hình bộ lọc cho bảng " + tableName));
                return panel;
        }
    }

    class PaymentFilterPanel extends JPanel {
        private final JToggleButton rbAllPay;
        private final JToggleButton rbCash;
        private final JToggleButton rbCard;
        private final JToggleButton rbEWallet;
        private final JComboBox<String> cbAmountRange;
        private final JTextField tfMin;
        private final JTextField tfMax;

        PaymentFilterPanel() {
            setLayout(new BoxLayout(PaymentFilterPanel.this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JPanel typeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
            typeRow.add(new JLabel("Hình thức thanh toán: "));
            ButtonGroup paymentTypeGroup = new ButtonGroup();
            rbAllPay = createToggleButton("Tất cả", true);
            rbCash = createToggleButton("Tiền mặt", false);
            rbCard = createToggleButton("Thẻ", false);
            rbEWallet = createToggleButton("Ví điện tử", false);
            paymentTypeGroup.add(rbAllPay);
            paymentTypeGroup.add(rbCash);
            paymentTypeGroup.add(rbCard);
            paymentTypeGroup.add(rbEWallet);
            typeRow.add(rbAllPay);
            typeRow.add(rbCash);
            typeRow.add(rbCard);
            typeRow.add(rbEWallet);
            add(typeRow);

            JPanel amountRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
            amountRow.add(new JLabel("Số tiền: "));
            String[] amountRanges = {"Tất cả", "< 100k", "100k - 500k", "> 500k", "Khoảng"};
            cbAmountRange = new JComboBox<>(amountRanges);
            amountRow.add(cbAmountRange);
            add(amountRow);

            JLabel lbMin = new JLabel("Từ: ");
            tfMin = new JTextField(6);
            JLabel lbMax = new JLabel("Đến: ");
            tfMax = new JTextField(6);
            JPanel amountRangePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
            amountRangePanel.add(lbMin);
            amountRangePanel.add(tfMin);
            amountRangePanel.add(lbMax);
            amountRangePanel.add(tfMax);
            amountRangePanel.setVisible(false);
            add(amountRangePanel);

            cbAmountRange.addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent e) {
                    boolean showRange = "Khoảng".equals(cbAmountRange.getSelectedItem());
                    amountRangePanel.setVisible(showRange);
                }
            });

            rbAllPay.addItemListener(e -> performSearch());
            rbCash.addItemListener(e -> performSearch());
            rbCard.addItemListener(e -> performSearch());
            rbEWallet.addItemListener(e -> performSearch());
            cbAmountRange.addActionListener(e -> performSearch());
            tfMin.addActionListener(e -> performSearch());
            tfMax.addActionListener(e -> performSearch());
        }

        public void resetToDefault() {
            rbAllPay.setSelected(true);
            rbCash.setSelected(false);
            rbCard.setSelected(false);
            rbEWallet.setSelected(false);
            cbAmountRange.setSelectedIndex(0);
            tfMin.setText("");
            tfMax.setText("");
        }

        public Pair<String, List<Object>> getWhereFragment() {
            StringBuilder where = new StringBuilder();
            List<Object> params = new ArrayList<>();

            if (!rbAllPay.isSelected()) {
                if (rbCash.isSelected()) {
                    where.append("EXISTS (SELECT 1 FROM Cash_Payment WHERE Cash_Payment.Payment_ID = Payment.Payment_ID) AND ");
                } else if (rbCard.isSelected()) {
                    where.append("EXISTS (SELECT 1 FROM Card_Payment WHERE Card_Payment.Payment_ID = Payment.Payment_ID) AND ");
                } else if (rbEWallet.isSelected()) {
                    where.append("EXISTS (SELECT 1 FROM EWallet_Payment WHERE EWallet_Payment.Payment_ID = Payment.Payment_ID) AND ");
                }
            }

            String amountRange = (String) cbAmountRange.getSelectedItem();
            if (!"Tất cả".equals(amountRange)) {
                switch (amountRange) {
                    case "< 100k":
                        where.append("Payment.Amount < ? AND ");
                        params.add(100000.0);
                        break;
                    case "100k - 500k":
                        where.append("Payment.Amount >= ? AND Payment.Amount <= ? AND ");
                        params.add(100000.0);
                        params.add(500000.0);
                        break;
                    case "> 500k":
                        where.append("Payment.Amount > ? AND ");
                        params.add(500000.0);
                        break;
                    case "Khoảng":
                        try {
                            double min = tfMin.getText().isEmpty() ? 0 : Double.parseDouble(tfMin.getText());
                            double max = tfMax.getText().isEmpty() ? 0 : Double.parseDouble(tfMax.getText());
                            if (min > 0 || max > 0) {
                                where.append("Payment.Amount >= ? AND Payment.Amount <= ? AND ");
                                params.add(min);
                                params.add(max);
                            }
                        } catch (NumberFormatException ex) {
                        }
                        break;
                }
            }

            if (where.length() > 0 && where.toString().endsWith(" AND ")) {
                where.setLength(where.length() - 5);
            }
            if (where.length() == 0) {
                return new Pair<>("", new ArrayList<>());
            }
            return new Pair<>(where.toString(), params);
        }
    }

    class InvoiceFilterPanel extends JPanel {
        private final JToggleButton rbAllStatus;
        private final JToggleButton rbPaid;
        private final JToggleButton rbVoided;
        private final JComboBox<String> cbDate;
        private final JToggleButton rbAllCustomer;
        private final JToggleButton rbWalkIn;
        private final JToggleButton rbMember;

        InvoiceFilterPanel() {
            setLayout(new BoxLayout(InvoiceFilterPanel.this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
            statusRow.add(new JLabel("Trạng thái hóa đơn: "));
            ButtonGroup statusGroup = new ButtonGroup();
            rbAllStatus = createToggleButton("Tất cả", true);
            rbPaid = createToggleButton("Đã hoàn tất", false);
            rbVoided = createToggleButton("Đã hủy", false);
            statusGroup.add(rbAllStatus);
            statusGroup.add(rbPaid);
            statusGroup.add(rbVoided);
            statusRow.add(rbAllStatus);
            statusRow.add(rbPaid);
            statusRow.add(rbVoided);
            add(statusRow);

            JPanel dateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
            dateRow.add(new JLabel("Thời gian: "));
            String[] dateOptions = {"Tất cả", "Hôm nay", "7 ngày qua", "Tháng này"};
            cbDate = new JComboBox<>(dateOptions);
            dateRow.add(cbDate);
            add(dateRow);

            JPanel customerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
            customerRow.add(new JLabel("Loại khách: "));
            ButtonGroup customerGroup = new ButtonGroup();
            rbAllCustomer = createToggleButton("Tất cả", true);
            rbWalkIn = createToggleButton("Khách vãng lai", false);
            rbMember = createToggleButton("Thành viên", false);
            customerGroup.add(rbAllCustomer);
            customerGroup.add(rbWalkIn);
            customerGroup.add(rbMember);
            customerRow.add(rbAllCustomer);
            customerRow.add(rbWalkIn);
            customerRow.add(rbMember);
            add(customerRow);

            rbAllStatus.addItemListener(e -> performSearch());
            rbPaid.addItemListener(e -> performSearch());
            rbVoided.addItemListener(e -> performSearch());
            cbDate.addActionListener(e -> performSearch());
            rbAllCustomer.addItemListener(e -> performSearch());
            rbWalkIn.addItemListener(e -> performSearch());
            rbMember.addItemListener(e -> performSearch());
        }

        public void resetToDefault() {
            rbAllStatus.setSelected(true);
            rbPaid.setSelected(false);
            rbVoided.setSelected(false);
            cbDate.setSelectedIndex(0);
            rbAllCustomer.setSelected(true);
            rbWalkIn.setSelected(false);
            rbMember.setSelected(false);
        }

        public Pair<String, List<Object>> getWhereFragment() {
            StringBuilder where = new StringBuilder();
            List<Object> params = new ArrayList<>();

            if (!rbAllStatus.isSelected()) {
                InvoiceStatus want = rbPaid.isSelected()   ? InvoiceStatus.PAID
                                   : rbVoided.isSelected() ? InvoiceStatus.VOIDED
                                   : null;
                if (want != null) {
                    where.append("Invoice.Status = ? AND ");
                    params.add(want.code());
                }
            }

            String dateOption = (String) cbDate.getSelectedItem();
            switch (dateOption) {
                case "Tất cả":
                    break;
                case "Hôm nay":
                    where.append("DATE(Invoice.Date) = CURDATE() AND ");
                    break;
                case "7 ngày qua":
                    where.append("Invoice.Date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) AND ");
                    break;
                case "Tháng này":
                    where.append("YEAR(Invoice.Date) = YEAR(CURDATE()) AND MONTH(Invoice.Date) = MONTH(CURDATE()) AND ");
                    break;
            }

            if (!rbAllCustomer.isSelected()) {
                if (rbWalkIn.isSelected()) {
                    where.append("(Invoice.Customer_ID IS NULL OR TRIM(Invoice.Customer_ID) = '') AND ");
                } else if (rbMember.isSelected()) {
                    where.append("(Invoice.Customer_ID IS NOT NULL AND TRIM(Invoice.Customer_ID) <> '') AND ");
                }
            }

            if (where.length() > 0 && where.toString().endsWith(" AND ")) {
                where.setLength(where.length() - 5);
            }
            if (where.length() == 0) {
                return new Pair<>("", new ArrayList<>());
            }
            return new Pair<>(where.toString(), params);
        }
    }

    class ProductFilterPanel extends JPanel {
        private final JComboBox<String> cbUnit;
        private final JComboBox<String> cbPriceRange;
        private final JComboBox<String> cbTaxRate;

        ProductFilterPanel() {
            setLayout(new BoxLayout(ProductFilterPanel.this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JPanel unitRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
            unitRow.add(new JLabel("Đơn vị tính: "));
            String[] units = {"Tất cả", "PIECE", "KG", "LITER", "PACK", "BOX"};
            cbUnit = new JComboBox<>(units);
            unitRow.add(cbUnit);
            add(unitRow);

            JPanel priceRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
            priceRow.add(new JLabel("Khoáng giá sản phẩm: "));
            String[] priceRanges = {"Tất cả", "< 20k", "20k - 50k", "> 50k"};
            cbPriceRange = new JComboBox<>(priceRanges);
            priceRow.add(cbPriceRange);
            add(priceRow);
            JPanel taxRateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
            taxRateRow.add(new JLabel("Thuế suất: "));
            String[] taxRates = {"Tất cả", "< 5%", "5% - 8%", "8% - 10%", "> 10%"};
            cbTaxRate = new JComboBox<>(taxRates);
            taxRateRow.add(cbTaxRate);
            add(taxRateRow);

            cbUnit.addActionListener(e -> performSearch());
            cbPriceRange.addActionListener(e -> performSearch());
            cbTaxRate.addActionListener(e -> performSearch());
        }

        public void resetToDefault() {
            cbUnit.setSelectedIndex(0);
            cbPriceRange.setSelectedIndex(0);
            cbTaxRate.setSelectedIndex(0);
        }

        public Pair<String, List<Object>> getWhereFragment() {
            StringBuilder where = new StringBuilder();
            List<Object> params = new ArrayList<>();

            String unit = (String) cbUnit.getSelectedItem();
            if (!"Tất cả".equals(unit)) {
                where.append("Product.Unit = ? AND ");
                params.add(unit);
            }

            String priceRange = (String) cbPriceRange.getSelectedItem();
            if (!"Tất cả".equals(priceRange)) {
                String latestPriceSubquery = "(SELECT ph.Price FROM Price_History ph WHERE ph.Barcode = Product.Barcode AND ph.Valid_From_Date <= CURDATE() ORDER BY ph.Valid_From_Date DESC LIMIT 1)";

                if (priceRange.equals("< 20k")) {
                    where.append(latestPriceSubquery).append(" < ? AND ");
                    params.add(20000.0);
                } else if (priceRange.equals("20k - 50k")) {
                    where.append(latestPriceSubquery).append(" >= ? AND ").append(latestPriceSubquery).append(" <= ? AND ");
                    params.add(20000.0);
                    params.add(50000.0);
                } else if (priceRange.equals("> 50k")) {
                    where.append(latestPriceSubquery).append(" > ? AND ");
                    params.add(50000.0);
                }
            }

            String taxRate = (String) cbTaxRate.getSelectedItem();
            if (!"Tất cả".equals(taxRate)) {
                if (taxRate.equals("< 5%")) {
                    where.append("Product.Tax_Rate < ? AND ");
                    params.add(5.0);
                } else if (taxRate.equals("5% - 8%")) {
                    where.append("Product.Tax_Rate >= ? AND Product.Tax_Rate <= ? AND ");
                    params.add(5.0);
                    params.add(8.0);
                } else if (taxRate.equals("8% - 10%")) {
                    where.append("Product.Tax_Rate >= ? AND Product.Tax_Rate <= ? AND ");
                    params.add(8.0);
                    params.add(10.0);
                } else if (taxRate.equals("> 10%")) {
                    where.append("Product.Tax_Rate > ? AND ");
                    params.add(10.0);
                }
            }

            if (where.length() > 0 && where.toString().endsWith(" AND ")) {
                where.setLength(where.length() - 5);
            }
            if (where.length() == 0) {
                return new Pair<>("", new ArrayList<>());
            }
            return new Pair<>(where.toString(), params);
        }
    }

    class ReturnFilterPanel extends JPanel {
        private final JComboBox<String> cbDate;
        private final JComboBox<String> cbQuantity;

        ReturnFilterPanel() {
            setLayout(new BoxLayout(ReturnFilterPanel.this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JPanel dateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
            dateRow.add(new JLabel("Thời gian trả hàng: "));
            String[] dateOptions = {"Tất cả", "Hôm nay", "Tuần này", "Tháng này"};
            cbDate = new JComboBox<>(dateOptions);
            dateRow.add(cbDate);
            add(dateRow);

            JPanel quantityRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
            quantityRow.add(new JLabel("Số lượng hoàn trả: "));
            String[] quantityOptions = {"Tất cả", "= 1", "> 1"};
            cbQuantity = new JComboBox<>(quantityOptions);
            quantityRow.add(cbQuantity);
            add(quantityRow);

            cbDate.addActionListener(e -> performSearch());
            cbQuantity.addActionListener(e -> performSearch());
        }

        public void resetToDefault() {
            cbDate.setSelectedIndex(0);
            cbQuantity.setSelectedIndex(0);
        }

        public Pair<String, List<Object>> getWhereFragment() {
            StringBuilder where = new StringBuilder();
            List<Object> params = new ArrayList<>();

            String dateOption = (String) cbDate.getSelectedItem();
            switch (dateOption) {
                case "Tất cả":
                    break;
                case "Hôm nay":
                    where.append("DATE(Return_Date) = CURDATE() AND ");
                    break;
                case "Tuần này":
                    where.append("YEARWEEK(Return_Date, 1) = YEARWEEK(CURDATE(), 1) AND ");
                    break;
                case "Tháng này":
                    where.append("YEAR(Return_Date) = YEAR(CURDATE()) AND MONTH(Return_Date) = MONTH(CURDATE()) AND ");
                    break;
            }

            String quantityOption = (String) cbQuantity.getSelectedItem();
            switch (quantityOption) {
                case "= 1":
                    where.append("Return_Quantity = 1 AND ");
                    break;
                case "> 1":
                    where.append("Return_Quantity > 1 AND ");
                    break;
            }

            if (where.length() > 0 && where.toString().endsWith(" AND ")) {
                where.setLength(where.length() - 5);
            }
            if (where.length() == 0) {
                return new Pair<>("", new ArrayList<>());
            }
            return new Pair<>(where.toString(), params);
        }
    }

    private JToggleButton createToggleButton(String text, boolean selected) {
        JToggleButton button = new JToggleButton(text);
        button.setSelected(selected);
        button.setFocusPainted(false);
        button.setMargin(new Insets(2, 5, 2, 5));
        return button;
    }

    private void loadTableData(String tableName) {
        if (tableName == null || tableName.isEmpty()) return;

        try (Connection conn = Db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT * FROM `" + tableName + "` LIMIT " + (ROW_LIMIT + 1))) {
            populateTable(rs);
        } catch (SQLException ex) {
            Ui.error(this, ex);
            ex.printStackTrace();
        }
    }

    private void performSearch() {
        String tableName = (String) cbTables.getSelectedItem();
        if (tableName == null || tableName.isEmpty()) return;

        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();

        switch (tableName) {
            case "Payment":
                if (paymentFilterPanel != null) {
                    Pair<String, List<Object>> fragment = paymentFilterPanel.getWhereFragment();
                    if (!fragment.first.isEmpty()) {
                        where.append(fragment.first);
                        params.addAll(fragment.second);
                    }
                }
                break;
            case "Invoice":
                if (invoiceFilterPanel != null) {
                    Pair<String, List<Object>> fragment = invoiceFilterPanel.getWhereFragment();
                    if (!fragment.first.isEmpty()) {
                        where.append(fragment.first);
                        params.addAll(fragment.second);
                    }
                }
                break;
            case "Product":
                if (productFilterPanel != null) {
                    Pair<String, List<Object>> fragment = productFilterPanel.getWhereFragment();
                    if (!fragment.first.isEmpty()) {
                        where.append(fragment.first);
                        params.addAll(fragment.second);
                    }
                }
                break;
            case "Return":
                if (returnFilterPanel != null) {
                    Pair<String, List<Object>> fragment = returnFilterPanel.getWhereFragment();
                    if (!fragment.first.isEmpty()) {
                        where.append(fragment.first);
                        params.addAll(fragment.second);
                    }
                }
                break;
            default:
                break;
        }

        String keyword = tfSearch.getText().trim();
        if (!keyword.isEmpty()) {
            try (Connection conn = Db.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM `" + tableName + "` LIMIT 0")) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                String numericKeyword = normalizeNumericKeyword(keyword);
                List<String> conditions = new ArrayList<>();
                List<Object> keywordParams = new ArrayList<>();
                for (int i = 1; i <= colCount; i++) {
                    String col = "`" + meta.getColumnLabel(i) + "`";
                    int type = meta.getColumnType(i);
                    if (isTextType(type)) {
                        conditions.add(col + " LIKE ?");
                        keywordParams.add("%" + keyword + "%");
                    } else if (isNumericType(type)) {
                        if (numericKeyword != null) {
                            conditions.add("CAST(" + col + " AS CHAR) LIKE ?");
                            keywordParams.add("%" + numericKeyword + "%");
                        }
                    } else if (isDateType(type)) {
                        conditions.add("CAST(" + col + " AS CHAR) LIKE ?");
                        keywordParams.add("%" + keyword + "%");
                    }
                }
                if (where.length() > 0) {
                    where.append(" AND ");
                }
                if (conditions.isEmpty()) {
                    where.append("1 = 0");
                } else {
                    where.append("(").append(String.join(" OR ", conditions)).append(")");
                    params.addAll(keywordParams);
                }
            } catch (SQLException ex) {
                Ui.error(this, ex);
                ex.printStackTrace();
            }
        }

        String sql = "SELECT * FROM `" + tableName + "`";
        if (where.length() > 0) {
            sql += " WHERE " + where.toString();
        }

        String orderBy = getOrderByClause(tableName);
        if (orderBy != null && !orderBy.isEmpty()) {
            sql += " ORDER BY " + orderBy;
        }

        sql += " LIMIT " + (ROW_LIMIT + 1);

        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                populateTable(rs);
            }
        } catch (SQLException ex) {
            Ui.error(this, ex);
            ex.printStackTrace();
        }
    }

    private static boolean isTextType(int type) {
        return type == Types.CHAR || type == Types.VARCHAR || type == Types.LONGVARCHAR
                || type == Types.NCHAR || type == Types.NVARCHAR || type == Types.LONGNVARCHAR;
    }

    private static boolean isNumericType(int type) {
        return type == Types.NUMERIC || type == Types.DECIMAL || type == Types.DOUBLE
                || type == Types.FLOAT || type == Types.REAL || type == Types.INTEGER
                || type == Types.SMALLINT || type == Types.TINYINT || type == Types.BIGINT
                || type == Types.BIT || type == Types.BOOLEAN;
    }

    private static boolean isDateType(int type) {
        return type == Types.DATE || type == Types.TIME || type == Types.TIMESTAMP
                || type == Types.TIME_WITH_TIMEZONE || type == Types.TIMESTAMP_WITH_TIMEZONE;
    }

    static String normalizeNumericKeyword(String keyword) {
        String s = keyword.replace(" ", "").replace("%", "");
        if (s.startsWith("-")) {
            s = s.substring(1);
        }
        if (s.isEmpty() || !s.matches("[0-9.,]+")) {
            return null;
        }
        if (s.contains(",")) {
            s = s.replace(".", "").replace(',', '.');
        } else if (s.matches("\\d{1,3}(\\.\\d{3})+")) {
            s = s.replace(".", "");
        }
        if (!s.matches("\\d*(\\.\\d*)?") || s.equals(".")) {
            return null;
        }
        return keyword.trim().startsWith("-") ? "-" + s : s;
    }

    private String getOrderByClause(String tableName) {
        return "";
    }

    private void reloadCurrentTable() {
        String tableName = (String) cbTables.getSelectedItem();
        if (tableName != null && !tableName.isEmpty()) {
            int index = cbTables.getSelectedIndex();
            resetFilterPanel(index);
            loadTableData(tableName);
            tfSearch.setText("");
        }
    }

    private void populateTable(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();

        String[] columnNames = new String[colCount];
        for (int i = 1; i <= colCount; i++) {
            columnNames[i - 1] = meta.getColumnLabel(i);
        }

        DefaultTableModel model = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        lastResultTruncated = false;
        while (rs.next()) {
            if (model.getRowCount() >= ROW_LIMIT) {
                lastResultTruncated = true;
                break;
            }
            Object[] row = new Object[colCount];
            for (int i = 1; i <= colCount; i++) {
                Object val = rs.getObject(i);
                int sqlType = meta.getColumnType(i);
                row[i - 1] = formatValue(val, sqlType, columnNames[i - 1]);
            }
            model.addRow(row);
        }

        table.setModel(model);
        Ui.autoSize(table);
        updateRowCount();
    }

    private String formatValue(Object value, int sqlType, String columnName) {
        if (value == null) {
            return "";
        }

        if (columnName != null && (columnName.equalsIgnoreCase("Tax_Rate")
                || columnName.equalsIgnoreCase("Discount_Percent"))) {
            return Ui.percent(value);
        }

        String upper = columnName == null ? "" : columnName.toUpperCase();
        if (upper.equals("QUANTITY") || upper.equals("RETURN_QUANTITY")) {
            return Ui.qty(value);
        }

        if (isQuantityColumn(columnName)) {
            return Ui.integer(value);
        }

        switch (sqlType) {
            case Types.NUMERIC:
            case Types.DECIMAL:
            case Types.DOUBLE:
            case Types.FLOAT:
            case Types.REAL:
                return Ui.money(value);
            case Types.INTEGER:
            case Types.SMALLINT:
            case Types.TINYINT:
            case Types.BIGINT:
                return Ui.integer(value);
            case Types.DATE:
            case Types.TIME:
            case Types.TIMESTAMP:
                return value.toString();
            default:
                return value.toString();
        }
    }

    private void updateRowCount() {
        int rows = table.getModel().getRowCount();
        if (lastResultTruncated) {
            lbRowCount.setText("Đang hiện " + Ui.integer(rows)
                    + " dòng đầu (bảng còn nhiều hơn — dùng bộ lọc để thu hẹp)");
        } else {
            lbRowCount.setText("Tổng số hàng: " + Ui.integer(rows) + " dòng");
        }
    }

    private void resetFilterPanel(int index) {
        JPanel panel = filterPanels.get(index);
        if (panel instanceof PaymentFilterPanel) {
            ((PaymentFilterPanel) panel).resetToDefault();
        } else if (panel instanceof InvoiceFilterPanel) {
            ((InvoiceFilterPanel) panel).resetToDefault();
        } else if (panel instanceof ProductFilterPanel) {
            ((ProductFilterPanel) panel).resetToDefault();
        } else if (panel instanceof ReturnFilterPanel) {
            ((ReturnFilterPanel) panel).resetToDefault();
        }
    }

    private boolean isQuantityColumn(String columnName) {
        if (columnName == null) return false;
        String upper = columnName.toUpperCase();
        return upper.equals("LINE_NUMBER") ||
               upper.equals("RETURN_ID") ||
               upper.equals("INVOICE_ID") ||
               upper.equals("PAYMENT_ID") ||
               upper.equals("BARCODE") ||
               upper.equals("PROMO_ID") ||
               upper.equals("COUNTER_ID") ||
               upper.equals("CASHIER_ID") ||
               upper.equals("CUSTOMER_ID") ||
               upper.equals("SHOP_ID") ||
               upper.equals("SUPERVISOR_ID") ||
               upper.endsWith("_ID");
    }

    private class Pair<F, S> {
        public final F first;
        public final S second;
        public Pair(F first, S second) {
            this.first = first;
            this.second = second;
        }
    }
}
