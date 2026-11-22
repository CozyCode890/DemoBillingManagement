package billing2.model;

/**
 * Tài khoản đăng nhập (kết quả của sp_authenticate_account).
 * role: 'cashier' = thu ngân, 'manager' = quản lý (được sửa / hủy / xóa / trả hàng).
 */
public class Account {

    private final String username;
    private final String role;
    private final String fullName;
    private final String cashierId;   // mã nhân viên trong bảng Cashier, có thể null

    public Account(String username, String role, String fullName, String cashierId) {
        this.username  = username;
        this.role      = role;
        this.fullName  = fullName;
        this.cashierId = cashierId;
    }

    public String getUsername() { return username; }
    public String getRole()     { return role; }
    public String getFullName() { return fullName; }

    /** Người duyệt khi trả hàng: cột Return.Approved_By cần đúng một Cashier_ID. */
    public String getCashierId() { return cashierId; }

    public boolean isManager() { return "manager".equalsIgnoreCase(role); }

    @Override
    public String toString() { return fullName + " (" + role + ")"; }
}
