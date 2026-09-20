package billing.ui;

import billing.dao.InvoiceDao;
import billing.db.QueryResult;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;

/**
 * ====================================================================
 *  MÀN HÌNH 2 -- XEM HÓA ĐƠN  (đề bài câu 1)
 * ====================================================================
 *  Bên trái: danh sách hóa đơn.
 *  Bên phải: bấm vào hóa đơn nào thì chạy 4 câu SELECT để lấy
 *            phần đầu, các dòng hàng, các lần thanh toán và trả hàng.
 *
 *  Chú ý là mỗi lần bạn bấm chuột, app gửi xuống MySQL 4 câu lệnh.
 *  Mở tab "Nhat ky SQL" để thấy chúng.
 */
public class InvoiceViewPanel extends JPanel {

    private final InvoiceDao dao = new InvoiceDao();

    private final JTable tbInvoices = Ui.readOnlyTable();
    private final JTable tbLines    = Ui.readOnlyTable();
    private final JTable tbPayments = Ui.readOnlyTable();
    private final JTable tbReturns  = Ui.readOnlyTable();
    private final JTextArea taHeader = new JTextArea(8, 40);

    public InvoiceViewPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        taHeader.setEditable(false);
        taHeader.setFont(Ui.MONO);

        JButton btReload = new JButton("Tai lai danh sach");
        btReload.addActionListener(e -> reload());

        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.add(btReload, BorderLayout.NORTH);
        left.add(new JScrollPane(tbInvoices), BorderLayout.CENTER);
        left.setBorder(BorderFactory.createTitledBorder("Danh sach hoa don (bam de xem chi tiet)"));

        JPanel right = new JPanel(new GridLayout(4, 1, 6, 6));
        right.add(Ui.titled("Thong tin chung + tong tien", new JScrollPane(taHeader)));
        right.add(Ui.titled("Cac dong hang (Invoice_Line)", new JScrollPane(tbLines)));
        right.add(Ui.titled("Thanh toan (Payment + bang con)", new JScrollPane(tbPayments)));
        right.add(Ui.titled("Tra hang (Return)", new JScrollPane(tbReturns)));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.42);
        add(split, BorderLayout.CENTER);

        tbInvoices.getSelectionModel().addListSelectionListener(this::onSelect);
        reload();
    }

    public void reload() {
        try {
            Ui.fill(tbInvoices, dao.recentInvoices());
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    private void onSelect(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;
        int r = tbInvoices.getSelectedRow();
        if (r < 0) return;

        String invoiceId = String.valueOf(tbInvoices.getValueAt(r, 0));
        try {
            // --- 1. Phần đầu hóa đơn -------------------------------
            QueryResult h = dao.invoiceHeader(invoiceId);
            StringBuilder sb = new StringBuilder();
            if (!h.isEmpty()) {
                Object[] row = h.rows.get(0);
                for (int i = 0; i < h.columns.size(); i++) {
                    sb.append(String.format("%-18s : %s%n", h.columns.get(i), row[i]));
                }
            }
            taHeader.setText(sb.toString());
            taHeader.setCaretPosition(0);

            // --- 2, 3, 4 -------------------------------------------
            Ui.fill(tbLines,    dao.invoiceLines(invoiceId));
            Ui.fill(tbPayments, dao.invoicePayments(invoiceId));
            Ui.fill(tbReturns,  dao.invoiceReturns(invoiceId));

        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }
}
