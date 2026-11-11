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

    private final JTextArea taSql   = Ui.sqlBox("-- Bấm một nút ở trên để chạy báo cáo");
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
        buttons.setBorder(BorderFactory.createTitledBorder("Chọn báo cáo"));

        buttons.add(makeButton("2. Doanh thu 7 ngày / cửa hàng",
                ReportDao.SQL_DAILY_TAKINGS,
                "Doanh thu từng NGÀY của từng CỬA HÀNG trong 7 ngày gần nhất. "
              + "Hóa đơn không nối thẳng tới Shop nên phải đi vòng: Invoice -> Counter -> Shop. "
              + "GROUP BY hai cột (shop, ngày) nên mỗi dòng kết quả là một ô trong bảng chéo.",
                dao::dailyTakings));

        buttons.add(makeButton("3. Sản phẩm tăng giá > 10%",
                ReportDao.SQL_PRICE_RISE,
                "SELF JOIN: nối bảng Price_History với CHÍNH NÓ để đặt giá mới cạnh giá cũ. "
              + "Subquery MAX(...) tìm mốc giá LIỀN TRƯỚC của cùng sản phẩm. "
              + "Điều kiện: giá mới > giá cũ * 1.10 và nằm trong năm nay.",
                dao::priceRise));

        buttons.add(makeButton("4. Thu ngân có giá trị hóa đơn trung bình cao nhất",
                ReportDao.SQL_BEST_CASHIER,
                "Bẫy phổ biến: AVG thẳng trên Invoice_Line sẽ ra trung bình mỗi DÒNG chứ không phải mỗi HÓA ĐƠN. "
              + "Phải tính tổng tiền từng hóa đơn trước (subquery), rồi mới AVG theo thu ngân.",
                dao::bestCashier));

        buttons.add(makeButton("5. Hóa đơn trả hàng > 50%",
                ReportDao.SQL_BIG_RETURNS,
                "Hai subquery: một tính tổng hóa đơn, một tính tiền trả lại. "
              + "Tiền trả lại tính theo TỶ LỆ (SL trả / SL mua) nhân thành tiền, để phần giảm giá cũng được hoàn đúng tỷ lệ.",
                dao::bigReturns));

        // Chữ giải thích SỬA Ở KHỐI 6 cùng lúc với câu truy vấn: bản cũ nói
        // "Hai hoa don INV-OLD-*..." vì đó là chỗ duy nhất có giá cũ. Từ khối
        // 6, mốc đổi giá nằm GIỮA cửa sổ 91 ngày nên chuyện đó xảy ra ở hàng
        // trăm dòng, và báo cáo gộp chúng lại (plan/15 mục 5.3).
        // Một chữ giải thích ghim bằng tay thì không có gì báo khi nó hết
        // đúng -- nên nó phải được sửa CÙNG LÚC với thủ tục.
        buttons.add(makeButton("Bonus: hóa đơn cũ giữ giá cũ",
                ReportDao.SQL_OLD_PRICE_PROOF,
                "Cột 'Gia_ghi_tren_HD' lấy từ Invoice_Line (đã đóng băng lúc bán). "
              + "Cột 'Gia_hom_nay' gọi fn_get_product_price(barcode, CURDATE()). "
              + "Báo cáo GOM theo (sản phẩm, đơn giá đã đóng băng), nên mỗi dòng trả lời: "
              + "'có bao nhiêu dòng hàng đang ghi giá này, bán từ ngày nào đến ngày nào, "
              + "và giá hôm nay là bao nhiêu'. Dòng có 'Ban_tu_ngay' xa nhất chính là hai "
              + "hóa đơn INV-OLD-* từ năm trước.",
                dao::oldPriceProof));

        JScrollPane spSql = new JScrollPane(taSql);
        spSql.setPreferredSize(new Dimension(100, 230));

        JPanel top = new JPanel(new BorderLayout(6, 6));
        top.add(buttons, BorderLayout.NORTH);
        top.add(Ui.titled("Ý nghĩa câu truy vấn", new JScrollPane(taWhat)), BorderLayout.CENTER);
        top.add(Ui.titled("Câu SQL đang chạy", spSql), BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(Ui.titled("Kết quả", new JScrollPane(tbData)), BorderLayout.CENTER);
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
            taWhat.setText(I18n.richText(explain));
            taWhat.setCaretPosition(0);

            // Khoá cả năm nút và đổi nhãn nút đang chạy: một cửa sổ không
            // phản hồi mà không nói gì thì người dùng tưởng app treo.
            setButtonsEnabled(false);
            b.setText(I18n.richText(label + " -- đang chạy..."));

            new SwingWorker<QueryResult, Void>() {
                @Override
                protected QueryResult doInBackground() {
                    return action.get();          // câu SQL: NGOÀI EDT
                }

                @Override
                protected void done() {
                    b.setText(I18n.richText(label));
                    setButtonsEnabled(true);
                    try {
                        QueryResult qr = get();   // ném lại lỗi của doInBackground
                        Ui.fill(tbData, qr);
                        if (qr.isEmpty()) {
                            I18n.showMessageDialog(ReportPanel.this,
                                    "Câu lệnh chạy xong nhưng không có dòng nào thỏa điều kiện.");
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
