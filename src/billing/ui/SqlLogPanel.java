package billing.ui;

import billing.db.SqlLog;

import javax.swing.*;
import java.awt.*;

/**
 * ====================================================================
 *  MÀN HÌNH 4 -- NHẬT KÝ SQL
 * ====================================================================
 *  Không liên quan tới đề bài, nhưng theo tôi đây là tab hữu ích nhất
 *  cho người mới: nó cho thấy MỖI THAO TÁC TRÊN GIAO DIỆN tương ứng với
 *  câu lệnh nào gửi xuống MySQL, mất bao nhiêu mili-giây, trả về mấy dòng.
 *
 *  Thử làm thế này:
 *    1. Mở tab nay, bam "Xoa nhat ky".
 *    2. Sang tab "Lap hoa don", them 1 san pham.
 *    3. Quay lai day, bam "Lam moi" -> ban se thay dung 2 cau SELECT:
 *       mot cau tim gia trong Price_History, mot cau tim khuyen mai.
 */
public class SqlLogPanel extends JPanel {

    private final JTextArea ta = new JTextArea();

    public SqlLogPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        ta.setEditable(false);
        ta.setFont(Ui.MONO);
        ta.setBackground(new Color(24, 26, 30));
        ta.setForeground(new Color(200, 220, 240));

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        JButton bRefresh = new JButton("Lam moi");
        bRefresh.addActionListener(e -> refresh());
        JButton bClear = new JButton("Xoa nhat ky");
        bClear.addActionListener(e -> { SqlLog.clear(); refresh(); });
        bar.add(bRefresh);
        bar.add(bClear);
        bar.add(new JLabel("Moi cau SQL app gui xuong MySQL deu duoc ghi lai o day "
                         + "(va in ra cua so console)."));

        add(bar, BorderLayout.NORTH);
        add(new JScrollPane(ta), BorderLayout.CENTER);
        refresh();
    }

    public void refresh() {
        ta.setText(SqlLog.dump());
        ta.setCaretPosition(ta.getDocument().getLength());
    }
}
