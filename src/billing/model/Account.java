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
    private final String cashierId;   // co the null -- xem plan/12 muc 3.3

    public Account(String username, String role, String fullName, String cashierId) {
        this.username = username;
        this.role = role;
        this.fullName = fullName;
        this.cashierId = cashierId;
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

    /**
     * Mã nhân viên trong bảng Cashier, hoặc null nếu tài khoản không gắn với
     * nhân viên nào. Cột `Return.Approved_By` đòi đúng một `Cashier_ID`, nên
     * đây là giá trị duy nhất dùng được làm "người duyệt" khi trả hàng.
     */
    public String getCashierId() {
        return cashierId;
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
