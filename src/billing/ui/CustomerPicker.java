package billing.ui;

import billing.dao.LookupDao;
import billing.model.CustomerRef;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * ====================================================================
 *  CustomerPicker -- ô SỐ ĐIỆN THOẠI + popup gợi ý khách hàng
 * ====================================================================
 *
 *  <p><b>Bất biến (plan/14 mục 3.5):</b>
 *  <pre>
 *  getSelected() != null   &lt;=&gt;   tfPhone.getText().trim() == selected.phone
 *  </pre>
 *
 *  <p>Mọi thay đổi nội dung ô làm vỡ đẳng thức này đều đưa về <b>khách vãng
 *  lai</b>. Bất biến được kiểm <b>hai lần</b>: {@code DocumentListener} xoá
 *  {@code selected} ở chỗ GHI, và {@link #getSelected()} kiểm lại ở chỗ ĐỌC.
 *  Lỗi #10 là một lỗi "quên xoá ở một nhánh" -- phép kiểm ở chỗ đọc thì không
 *  nhánh nào thoát được.
 *
 *  <p>Bốn điểm khác bản cũ ({@code NewInvoicePanel} dòng 356-527):
 *  <ul>
 *    <li><b>Chọn xong thì ô chứa SỐ ĐIỆN THOẠI</b>, không chứa
 *        {@code "CUS001 - Nguyen Van A - 0901234567"}. Tên khách đi vào nhãn
 *        bên cạnh, nên gõ thêm một ký tự vẫn là một lần tìm hợp lệ (lỗi #12).</li>
 *    <li><b>Không giật con trỏ</b> (lỗi #13): {@code JPopupMenu} không nhận
 *        focus, và không gọi {@code requestFocusInWindow()} -- mũi lên/xuống/
 *        Enter/Esc xử lý ngay trên ô nhập, con trỏ ở lại trong ô.</li>
 *    <li><b>Chỉ tìm khi nội dung toàn chữ số và dài &ge; 3</b>, có
 *        <b>hoãn 250ms</b> và chạy trong {@code SwingWorker}: gõ 10 số là
 *        1-2 câu SQL, không phải 10, và không câu nào chạy trên EDT (lỗi #14).</li>
 *    <li><b>Không gọi lại database</b> để lấy object vừa hiện trên màn hình:
 *        popup giữ {@code List&lt;CustomerRef&gt;} của lần tìm.</li>
 *  </ul>
 */
public class CustomerPicker extends JPanel {

    /** Dưới 3 chữ số thì mọi khách đều khớp -- một câu SQL không nói gì cả. */
    private static final int MIN_DIGITS = 3;

    /** Hoãn giữa ký tự cuối và câu SQL. Gõ nhanh hơn thế thì timer khởi động lại. */
    private static final int DEBOUNCE_MS = 250;

    private final LookupDao lookupDao;

    private final JTextField tfPhone  = new JTextField(14);
    private final JLabel     lbStatus = new JLabel();

    private final DefaultListModel<CustomerRef> listModel = new DefaultListModel<>();
    private final JList<CustomerRef>            list      = new JList<>(listModel);
    private final JPopupMenu                    popup     = new JPopupMenu();
    private final JScrollPane                   popupScroll;

    private final Timer debounce;

    /** Khách đang chọn, hoặc null = khách vãng lai. */
    private CustomerRef selected;

    /** Kết quả lần tìm gần nhất -- để chọn xong KHÔNG phải gọi lại database. */
    private List<CustomerRef> lastMatches = new ArrayList<>();

    /** Đang tự đặt nội dung ô -- đừng để DocumentListener hiểu đó là người gõ. */
    private boolean settingText;

    public CustomerPicker(LookupDao lookupDao) {
        this.lookupDao = lookupDao;

        setLayout(new FlowLayout(FlowLayout.LEFT, 6, 0));
        setOpaque(false);

        tfPhone.setToolTipText("<html>Go SO DIEN THOAI cua khach (tu 3 chu so tro len)"
                + " roi chon tu danh sach goi y.<br>"
                + "Chi khach <b>duoc chon tu danh sach</b> moi duoc gan vao hoa don"
                + " -- go du so ma khong chon van la khach vang lai.</html>");

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(22);
        list.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        list.setFocusable(false);                 // con tro o lai trong o nhap
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                                                          boolean sel, boolean focus) {
                super.getListCellRendererComponent(l, value, index, sel, focus);
                if (value instanceof CustomerRef) {
                    setText(((CustomerRef) value).display());
                }
                return this;
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { commitSelection(); }
        });

        popupScroll = new JScrollPane(list);
        popupScroll.setBorder(BorderFactory.createEmptyBorder());
        popup.setFocusable(false);                // KHONG lay focus khoi o nhap
        popup.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        popup.add(popupScroll);

        debounce = new Timer(DEBOUNCE_MS, e -> runSearch());
        debounce.setRepeats(false);

        tfPhone.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { onTyped(); }
            @Override public void removeUpdate(DocumentEvent e)  { onTyped(); }
            @Override public void changedUpdate(DocumentEvent e) { onTyped(); }
        });

        tfPhone.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { onKey(e); }
        });

        add(tfPhone);
        add(lbStatus);
        updateStatus();
    }

    /** Ô nhập, để chỗ gọi đặt tooltip / kích thước / thứ tự tab nếu cần. */
    public JTextField field() { return tfPhone; }

    /**
     * Khách đang chọn, hoặc {@code null} = khách vãng lai.
     *
     * <p>KHÔNG BAO GIỜ trả về một khách mà số điện thoại của họ khác nội dung
     * đang hiện trong ô -- bất biến mục 3.5, kiểm lần thứ hai ngay tại đây.
     */
    public CustomerRef getSelected() {
        if (selected == null) return null;
        return selected.phone.equals(tfPhone.getText().trim()) ? selected : null;
    }

    /**
     * Xoá ô và khách đang chọn. {@code resetForm()} của tab 1 gọi hàm này sau
     * khi lưu, nếu không thì hoá đơn TIẾP THEO tự động gắn cho khách của hoá
     * đơn trước (plan/14 lỗi #11).
     *
     * <p>Dừng luôn timer: nó có thể còn đang đếm 250ms khi người dùng đăng xuất
     * giữa lúc đang gõ (plan/14 rủi ro #12).
     */
    public void clear() {
        debounce.stop();
        hidePopup();
        selected = null;
        setTextQuietly("");
        updateStatus();
    }

    // -----------------------------------------------------------------

    private void onTyped() {
        if (settingText) return;

        // Bất biến mục 3.5 ở chỗ GHI: nội dung ô không còn là số điện thoại
        // của khách đang chọn -> về khách vãng lai, NGAY, không chờ gì.
        if (selected != null && !selected.phone.equals(tfPhone.getText().trim())) {
            selected = null;
        }
        updateStatus();

        if (digitsOnly(tfPhone.getText().trim())) {
            debounce.restart();
        } else {
            debounce.stop();
            hidePopup();
        }
    }

    /** Toàn chữ số và dài &ge; {@value #MIN_DIGITS}. */
    private static boolean digitsOnly(String s) {
        if (s.length() < MIN_DIGITS) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    /**
     * Một câu SQL, NGOÀI EDT.
     *
     * <p>{@code p_prefix} ở đây chắc chắn là chuỗi toàn chữ số, nên không có ký
     * tự {@code %} hay {@code _} lọt vào làm rộng điều kiện {@code LIKE} của
     * thủ tục.
     */
    private void runSearch() {
        final String prefix = tfPhone.getText().trim();
        if (!digitsOnly(prefix)) return;

        new SwingWorker<List<CustomerRef>, Void>() {
            @Override
            protected List<CustomerRef> doInBackground() {
                return lookupDao.searchCustomersByPhone(prefix);
            }

            @Override
            protected void done() {
                // Panel co the da bi dispose trong luc cho (dang xuat giua luc
                // dang go) -- ve popup tren mot o khong con tren man hinh thi
                // popup treo lo lung giua desktop.
                if (!tfPhone.isShowing()) return;
                // Nguoi dung da go tiep -> ket qua nay da cu, bo di.
                if (!prefix.equals(tfPhone.getText().trim())) return;

                List<CustomerRef> matches;
                try {
                    matches = get();
                } catch (Exception ex) {
                    // Tim khach hang that bai KHONG duoc chan viec lap hoa don:
                    // khach vang lai van ban duoc. Mot dong ra console la du.
                    System.out.println("[CustomerPicker] Khong tim duoc khach hang: " + ex.getMessage());
                    hidePopup();
                    return;
                }

                lastMatches = matches;
                listModel.clear();
                for (CustomerRef c : matches) listModel.addElement(c);

                if (matches.isEmpty()) {
                    hidePopup();
                } else {
                    showPopup();
                }
            }
        }.execute();
    }

    private void showPopup() {
        int rows   = Math.min(listModel.size(), 8);
        int height = rows * list.getFixedCellHeight() + 6;
        int width  = Math.max(tfPhone.getWidth(), contentWidth() + 24);
        popupScroll.setPreferredSize(new Dimension(width, Math.min(height, 200)));
        popup.setPreferredSize(null);
        popup.pack();
        if (list.getSelectedIndex() < 0 && !listModel.isEmpty()) {
            list.setSelectedIndex(0);
        }
        popup.show(tfPhone, 0, tfPhone.getHeight());
        // show() co the doi focus tuy look-and-feel; dua con tro ve o nhap.
        tfPhone.requestFocusInWindow();
    }

    private int contentWidth() {
        FontMetrics fm = list.getFontMetrics(list.getFont());
        int max = 0;
        for (int i = 0; i < listModel.size(); i++) {
            max = Math.max(max, fm.stringWidth(listModel.get(i).display()));
        }
        return max;
    }

    private void hidePopup() {
        if (popup.isVisible()) popup.setVisible(false);
    }

    /**
     * Mũi lên / xuống / Enter / Esc xử lý ngay trên ô nhập.
     *
     * <p>Chỉ {@code consume()} khi popup đang hiện: Enter lúc popup đóng vẫn
     * phải đi tới chỗ khác (ví dụ nút mặc định của hộp thoại).
     */
    private void onKey(KeyEvent e) {
        if (!popup.isVisible()) return;
        int n = listModel.size();
        if (n == 0) return;
        int i = list.getSelectedIndex();

        switch (e.getKeyCode()) {
            case KeyEvent.VK_DOWN:
                list.setSelectedIndex(Math.min(i + 1, n - 1));
                list.ensureIndexIsVisible(list.getSelectedIndex());
                e.consume();
                break;
            case KeyEvent.VK_UP:
                list.setSelectedIndex(Math.max(i - 1, 0));
                list.ensureIndexIsVisible(list.getSelectedIndex());
                e.consume();
                break;
            case KeyEvent.VK_ENTER:
                commitSelection();
                e.consume();
                break;
            case KeyEvent.VK_ESCAPE:
                hidePopup();
                e.consume();
                break;
            default:
                break;
        }
    }

    /** Chốt khách đang trỏ trong popup: ô nhận SỐ ĐIỆN THOẠI, tên vào nhãn. */
    private void commitSelection() {
        int i = list.getSelectedIndex();
        if (i < 0 || i >= lastMatches.size()) return;

        CustomerRef c = lastMatches.get(i);
        setTextQuietly(c.phone);       // o chua SO DIEN THOAI (loi #12)
        selected = c;                  // dat SAU khi doi text, de onTyped khong xoa
        updateStatus();
        hidePopup();
        debounce.stop();               // noi dung o vua doi -> dung tim lai
        tfPhone.requestFocusInWindow();
        tfPhone.setCaretPosition(tfPhone.getText().length());
    }

    private void setTextQuietly(String text) {
        settingText = true;
        try {
            tfPhone.setText(text);
        } finally {
            settingText = false;
        }
    }

    /**
     * Nhãn cạnh ô: bất biến mục 3.5 <b>kiểm được bằng mắt</b>.
     *
     * <p>Người dùng thấy hoá đơn sắp gắn cho ai TRƯỚC khi bấm lưu, thay vì phát
     * hiện ra sau (lỗi #10, #11, #47).
     */
    private void updateStatus() {
        CustomerRef c = getSelected();
        if (c == null) {
            lbStatus.setText("(khach vang lai)");
            lbStatus.setForeground(Color.GRAY);
            lbStatus.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        } else {
            lbStatus.setText("→ " + c.shortLabel());
            lbStatus.setForeground(new Color(0, 110, 0));
            lbStatus.setFont(Ui.BOLD);
        }
    }
}
