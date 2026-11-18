package billing2.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Một tài khoản trên hàng nút "Quick login" (khóa quick.login.accounts trong config.properties).
 * Đăng nhập nhanh là ĐIỀN NHANH, không phải bỏ qua xác thực: vẫn gọi sp_authenticate_account.
 */
public record QuickAccount(String username, String password, String label) {

    /**
     * Cú pháp "user1:pass1:Nhan 1,user2:pass2". Mục sai cú pháp bị bỏ qua kèm cảnh báo,
     * các mục còn lại vẫn dùng được. Thiếu nhãn -> nhãn = username. KHÔNG trim mật khẩu.
     */
    public static List<QuickAccount> parse(String raw) {
        List<QuickAccount> list = new ArrayList<>();
        if (raw == null) return list;
        for (String item : raw.split(",")) {
            if (item.isBlank()) continue;
            String[] f = item.split(":", 3);
            String username = f[0].trim();
            String password = f.length >= 2 ? f[1] : "";
            if (username.isEmpty() || password.isEmpty()) {
                System.out.println("[quick.login.accounts] Skipped malformed entry: '" + item.trim()
                    + "'. Use the format: <username>:<password>[:<label>]");
                continue;
            }
            list.add(new QuickAccount(username, password,
                    f.length >= 3 && !f[2].isBlank() ? f[2].trim() : username));
        }
        return list;
    }

    /** Không bao giờ in mật khẩu. */
    @Override
    public String toString() { return label + " (" + username + ")"; }
}
