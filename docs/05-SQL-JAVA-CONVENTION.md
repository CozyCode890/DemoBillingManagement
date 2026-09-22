# Quy Chuẩn Chung Giữa Dev Backend Java & Dev SQL (Contract & Best Practices)

> **Mục tiêu tài liệu:** Đặt ra bộ quy chuẩn (Contract) rõ ràng giữa **Dev Backend Java** và **Dev SQL** để phối hợp mượt mà, phân định rạch ròi trách nhiệm của từng bên và đảm bảo khi sửa code/database không bao giờ bị "bể" hệ thống.

---

## 1. Phân Định Rõ Vai Trò & Trách Nhiệm

| Tiêu chí | Dev SQL | Dev Backend Java |
|---|---|---|
| **Khu vực quản lý** | Thư mục `sql/` (`01_schema.sql`, `02_seed.sql`, `04_routines.sql`) | Thư mục `src/billing/` (`dao/`, `model/`, `db/`, `ui/`) |
| **Trách nhiệm chính** | Thiết kế bảng, quan hệ khóa ngoại, tạo Index, viết & tối ưu hóa **Hàm (Function)** và **Thủ tục (Stored Procedure)** | Viết giao diện, nhận diện luồng nghiệp vụ, gọi thủ tục qua JDBC, map `ResultSet` vào Java Object, quản lý Transaction |
| **Không được làm** | Không sửa code trong các file `.java` | Không viết các câu lệnh SELECT/JOIN/GROUP BY phức tạp trực tiếp trong Java |

```
                +----------------------------------------+
                |          DEV JAVA (Tầng DAO)           |
                |   - Gọi CallableStatement              |
                |   - Truyền tham số IN / nhận OUT       |
                |   - Ánh xạ sang Entity / JTable        |
                +----------------------------------------+
                                   ▲
                                   │  (HỢP ĐỒNG GIAO TIẾP - CONTRACT)
                                   ▼
                +----------------------------------------+
                |          DEV SQL (04_routines.sql)     |
                |   - fn_*: Stored Functions (giá trị đơn)|
                |   - sp_*: Stored Procedures (kết quả)  |
                |   - Tối ưu truy vấn, INDEX, JOIN       |
                +----------------------------------------+
```

---

## 2. Quy Chuẩn Đặt Tên (Naming Conventions)

Để hai bên nhìn vào là hiểu ngay ý nghĩa của hàm/biến, tránh nhầm lẫn:

### 2.1. Phía SQL
1. **Hàm SQL (Stored Function - trả về giá trị đơn):**
   - Bắt buộc có tiền tố: `fn_` (viết tắt của Function).
   - Ví dụ: `fn_get_product_price(p_barcode, p_date)`, `fn_next_invoice_id()`.
2. **Thủ tục SQL (Stored Procedure - trả về bảng dữ liệu hoặc thực thi logic):**
   - Bắt buộc có tiền tố: `sp_` (viết tắt của Stored Procedure).
   - Ví dụ: `sp_get_recent_invoices()`, `sp_report_daily_takings()`, `sp_add_payment()`.
3. **Tham số đầu vào của hàm/thủ tục:**
   - Bắt buộc có tiền tố: `p_` (viết tắt của Parameter) để không bị trùng tên với tên cột của bảng.
   - Ví dụ: `p_barcode`, `p_invoice_id`, `p_date`.
4. **Biến nội bộ (Local variable) khai báo trong SQL:**
   - Bắt buộc có tiền tố: `v_` (viết tắt của Variable).
   - Ví dụ: `DECLARE v_price DECIMAL(12,2);`.

### 2.2. Phía Java
- Tên phương thức trong DAO: Tương ứng với nghiệp vụ (ví dụ: `priceOn(...)`, `recentInvoices()`, `createInvoice(...)`).
- Tham số truyền vào đúng kiểu dữ liệu của JDBC.

---

## 3. Bảng Quy Chuẩn Ánh Xạ Kiểu Dữ Liệu (Type Mapping Contract)

Đây là nơi dễ gây lỗi ("bể") nhất nếu một bên dùng kiểu này còn bên kia dùng kiểu khác. Hai bên phải tuân thủ bảng sau:

| Kiểu dữ liệu MySQL | Kiểu dữ liệu Java | Hằng số `java.sql.Types` | Phương thức PreparedStatement/CallableStatement | Phương thức ResultSet |
|---|---|---|---|---|
| `VARCHAR`, `CHAR`, `TEXT` | `String` | `Types.VARCHAR` | `cs.setString(index, str)` | `rs.getString(colName)` |
| `DECIMAL`, `NUMERIC` | `BigDecimal` | `Types.DECIMAL` | `cs.setBigDecimal(index, bigDec)` | `rs.getBigDecimal(colName)` |
| `INT`, `INTEGER` | `int` hoặc `Integer` | `Types.INTEGER` | `cs.setInt(index, val)` | `rs.getInt(colName)` |
| `DATE` | `LocalDate` / `java.sql.Date` | `Types.DATE` | `cs.setDate(index, Date.valueOf(localDate))` | `rs.getDate(colName).toLocalDate()` |
| `TIME` | `LocalTime` / `java.sql.Time` | `Types.TIME` | `cs.setTime(index, Time.valueOf(localTime))` | `rs.getTime(colName).toLocalTime()` |
| `NULL` | `null` | `Types.VARCHAR` / ... | `cs.setNull(index, Types.VARCHAR)` | `rs.wasNull()` |

> [!IMPORTANT]
> **Quy tắc về Tiền tệ và Số thập phân:**
> - Cả hai bên **TUYỆT ĐỐI KHÔNG** dùng `float` hay `double` để tính tiền.
> - SQL luôn dùng `DECIMAL(12,2)` (hoặc quy mô phù hợp).
> - Java luôn dùng `BigDecimal` và khởi tạo từ chuỗi: `new BigDecimal("100.50")`.

---

## 4. Hướng Dẫn Kỹ Thuật: Cách Gọi Hàm & Thủ Tục trong JDBC

### 4.1. Cách gọi Hàm SQL (Stored Function)
Hàm SQL luôn trả về **MỘT GIÁ TRỊ ĐƠN** (Scalar).
Cú pháp JDBC chuẩn bắt buộc có dạng: `{? = call fn_ten_ham(?, ?)}`.

```java
// Ví dụ: gọi fn_get_product_price(barcode, date)
String sql = "{? = call fn_get_product_price(?, ?)}";

try (Connection c = Db.getConnection();
     CallableStatement cs = c.prepareCall(sql)) {

    // 1. Đăng ký tham số 1 là giá trị trả về của Function (OUT parameter)
    cs.registerOutParameter(1, java.sql.Types.DECIMAL);

    // 2. Truyền các tham số đầu vào (bắt đầu từ vị trí 2)
    cs.setString(2, barcode);
    cs.setDate(3, java.sql.Date.valueOf(date));

    // 3. Thực thi
    cs.execute();

    // 4. Lấy kết quả từ tham số 1
    BigDecimal price = cs.getBigDecimal(1);
}
```

### 4.2. Cách gọi Thủ tục SQL (Stored Procedure) trả về bảng dữ liệu (ResultSet)
Cú pháp JDBC chuẩn: `{call sp_ten_thu_tuc(?, ?)}`.

```java
// Ví dụ: gọi sp_get_invoice_lines(invoiceId)
String sql = "{call sp_get_invoice_lines(?)}";

try (Connection c = Db.getConnection();
     CallableStatement cs = c.prepareCall(sql)) {

    cs.setString(1, invoiceId);

    try (ResultSet rs = cs.executeQuery()) {
        while (rs.next()) {
            int lineNo = rs.getInt("Dong");
            String name = rs.getString("San_pham");
            BigDecimal total = rs.getBigDecimal("Thanh_tien");
            // ...
        }
    }
}
```
Hoặc dùng hàm tiện ích có sẵn trong dự án:
```java
QueryResult qr = QueryResult.call("{call sp_get_invoice_lines(?)}", invoiceId);
```

---

## 5. Bốn Quy Tắc Sống Còn Để "Không Bị Bể Code"

### Quy tắc 1: Khóa cứng tên cột trả về (Column Alias Contract)
- **Vấn đề:** Trong Java, code đọc dữ liệu theo tên: `rs.getString("San_pham")` hoặc `rs.getBigDecimal("Doanh_thu")`.
- **Nếu Dev SQL đổi tên:** Ví dụ đổi `AS San_pham` thành `AS Ten_hang`, code Java sẽ ném ra ngoại lệ `SQLException: Column 'San_pham' not found`.
- **Cam kết:** 
  - Dev SQL **không tự ý đổi alias** của các cột đang phục vụ cho Java.
  - Nếu bắt buộc phải đổi, Dev SQL và Dev Java phải thống nhất trước khi sửa.

### Quy tắc 2: Giữ đúng thứ tự tham số (Parameter Order Contract)
- **Vấn đề:** JDBC đánh số tham số `1, 2, 3...` theo vị trí xuất hiện của dấu `?`.
- **Cam kết:** 
  - Dev SQL giữ nguyên thứ tự các tham số đầu vào của Stored Procedure/Function.
  - Khi thêm tham số mới, hãy thêm vào **cuối danh sách** (hoặc đặt giá trị mặc định nếu CSDL hỗ trợ).

### Quy tắc 3: Phân định ranh giới Transaction
- **Transaction nghiệp vụ lớn (Lập hóa đơn):** Java nắm quyền kiểm soát vì nghiệp vụ gồm nhiều bước: ghi hóa đơn, ghi N dòng hàng, ghi thanh toán.
  - Java mở `conn.setAutoCommit(false)`.
  - Java gọi tuần tự các Stored Procedure của SQL (`sp_create_invoice_header`, `sp_add_invoice_line`, `sp_add_payment`) **trên cùng một Connection**.
  - Nếu mọi thứ thành công -> Java gọi `conn.commit()`.
  - Nếu có bất kỳ lỗi nào -> Java gọi `conn.rollback()`.
- **Transaction nội bộ logic CSDL:** Nếu một thao tác độc lập cần xử lý nhiều bảng con nội bộ (như `sp_add_payment` rẽ nhánh vào `Cash_Payment` / `Card_Payment`), Dev SQL tự đóng gói bên trong procedure để Java không phải biết chi tiết các bảng con.

### Quy tắc 4: Luôn đóng Connection bằng `try-with-resources`
- Cả `CallableStatement`, `PreparedStatement` và `Connection` phải luôn được bọc trong `try (...)`:
  ```java
  try (Connection c = Db.getConnection();
       CallableStatement cs = c.prepareCall("{call sp_...}")) {
      // thực thi...
  }
  ```
  Tránh rò rỉ kết nối (Connection Leak) làm sập MySQL server.

---

## 6. Bảng Tóm Tắt Các Hàm & Thủ Tục Trong Dự Án

| Tên Routine | Loại | Tham số | Ý nghĩa nghiệp vụ | Nơi gọi trong Java |
|---|---|---|---|---|
| `fn_get_product_price` | FUNCTION | `p_barcode`, `p_date` | Lấy đơn giá tại ngày bán | `ProductDao.priceOn()` |
| `fn_next_invoice_id` | FUNCTION | *(không)* | Tự sinh mã hóa đơn `INV-xxxx` | `InvoiceDao.nextInvoiceId()` |
| `fn_next_payment_id` | FUNCTION | *(không)* | Tự sinh mã thanh toán `PAY-xxxx` | SQL & Java |
| `sp_lookup_counters` | PROCEDURE | *(không)* | Danh sách quầy tính tiền | `LookupDao.counters()` |
| `sp_lookup_cashiers` | PROCEDURE | *(không)* | Danh sách thu ngân | `LookupDao.cashiers()` |
| `sp_lookup_customers` | PROCEDURE | *(không)* | Danh sách khách có thẻ | `LookupDao.customers()` |
| `sp_get_all_products` | PROCEDURE | *(không)* | Danh mục tất cả sản phẩm | `ProductDao.findAll()` |
| `sp_get_active_promo` | PROCEDURE | `p_barcode`, `p_date` | Khuyến mãi cao nhất ngày bán | `ProductDao.activePromoOn()` |
| `sp_get_recent_invoices` | PROCEDURE | *(không)* | Hóa đơn gần đây + tổng tiền | `InvoiceDao.recentInvoices()` |
| `sp_get_invoice_header` | PROCEDURE | `p_invoice_id` | Đầu hóa đơn và tổng tiền | `InvoiceDao.invoiceHeader()` |
| `sp_get_invoice_lines` | PROCEDURE | `p_invoice_id` | Các dòng sản phẩm của hóa đơn | `InvoiceDao.invoiceLines()` |
| `sp_get_invoice_payments` | PROCEDURE | `p_invoice_id` | Các lượt thanh toán của hóa đơn | `InvoiceDao.invoicePayments()` |
| `sp_get_invoice_returns` | PROCEDURE | `p_invoice_id` | Thông tin trả hàng | `InvoiceDao.invoiceReturns()` |
| `sp_report_daily_takings` | PROCEDURE | *(không)* | Báo cáo doanh thu 7 ngày | `ReportDao.dailyTakings()` |
| `sp_report_price_rise` | PROCEDURE | *(không)* | Báo cáo sản phẩm tăng giá > 10% | `ReportDao.priceRise()` |
| `sp_report_best_cashier` | PROCEDURE | *(không)* | Báo cáo thu ngân xuất sắc | `ReportDao.bestCashier()` |
| `sp_report_big_returns` | PROCEDURE | *(không)* | Báo cáo hóa đơn trả hàng > 50% | `ReportDao.bigReturns()` |
| `sp_report_old_price_proof`| PROCEDURE | *(không)* | Chứng minh hóa đơn cũ giữ giá | `ReportDao.oldPriceProof()` |
| `sp_create_invoice_header` | PROCEDURE | `(6 tham số)` | Ghi đầu hóa đơn mới | `InvoiceDao.createInvoice()` |
| `sp_add_invoice_line` | PROCEDURE | `(6 tham số)` | Ghi dòng chi tiết hóa đơn | `InvoiceDao.createInvoice()` |
| `sp_add_payment` | PROCEDURE | `(6 tham số)` | Ghi thanh toán & rẽ nhánh bảng con | `InvoiceDao.createInvoice()` |
