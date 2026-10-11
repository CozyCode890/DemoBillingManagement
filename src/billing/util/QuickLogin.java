package billing.util;

import billing.model.QuickAccount;

import java.util.List;

/**
 * ====================================================================
 *  QuickLogin -- Cửa ngõ duy nhất tới 4 khoá cấu hình "đăng nhập nhanh"
 * ====================================================================
 *
 *  <p>Ba chỗ cần danh sách tài khoản nhanh ({@code LoginDialog} vẽ hàng nút,
 *  {@code MainWindow} dựng menu "Đổi vai trò", {@code AppFlow} thử auto-login).
 *  Nếu mỗi chỗ tự gọi {@code AppConfig.getBoolean("quick.login.enabled", ...)}
 *  thì tên khoá bị viết lại 3 lần: gõ sai một lần là một lỗi im lặng -- tính
 *  năng tắt ở đúng một chỗ mà không ai biết vì sao. Nên 4 khoá đó chỉ xuất
 *  hiện trong file này.
 *
 *  <p>Đặc tả hợp đồng cấu hình:
 *  {@code plan/11-KE-HOACH-KHOI-2-DANG-NHAP-NHANH.md} mục 3. Sửa ở đây phải
 *  sửa tài liệu đó và phần comment trong {@code config.properties}.
 */
public final class QuickLogin {

    private static final String KEY_ENABLED  = "quick.login.enabled";
    private static final String KEY_ACCOUNTS = "quick.login.accounts";
    private static final String KEY_AUTO     = "quick.login.auto";

    /**
     * Tính năng đăng nhập nhanh có đang bật hay không.
     *
     * <p>Mặc định trong code là <b>false</b>, không phải true: nếu ai xoá mất
     * khoá đó khỏi {@code config.properties} thì hành vi an toàn hơn là trở về
     * màn hình đăng nhập thường, chứ không phải tự bật một tính năng tiện lợi
     * có kèm mật khẩu để nguyên văn trong file cấu hình.
     *
     * <p>Một công tắc này tắt được tất cả: hàng nút nhanh, auto-login, tham số
     * {@code --login=} và nút "Đổi vai trò".
     */
    public static boolean isEnabled() {
        return AppConfig.getBoolean(KEY_ENABLED, false);
    }

    /**
     * Danh sách tài khoản nhanh đọc từ cấu hình.
     * Trả về danh sách <b>rỗng</b> khi tính năng đang tắt -- chỗ gọi không phải
     * tự kiểm tra {@link #isEnabled()} nữa.
     */
    public static List<QuickAccount> accounts() {
        if (!isEnabled()) {
            return List.of();
        }
        return QuickAccount.parse(AppConfig.get(KEY_ACCOUNTS, ""));
    }

    /** Username ở khoá {@code quick.login.auto}, hoặc chuỗi rỗng nếu không cấu hình. */
    public static String autoUsername() {
        if (!isEnabled()) {
            return "";
        }
        return AppConfig.get(KEY_AUTO, "");
    }

    /**
     * Tìm một tài khoản nhanh theo tên đăng nhập (không phân biệt hoa thường).
     *
     * @return tài khoản khớp, hoặc {@code null} nếu không có trong
     *         {@code quick.login.accounts} / tính năng đang tắt
     */
    public static QuickAccount find(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        for (QuickAccount a : accounts()) {
            if (a.getUsername().equalsIgnoreCase(username.trim())) {
                return a;
            }
        }
        return null;
    }

    private QuickLogin() { }
}
