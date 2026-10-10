package billing.ui;

import billing.db.Db;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tab 5: Tra cứu dữ liệu (Search Form)
 * Chọn bảng, xem dữ liệu hoặc tìm kiếm theo từ khóa.
 * Giao diện giống tab 2: bảng read-only, sắp xếp cột, định dạng số.
 *
 * Phiên bản nâng cao: Tìm kiếm đa tiêu chí (Multi-criteria Filter)
 */
public class SearchPanel extends JPanel {

    // UI components
    private final JComboBox<String> cbTables;
    private final JTextField tfSearch;
    private final JButton btSearch;
    private final JButton btReload;
    private final JTable table;
    private final JLabel lbRowCount;

    // Formatters for Vietnamese locale (matching tab 2)
    private static final DecimalFormat CURRENCY_FORMAT;
    private static final DecimalFormat INTEGER_FORMAT;
    static {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(new Locale("vi", "VN"));
        sym.setGroupingSeparator('.');
        sym.setDecimalSeparator(',');
        CURRENCY_FORMAT = new DecimalFormat("#,##0", sym);
        INTEGER_FORMAT = new DecimalFormat("#,##0", sym);
    }

    // Dynamic filter panel with CardLayout
    private final JPanel filterCardPanel;
    private final CardLayout cardLayout;

    // Map table name to filter panel index (we'll store panels in an array aligned with combo box items)
    private final List<JPanel> filterPanels;

    // We'll also keep references to the specific filter panels for easy access to their state
    private PaymentFilterPanel paymentFilterPanel;
    private InvoiceFilterPanel invoiceFilterPanel;
    private ProductFilterPanel productFilterPanel;
    private ReturnFilterPanel returnFilterPanel;

    public SearchPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Initialize filter card panel and layout
        filterCardPanel = new JPanel();
        cardLayout = new CardLayout();
        filterCardPanel.setLayout(cardLayout);

        // Initialize filterPanels list (empty for now)
        filterPanels = new ArrayList<>();

        // Create the specific filter panels and add them to filterPanels and to the cardLayout
        String[] tableNames = {
                "Product", "Invoice", "Invoice_Line", "Price_History",
                "Promotion", "Return", "Customer", "Cashier",
                "Shop", "Counter", "Payment", "Cash_Payment", "Card_Payment", "EWallet_Payment"
        };
        // Sort table names alphabetically
        java.util.Arrays.sort(tableNames);
        for (String tableName : tableNames) {
            filterPanels.add(createFilterPanel(tableName));
        }
        // Add all filter panels to the card layout
        for (int i = 0; i < filterPanels.size(); i++) {
            filterCardPanel.add(filterPanels.get(i), String.valueOf(i));
        }

        // === Toolbar ===
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        cbTables = new JComboBox<>(tableNames); // reuse the same array
        cbTables.setPreferredSize(new Dimension(180, 28));
        cbTables.addActionListener(e -> {
            String tableName = (String) cbTables.getSelectedItem();
            int index = cbTables.getSelectedIndex();
            // Switch to the corresponding filter panel
            cardLayout.show(filterCardPanel, String.valueOf(index));
            // Reset filter panel to default state
            resetFilterPanel(index);
            // Load table data with default filters
            loadTableData(tableName);
        });

        tfSearch = new JTextField(20);
        tfSearch.addActionListener(e -> performSearch());

        btSearch = new JButton("Tìm kiếm");
        btSearch.addActionListener(e -> performSearch());

        btReload = new JButton("Tải lại / Xem tất cả");
        btReload.addActionListener(e -> reloadCurrentTable());

        toolbar.add(new JLabel("Chọn bảng: "));
        toolbar.add(cbTables);
        toolbar.add(new JLabel("Từ khóa: "));
        toolbar.add(tfSearch);
        toolbar.add(btSearch);
        toolbar.add(btReload);

        add(toolbar, BorderLayout.NORTH);

        // === Filter and Table Panel ===
        JPanel filterAndTablePanel = new JPanel(new BorderLayout(5, 5));

        // Filter panel with CardLayout (already initialized above)
        filterAndTablePanel.add(filterCardPanel, BorderLayout.NORTH);

        // === Table ===
        table = Ui.readOnlyTable(); // Same look as tab 2
        table.setAutoCreateRowSorter(true); // Enable column sorting (3-state: ASC, DESC, UNSORTED)
        // Note: Default behavior clicks: ASC -> DESC -> UNSORTED -> ASC ...

        filterAndTablePanel.add(new JScrollPane(table), BorderLayout.CENTER);

        add(filterAndTablePanel, BorderLayout.CENTER);

        // === Footer ===
        JPanel footer = new JPanel(new BorderLayout(5, 0));
        lbRowCount = new JLabel("Tổng số hàng: 0 dòng");
        lbRowCount.setFont(new Font("Consolas", Font.PLAIN, 12));
        footer.add(lbRowCount, BorderLayout.WEST);
        footer.add(Box.createHorizontalGlue(), BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);

        // Load first table by default
        loadTableData((String) cbTables.getSelectedItem());
        // Initialize the first filter panel to default state
        resetFilterPanel(0);
    }

    /**
     * Creates a filter panel for the given table name.
     * @param tableName the name of the table
     * @return a panel containing filter chips and controls for that table
     */
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
                // For other tables, we can provide a simple placeholder or just a label
                JPanel panel = new JPanel();
                panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
                panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
                panel.add(new JLabel("Chưa cấu hình bộ lọc cho bảng " + tableName));
                return panel;
        }
    }

    // =================================================================
    // SPECIFIC FILTER PANEL CLASSES
    // =================================================================

    /** Filter panel for Payment table */
    class PaymentFilterPanel extends JPanel {
        // Payment type: all, cash, card, ewallet
        private final JToggleButton rbAllPay;
        private final JToggleButton rbCash;
        private final JToggleButton rbCard;
        private final JToggleButton rbEWallet;
        // Amount range: all, <100k, 100k-500k, >500k, custom range
        private final JComboBox<String> cbAmountRange;
        private final JTextField tfMin;
        private final JTextField tfMax;

        PaymentFilterPanel() {
            setLayout(new BoxLayout(PaymentFilterPanel.this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            // Row 1: Payment Type chips
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

            // Row 2: Amount range
            JPanel amountRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
            amountRow.add(new JLabel("Số tiền: "));
            String[] amountRanges = {"Tất cả", "< 100k", "100k - 500k", "> 500k", "Khoảng"};
            cbAmountRange = new JComboBox<>(amountRanges);
            amountRow.add(cbAmountRange);
            add(amountRow);

            // Min and max fields for custom range (initially hidden)
            JLabel lbMin = new JLabel("Từ: ");
            tfMin = new JTextField(6);
            JLabel lbMax = new JLabel("Đến: ");
            tfMax = new JTextField(6);
            JPanel amountRangePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
            amountRangePanel.add(lbMin);
            amountRangePanel.add(tfMin);
            amountRangePanel.add(lbMax);
            amountRangePanel.add(tfMax);
            amountRangePanel.setVisible(false); // Initially hidden
            add(amountRangePanel);

            // Show min/max fields when "Khoảng" is selected
            cbAmountRange.addItemListener(new ItemListener() {
                @Override
                public void itemStateChanged(ItemEvent e) {
                    boolean showRange = "Khoảng".equals(cbAmountRange.getSelectedItem());
                    amountRangePanel.setVisible(showRange);
                }
            });

            // Add action listeners to all interactive components to trigger search
            rbAllPay.addItemListener(e -> performSearch());
            rbCash.addItemListener(e -> performSearch());
            rbCard.addItemListener(e -> performSearch());
            rbEWallet.addItemListener(e -> performSearch());
            cbAmountRange.addActionListener(e -> performSearch());
            tfMin.addActionListener(e -> performSearch());
            tfMax.addActionListener(e -> performSearch());
        }

        /**
         * Reset all controls to default state.
         */
        public void resetToDefault() {
            rbAllPay.setSelected(true);
            rbCash.setSelected(false);
            rbCard.setSelected(false);
            rbEWallet.setSelected(false);
            cbAmountRange.setSelectedIndex(0);
            tfMin.setText("");
            tfMax.setText("");
        }

        /**
         * Returns the WHERE clause fragment and parameters for this filter panel.
         * @return a pair of (whereFragment, paramsList) where whereFragment is a string
         *         that can be appended to a WHERE clause (without the leading "WHERE"),
         *         and paramsList is a list of parameter values for PreparedStatement.
         */
        public Pair<String, List<Object>> getWhereFragment() {
            StringBuilder where = new StringBuilder();
            List<Object> params = new ArrayList<>();

            // Payment type
            if (!rbAllPay.isSelected()) {
                if (rbCash.isSelected()) {
                    where.append("EXISTS (SELECT 1 FROM Cash_Payment WHERE Cash_Payment.Payment_ID = Payment.Payment_ID) AND ");
                } else if (rbCard.isSelected()) {
                    where.append("EXISTS (SELECT 1 FROM Card_Payment WHERE Card_Payment.Payment_ID = Payment.Payment_ID) AND ");
                } else if (rbEWallet.isSelected()) {
                    where.append("EXISTS (SELECT 1 FROM EWallet_Payment WHERE EWallet_Payment.Payment_ID = Payment.Payment_ID) AND ");
                }
            }

            // Amount range
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
                            // Ignore invalid input
                        }
                        break;
                }
            }

            // Remove trailing " AND " if any
            if (where.length() > 0 && where.toString().endsWith(" AND ")) {
                where.setLength(where.length() - 5);
            }
            // If no conditions, return empty string and empty params
            if (where.length() == 0) {
                return new Pair<>("", new ArrayList<>());
            }
            return new Pair<>(where.toString(), params);
        }
    }

    /** Filter panel for Invoice table */
    class InvoiceFilterPanel extends JPanel {
        // Status: all, completed, voided
        private final JToggleButton rbAllStatus;
        private final JToggleButton rbPaid;
        private final JToggleButton rbVoided;
        // Date: all, today, last 7 days, this month
        private final JComboBox<String> cbDate;
        // Customer type: all, walk-in, member
        private final JToggleButton rbAllCustomer;
        private final JToggleButton rbWalkIn;
        private final JToggleButton rbMember;

        InvoiceFilterPanel() {
            setLayout(new BoxLayout(InvoiceFilterPanel.this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            // Row 1: Status chips
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

            // Row 2: Date
            JPanel dateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
            dateRow.add(new JLabel("Thời gian: "));
            String[] dateOptions = {"Tất cả", "Hôm nay", "7 ngày qua", "Tháng này"};
            cbDate = new JComboBox<>(dateOptions);
            dateRow.add(cbDate);
            add(dateRow);

            // Row 3: Customer type
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

            // Add action listeners to all interactive components to trigger search
            rbAllStatus.addItemListener(e -> performSearch());
            rbPaid.addItemListener(e -> performSearch());
            rbVoided.addItemListener(e -> performSearch());
            cbDate.addActionListener(e -> performSearch());
            rbAllCustomer.addItemListener(e -> performSearch());
            rbWalkIn.addItemListener(e -> performSearch());
            rbMember.addItemListener(e -> performSearch());
        }

        /**
         * Reset all controls to default state.
         */
        public void resetToDefault() {
            rbAllStatus.setSelected(true);
            rbPaid.setSelected(false);
            rbVoided.setSelected(false);
            cbDate.setSelectedIndex(0);
            rbAllCustomer.setSelected(true);
            rbWalkIn.setSelected(false);
            rbMember.setSelected(false);
        }

        /**
         * Returns the WHERE clause fragment and parameters for this filter panel.
         * @return a pair of (whereFragment, paramsList) where whereFragment is a string
         *         that can be appended to a WHERE clause (without the leading "WHERE"),
         *         and paramsList is a list of parameter values for PreparedStatement.
         */
        public Pair<String, List<Object>> getWhereFragment() {
            StringBuilder where = new StringBuilder();
            List<Object> params = new ArrayList<>();

            // Status
            if (!rbAllStatus.isSelected()) {
                if (rbPaid.isSelected()) {
                    where.append("Invoice.Status = 'PAID' AND ");
                } else if (rbVoided.isSelected()) {
                    where.append("Invoice.Status = 'VOIDED' AND ");
                }
            }

            // Date
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
                // Note: No default case because all options are covered
            }

            // Customer type
            if (!rbAllCustomer.isSelected()) {
                if (rbWalkIn.isSelected()) {
                    where.append("(Invoice.Customer_ID IS NULL OR TRIM(Invoice.Customer_ID) = '') AND ");
                } else if (rbMember.isSelected()) {
                    where.append("(Invoice.Customer_ID IS NOT NULL AND TRIM(Invoice.Customer_ID) <> '') AND ");
                }
            }

            // Remove trailing " AND " if any
            if (where.length() > 0 && where.toString().endsWith(" AND ")) {
                where.setLength(where.length() - 5);
            }
            // If no conditions, return empty string and empty params
            if (where.length() == 0) {
                return new Pair<>("", new ArrayList<>());
            }
            return new Pair<>(where.toString(), params);
        }
    }

    /** Filter panel for Product table */
    class ProductFilterPanel extends JPanel {
        // Unit: all, PIECE, KG, LITER, PACK, BOX
        private final JComboBox<String> cbUnit;
        // Price range: all, <20k, 20k-50k, >50k, custom range? spec only gives three ranges.
        // We'll implement as combo with options: All, <20k, 20k-50k, >50k
        private final JComboBox<String> cbPriceRange;
        private final JComboBox<String> cbTaxRate;

        ProductFilterPanel() {
            setLayout(new BoxLayout(ProductFilterPanel.this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            // Row 1: Unit
            JPanel unitRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
            unitRow.add(new JLabel("Đơn vị tính: "));
            String[] units = {"Tất cả", "PIECE", "KG", "LITER", "PACK", "BOX"};
            cbUnit = new JComboBox<>(units);
            unitRow.add(cbUnit);
            add(unitRow);

            // Row 2: Price range
            JPanel priceRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
            priceRow.add(new JLabel("Khoáng giá sản phẩm: "));
            String[] priceRanges = {"Tất cả", "< 20k", "20k - 50k", "> 50k"};
            cbPriceRange = new JComboBox<>(priceRanges);
            priceRow.add(cbPriceRange);
            add(priceRow);
            // Row 3: Tax rate
            JPanel taxRateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
            taxRateRow.add(new JLabel("Thuế suất: "));
            String[] taxRates = {"Tất cả", "< 5%", "5% - 8%", "8% - 10%", "> 10%"};
            cbTaxRate = new JComboBox<>(taxRates);
            taxRateRow.add(cbTaxRate);
            add(taxRateRow);

            // Add action listeners
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

            // Unit
            String unit = (String) cbUnit.getSelectedItem();
            if (!"Tất cả".equals(unit)) {
                where.append("Product.Unit = ? AND ");
                params.add(unit);
            }

            // Price range: need to get latest price for each product.
            // We'll join with Price_History to get the price where Valid_From_Date is the max <= current date.
            String priceRange = (String) cbPriceRange.getSelectedItem();
            if (!"Tất cả".equals(priceRange)) {
                // We'll use a subquery to get the latest price for each product.
                // We'll build a condition like:
                // EXISTS (SELECT 1 FROM Price_History ph WHERE ph.Barcode = Product.Barcode AND ph.Valid_From_Date = (SELECT MAX(ph2.Valid_From_Date) FROM Price_History ph2 WHERE ph2.Barcode = Product.Barcode AND ph2.Valid_From_Date <= CURDATE()) AND ph.Price < 20000)
                // But we need to handle different ranges.
                // We'll build a generic condition: we will compute the latest price and then compare.
                // Since we cannot easily compute in WHERE without repeating subquery, we'll do:
                // WHERE (SELECT ph.Price FROM Price_History ph WHERE ph.Barcode = Product.Barcode ORDER BY ph.Valid_From_Date DESC LIMIT 1) < 20000
                // MySQL supports LIMIT in subquery? Yes, but we need to correlate.
                // We'll do:
                // WHERE (SELECT ph.Price FROM Price_History ph WHERE ph.Barcode = Product.Barcode AND ph.Valid_From_Date <= CURDATE() ORDER BY ph.Valid_From_Date DESC LIMIT 1) < 20000
                // We'll build the condition based on the selected range.

                // We'll build a subquery string that returns the latest price.
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

            // Tax rate
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

            // Remove trailing " AND " if any
            if (where.length() > 0 && where.toString().endsWith(" AND ")) {
                where.setLength(where.length() - 5);
            }
            if (where.length() == 0) {
                return new Pair<>("", new ArrayList<>());
            }
            return new Pair<>(where.toString(), params);
        }
    }

    /** Filter panel for Return table */
    class ReturnFilterPanel extends JPanel {
        // Return date: all, today, this week, this month
        private final JComboBox<String> cbDate;
        // Return quantity: all, =1, >1
        private final JComboBox<String> cbQuantity;

        ReturnFilterPanel() {
            setLayout(new BoxLayout(ReturnFilterPanel.this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            // Row 1: Return date
            JPanel dateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
            dateRow.add(new JLabel("Thời gian trả hàng: "));
            String[] dateOptions = {"Tất cả", "Hôm nay", "Tuần này", "Tháng này"};
            cbDate = new JComboBox<>(dateOptions);
            dateRow.add(cbDate);
            add(dateRow);

            // Row 2: Return quantity
            JPanel quantityRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
            quantityRow.add(new JLabel("Số lượng hoàn trả: "));
            String[] quantityOptions = {"Tất cả", "= 1", "> 1"};
            cbQuantity = new JComboBox<>(quantityOptions);
            quantityRow.add(cbQuantity);
            add(quantityRow);

            // Add action listeners
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

            // Return date
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
                // Note: No default case because all options are covered
            }

            // Return quantity
            String quantityOption = (String) cbQuantity.getSelectedItem();
            switch (quantityOption) {
                case "= 1":
                    where.append("Return_Quantity = 1 AND ");
                    break;
                case "> 1":
                    where.append("Return_Quantity > 1 AND ");
                    break;
                // Note: No default case because all options are covered
            }

            // Remove trailing " AND " if any
            if (where.length() > 0 && where.toString().endsWith(" AND ")) {
                where.setLength(where.length() - 5);
            }
            // If no conditions, return empty string and empty params
            if (where.length() == 0) {
                return new Pair<>("", new ArrayList<>());
            }
            return new Pair<>(where.toString(), params);
        }
    }

    // =================================================================
    // HELPER METHODS
    // =================================================================

    private JToggleButton createToggleButton(String text, boolean selected) {
        JToggleButton button = new JToggleButton(text);
        button.setSelected(selected);
        button.setFocusPainted(false);
        button.setMargin(new Insets(2, 5, 2, 5));
        return button;
    }

    /** Load all data from the selected table and display formatted */
    private void loadTableData(String tableName) {
        if (tableName == null || tableName.isEmpty()) return;

        try (Connection conn = Db.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM `" + tableName + "`")) {

            populateTable(rs);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Lỗi khi tải dữ liệu từ bảng '" + tableName + "':\n" + ex.getMessage(),
                    "Lỗi cơ sở dữ liệu", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    /** Perform search based on structured filters and keyword */
    private void performSearch() {
        String tableName = (String) cbTables.getSelectedItem();
        if (tableName == null || tableName.isEmpty()) return;

        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();

        // Add structured filters based on table
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
                // For other tables, we only have keyword search (if any)
                break;
        }

        // Add keyword search if not empty
        String keyword = tfSearch.getText().trim();
        if (!keyword.isEmpty()) {
            // We need to search across string-like columns; we'll get them dynamically
            // For simplicity, we'll search across all columns that are CHAR/VARCHAR etc.
            // We'll reuse the logic from the original performSearch method but only for keyword.
            try (Connection conn = Db.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM `" + tableName + "` LIMIT 1")) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                List<String> searchableCols = new ArrayList<>();
                for (int i = 1; i <= colCount; i++) {
                    int type = meta.getColumnType(i);
                    if (type == Types.CHAR || type == Types.VARCHAR ||
                            type == Types.LONGVARCHAR || type == Types.NCHAR ||
                            type == Types.NVARCHAR || type == Types.LONGNVARCHAR) {
                        searchableCols.add(meta.getColumnLabel(i));
                    }
                }
                if (!searchableCols.isEmpty()) {
                    if (where.length() > 0) {
                        where.append(" AND ");
                    }
                    where.append("(");
                    for (int i = 0; i < searchableCols.size(); i++) {
                        if (i > 0) where.append(" OR ");
                        where.append("`").append(searchableCols.get(i)).append("` LIKE ?");
                    }
                    // Note: we need to close the parenthesis
                    where.append(")");
                    for (int i = 0; i < searchableCols.size(); i++) {
                        params.add("%" + keyword + "%");
                    }
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this,
                        "Lỗi khi xác định cột tìm kiếm:\n" + ex.getMessage(),
                        "Lỗi cơ sở dữ liệu", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        }

        // If no WHERE clause, we select all
        String sql = "SELECT * FROM `" + tableName + "`";
        if (where.length() > 0) {
            sql += " WHERE " + where.toString();
        }

        // Add ORDER BY based on sort selection (if any)
        String orderBy = getOrderByClause(tableName);
        if (orderBy != null && !orderBy.isEmpty()) {
            sql += " ORDER BY " + orderBy;
        }

        try (Connection conn = Db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            // Set parameters
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                populateTable(rs);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Lỗi khi thực hiện tìm kiếm:\n" + ex.getMessage(),
                    "Lỗi cơ sở dữ liệu", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    /** Get ORDER BY clause for the given table based on the current filter panel selection */
    private String getOrderByClause(String tableName) {
        // We removed sorting rows, so no ORDER BY.
        return "";
    }

    /** Reload current table (show all data) */
    private void reloadCurrentTable() {
        String tableName = (String) cbTables.getSelectedItem();
        if (tableName != null && !tableName.isEmpty()) {
            // Reset filter panel to default state
            int index = cbTables.getSelectedIndex();
            resetFilterPanel(index);
            loadTableData(tableName);
            tfSearch.setText("");
        }
    }

    /** Fill table with formatted data from ResultSet */
    private void populateTable(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();

        // Prepare column names
        String[] columnNames = new String[colCount];
        for (int i = 1; i <= colCount; i++) {
            columnNames[i - 1] = meta.getColumnLabel(i);
        }

        // Prepare data rows with formatting
        DefaultTableModel model = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // read-only
            }
        };

        while (rs.next()) {
            Object[] row = new Object[colCount];
            for (int i = 1; i <= colCount; i++) {
                Object val = rs.getObject(i);
                int sqlType = meta.getColumnType(i);
                row[i - 1] = formatValue(val, sqlType, columnNames[i - 1]);
            }
            model.addRow(row);
        }

        table.setModel(model);
        Ui.autoSize(table); // Adjust column widths to content (like tab 2)
        updateRowCount();
    }

    /** Format a value according to its SQL type and column name for display */
    private String formatValue(Object value, int sqlType, String columnName) {
        if (value == null) {
            return "";
        }

        // Handle Tax_Rate column as percentage
        if (columnName != null && columnName.equalsIgnoreCase("Tax_Rate")) {
            if (value instanceof Number) {
                double d = ((Number) value).doubleValue();
                // Format without trailing zeros, then add '%'
                java.text.DecimalFormat fmt = new java.text.DecimalFormat("#0.##");
                fmt.setDecimalSeparatorAlwaysShown(false);
                fmt.setGroupingUsed(false);
                return fmt.format(d) + "%";
            }
            return value.toString();
        }

        // Treat quantity columns as integer (no currency symbol)
        if (isQuantityColumn(columnName)) {
            return formatInteger(value);
        }

        // Numeric types: treat as currency if fractional, else integer
        switch (sqlType) {
            case Types.NUMERIC:
            case Types.DECIMAL:
            case Types.DOUBLE:
            case Types.FLOAT:
            case Types.REAL:
                return formatCurrency(value);
            case Types.INTEGER:
            case Types.SMALLINT:
            case Types.TINYINT:
            case Types.BIGINT:
                return formatInteger(value);
            // Date/time types: show as string (default formatting)
            case Types.DATE:
            case Types.TIME:
            case Types.TIMESTAMP:
                return value.toString();
            // Other types: default string representation
            default:
                return value.toString();
        }
    }

    /** Format as currency (with thousand separator dot, and "đ" suffix) */
    private String formatCurrency(Object value) {
        if (value instanceof Number) {
            return CURRENCY_FORMAT.format(((Number) value).doubleValue()) + "đ";
        }
        return value.toString();
    }

    /** Format as integer (with thousand separator dot, no decimal) */
    private String formatInteger(Object value) {
        if (value instanceof Number) {
            return INTEGER_FORMAT.format(((Number) value).longValue());
        }
        return value.toString();
    }

    /** Update row count label */
    private void updateRowCount() {
        int rows = table.getModel().getRowCount();
        lbRowCount.setText("Tổng số hàng: " + rows + " dòng");
    }

    /** Reset the filter panel for the given index to default state */
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
        // For other panels (default message panels), there is no state to reset.
    }

    /** Helper to check if a column should be treated as a quantity (integer) rather than currency */
    private boolean isQuantityColumn(String columnName) {
        if (columnName == null) return false;
        String upper = columnName.toUpperCase();
        // Common quantity columns
        return upper.equals("RETURN_QUANTITY") ||
               upper.equals("QUANTITY") ||
               upper.equals("LINE_NUMBER") ||
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

    /**
     * Simple pair class to hold two related objects.
     * @param <F> type of first element
     * @param <S> type of second element
     */
    private class Pair<F, S> {
        public final F first;
        public final S second;
        public Pair(F first, S second) {
            this.first = first;
            this.second = second;
        }
    }
}