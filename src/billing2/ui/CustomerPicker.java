package billing2.ui;

import billing2.dao.LookupDao;
import billing2.model.CustomerRef;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Ô "Customer (phone)": gõ >= 3 chữ số -> chờ 250 ms -> tìm theo TIỀN TỐ số điện thoại
 * (sp_search_customers_by_phone) -> chọn trong danh sách gợi ý (chuột, hoặc lên/xuống + Enter).
 *
 * Chỉ khách ĐƯỢC CHỌN mới gắn vào hóa đơn; gõ đủ số mà không chọn vẫn là khách vãng lai.
 */
public class CustomerPicker extends JPanel {

    private final LookupDao lookupDao;
    private final JTextField tfPhone  = new JTextField(14);
    private final JLabel     lbStatus = new JLabel();
    private final DefaultListModel<CustomerRef> matches = new DefaultListModel<>();
    private final JList<CustomerRef> list  = new JList<>(matches);
    private final JPopupMenu popup = new JPopupMenu();
    private final Timer debounce = new Timer(250, e -> runSearch());   // gõ 10 số = 1-2 câu SQL, không phải 10

    private CustomerRef selected;
    private boolean settingText;          // đang tự đổi chữ trong ô -> đừng coi là người dùng gõ

    public CustomerPicker(LookupDao lookupDao) {
        super(new FlowLayout(FlowLayout.LEFT, 6, 0));
        this.lookupDao = lookupDao;
        setOpaque(false);

        tfPhone.setToolTipText("<html>Type the customer's PHONE NUMBER (3 digits or more) and pick from the suggestions.<br>"
                + "Only a customer <b>picked from the list</b> is added to the bill.</html>");
        list.setFocusable(false);         // con trỏ ở lại trong ô nhập
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { commitSelection(); }
        });
        popup.setFocusable(false);
        popup.add(new JScrollPane(list));
        debounce.setRepeats(false);

        tfPhone.getDocument().addDocumentListener(Ui.onChange(this::onTyped));
        tfPhone.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { onKey(e); }
        });
        add(tfPhone);
        add(lbStatus);
        updateStatus();
    }

    /** Khách đã chọn -- CHỈ khi ô nhập vẫn đúng số điện thoại của khách đó; null = khách vãng lai. */
    public CustomerRef getSelected() {
        return selected != null && selected.phone.equals(tfPhone.getText().trim()) ? selected : null;
    }

    public void clear() {
        debounce.stop();
        popup.setVisible(false);
        selected = null;
        setTextQuietly("");
        updateStatus();
    }

    private void onTyped() {
        if (settingText) return;
        String s = tfPhone.getText().trim();
        if (selected != null && !selected.phone.equals(s)) selected = null;     // sửa số -> bỏ chọn
        updateStatus();
        if (s.length() >= 3 && s.chars().allMatch(Character::isDigit)) {
            debounce.restart();
        } else {
            debounce.stop();
            popup.setVisible(false);
        }
    }

    /** Tìm NGOÀI EDT; kết quả về muộn (người dùng đã gõ tiếp) thì bỏ. */
    private void runSearch() {
        String prefix = tfPhone.getText().trim();
        new SwingWorker<List<CustomerRef>, Void>() {
            @Override protected List<CustomerRef> doInBackground() { return lookupDao.searchCustomersByPhone(prefix); }
            @Override protected void done() {
                if (!tfPhone.isShowing() || !prefix.equals(tfPhone.getText().trim())) return;
                matches.clear();
                try {
                    get().forEach(matches::addElement);
                } catch (Exception ex) {
                    System.out.println("[CustomerPicker] Cannot find customer: " + ex.getMessage());
                }
                if (matches.isEmpty()) {
                    popup.setVisible(false);
                    return;
                }
                list.setSelectedIndex(0);
                list.setVisibleRowCount(Math.min(matches.size(), 8));
                popup.pack();
                popup.show(tfPhone, 0, tfPhone.getHeight());
                tfPhone.requestFocusInWindow();
            }
        }.execute();
    }

    private void onKey(KeyEvent e) {
        if (!popup.isVisible() || matches.isEmpty()) return;
        int i = list.getSelectedIndex();
        switch (e.getKeyCode()) {
            case KeyEvent.VK_DOWN   -> list.setSelectedIndex(Math.min(i + 1, matches.size() - 1));
            case KeyEvent.VK_UP     -> list.setSelectedIndex(Math.max(i - 1, 0));
            case KeyEvent.VK_ENTER  -> commitSelection();
            case KeyEvent.VK_ESCAPE -> popup.setVisible(false);
            default -> { return; }
        }
        list.ensureIndexIsVisible(list.getSelectedIndex());
        e.consume();
    }

    private void commitSelection() {
        CustomerRef c = list.getSelectedValue();
        if (c == null) return;
        setTextQuietly(c.phone);          // ô nhập giữ SỐ ĐIỆN THOẠI, tên hiện ở nhãn bên cạnh
        selected = c;
        updateStatus();
        popup.setVisible(false);
        debounce.stop();
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

    private void updateStatus() {
        CustomerRef c = getSelected();
        lbStatus.setText(c == null ? "(walk-in customer)" : "→ " + c.shortLabel());
        lbStatus.setForeground(c == null ? Color.GRAY : Ui.GREEN);
        lbStatus.setFont(c == null ? new Font("Segoe UI", Font.ITALIC, 12) : Ui.BOLD);
    }
}
