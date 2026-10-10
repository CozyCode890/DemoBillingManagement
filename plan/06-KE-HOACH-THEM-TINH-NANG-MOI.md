# Kế Hoạch Chi Tiết: Bổ Sung Chức Năng Đăng Nhập, Phân Quyền & Thao Tác Trực Tiếp Database Cho Hệ Thống Billing

> **Dành cho AI / Lập trình viên tiếp theo:** Tài liệu này chứa đặc tả kỹ thuật chi tiết, thiết kế database, kiến trúc code và mã nguồn mẫu có chú thích tiếng Việt cho từng file. Hãy đọc kỹ và tuân thủ các nguyên tắc thiết kế trước khi bắt tay vào viết code.

---

## 1. Mục Tiêu & Các Nguyên Tắc Bắt Buộc

### 1.1. Mục tiêu chức năng
1. **Màn hình Đăng nhập (Login):** Hiển thị ngay khi bật ứng dụng. Yêu cầu nhập Username/Password, kiểm tra từ CSDL MySQL.
2. **Khởi tạo tài khoản mẫu (Seed Data):**
   - Tài khoản Thu ngân: username = `cashier`, password = `cashier`, vai trò = `cashier`.
   - Tài khoản Quản lý: username = `manager`, password = `manager`, vai trò = `manager`.
3. **Nút Đăng xuất (Logout):** Đặt trực tiếp trong giao diện ứng dụng để quay về màn hình đăng nhập bất cứ lúc nào.
4. **Bảng Account trong CSDL:** Lưu trữ thông tin tài khoản và vai trò (`cashier` / `manager`).
5. **Nút Thao tác Database trong Tab "Xem hóa đơn":**
   - Chỉnh sửa hóa đơn trực tiếp trong database.
   - Xóa hóa đơn trực tiếp trong database.
   - Chỉnh sửa dòng hàng/giá trị trong hóa đơn trực tiếp trong database.
   - Xóa dòng hàng/giá trị trong hóa đơn trực tiếp trong database.
   - **Cảnh báo ràng buộc NOT NULL:** Nếu người dùng cố tình xóa giá trị của một trường không được phép `NULL` (ví dụ: đơn giá, số lượng, ngày lập, v.v.), ứng dụng phải bật cảnh báo và ngăn chặn hành động.
6. **Phân quyền trên giao diện (UI Locking):**
   - Khi đăng nhập bằng `cashier`: Toàn bộ các nút chỉnh sửa/xóa nói trên bị **KHÓA (Disabled)** trên giao diện.
   - Khi đăng nhập bằng `manager`: Các nút được **MỞ KHÓA (Enabled)**.
   - **TUYỆT ĐỐI KHÔNG KHÓA TRONG SERVICE / DAO:** Tầng backend/DAO vẫn cho phép gọi hàm bình thường để phục vụ các yêu cầu nghiệp vụ phía sau.

### 1.2. Các nguyên tắc kỹ thuật (Rất quan trọng!)
- **Hạn chế tối đa sửa code cũ (Additive First):** Ưu tiên tạo file mới (`.java`, `.sql`). Với các file hiện có, chỉ thêm vào cuối file, không viết lại code cũ.
- **Tuân thủ kiến trúc hiện có:** Swing UI -> DAO (JDBC thuần qua Stored Procedure / `CallableStatement`) -> MySQL 8. Không dùng Spring, không dùng ORM, không dùng Maven plugin phức tạp.
- **Tuân thủ quy chuẩn đặt tên (Contract SQL & Java):**
  - Stored Procedure: `sp_<ten_thu_tuc>`
  - Tham số SQL: `p_<ten_tham_so>`
  - Biến nội bộ SQL: `v_<ten_bien>`
- **Comment giải thích chi tiết bằng tiếng Việt:** Dành cho người mới học, dễ hiểu, dễ làm lại.

---

## 2. Thiết Kế Cơ Sở Dữ Liệu (MySQL)

### 2.1. Thêm Bảng `Account` vào `sql/01_schema.sql`
Thêm vào cuối file `sql/01_schema.sql`:

```sql
-- ---------------------------------------------------------------------
-- 16. Account -- Bảng lưu tài khoản người dùng và phân quyền hệ thống
-- ---------------------------------------------------------------------
CREATE TABLE Account (
    Username   VARCHAR(50)  NOT NULL,
    Password   VARCHAR(100) NOT NULL,
    Role       ENUM('cashier', 'manager') NOT NULL,
    Full_Name  VARCHAR(100) NULL,
    PRIMARY KEY (Username)
) ENGINE=InnoDB;
```

### 2.2. Bổ sung Seed Data vào `sql/02_seed.sql`
Thêm vào cuối file `sql/02_seed.sql`:

```sql
-- ---------------------------------------------------------------------
-- Tài khoản đăng nhập mặc định: cashier và manager
-- ---------------------------------------------------------------------
INSERT INTO Account (Username, Password, Role, Full_Name) VALUES
('cashier', 'cashier', 'cashier', 'Nhan Vien Thu Ngan'),
('manager', 'manager', 'manager', 'Quan Ly He Thong');
```

### 2.3. Bổ sung các Stored Procedure vào `sql/04_routines.sql`
Thêm vào cuối file `sql/04_routines.sql` (bên trong block `DELIMITER $$`):

```sql
-- ---------------------------------------------------------------------
-- 4.1 sp_authenticate_account: Kiểm tra đăng nhập
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_authenticate_account(
    IN p_username VARCHAR(50),
    IN p_password VARCHAR(100)
)
BEGIN
    SELECT Username, Role, Full_Name
    FROM   Account
    WHERE  Username = p_username
      AND  Password = p_password;
END$$

-- ---------------------------------------------------------------------
-- 4.2 sp_delete_invoice: Xóa an toàn hóa đơn và các quan hệ khóa ngoại
--     Thứ tự xóa bắt buộc:
--     1. Return (bảng con của Invoice_Line)
--     2. Cash_Payment / Card_Payment / EWallet_Payment (bảng con của Payment)
--     3. Payment (bảng con của Invoice)
--     4. Invoice_Line (bảng con của Invoice)
--     5. Invoice (bảng cha)
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_delete_invoice(
    IN p_invoice_id VARCHAR(30)
)
BEGIN
    -- Bắt đầu xử lý xóa sạch các bảng phụ thuộc
    -- 1. Xóa các lượt trả hàng thuộc các dòng của hóa đơn này
    DELETE FROM `Return`
    WHERE Invoice_ID = p_invoice_id;

    -- 2. Xóa các chi tiết phương thức thanh toán bảng con
    DELETE FROM Cash_Payment
    WHERE Payment_ID IN (SELECT Payment_ID FROM Payment WHERE Invoice_ID = p_invoice_id);

    DELETE FROM Card_Payment
    WHERE Payment_ID IN (SELECT Payment_ID FROM Payment WHERE Invoice_ID = p_invoice_id);

    DELETE FROM EWallet_Payment
    WHERE Payment_ID IN (SELECT Payment_ID FROM Payment WHERE Invoice_ID = p_invoice_id);

    -- 3. Xóa các lượt thanh toán chính
    DELETE FROM Payment
    WHERE Invoice_ID = p_invoice_id;

    -- 4. Xóa các dòng hàng
    DELETE FROM Invoice_Line
    WHERE Invoice_ID = p_invoice_id;

    -- 5. Xóa phần đầu hóa đơn
    DELETE FROM Invoice
    WHERE Invoice_ID = p_invoice_id;
END$$

-- ---------------------------------------------------------------------
-- 4.3 sp_update_invoice_header: Chỉnh sửa thông tin chung của hóa đơn
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_update_invoice_header(
    IN p_invoice_id  VARCHAR(30),
    IN p_date        DATE,
    IN p_time        TIME,
    IN p_status      ENUM('OPEN','PAID','VOIDED'),
    IN p_counter_id  VARCHAR(10),
    IN p_cashier_id  VARCHAR(10),
    IN p_customer_id VARCHAR(15)
)
BEGIN
    UPDATE Invoice
    SET    `Date`      = p_date,
           `Time`      = p_time,
           Status      = p_status,
           Counter_ID  = p_counter_id,
           Cashier_ID  = p_cashier_id,
           Customer_ID = p_customer_id
    WHERE  Invoice_ID  = p_invoice_id;
END$$

-- ---------------------------------------------------------------------
-- 4.4 sp_update_invoice_line: Chỉnh sửa dòng hàng trong hóa đơn
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_update_invoice_line(
    IN p_invoice_id  VARCHAR(30),
    IN p_line_number INT,
    IN p_barcode     VARCHAR(20),
    IN p_quantity    DECIMAL(10,3),
    IN p_unit_price  DECIMAL(12,2),
    IN p_discount    DECIMAL(12,2)
)
BEGIN
    UPDATE Invoice_Line
    SET    Barcode    = p_barcode,
           Quantity   = p_quantity,
           Unit_Price = p_unit_price,
           Discount   = p_discount
    WHERE  Invoice_ID  = p_invoice_id
      AND  Line_Number = p_line_number;
END$$

-- ---------------------------------------------------------------------
-- 4.5 sp_delete_invoice_line: Xóa 1 dòng hàng cụ thể trong hóa đơn
-- ---------------------------------------------------------------------
CREATE PROCEDURE sp_delete_invoice_line(
    IN p_invoice_id  VARCHAR(30),
    IN p_line_number INT
)
BEGIN
    -- Xóa lượt trả hàng tham chiếu đến dòng này nếu có
    DELETE FROM `Return`
    WHERE Invoice_ID = p_invoice_id
      AND Line_Number = p_line_number;

    -- Xóa dòng hàng
    DELETE FROM Invoice_Line
    WHERE Invoice_ID = p_invoice_id
      AND Line_Number = p_line_number;
END$$
```

---

## 3. Thiết Kế Tầng Java (Model & DAO & Session)

### 3.1. Tạo mới: `src/billing/model/Account.java`
Lớp Entity đại diện cho tài khoản người dùng:
```java
package billing.model;

/**
 * Lớp đại diện cho tài khoản người dùng trong hệ thống.
 * Chứa thông tin đăng nhập và vai trò (Role): 'cashier' hoặc 'manager'.
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

    public String getUsername() { return username; }
    public String getRole() { return role; }
    public String getFullName() { return fullName; }

    /** Kiểm tra xem có phải là Quản lý hay không */
    public boolean isManager() {
        return "manager".equalsIgnoreCase(role);
    }

    /** Kiểm tra xem có phải là Thu ngân hay không */
    public boolean isCashier() {
        return "cashier".equalsIgnoreCase(role);
    }

    @Override
    public String toString() {
        return fullName + " (" + role + ")";
    }
}
```

### 3.2. Tạo mới: `src/billing/model/UserSession.java`
Lưu trữ phiên làm việc của người dùng hiện tại (Current Logged-in User):
```java
package billing.model;

/**
 * Quản lý phiên làm việc hiện tại của ứng dụng.
 * Lưu thông tin Account của người vừa đăng nhập thành công.
 */
public class UserSession {
    private static Account currentUser = null;

    public static Account getCurrentUser() {
        return currentUser;
    }

    public static void setCurrentUser(Account user) {
        currentUser = user;
    }

    public static void clear() {
        currentUser = null;
    }

    public static boolean isLoggedIn() {
        return currentUser != null;
    }
}
```

### 3.3. Tạo mới: `src/billing/dao/AccountDao.java`
Thực thi kiểm tra đăng nhập qua JDBC thuần và Stored Procedure `sp_authenticate_account`:
```java
package billing.dao;

import billing.db.Db;
import billing.db.SqlLog;
import billing.model.Account;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * DAO xử lý các thao tác liên quan đến tài khoản người dùng.
 */
public class AccountDao {

    /**
     * Xác thực tài khoản đăng nhập.
     * @param username Tên đăng nhập
     * @param password Mật khẩu
     * @return Đối tượng Account nếu đúng thông tin, ngược lại trả về null
     */
    public Account authenticate(String username, String password) {
        String sql = "{call sp_authenticate_account(?, ?)}";
        long t0 = System.currentTimeMillis();

        try (Connection c = Db.getConnection();
             CallableStatement cs = c.prepareCall(sql)) {

            cs.setString(1, username);
            cs.setString(2, password);

            try (ResultSet rs = cs.executeQuery()) {
                if (rs.next()) {
                    String user = rs.getString("Username");
                    String role = rs.getString("Role");
                    String fullName = rs.getString("Full_Name");
                    SqlLog.add(sql, new Object[]{username, "******"}, System.currentTimeMillis() - t0, 1);
                    return new Account(user, role, fullName);
                }
            }
            SqlLog.add(sql, new Object[]{username, "******"}, System.currentTimeMillis() - t0, 0);
            return null;

        } catch (SQLException e) {
            throw new RuntimeException("Lỗi khi kiểm tra đăng nhập: " + e.getMessage(), e);
        }
    }
}
```

### 3.4. Bổ sung vào `src/billing/dao/InvoiceDao.java`
Thêm các hàm sửa/xóa vào cuối file `InvoiceDao.java`.
> **LƯU Ý QUAN TRỌNG:** Tuyệt đối không kiểm tra quyền `isManager()` trong DAO này theo đúng chỉ đạo của người dùng: *"Không muốn lock trong service bởi vì tôi có cái cần làm phía sau"*. Tầng DAO luôn thực hiện nếu được gọi.

```java
    // =================================================================
    //  PHẦN 3 -- THAO TÁC SỬA / XÓA TRỰC TIẾP TRONG DATABASE (DAO)
    //  (Lưu ý: Không kiểm tra quyền ở đây, quyền được kiểm soát tại UI)
    // =================================================================

    /**
     * Xóa hoàn toàn một hóa đơn và các bảng con liên quan.
     */
    public void deleteInvoice(String invoiceId) {
        String sql = "{call sp_delete_invoice(?)}";
        long t0 = System.currentTimeMillis();
        try (Connection c = Db.getConnection();
             CallableStatement cs = c.prepareCall(sql)) {
            cs.setString(1, invoiceId);
            cs.executeUpdate();
            SqlLog.add(sql, new Object[]{invoiceId}, System.currentTimeMillis() - t0, 1);
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi xóa hóa đơn " + invoiceId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Cập nhật thông tin phần đầu hóa đơn (Header).
     */
    public void updateInvoiceHeader(String invoiceId, LocalDate date, LocalTime time,
                                    String status, String counterId, String cashierId, String customerId) {
        String sql = "{call sp_update_invoice_header(?, ?, ?, ?, ?, ?, ?)}";
        long t0 = System.currentTimeMillis();
        try (Connection c = Db.getConnection();
             CallableStatement cs = c.prepareCall(sql)) {
            cs.setString(1, invoiceId);
            cs.setDate(2, Date.valueOf(date));
            cs.setTime(3, Time.valueOf(time));
            cs.setString(4, status);
            cs.setString(5, counterId);
            cs.setString(6, cashierId);
            if (customerId == null || customerId.trim().isEmpty()) {
                cs.setNull(7, Types.VARCHAR);
            } else {
                cs.setString(7, customerId);
            }
            cs.executeUpdate();
            SqlLog.add(sql, new Object[]{invoiceId, date, time, status, counterId, cashierId, customerId},
                       System.currentTimeMillis() - t0, 1);
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi cập nhật hóa đơn " + invoiceId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Cập nhật thông tin một dòng hàng (Invoice_Line).
     */
    public void updateInvoiceLine(String invoiceId, int lineNumber, String barcode,
                                  BigDecimal quantity, BigDecimal unitPrice, BigDecimal discount) {
        String sql = "{call sp_update_invoice_line(?, ?, ?, ?, ?, ?)}";
        long t0 = System.currentTimeMillis();
        try (Connection c = Db.getConnection();
             CallableStatement cs = c.prepareCall(sql)) {
            cs.setString(1, invoiceId);
            cs.setInt(2, lineNumber);
            cs.setString(3, barcode);
            cs.setBigDecimal(4, quantity);
            cs.setBigDecimal(5, unitPrice);
            cs.setBigDecimal(6, discount);
            cs.executeUpdate();
            SqlLog.add(sql, new Object[]{invoiceId, lineNumber, barcode, quantity, unitPrice, discount},
                       System.currentTimeMillis() - t0, 1);
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi cập nhật dòng hàng: " + e.getMessage(), e);
        }
    }

    /**
     * Xóa một dòng hàng cụ thể trong hóa đơn.
     */
    public void deleteInvoiceLine(String invoiceId, int lineNumber) {
        String sql = "{call sp_delete_invoice_line(?, ?)}";
        long t0 = System.currentTimeMillis();
        try (Connection c = Db.getConnection();
             CallableStatement cs = c.prepareCall(sql)) {
            cs.setString(1, invoiceId);
            cs.setInt(2, lineNumber);
            cs.executeUpdate();
            SqlLog.add(sql, new Object[]{invoiceId, lineNumber}, System.currentTimeMillis() - t0, 1);
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi xóa dòng hàng: " + e.getMessage(), e);
        }
    }
```

---

## 4. Thiết Kế Tầng Giao Diện (UI) & Phân Quyền

### 4.1. Tạo mới: `src/billing/ui/LoginDialog.java`
Hộp thoại Modal hiển thị khi mở ứng dụng:
- Cho phép nhập Username và Password.
- Có nút "Đăng nhập" và "Thoát".
- Hiển thị gợi ý tài khoản mẫu bên dưới (`cashier / cashier` và `manager / manager`).
- Khi đăng nhập thành công: gán vào `UserSession` và cho phép mở `MainWindow`.

```java
package billing.ui;

import billing.dao.AccountDao;
import billing.model.Account;
import billing.model.UserSession;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Màn hình Đăng nhập (Login Dialog).
 * Xuất hiện đầu tiên khi khởi động ứng dụng.
 */
public class LoginDialog extends JDialog {

    private final JTextField tfUsername = new JTextField(15);
    private final JPasswordField pfPassword = new JPasswordField(15);
    private final AccountDao accountDao = new AccountDao();
    private boolean succeeded = false;

    public LoginDialog(Frame parent) {
        super(parent, "Đăng nhập hệ thống - Retail Invoicing", true);
        setLayout(new BorderLayout(10, 10));
        setResizable(false);

        // Panel nhập thông tin
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(15, 20, 10, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        form.add(new JLabel("Tên đăng nhập:"), gbc);
        gbc.gridx = 1;
        form.add(tfUsername, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        form.add(new JLabel("Mật khẩu:"), gbc);
        gbc.gridx = 1;
        form.add(pfPassword, gbc);

        // Gợi ý tài khoản mẫu
        JLabel lbHint = new JLabel("<html><i style='color:gray;'>Gợi ý tài khoản mẫu:<br>• Thu ngân: <b>cashier</b> / <b>cashier</b> (Khóa chức năng sửa/xóa)<br>• Quản lý: <b>manager</b> / <b>manager</b> (Đầy đủ quyền)</i></html>");
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.gridwidth = 2;
        form.add(lbHint, gbc);

        // Panel nút bấm
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btLogin = new JButton("Đăng nhập");
        JButton btCancel = new JButton("Thoát");

        btLogin.setFont(Ui.BOLD);
        btLogin.addActionListener(e -> doLogin());
        btCancel.addActionListener(e -> System.exit(0));

        buttons.add(btLogin);
        buttons.add(btCancel);

        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(btLogin);
        pack();
        setLocationRelativeTo(parent);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });
    }

    private void doLogin() {
        String username = tfUsername.getText().trim();
        String password = new String(pfPassword.getPassword()).trim();

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu!", "Lỗi nhập liệu", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            Account account = accountDao.authenticate(username, password);
            if (account != null) {
                UserSession.setCurrentUser(account);
                succeeded = true;
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "Sai tên đăng nhập hoặc mật khẩu! Vui lòng thử lại.", "Đăng nhập thất bại", JOptionPane.ERROR_MESSAGE);
                pfPassword.setText("");
                pfPassword.requestFocus();
            }
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    public boolean isSucceeded() {
        return succeeded;
    }
}
```

### 4.2. Tạo mới: Dialog chỉnh sửa hóa đơn & Cảnh báo NOT NULL
Tạo file `src/billing/ui/EditInvoiceDialog.java`:
- Cho phép chỉnh sửa: Ngày, Giờ, Trạng thái (Status: OPEN, PAID, VOIDED), Mã Quầy (Counter_ID), Mã Thu ngân (Cashier_ID), Mã Khách hàng (Customer_ID).
- **Cơ chế Cảnh báo NOT NULL:**
  + Các trường `Invoice_ID`, `Date`, `Time`, `Status`, `Counter_ID`, `Cashier_ID` là `NOT NULL` trong database. Nếu người dùng xóa trắng (empty), dialog sẽ chặn và hiển thị:
    `JOptionPane.showMessageDialog(..., "CẢNH BÁO DATABASE: Trường [X] có ràng buộc NOT NULL, không được để trống hoặc xóa về NULL!", "Ràng buộc dữ liệu", JOptionPane.WARNING_MESSAGE);`
  + Trường `Customer_ID` là nullable: nếu người dùng xóa trắng, trường này sẽ được cập nhật là `null` (khách vãng lai).

### 4.3. Tạo mới: Dialog chỉnh sửa dòng hàng & Cảnh báo NOT NULL
Tạo file `src/billing/ui/EditLineDialog.java`:
- Cho phép chỉnh sửa số lượng (`Quantity`), đơn giá (`Unit_Price`), giảm giá (`Discount`).
- **Cơ chế Cảnh báo NOT NULL:**
  + Cột `Quantity` và `Unit_Price` là `NOT NULL`. Nếu người dùng để trống hoặc nhập chuỗi rỗng/nhập số <= 0 (với số lượng):
    Bật thông báo cảnh báo và từ chối lưu.
  + Nếu người dùng muốn bấm nút **"Xóa giá trị (Set NULL)"**, hệ thống kiểm tra nếu cột đó là NOT NULL thì cảnh báo: *"Trường này không được phép NULL trong database!"*.

### 4.4. Cập nhật `src/billing/ui/InvoiceViewPanel.java` (Tab Database)
Bổ sung các nút bấm và hàm phân quyền:
1. **Các nút mới được thêm vào:**
   - Dưới bảng danh sách hóa đơn (Panel Trái):
     + `btEditInvoice`: "Sửa Hóa Đơn"
     + `btDeleteInvoice`: "Xóa Hóa Đơn"
   - Dưới bảng dòng hàng `tbLines` (Panel Phải):
     + `btEditLine`: "Sửa Dòng Hàng"
     + `btDeleteLine`: "Xóa Dòng Hàng"
     + `btClearLineValue`: "Xóa Giá Trị (Thử gán NULL)"
2. **Hàm Phân quyền UI `applyRolePermissions()`:**
   ```java
   public void applyRolePermissions() {
       Account user = UserSession.getCurrentUser();
       boolean isManager = (user != null && user.isManager());

       // Nếu là Cashier -> Các nút bị LOCK (disabled)
       // Nếu là Manager -> Các nút được UNLOCK (enabled)
       btEditInvoice.setEnabled(isManager);
       btDeleteInvoice.setEnabled(isManager);
       btEditLine.setEnabled(isManager);
       btDeleteLine.setEnabled(isManager);
       btClearLineValue.setEnabled(isManager);

       if (!isManager) {
           String hint = "Chức năng chỉ dành cho Quản lý (Manager). Thu ngân bị khóa!";
           btEditInvoice.setToolTipText(hint);
           btDeleteInvoice.setToolTipText(hint);
           btEditLine.setToolTipText(hint);
           btDeleteLine.setToolTipText(hint);
           btClearLineValue.setToolTipText(hint);
       }
   }
   ```
3. **Logic nút "Xóa Hóa Đơn":**
   - Lấy `invoiceId` của dòng đang chọn trên `tbInvoices`.
   - Nếu chưa chọn: Nhắc người dùng chọn một hóa đơn.
   - Bật popup xác nhận: *"Bạn có chắc chắn muốn xóa vĩnh viễn hóa đơn " + invoiceId + " khỏi database? Mọi thông tin thanh toán, dòng hàng và trả hàng sẽ bị xóa."*
   - Gọi `dao.deleteInvoice(invoiceId)`.
   - Gọi `reload()` để làm mới bảng.
4. **Logic nút "Xóa Giá Trị (Thử gán NULL)":**
   - Cho phép người dùng chọn một ô/dòng.
   - Nếu người dùng cố ý xóa các cột bắt buộc (`Barcode`, `Quantity`, `Unit_Price`) -> Cảnh báo ngay:
     *"LỖI DATABASE: Cột này có ràng buộc NOT NULL! Không thể gán NULL hoặc xóa rỗng."*

### 4.5. Cập nhật `src/billing/ui/MainWindow.java`
1. Thêm thông tin tài khoản hiện tại vào Status Bar:
   `" | Đang đăng nhập: " + user.getFullName() + " [" + user.getRole().toUpperCase() + "] "`
2. Thêm nút **"Đăng xuất" (Logout)** vào góc phải của Status Bar:
   - Khi bấm:
     ```java
     int choice = JOptionPane.showConfirmDialog(this,
             "Bạn có chắc chắn muốn đăng xuất khỏi hệ thống?",
             "Xác nhận đăng xuất", JOptionPane.YES_NO_OPTION);
     if (choice == JOptionPane.YES_OPTION) {
         this.dispose(); // Đóng cửa sổ chính
         UserSession.clear(); // Xóa phiên đăng nhập
         // Mở lại LoginDialog
         LoginDialog login = new LoginDialog(null);
         login.setVisible(true);
         if (login.isSucceeded()) {
             new MainWindow().setVisible(true);
         }
     }
     ```
3. Gọi `viewPanel.applyRolePermissions()` trong hàm khởi tạo để áp dụng ngay trạng thái khóa/mở nút.

### 4.6. Cập nhật `src/billing/Main.java`
Thay đổi flow khởi động:
```java
// Thay vì mở ngay MainWindow, hiển thị LoginDialog trước:
SwingUtilities.invokeLater(() -> {
    LoginDialog login = new LoginDialog(null);
    login.setVisible(true);
    if (login.isSucceeded()) {
        new MainWindow().setVisible(true);
    }
});
```

---

## 5. Quy Trình Kiểm Thử & Nghiệm Thu (Verification Checklist)

| STT | Kịch bản kiểm thử | Hành động | Kết quả mong đợi |
|---|---|---|---|
| 1 | Mở ứng dụng | Chạy `powershell .\run.ps1` | Màn hình Đăng nhập hiện lên trước tiên, cửa sổ chính chưa mở. |
| 2 | Đăng nhập sai | Nhập sai mật khẩu | Báo lỗi rõ ràng, không văng exception, cho phép nhập lại. |
| 3 | Đăng nhập bằng `cashier` | Nhập `cashier` / `cashier` | Vào ứng dụng thành công. Status bar hiện vai trò CASHIER. Tab "Xem hóa đơn" có các nút Chỉnh sửa / Xóa bị **KHÓA (mờ đi, không click được)**. |
| 4 | Đăng xuất | Bấm nút "Đăng xuất" trên Status bar | App đóng cửa sổ chính và hiển thị lại Login Dialog. |
| 5 | Đăng nhập bằng `manager` | Nhập `manager` / `manager` | Vào ứng dụng thành công. Status bar hiện vai trò MANAGER. Toàn bộ các nút Chỉnh sửa / Xóa đều **SÁNG (click được bình thường)**. |
| 6 | Thử xóa trường NOT NULL | Chọn sửa hóa đơn / dòng hàng, xóa trắng trường Quantity hoặc Counter_ID | Ứng dụng bật popup cảnh báo: *"Trường có ràng buộc NOT NULL, không được để trống!"* và không gửi query lỗi xuống DB. |
| 7 | Sửa trường cho phép NULL | Xóa trường `Customer_ID` trong hóa đơn | Hệ thống lưu thành công, `Customer_ID` chuyển thành `NULL` (Khách vãng lai). |
| 8 | Xóa hóa đơn | Chọn một hóa đơn và bấm "Xóa Hóa Đơn" | Hỏi xác nhận -> Xóa thành công sạch sẽ các bảng con (Return, Payment con, Payment, Line) mà không bị lỗi Foreign Key. Bảng tự động reload. |
| 9 | Kiểm tra tầng DAO | Gọi hàm `InvoiceDao.deleteInvoice()` trực tiếp | Thao tác thành công, chứng minh tầng Service/DAO không bị lock cứng. |

---

## 6. Hướng Dẫn Thực Hiện Cho AI Phía Sau

1. **Bước 1:** Cập nhật `sql/01_schema.sql`, `sql/02_seed.sql`, `sql/04_routines.sql` theo đúng mã SQL ở Mục 2. Chạy `powershell .\db-setup.ps1` để nạp database mới.
2. **Bước 2:** Tạo 2 model `Account.java` và `UserSession.java` theo Mục 3.1 & 3.2.
3. **Bước 3:** Tạo `AccountDao.java` theo Mục 3.3.
4. **Bước 4:** Bổ sung các phương thức sửa/xóa vào cuối `InvoiceDao.java` theo Mục 3.4.
5. **Bước 5:** Tạo `LoginDialog.java` theo Mục 4.1.
6. **Bước 6:** Tạo `EditInvoiceDialog.java` và `EditLineDialog.java` có kiểm tra ràng buộc NOT NULL theo Mục 4.2 & 4.3.
7. **Bước 7:** Bổ sung các nút và hàm `applyRolePermissions()` vào `InvoiceViewPanel.java` theo Mục 4.4.
8. **Bước 8:** Cập nhật Status Bar và nút Đăng xuất trong `MainWindow.java` theo Mục 4.5.
9. **Bước 9:** Đổi điểm khởi chạy trong `Main.java` theo Mục 4.6.
10. **Bước 10:** Biên dịch và chạy thử bằng `powershell .\run.ps1`, thực hiện lần lượt 9 bài test trong bảng kiểm thử ở Mục 5.
