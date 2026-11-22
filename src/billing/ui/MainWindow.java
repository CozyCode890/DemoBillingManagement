package billing.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * ====================================================================
 *  MainWindow -- Cửa sổ chính của ứng dụng
 * ====================================================================
 *  Gom 4 màn hình vào các tab.
 *  Có thanh trạng thái hiển thị trạng thái hệ thống và nút đổi ngôn ngữ.
 */
public class MainWindow extends JFrame {

    private final InvoiceViewPanel viewPanel   = new InvoiceViewPanel();
    private final SearchPanel      searchPanel = new SearchPanel();

    /** Giữ làm field để kiểm tra giỏ hàng chưa lưu trước khi đóng ứng dụng. */
    private final NewInvoicePanel newInvoice;

    private final JLabel    lbStatus   = new JLabel();
    private final JButton   btLanguage = new JButton();
    private final JMenuItem miLanguage = new JMenuItem();

    public MainWindow() {
        super("Retail Invoicing");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1280, 820);
        setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(Ui.BOLD);

        // Lưu hóa đơn xong thì danh sách bên tab 3 tự tải lại.
        newInvoice = new NewInvoicePanel(viewPanel::reload);

        tabs.addTab(I18n.text("1. Lập hóa đơn", "1. Create Billing"), newInvoice);
        tabs.addTab(I18n.text("2. Tra cứu", "2. Search"),             searchPanel);
        tabs.addTab(I18n.text("3. Xem hóa đơn", "3. Invoices"),       viewPanel);
        tabs.addTab(I18n.text("4. Báo cáo", "4. Reports"),            new ReportPanel());

        add(tabs, BorderLayout.CENTER);
        add(statusBar(), BorderLayout.SOUTH);

        JMenu languageMenu = new JMenu(I18n.text("Ngôn ngữ", "Language"));
        miLanguage.addActionListener(e -> toggleLanguage());
        I18n.markDynamicText(miLanguage);
        languageMenu.add(miLanguage);
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(languageMenu);
        setJMenuBar(menuBar);
        btLanguage.addActionListener(e -> toggleLanguage());
        I18n.markDynamicText(btLanguage);
        refreshLanguageControls();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                confirmExit();
            }
        });

        I18n.install(this);
    }

    // ------------------------------------------------------------------
    //  Thanh trạng thái
    // ------------------------------------------------------------------

    /** Thanh trạng thái dưới đáy cửa sổ: Trạng thái + Đổi ngôn ngữ */
    private JComponent statusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(210, 210, 210)));

        lbStatus.setText(I18n.text("Hệ thống quản lý hóa đơn bán lẻ -- Sẵn sàng",
                                   "Retail Invoicing System -- Ready"));
        lbStatus.setFont(new Font("Consolas", Font.PLAIN, 12));
        lbStatus.setForeground(new Color(60, 60, 60));
        lbStatus.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 6));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        right.setOpaque(false);
        right.add(btLanguage);

        bar.add(lbStatus, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private void toggleLanguage() {
        I18n.toggle();
        refreshLanguageControls();
    }

    private void refreshLanguageControls() {
        btLanguage.setText(I18n.languageSwitchLabel());
        miLanguage.setText(I18n.languageSwitchLabel());
        String nextLanguage = I18n.isEnglish() ? "Tiếng Việt" : "English";
        String tip = I18n.isEnglish()
                ? "Switch the interface to " + nextLanguage
                : "Chuyển giao diện sang " + nextLanguage;
        btLanguage.setToolTipText(tip);
        miLanguage.setToolTipText(tip);
    }

    private void confirmExit() {
        String pending = newInvoice.pendingWorkSummary();
        if (!pending.isEmpty()) {
            String question = I18n.msg(
                "Tab \"Lập hóa đơn\" đang có " + pending + " chưa lưu.\n"
              + "Thoát ứng dụng sẽ mất toàn bộ. Vẫn thoát?",
                "Tab \"Create Billing\" has " + pending + " unsaved.\n"
              + "Exiting will discard everything. Exit anyway?");
            int choice = JOptionPane.showConfirmDialog(this, question,
                    I18n.msg("Xác nhận thoát", "Confirm Exit"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }
        dispose();
        System.exit(0);
    }
}
