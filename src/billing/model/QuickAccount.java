package billing.model;

import java.util.ArrayList;
import java.util.List;

/**
 * ====================================================================
 *  QuickAccount -- Một tài khoản trên hàng nút "Đăng nhập nhanh"
 * ====================================================================
 *  Đọc từ khoá {@code quick.login.accounts} trong {@code config.properties}.
 *
 *  <p><b>Đăng nhập nhanh là ĐIỀN NHANH, không phải BỎ QUA XÁC THỰC.</b>
 *  Lớp này chỉ mang cặp username/password đi; mọi đường vào app vẫn gọi thật
 *  {@code AccountDao.authenticate()} -> {@code sp_authenticate_account} trên
 *  MySQL. Xem plan/11-KE-HOACH-KHOI-2-DANG-NHAP-NHANH.md mục 2.2.
 */
public class QuickAccount {

    private final String username;
    private final String password;
    private final String label;      // nhãn hiển thị, ví dụ "Thu ngan"

    public QuickAccount(String username, String password, String label) {
        this.username = username;
        this.password = password;
        this.label = label;
    }

    /**
     * Phân tích chuỗi cấu hình thành danh sách tài khoản nhanh.
     *
     * <p>Cú pháp: {@code "user1:pass1:Nhan 1,user2:pass2:Nhan 2"}
     *
     * <p>Quy tắc, viết rõ ra đây để người sau không phải đoán:
     * <ul>
     *   <li>Tách mục bằng {@code ,}; mục rỗng (do {@code ,,} hay dấu phẩy cuối
     *       dòng) <b>bỏ qua im lặng</b> -- đó là lỗi gõ vô hại, không đáng một
     *       dòng cảnh báo.</li>
     *   <li>Mỗi mục {@code split(":", 3)}. Thiếu phần thứ 2 (mật khẩu) hoặc
     *       thiếu username -> <b>cảnh báo rồi bỏ qua mục đó</b>, các mục còn lại
     *       vẫn dùng được. Một dấu phẩy thừa không được làm màn hình đăng nhập
     *       chết.</li>
     *   <li>Thiếu phần thứ 3 -> nhãn mặc định là chính username.</li>
     *   <li>{@code trim()} từng trường, <b>trừ mật khẩu</b>: mật khẩu
     *       {@code " cashier "} và {@code "cashier"} là hai mật khẩu khác nhau,
     *       tự ý trim sẽ sinh ra một lỗi "sai mật khẩu" không thể hiểu nổi.</li>
     * </ul>
     *
     * @param raw giá trị thô của {@code quick.login.accounts}, null hoặc rỗng
     *            đều cho ra danh sách rỗng
     * @return danh sách tài khoản đọc được, không bao giờ null
     */
    public static List<QuickAccount> parse(String raw) {
        List<QuickAccount> list = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return list;
        }

        for (String item : raw.split(",", -1)) {
            if (item.isBlank()) {
                continue;               // ",," hay dấu phẩy cuối: lỗi gõ vô hại
            }

            String[] f = item.split(":", 3);
            String username = f[0].trim();
            // Không trim mật khẩu -- xem Javadoc phía trên.
            String password = (f.length >= 2) ? f[1] : null;

            if (username.isEmpty() || password == null || password.isEmpty()) {
                System.out.println("[quick.login.accounts] Bo qua muc sai cu phap: '" + item.trim()
                    + "'. Dung cu phap: <username>:<password>[:<nhan>]");
                continue;
            }

            String label = (f.length >= 3 && !f[2].isBlank()) ? f[2].trim() : username;
            list.add(new QuickAccount(username, password, label));
        }
        return list;
    }

    /** Nhãn đầy đủ cho nút: {@code "Thu ngan (cashier)"}. Không bao giờ chứa mật khẩu. */
    public String getButtonLabel() {
        return label + " (" + username + ")";
    }

    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getLabel()    { return label; }

    @Override
    public String toString() {
        return getButtonLabel();        // cố tình KHÔNG in mật khẩu
    }
}
