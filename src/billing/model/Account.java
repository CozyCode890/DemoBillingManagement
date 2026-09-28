package billing.model;

/**
 * ====================================================================
 *  Account -- Lớp mô hình biểu diễn Tài khoản người dùng
 * ====================================================================
 *  Lưu trữ thông tin định danh và vai trò (Role) trong hệ thống:
 *  - 'cashier' : Thu ngân (bị khóa các chức năng can thiệp DB trên UI)
 *  - 'manager' : Quản lý (được toàn quyền chỉnh sửa và xóa dữ liệu)
 */
public class Account {

    private final String username;
    private final String role;
    private final String fullName;

    public Account(String username, String role, String fullName) {
        this.username = username;
        this.role = role;
        this.fullName = fullName;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public String getFullName() {
        return fullName;
    }

    /** Kiểm tra xem tài khoản này có phải là Quản lý hay không */
    public boolean isManager() {
        return "manager".equalsIgnoreCase(role);
    }

    /** Kiểm tra xem tài khoản này có phải là Thu ngân hay không */
    public boolean isCashier() {
        return "cashier".equalsIgnoreCase(role);
    }

    @Override
    public String toString() {
        return fullName + " (" + role + ")";
    }
}
