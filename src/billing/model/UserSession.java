package billing.model;

/**
 * ====================================================================
 *  UserSession -- Quản lý phiên làm việc hiện tại của ứng dụng
 * ====================================================================
 *  Lưu đối tượng Account của người dùng vừa đăng nhập thành công.
 *  Cung cấp hàm kiểm tra và đăng xuất (clear).
 */
public class UserSession {

    private static Account currentUser = null;

    /** Lấy tài khoản đang đăng nhập */
    public static Account getCurrentUser() {
        return currentUser;
    }

    /** Thiết lập tài khoản khi đăng nhập thành công */
    public static void setCurrentUser(Account user) {
        currentUser = user;
    }

    /** Đăng xuất, xóa phiên làm việc */
    public static void clear() {
        currentUser = null;
    }

    /** Kiểm tra xem đã có ai đăng nhập chưa */
    public static boolean isLoggedIn() {
        return currentUser != null;
    }
}
