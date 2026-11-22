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
 * ====================================================================
 *  MÀN HÌNH 3 -- BÁO CÁO  (đề bài câu 2, 3, 4, 5)
 * ====================================================================
 *  Bấm một nút -> app hiện MÔ TẢ báo cáo (lời thường, cho nhân viên) và
 *  KẾT QUẢ ở bảng bên dưới.
 *
 *  <p>Khung "Câu SQL đang chạy" đã bỏ khỏi giao diện (feat/i18n): app dành
 *  cho nhân viên, họ không cần đọc SQL. Câu SQL vẫn nằm trong ReportDao
 *  (ContractCheck dùng) và vẫn được ghi vào SqlLog.
 */
public class ReportPanel extends JPanel {

    private final ReportDao dao = new ReportDao();

    private final JTextArea taWhat  = new JTextArea(3, 40);
    private final JTable    tbData  = Ui.readOnlyTable();

    // =================================================================
    //  NĂM NÚT BÁO CÁO -- để khoá hết trong lúc một báo cáo đang chạy
    // =================================================================
    //  Giữ danh sách vì cả năm phải bị khoá cùng lúc: bấm nhanh năm nút
    //  liên tiếp mà không khoá là năm câu SQL cùng chạy và năm lần đổ bảng
    //  tranh nhau (kịch bản #107).
    private final List<JButton> reportButtons = new ArrayList<>(6);

    /** Báo cáo 6 cần chọn sản phẩm; danh mục nạp NGOÀI EDT. */
    private final JComboBox<Product> cbPriceProduct = new JComboBox<>();
    /** Ba sản phẩm seed cho đổi giá MỖI NGÀY trong 30 ngày (02_seed.sql). */
    private static final String DEFAULT_PRICE_BARCODE = "8934005";

    public ReportPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        taWhat.setEditable(false);
        taWhat.setLineWrap(true);
        taWhat.setWrapStyleWord(true);
        I18n.markLocalizedText(taWhat);
        taWhat.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        taWhat.setBackground(new Color(255, 250, 225));
        taWhat.setMargin(new Insets(8, 8, 8, 8));

        // Hai hang CO DINH: nhan tieng Anh dai hon, mot FlowLayout tu xuong dong
        // thi hang thu hai bi cat mat (khung khong tu cao them).
        JPanel buttons = new JPanel(new GridLayout(2, 1, 0, 0));
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        buttons.add(row1);
        buttons.add(row2);
        buttons.setBorder(BorderFactory.createTitledBorder(I18n.text("Chọn báo cáo", "Choose a report")));

        row1.add(makeButton(I18n.text("1. Doanh thu 7 ngày / cửa hàng", "1. 7-day revenue by shop"),
                I18n.text("Doanh thu từng NGÀY của từng CỬA HÀNG trong 7 ngày gần nhất (không tính hôm nay). "
                        + "Hóa đơn đã trả hàng tính doanh thu ròng sau hoàn tiền; hóa đơn bị hủy không tính.",
                          "Revenue for each SHOP on each DAY of the last 7 completed days (excluding today). "
                        + "Invoices with returns count net revenue after refunds; voided invoices are excluded."),
                dao::dailyTakings));

        row1.add(makeButton(I18n.text("2. Sản phẩm tăng giá > 10%", "2. Price rises > 10%"),
                I18n.text("Các lần đổi giá trong năm nay mà giá mới cao hơn giá ngay trước đó trên 10%.",
                          "Price changes this year where the new price is more than 10% above the previous one."),
                dao::priceRise));

        row1.add(makeButton(I18n.text("3. Thu ngân giỏ hàng TB cao nhất", "3. Cashier with the highest average basket"),
                I18n.text("Giá trị trung bình MỘT HÓA ĐƠN của từng thu ngân, cao nhất ở trên cùng.",
                          "Average value PER INVOICE for each cashier, highest first."),
                dao::bestCashier));

        row1.add(makeButton(I18n.text("4. Hóa đơn trả hàng > 50%", "4. Invoices with > 50% returned"),
                I18n.text("Hóa đơn mà khách đã trả lại hơn một nửa giá trị. "
                        + "Tiền hoàn tính theo tỷ lệ số lượng trả, kể cả phần giảm giá.",
                          "Invoices where the customer returned more than half of the value. "
                        + "Refunds follow the returned quantity, discounts included."),
                dao::bigReturns));

        // --- Báo cáo 5: cần chọn sản phẩm -> nút riêng + ô chọn -----------
        JButton btPrice30 = new JButton(I18n.text("5. Giá 30 ngày (SL 1)", "5. 30-day price (qty 1)"));
        reportButtons.add(btPrice30);
        cbPriceProduct.setPreferredSize(new Dimension(300, 26));
        btPrice30.addActionListener(e -> {
            Product p = (Product) cbPriceProduct.getSelectedItem();
            if (p == null) {
                Ui.warn(this, I18n.msg("Chưa nạp được danh sách sản phẩm.", "Cannot read product list!"),
                        I18n.msg("Chưa chọn sản phẩm", "No Product Selected"));
                return;
            }
            final String barcode = p.barcode;            // doc o tren EDT
            runReport(btPrice30, btPrice30.getText(),
                    I18n.text("Giá của MỘT sản phẩm khi mua SỐ LƯỢNG 1, từng ngày trong 30 ngày gần nhất, "
                           + "đã trừ khuyến mãi -- đúng giá thu ngân thấy khi lập hóa đơn. "
                           + "Cột Chênh lệch so với hôm trước. Ba sản phẩm đổi giá MỖI NGÀY: "
                           + "Nước mắm Nam Ngư, Cà phê G7, Nước rửa tay Lifebuoy.",
                             "The price of ONE product at QUANTITY 1 for each of the last 30 days, "
                           + "after promotions -- the same price the cashier sees on a new invoice. "
                           + "The Change column compares with the previous day. Three products change price "
                           + "EVERY DAY: Nam Ngu fish sauce, G7 coffee, Lifebuoy hand wash."),
                    () -> dao.price30Days(barcode));
        });
        JPanel pricePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        pricePanel.add(cbPriceProduct);
        pricePanel.add(btPrice30);
        row2.add(pricePanel);
        // Đặt Bonus ở hàng 2 để hàng 1 (nhãn tiếng Anh dài hơn) không bị xuống dòng và che mất nút Bonus.
        row2.add(makeButton(I18n.text("Bonus: hóa đơn cũ giữ giá cũ", "Bonus: old invoices keep old prices"),
                I18n.text("So sánh giá ghi trên hóa đơn (giá lúc bán) với giá hôm nay. "
                        + "Mỗi dòng: bao nhiêu dòng hàng đang ghi giá đó, bán từ ngày nào đến ngày nào, "
                        + "và giá hôm nay là bao nhiêu -- giá trên hóa đơn cũ không đổi khi giá mới được áp dụng.",
                          "Compares the price on each invoice (the price at the time of sale) with today's price. "
                        + "Each row: how many item lines carry that price, when they were sold, "
                        + "and today's price -- old invoices keep their price when a new price takes effect."),
                dao::oldPriceProof));
        loadPriceProducts();

        JPanel top = new JPanel(new BorderLayout(6, 6));
        top.add(buttons, BorderLayout.NORTH);
        top.add(Ui.titled(I18n.text("Báo cáo này cho biết", "About this report"),
                new JScrollPane(taWhat)), BorderLayout.CENTER);

        add(top, BorderLayout.NORTH);
        add(Ui.titled(I18n.text("Kết quả", "Results"), new JScrollPane(tbData)), BorderLayout.CENTER);
    }

    /**
     * Dựng một nút báo cáo. Câu SQL chạy <b>ngoài EDT</b>.
     *
     * <p>Bản trước khối 6 gọi {@code action.get()} ngay trong
     * {@code ActionListener}, tức là một câu SQL chạy trên <b>Event Dispatch
     * Thread</b> và cửa sổ đứng hình cho tới khi MySQL trả lời. Đây là panel
     * <b>cuối cùng</b> còn làm thế: khối 2 đã sửa cho đăng nhập
     * (plan/11 mục 4.2), khối 3 cho trả hàng, khối 5 cho lưu hoá đơn và cho
     * danh mục. Với 7 ngày dữ liệu không ai thấy; với 91 ngày và một báo cáo
     * gọi hàm trên từng dòng thì thấy (plan/15 lỗi #20).
     *
     * <p>Năm nút dùng chung hàm này, nên đây là <b>một</b> chỗ sửa cho cả năm.
     */
    private JButton makeButton(String label, String explain, Supplier<QueryResult> action) {
        JButton b = new JButton(label);
        reportButtons.add(b);
        b.addActionListener(e -> runReport(b, b.getText(), explain, action));
        return b;
    }

    /**
     * Hiện mô tả báo cáo, rồi chạy {@code action} NGOÀI EDT và đổ kết quả
     * vào bảng. Sáu nút báo cáo dùng chung -- một chỗ sửa cho cả sáu.
     */
    private void runReport(JButton b, String label, String explain,
                           Supplier<QueryResult> action) {
        taWhat.setText(I18n.translate(explain));
        taWhat.setCaretPosition(0);

        // Khoá cả sáu nút và đổi nhãn nút đang chạy: một cửa sổ không
        // phản hồi mà không nói gì thì người dùng tưởng app treo.
        setButtonsEnabled(false);
        b.setText(label + I18n.msg(" -- đang chạy...", " -- running..."));

        new SwingWorker<QueryResult, Void>() {
            @Override
            protected QueryResult doInBackground() {
                return action.get();          // câu SQL: NGOÀI EDT
            }

            @Override
            protected void done() {
                b.setText(label);
                setButtonsEnabled(true);
                try {
                    QueryResult qr = get();   // ném lại lỗi của doInBackground
                    Ui.fill(tbData, qr);
                    if (qr.isEmpty()) {
                        JOptionPane.showMessageDialog(ReportPanel.this,
                                I18n.msg("Báo cáo không có dòng nào.", "The report has no rows."));
                    }
                } catch (Exception ex) {
                    Ui.error(ReportPanel.this, ex);
                }
            }
        }.execute();
    }

    /** Nạp danh mục sản phẩm cho ô chọn của báo cáo 6 -- NGOÀI EDT. */
    private void loadPriceProducts() {
        new SwingWorker<List<Product>, Void>() {
            @Override
            protected List<Product> doInBackground() {
                return new ProductDao().findAll();
            }

            @Override
            protected void done() {
                try {
                    cbPriceProduct.removeAllItems();
                    for (Product p : get()) {
                        cbPriceProduct.addItem(p);
                        if (DEFAULT_PRICE_BARCODE.equals(p.barcode)) cbPriceProduct.setSelectedItem(p);
                    }
                } catch (Exception ex) {
                    // Khong chan ca tab: 5 bao cao kia van chay duoc.
                    System.out.println("[ReportPanel] Cannot read product list: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void setButtonsEnabled(boolean on) {
        for (JButton b : reportButtons) {
            b.setEnabled(on);
        }
    }
}
