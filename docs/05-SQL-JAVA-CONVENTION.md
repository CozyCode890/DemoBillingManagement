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
| `fn_next_payment_id` | FUNCTION | *(không)* | Tự sinh mã thanh toán `PAY-xxxx`. Gọi **trong** transaction, một lần cho mỗi dòng thanh toán | `InvoiceDao.createInvoice()` |
| `fn_return_refund` | FUNCTION | `p_invoice_id`, `p_line_number`, `p_quantity` | **Bản duy nhất** của công thức tiền hoàn: `ROUND(n / Q * T, 2)`. Java không được tự tính | `InvoiceDao.returnRefund()` |
| `fn_next_return_id` | FUNCTION | `p_invoice_id`, `p_line_number` | Số thứ tự lượt trả kế tiếp **trong một dòng** (không phải `AUTO_INCREMENT`) | *(chỉ SQL -- `sp_add_return` gọi)* |
| `fn_invoice_amount_due` | FUNCTION | `p_invoice_id` | Tổng tiền hoá đơn phải thu. `0.00` nếu chưa có dòng hàng → **người gọi phải tự kiểm có dòng hàng hay không** | `InvoiceDao.amountDue()` |
| `fn_invoice_amount_paid` | FUNCTION | `p_invoice_id` | Tổng tiền đã ghi nhận (`Payment.Amount`, không phải tiền khách đưa) | `InvoiceDao.amountPaid()` |
| `sp_lookup_counters` | PROCEDURE | *(không)* | Danh sách quầy tính tiền | `LookupDao.counters()` |
| `sp_lookup_cashiers` | PROCEDURE | *(không)* | Danh sách thu ngân | `LookupDao.cashiers()` |
| `sp_lookup_customers` | PROCEDURE | *(không)* | Danh sách khách có thẻ | *(không còn chỗ gọi trong Java -- thay bằng `sp_search_customers_by_phone`)* |
| `sp_search_customers_by_phone` | PROCEDURE | `p_prefix` | Tìm khách theo tiền tố số điện thoại, 3 cột, `LIMIT 20`. Trả **cột riêng**, không ghép chuỗi hiển thị | `LookupDao.searchCustomersByPhone()` |
| `sp_get_all_products` | PROCEDURE | *(không)* | Danh mục tất cả sản phẩm | `ProductDao.findAll()` |
| `sp_get_active_promo` | PROCEDURE | `p_barcode`, `p_date` | Khuyến mãi cao nhất ngày bán | `ProductDao.activePromoOn()` |
| `sp_get_recent_invoices` | PROCEDURE | *(không)* | Hóa đơn gần đây + tổng tiền | `InvoiceDao.recentInvoices()` |
| `sp_get_invoice_header` | PROCEDURE | `p_invoice_id` | Đầu hóa đơn và tổng tiền | `InvoiceDao.invoiceHeader()` |
| `sp_get_invoice_lines` | PROCEDURE | `p_invoice_id` | Các dòng sản phẩm của hóa đơn | `InvoiceDao.invoiceLines()` |
| `sp_get_invoice_payments` | PROCEDURE | `p_invoice_id` | Các lượt thanh toán của hóa đơn. **5 cột, đúng thứ tự** `Ma_TT, So_tien, Phuong_thuc, Chi_tiet, Tien_khach_dua`. `Tien_khach_dua` là cột **số** riêng (`NULL` cho thẻ/ví), không `CONCAT` vào `Chi_tiet` -- định dạng tiền là việc của Java | `InvoiceDao.invoicePayments()` |
| `sp_get_invoice_returns` | PROCEDURE | `p_invoice_id` | Thông tin trả hàng (7 cột, có `Lan_tra`) | `InvoiceDao.invoiceReturns()` |
| `sp_get_returnable_lines` | PROCEDURE | `p_invoice_id` | Các dòng của hóa đơn kèm **số lượng còn trả được** | `InvoiceDao.returnableLines()` |
| `sp_get_invoice_edit_fields` | PROCEDURE | `p_invoice_id` | 7 trường thô của hóa đơn cho hộp thoại sửa (trả **mã**, không join `Invoice_Line`) | `InvoiceDao.invoiceEditFields()` |
| `sp_get_invoice_line_edit_fields` | PROCEDURE | `p_invoice_id`, `p_line_number` | 4 trường thô của một dòng hàng cho hộp thoại sửa, đúng thứ tự `Barcode, Quantity, Unit_Price, Discount` | `InvoiceDao.invoiceLineEditFields()` |
| `sp_report_daily_takings` | PROCEDURE | *(không)* | Báo cáo doanh thu 7 ngày | `ReportDao.dailyTakings()` |
| `sp_report_price_rise` | PROCEDURE | *(không)* | Báo cáo sản phẩm tăng giá > 10% | `ReportDao.priceRise()` |
| `sp_report_best_cashier` | PROCEDURE | *(không)* | Báo cáo thu ngân xuất sắc | `ReportDao.bestCashier()` |
| `sp_report_big_returns` | PROCEDURE | *(không)* | Báo cáo hóa đơn trả hàng > 50% | `ReportDao.bigReturns()` |
| `sp_report_old_price_proof`| PROCEDURE | *(không)* | Chứng minh hóa đơn cũ giữ giá | `ReportDao.oldPriceProof()` |
| `sp_create_invoice_header` | PROCEDURE | `(6 tham số)` | Ghi đầu hóa đơn mới, **luôn là `OPEN`** -- chưa thu đủ tiền thì chưa `PAID` | `InvoiceDao.createInvoice()` |
| `sp_add_invoice_line` | PROCEDURE | `(6 tham số)` | Ghi dòng chi tiết hóa đơn | `InvoiceDao.createInvoice()` |
| `sp_add_payment` | PROCEDURE | `(6 tham số)` | Ghi thanh toán & rẽ nhánh bảng con | `InvoiceDao.createInvoice()` |
| `sp_settle_invoice` | PROCEDURE | `p_invoice_id` | **Chỗ duy nhất đặt `PAID`**: chuyển khi đã thu đủ (`paid >= due` và có dòng hàng). Không trả ResultSet, idempotent | `InvoiceDao.createInvoice()` |
| `sp_void_invoice` | PROCEDURE | `p_invoice_id` | **Đường duy nhất tới `VOIDED`**. Huỷ ≠ xoá: dữ liệu còn nguyên, chỉ không vào báo cáo | `InvoiceDao.voidInvoice()` |
| `sp_add_return` | PROCEDURE | `(5 tham số)` | Ghi một lượt trả hàng. **Chốt cuối** của các quy tắc nghiệp vụ: `SIGNAL '45000'` khi số lượng sai / hóa đơn không phải `PAID` | `InvoiceDao.processReturn()` |
| `sp_delete_return` | PROCEDURE | `p_invoice_id`, `p_line_number`, `p_return_id` | Hủy một lượt trả hàng đã ghi | `InvoiceDao.deleteReturn()` |
| `sp_update_invoice_header` | PROCEDURE | `(7 tham số)` | Sửa ngày/giờ/quầy/thu ngân/khách. **`p_status` là một phép kiểm, không đổi trạng thái được**: phải bằng trạng thái đang có, khác là `SIGNAL '45000'` | `InvoiceDao.updateInvoiceHeader()` |

---

## 7. Quy Ước Nhãn & Con Số Trên Giao Diện

Bốn quy ước dưới đây sinh ra từ khối 5 (`plan/14`), sau khi phát hiện bộ định
dạng số bị chép **bốn** lần và nhãn phương thức thanh toán bị chép **ba** lần.

### 7.1. Định dạng số chỉ qua `Ui`

**Không `new DecimalFormat(...)` ở bất kỳ file nào ngoài `billing.ui.Ui`.**

| Hàm | Mẫu | Ví dụ | Dùng cho |
|---|---|---|---|
| `Ui.money(Object)` | `#,##0` + `đ`, dấu nghìn `.` | `135.100đ` | mọi cột tiền; `null` → `0đ` |
| `Ui.qty(Object)` | `#,##0.###`, dấu thập phân `,` | `1,5` / `2` | `Quantity`, `Return_Quantity` |
| `Ui.percent(Object)` | `#0.##` + `%`, không dấu nghìn | `8%` / `7,5%` | `Tax_Rate`, `VAT_pct`, `Phan_tram_tra` |
| `Ui.integer(Object)` | `#,##0` | `1.234` | số lượt, số hóa đơn, cột dạng mã |
| `Ui.qtyInput(BigDecimal)` | `0.###`, **không** dấu nghìn, dấu thập phân `.` | `1.5` | giá trị điền sẵn vào ô **số lượng** |
| `Ui.moneyInput(BigDecimal)` | `#,##0`, có dấu nghìn | `158.000` | giá trị điền sẵn vào ô **tiền** |
| `Ui.dec(Object)` | — | — | giá trị thô từ JDBC → `BigDecimal`, không qua `double` |

**Phần trăm không thuộc nhóm tiền tệ.** Xếp `VAT_pct` vào nhóm tiền là nó hiện
ra thành `"8đ"` -- lỗi #21 của `plan/14`, và là lý do
`InvoiceViewPanel.fillFormatted` có tham số thứ sáu `percentCols`.

### 7.2. Đọc số chỉ qua `Ui.readMoney` / `Ui.readQuantity`

**Không `new BigDecimal(textField.getText())` ở bất kỳ đâu.** Hai hàm này tự
hiện thông báo rồi trả `null`, nên chỗ gọi chỉ cần `if (v == null) return;`.

**Hai quy tắc, và sự khác nhau là CỐ Ý:**

| | **Tiền** (`readMoney`) | **Số lượng** (`readQuantity`) |
|---|---|---|
| Dấu `.` | **tách nghìn**, phải đủ 3 chữ số theo sau | **dấu thập phân** |
| Dấu `,` | dấu thập phân, tối đa 2 chữ số | dấu thập phân |
| Nhận | `500.000`, `1.234,50`, `500000`, `35 000 đ` | `2`, `1,5`, `1.5`, `0,25` |
| Từ chối | `500.00`, `1.2.3`, `1,234`, `500,000.00`, `<= 0` | quá 3 chữ số thập phân, hai dấu thập phân, `<= 0`, vượt `max` |
| Kiểu đích | `DECIMAL(12,2)` | `DECIMAL(10,3)` |

Một quy tắc chung buộc phải chọn một nghĩa duy nhất cho dấu `.`, và nghĩa nào
cũng làm sai một nửa các ô nhập. Vì hai quy tắc là cố ý, **mỗi ô nhập phải có
tooltip nêu đúng quy tắc của nó kèm ví dụ**, và ô tiền có **nhãn vọng lại**
(`Ui.attachMoneyEcho`) để người dùng thấy app hiểu gì *trước khi* bấm nút.

`readMoneyAllowZero` là biến thể nhận số `0`: một dòng thanh toán 0 đồng là vô
nghĩa, nhưng `Invoice_Line.Discount = 0.00` là giá trị bình thường nhất của cột đó.

### 7.3. Không rẽ nhánh theo nhãn hiển thị

So sánh bằng **mã**, không bằng chữ hiện trên màn hình. Án lệ: lỗi #5 của
`plan/14` so `"Tiền mặt"` (có dấu) với `PaymentEntry.methodLabel()` trả
`"Tien mat"` (không dấu) -- hai chuỗi không bao giờ bằng nhau, nên 12 dòng code
**chưa từng chạy một lần nào**.

Dùng `enum`: `InvoiceStatus` (khối 4) và `PayMethod` (khối 5) là **bản chép duy
nhất trong Java** của hai bộ giá trị đó. Nhãn của `PayMethod` phải **khớp từng
chữ** với chuỗi mà `CASE` trong `sp_get_invoice_payments` trả về, nếu không một
hóa đơn sẽ hiện hai cách viết cho cùng một phương thức.

### 7.4. Một file dùng một lối viết

Tiếng Việt **có dấu** hoặc **không dấu**, nhưng không lẫn trong cùng một file.
Một màn hình hiện `"Tiền mặt"` trong ô chọn và `"Tien mat"` ở bảng ngay dưới thì
tệ hơn là hiện `"Tien mat"` ở cả hai.

**Chuỗi do SQL sinh ra thì giữ nguyên dạng SQL.** Vì `CASE` trong
`sp_get_invoice_payments` trả về chuỗi không dấu và chuỗi đó hiện ở tab 2 và tab
3, `NewInvoicePanel` (tab 1), `InvoiceViewPanel` (tab 3) và các hộp thoại đều
dùng **không dấu**. `SearchPanel` (tab 2) giữ **có dấu** như `plan/13` mục 5.6
đã chốt -- nó không hiển thị nhãn nào do SQL sinh ra.

### 7.5. Bốn quy ước trên có một cái chốt bằng số

```powershell
.\scripts\ps1\check-contracts.ps1      # macOS/Linux: ./scripts/bash/check-contracts.sh
```

`tools/check/ContractCheck.java` kiểm bằng code những thứ **sai thì không ai
thấy**: hai quy tắc đọc số, công thức giảm giá, hợp đồng tiền mặt
(`tendered`/`recorded`/`change`), và mã QR có thật sự đọc `config.properties`
hay không. Án lệ của nó là lỗi #1 của `plan/14` -- thu ngân gõ `500.000` và app
ghi vào database **500 đồng**, sai đúng 1000 lần, không một ngoại lệ, không một
cảnh báo. Loại sai đó phải có một cái chốt bằng số.

Nó **chỉ** kiểm các kịch bản không cần bấm chuột; phần tương tác Swing vẫn phải
kiểm bằng tay (`plan/14` mục 9.3 ghi rõ cái nào đã chạy, cái nào chưa). Nó ghi
3 hoá đơn thật rồi xoá, không chạm dữ liệu seed. Sửa file đó thì đọc `plan/14`
mục 9.4 trước -- tham số `-Djava.awt.headless=true` là bắt buộc, không phải
tuỳ chọn.
