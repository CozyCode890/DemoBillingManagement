package billing2.ui;

import billing2.dao.ProductDao;
import billing2.dao.ReportDao;
import billing2.db.QueryResult;
import billing2.model.Product;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Tab 3 -- BÁO CÁO: các câu truy vấn đề bài yêu cầu (+ 2 báo cáo thêm). Mỗi nút gọi một
 * thủ tục sp_report_* qua ReportDao, kết quả đổ vào bảng, kèm một đoạn giải thích.
 */
public class ReportPanel extends JPanel {

    private static final String DEFAULT_PRICE_BARCODE = "8934005";   // Nuoc mam Nam Ngu: đổi giá mỗi ngày
    private static final String PRICE_30 = "30-day price (qty 1)";

    private final ReportDao dao = new ReportDao();
    private final JTextArea taWhat = new JTextArea(3, 40);
    private final JTable    tbData = Ui.readOnlyTable();
    private final List<JButton> buttons = new ArrayList<>();
    private final JComboBox<Product> cbProduct = new JComboBox<>();

    public ReportPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        taWhat.setEditable(false);
        taWhat.setLineWrap(true);
        taWhat.setWrapStyleWord(true);
        taWhat.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        taWhat.setBackground(new Color(255, 250, 225));
        taWhat.setMargin(new Insets(8, 8, 8, 8));

        JPanel row1 = Ui.row(
            button("Q2. 7-day revenue by shop", "Revenue for each SHOP on each DAY of the last 7 days. "
                    + "Invoices with returns count only what the customer kept; voided invoices are excluded.", dao::dailyTakings),
            button("Q3. Price rises > 10%", "Price changes this year where the new price is more than 10% "
                    + "above the previous one.", dao::priceRise),
            button("Q4. Cashier with the highest average basket", "Average value PER INVOICE for each cashier, "
                    + "highest first.", dao::bestCashier),
            button("Q5. Invoices with > 50% returned", "Invoices where the customer returned more than half of the value. "
                    + "Refunds follow the returned quantity, discounts included.", dao::bigReturns),
            button("Bonus: old invoices keep old prices", "Compares the price on each invoice (the price at the time "
                    + "of sale) with today's price: old invoices keep their price when a new price takes effect.", dao::oldPriceProof));

        JButton bt30 = new JButton(PRICE_30);
        buttons.add(bt30);
        bt30.addActionListener(e -> {
            Product p = (Product) cbProduct.getSelectedItem();          // đọc ô chọn trên EDT
            if (p == null) {
                Ui.warn(this, "Cannot read product list!", "No Product Selected");
                return;
            }
            run(bt30, PRICE_30, "The price of ONE product at QUANTITY 1 for each of the last 30 days, after promotions "
                    + "-- the same price the cashier sees on a new invoice. The last column compares with the previous day. "
                    + "Three products change price EVERY DAY: Nam Ngu fish sauce, G7 coffee, Lifebuoy hand wash.",
                    () -> dao.price30Days(p.barcode));
        });
        cbProduct.setPreferredSize(new Dimension(300, 26));

        JPanel choose = new JPanel(new GridLayout(2, 1));
        choose.setBorder(BorderFactory.createTitledBorder("Choose a report"));
        choose.add(row1);
        choose.add(Ui.row(cbProduct, bt30));
        JPanel top = new JPanel(new BorderLayout(6, 6));
        top.add(choose, BorderLayout.NORTH);
        top.add(Ui.titled("About this report", new JScrollPane(taWhat)), BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);
        add(Ui.titled("Results", new JScrollPane(tbData)), BorderLayout.CENTER);

        // Danh sách sản phẩm cho báo cáo 30 ngày -- lỗi chỉ in ra console, các báo cáo khác vẫn chạy.
        new SwingWorker<List<Product>, Void>() {
            @Override protected List<Product> doInBackground() { return new ProductDao().findAll(); }
            @Override protected void done() {
                try {
                    for (Product p : get()) {
                        cbProduct.addItem(p);
                        if (DEFAULT_PRICE_BARCODE.equals(p.barcode)) cbProduct.setSelectedItem(p);
                    }
                } catch (Exception ex) {
                    System.out.println("[ReportPanel] Cannot read product list: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private JButton button(String label, String explain, Supplier<QueryResult> report) {
        JButton b = new JButton(label);
        buttons.add(b);
        b.addActionListener(e -> run(b, label, explain, report));
        return b;
    }

    /** Hiện giải thích -> chạy thủ tục NGOÀI EDT (khóa các nút trong lúc chờ) -> đổ kết quả vào bảng. */
    private void run(JButton b, String label, String explain, Supplier<QueryResult> report) {
        taWhat.setText(explain);
        taWhat.setCaretPosition(0);
        buttons.forEach(x -> x.setEnabled(false));
        b.setText(label + " -- running...");
        Ui.async(this, report, () -> { b.setText(label); buttons.forEach(x -> x.setEnabled(true)); }, qr -> {
            Ui.fill(tbData, qr);
            if (qr.isEmpty()) JOptionPane.showMessageDialog(this, "The report has no rows.");
        });
    }
}
