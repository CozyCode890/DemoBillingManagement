package billing.ui;

import billing.dao.ProductDao;
import billing.dao.ReportDao;
import billing.db.QueryResult;
import billing.model.Product;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Tab 4 -- BÁO CÁO: các câu truy vấn đề bài yêu cầu (+ 2 báo cáo thêm). Mỗi nút gọi một
 * thủ tục sp_report_* qua ReportDao, kết quả đổ vào bảng, kèm một đoạn giải thích.
 */
public class ReportPanel extends JPanel {

    private static final String DEFAULT_PRICE_BARCODE = "8934005";   // Nuoc mam Nam Ngu: đổi giá mỗi ngày

    private final ReportDao dao = new ReportDao();
    private final JTextArea taWhat = new JTextArea(3, 40);
    private final JTable    tbData = Ui.readOnlyTable();
    private final List<JButton> buttons = new ArrayList<>();
    private final JComboBox<Product> cbProduct = new JComboBox<>();
    private String[] explain = {"", ""};                    // {vi, en} của báo cáo đang hiện

    public ReportPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        taWhat.setEditable(false);
        taWhat.setLineWrap(true);
        taWhat.setWrapStyleWord(true);
        taWhat.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        taWhat.setBackground(new Color(255, 250, 225));
        taWhat.setMargin(new Insets(8, 8, 8, 8));

        // Đánh số 1-5 theo thứ tự nút (như nhóm sửa ở nhánh Loc-sql-v2); số câu đề bài
        // tương ứng ghi trong ngoặc: 1 = Q2, 2 = Q3, 3 = Q4, 4 = Q5.
        JPanel row1 = Ui.row(
            button("1. Doanh thu 7 ngày / cửa hàng", "1. 7-day revenue by shop",
                    "(Q2) Doanh thu từng NGÀY của từng CỬA HÀNG trong 7 ngày ĐÃ TRỌN gần nhất (không tính hôm nay -- "
                  + "ngày chưa kết thúc). Hóa đơn có trả hàng tính doanh thu ròng sau hoàn tiền; hóa đơn bị hủy không tính.",
                    "(Q2) Revenue for each SHOP on each DAY of the last 7 COMPLETED days (today is not included -- "
                  + "it is not over yet). Invoices with returns count their net revenue after refunds; voided invoices are excluded.",
                    dao::dailyTakings),
            button("2. Sản phẩm tăng giá > 10%", "2. Price rises > 10%",
                    "(Q3) Các lần đổi giá trong năm nay mà giá mới cao hơn giá ngay trước đó quá 10%.",
                    "(Q3) Price changes this year where the new price is more than 10% above the previous one.",
                    dao::priceRise),
            button("3. Thu ngân giỏ hàng TB cao nhất", "3. Cashier with the highest average basket",
                    "(Q4) Giá trị trung bình MỖI HÓA ĐƠN của từng thu ngân (sau trả hàng), cao nhất trước.",
                    "(Q4) Average value PER INVOICE for each cashier (after returns), highest first.",
                    dao::bestCashier),
            button("4. Hóa đơn trả hàng > 50%", "4. Invoices with > 50% returned",
                    "(Q5) Hóa đơn đã thanh toán mà khách trả lại hơn một nửa giá trị. Tiền hoàn tính theo số lượng trả, "
                  + "đã trừ phần giảm giá.",
                    "(Q5) Paid invoices where the customer returned more than half of the value. Refunds follow the "
                  + "returned quantity, discounts included.",
                    dao::bigReturns));
        JButton btOld = button("Bonus: hóa đơn cũ giữ giá cũ", "Bonus: old invoices keep old prices",
                "So sánh giá ghi trên hóa đơn (giá lúc bán) với giá hôm nay. Mỗi dòng: bao nhiêu dòng hàng đang ghi giá đó, "
              + "bán từ ngày nào đến ngày nào, và giá hôm nay là bao nhiêu -- giá trên hóa đơn cũ không đổi khi giá mới "
              + "có hiệu lực.",
                "Compares the price on each invoice (the price at the time of sale) with today's price. Each row: how many "
              + "item lines carry that price, when they were sold, and today's price -- old invoices keep their price "
              + "when a new price takes effect.",
                dao::oldPriceProof);

        String vi30 = "5. Giá 30 ngày (SL 1)", en30 = "5. 30-day price (qty 1)";
        JButton bt30 = new JButton(I18n.text(vi30, en30));
        buttons.add(bt30);
        bt30.addActionListener(e -> {
            Product p = (Product) cbProduct.getSelectedItem();          // đọc ô chọn trên EDT
            if (p == null) {
                Ui.warn(this, I18n.msg("Không đọc được danh sách sản phẩm!", "Cannot read product list!"),
                        I18n.msg("Chưa chọn sản phẩm", "No Product Selected"));
                return;
            }
            run(bt30, vi30, en30,
                    "Giá của MỘT sản phẩm với SỐ LƯỢNG 1 trong từng ngày của 30 ngày gần nhất, sau khuyến mãi -- đúng con "
                  + "số thu ngân thấy khi lập hóa đơn. Cột cuối so với ngày hôm trước. Ba sản phẩm đổi giá MỖI NGÀY: "
                  + "nước mắm Nam Ngư, cà phê G7, nước rửa tay Lifebuoy.",
                    "The price of ONE product at QUANTITY 1 for each of the last 30 days, after promotions -- the same "
                  + "price the cashier sees on a new invoice. The last column compares with the previous day. Three "
                  + "products change price EVERY DAY: Nam Ngu fish sauce, G7 coffee, Lifebuoy hand wash.",
                    () -> dao.price30Days(p.barcode));
        });
        cbProduct.setPreferredSize(new Dimension(300, 26));

        JPanel choose = new JPanel(new GridLayout(2, 1));
        choose.setBorder(BorderFactory.createTitledBorder(I18n.text("Chọn báo cáo", "Choose a report")));
        choose.add(row1);
        choose.add(Ui.row(cbProduct, bt30, new JLabel("     "), btOld));
        JPanel top = new JPanel(new BorderLayout(6, 6));
        top.add(choose, BorderLayout.NORTH);
        top.add(Ui.titled(I18n.text("Báo cáo này tính gì", "About this report"), new JScrollPane(taWhat)), BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);
        add(Ui.titled(I18n.text("Kết quả", "Results"), new JScrollPane(tbData)), BorderLayout.CENTER);

        // Đổi ngôn ngữ: viết lại đoạn giải thích (JTextArea không nằm trong từ điển).
        I18n.onLanguageChange(this, () -> taWhat.setText(I18n.msg(explain[0], explain[1])));

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

    private JButton button(String vi, String en, String explainVi, String explainEn, Supplier<QueryResult> report) {
        JButton b = new JButton(I18n.text(vi, en));
        buttons.add(b);
        b.addActionListener(e -> run(b, vi, en, explainVi, explainEn, report));
        return b;
    }

    /** Hiện giải thích -> chạy thủ tục NGOÀI EDT (khóa các nút trong lúc chờ) -> đổ kết quả vào bảng. */
    private void run(JButton b, String vi, String en, String explainVi, String explainEn, Supplier<QueryResult> report) {
        explain = new String[]{explainVi, explainEn};
        taWhat.setText(I18n.msg(explainVi, explainEn));
        taWhat.setCaretPosition(0);
        buttons.forEach(x -> x.setEnabled(false));
        b.setText(I18n.text(vi, en) + I18n.msg(" -- đang chạy...", " -- running..."));
        Ui.async(this, report, () -> { b.setText(I18n.text(vi, en)); buttons.forEach(x -> x.setEnabled(true)); }, qr -> {
            Ui.fill(tbData, qr);
            if (qr.isEmpty()) JOptionPane.showMessageDialog(this, I18n.msg("Báo cáo không có dòng nào.", "The report has no rows."));
        });
    }
}
