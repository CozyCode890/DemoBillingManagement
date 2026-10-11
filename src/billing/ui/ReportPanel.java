package billing.ui;

import billing.dao.ReportDao;
import billing.db.QueryResult;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * ====================================================================
 *  MÀN HÌNH 3 -- BÁO CÁO  (đề bài câu 2, 3, 4, 5)
 * ====================================================================
 *  Bấm một nút -> app hiện CÂU SQL ở khung đen bên trên và KẾT QUẢ ở
 *  bảng bên dưới. Đọc song song hai thứ đó là cách nhanh nhất để hiểu
 *  một câu SQL đang làm gì.
 */
public class ReportPanel extends JPanel {

    private final ReportDao dao = new ReportDao();

    private final JTextArea taSql   = Ui.sqlBox("-- Bam mot nut o tren de chay bao cao");
    private final JTextArea taWhat  = new JTextArea(3, 40);
    private final JTable    tbData  = Ui.readOnlyTable();

    // =================================================================
    //  NĂM NÚT BÁO CÁO -- để khoá hết trong lúc một báo cáo đang chạy
    // =================================================================
    //  Giữ danh sách vì cả năm phải bị khoá cùng lúc: bấm nhanh năm nút
    //  liên tiếp mà không khoá là năm câu SQL cùng chạy và năm lần đổ bảng
    //  tranh nhau (kịch bản #107).
    private final List<JButton> reportButtons = new ArrayList<>(5);

    public ReportPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        taWhat.setEditable(false);
        taWhat.setLineWrap(true);
        taWhat.setWrapStyleWord(true);
        taWhat.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        taWhat.setBackground(new Color(255, 250, 225));
        taWhat.setMargin(new Insets(8, 8, 8, 8));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        buttons.setBorder(BorderFactory.createTitledBorder("Chon bao cao"));

        buttons.add(makeButton("2. Doanh thu 7 ngay / cua hang",
                ReportDao.SQL_DAILY_TAKINGS,
                "Doanh thu tung NGAY cua tung CUA HANG trong 7 ngay gan nhat. "
              + "Hoa don khong noi thang toi Shop nen phai di vong: Invoice -> Counter -> Shop. "
              + "GROUP BY hai cot (shop, ngay) nen moi dong ket qua la mot o trong bang cheo.",
                dao::dailyTakings));

        buttons.add(makeButton("3. San pham tang gia > 10%",
                ReportDao.SQL_PRICE_RISE,
                "SELF JOIN: noi bang Price_History voi CHINH NO de dat gia moi canh gia cu. "
              + "Subquery MAX(...) tim moc gia LIEN TRUOC cua cung san pham. "
              + "Dieu kien: gia moi > gia cu * 1.10 va nam trong nam nay.",
                dao::priceRise));

        buttons.add(makeButton("4. Thu ngan gio hang TB cao nhat",
                ReportDao.SQL_BEST_CASHIER,
                "Bay pho bien: AVG thang tren Invoice_Line se ra trung binh moi DONG chu khong phai moi HOA DON. "
              + "Phai tinh tong tien tung hoa don truoc (subquery), roi moi AVG theo thu ngan.",
                dao::bestCashier));

        buttons.add(makeButton("5. Hoa don tra hang > 50%",
                ReportDao.SQL_BIG_RETURNS,
                "Hai subquery: mot tinh tong hoa don, mot tinh tien tra lai. "
              + "Tien tra lai tinh theo TY LE (SL tra / SL mua) nhan thanh tien, de phan giam gia cung duoc hoan dung ty le.",
                dao::bigReturns));

        // Chữ giải thích SỬA Ở KHỐI 6 cùng lúc với câu truy vấn: bản cũ nói
        // "Hai hoa don INV-OLD-*..." vì đó là chỗ duy nhất có giá cũ. Từ khối
        // 6, mốc đổi giá nằm GIỮA cửa sổ 91 ngày nên chuyện đó xảy ra ở hàng
        // trăm dòng, và báo cáo gộp chúng lại (plan/15 mục 5.3).
        // Một chữ giải thích ghim bằng tay thì không có gì báo khi nó hết
        // đúng -- nên nó phải được sửa CÙNG LÚC với thủ tục.
        buttons.add(makeButton("Bonus: hoa don cu giu gia cu",
                ReportDao.SQL_OLD_PRICE_PROOF,
                "Cot 'Gia_ghi_tren_HD' lay tu Invoice_Line (da dong bang luc ban). "
              + "Cot 'Gia_hom_nay' goi fn_get_product_price(barcode, CURDATE()). "
              + "Bao cao GOM theo (san pham, don gia da dong bang), nen moi dong tra loi: "
              + "'co bao nhieu dong hang dang ghi gia nay, ban tu ngay nao den ngay nao, "
              + "va gia hom nay la bao nhieu'. Dong co 'Ban_tu_ngay' xa nhat chinh la hai "
              + "hoa don INV-OLD-* tu nam truoc.",
                dao::oldPriceProof));

        JScrollPane spSql = new JScrollPane(taSql);
        spSql.setPreferredSize(new Dimension(100, 230));

        JPanel top = new JPanel(new BorderLayout(6, 6));
        top.add(buttons, BorderLayout.NORTH);
        top.add(Ui.titled("Y nghia cau truy van", new JScrollPane(taWhat)), BorderLayout.CENTER);
        top.add(Ui.titled("Cau SQL dang chay", spSql), BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(Ui.titled("Ket qua", new JScrollPane(tbData)), BorderLayout.CENTER);
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
    private JButton makeButton(String label, String sql, String explain,
                               Supplier<QueryResult> action) {
        JButton b = new JButton(label);
        reportButtons.add(b);
        b.addActionListener(e -> {
            taSql.setText(sql);
            taSql.setCaretPosition(0);
            taWhat.setText(explain);
            taWhat.setCaretPosition(0);

            // Khoá cả năm nút và đổi nhãn nút đang chạy: một cửa sổ không
            // phản hồi mà không nói gì thì người dùng tưởng app treo.
            setButtonsEnabled(false);
            b.setText(label + " -- dang chay...");

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
                                    "Cau lenh chay xong nhung khong co dong nao thoa dieu kien.");
                        }
                    } catch (Exception ex) {
                        Ui.error(ReportPanel.this, ex);
                    }
                }
            }.execute();
        });
        return b;
    }

    private void setButtonsEnabled(boolean on) {
        for (JButton b : reportButtons) {
            b.setEnabled(on);
        }
    }
}
