# Java nói chuyện với MySQL như thế nào

Tài liệu này giải thích đúng thứ bạn đang thiếu: **cơ chế** Java gửi query xuống database và nhận kết quả về.

---

## 1. Bức tranh tổng thể

```
   Code của bạn
        |
        v
   java.sql.*          <-- JDBC: chỉ là INTERFACE, không có code thật
        |
        v
   mysql-connector-j.jar   <-- driver: bản cài đặt thật cho MySQL
        |
        v  (giao thức mạng MySQL, cổng 3306)
        |
   MySQL Server
```

Ba điều rút ra:

1. **Java không biết MySQL.** Nó chỉ biết bộ interface `java.sql.*` (Connection, Statement, ResultSet...). Bộ này giống hệt nhau dù bạn dùng MySQL, PostgreSQL hay Oracle.
2. **Driver là bản dịch.** File `.jar` trong thư mục `lib/` chứa code thật để nói chuyện với MySQL. Đổi sang PostgreSQL chỉ cần đổi file jar và chuỗi URL — code Java gần như giữ nguyên.
3. **Thiếu jar trong classpath** ⇒ lỗi kinh điển `No suitable driver found for jdbc:mysql://...`

> Từ JDBC 4.0 trở đi bạn **không cần** viết `Class.forName("com.mysql.jdbc.Driver")` nữa. Driver tự đăng ký khi có mặt trong classpath. Tutorial cũ nào còn dạy dòng đó là đã lỗi thời.

---

## 2. Sáu bước, không bao giờ thay đổi

Mọi lần truy vấn đều đi qua đúng các bước này. Xem `src/billing/db/QueryResult.java` để thấy cả sáu bước nằm trong một hàm.

```java
// BƯỚC 1 - Mở kết nối
Connection conn = DriverManager.getConnection(url, user, password);

// BƯỚC 2 - Chuẩn bị câu lệnh. Chỗ cần giá trị thì đặt dấu ?
String sql = "SELECT Price FROM Price_History WHERE Barcode = ? AND Valid_From_Date <= ?";
PreparedStatement ps = conn.prepareStatement(sql);

// BƯỚC 3 - Điền giá trị vào các dấu ?   (ĐÁNH SỐ TỪ 1, KHÔNG PHẢI 0)
ps.setString(1, "8934008");
ps.setDate(2, java.sql.Date.valueOf(LocalDate.now()));

// BƯỚC 4 - Thực thi
ResultSet rs = ps.executeQuery();      // SELECT      -> trả ResultSet
// int n   = ps.executeUpdate();       // INSERT/UPDATE/DELETE -> trả số dòng bị ảnh hưởng

// BƯỚC 5 - Duyệt kết quả
while (rs.next()) {                    // next() nhảy tới dòng kế, trả false khi hết
    BigDecimal price = rs.getBigDecimal("Price");
}

// BƯỚC 6 - Đóng (ngược thứ tự)
rs.close(); ps.close(); conn.close();
```

### `ResultSet` hoạt động ra sao

Hãy tưởng tượng một con trỏ đứng **trước** dòng đầu tiên:

```
          [con trỏ ở đây]
   ------------------------
   dòng 1 :  33000
   dòng 2 :  28000
```

`rs.next()` đẩy con trỏ xuống một bậc và trả `true` nếu còn dữ liệu. Vì vậy:

- Muốn lấy **nhiều dòng**: `while (rs.next()) { ... }`
- Muốn lấy **đúng một dòng**: `if (rs.next()) { ... }`
- **Chưa gọi `next()` mà đã `rs.getString(...)`** ⇒ lỗi `Before start of result set`. Đây là lỗi số 1 của người mới.

---

## 3. `try-with-resources` — đừng bao giờ tự gọi `close()`

Đoạn code ở mục 2 có một lỗ hổng: nếu `executeQuery()` ném exception thì `conn.close()` không bao giờ chạy. Kết nối bị rò rỉ. Rò đủ nhiều thì MySQL từ chối kết nối mới và cả hệ thống chết.

Cách viết đúng — mở tài nguyên trong ngoặc của `try`:

```java
try (Connection conn = Db.getConnection();
     PreparedStatement ps = conn.prepareStatement(sql)) {

    ps.setString(1, barcode);

    try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) return rs.getBigDecimal("Price");
    }
}   // Java TỰ gọi close() theo thứ tự ngược lại, kể cả khi có exception
```

Điều kiện: lớp đó phải implement `AutoCloseable` — `Connection`, `Statement`, `ResultSet` đều có.

---

## 4. `PreparedStatement` vs `Statement` — vì sao luôn chọn cái đầu

### Cách SAI

```java
String barcode = tfBarcode.getText();
String sql = "SELECT * FROM Product WHERE Barcode = '" + barcode + "'";
stmt.executeQuery(sql);
```

Nếu người dùng gõ vào ô nhập:

```
' OR '1'='1
```

Câu lệnh gửi xuống MySQL trở thành:

```sql
SELECT * FROM Product WHERE Barcode = '' OR '1'='1'
```

`'1'='1'` luôn đúng ⇒ trả về **toàn bộ bảng**. Với ô đăng nhập thì đây là đăng nhập không cần mật khẩu. Đây là **SQL Injection**, và nó vẫn đang đứng top các lỗ hổng web phổ biến nhất.

### Cách ĐÚNG

```java
PreparedStatement ps = conn.prepareStatement(
        "SELECT * FROM Product WHERE Barcode = ?");
ps.setString(1, barcode);
```

Câu lệnh và dữ liệu được gửi xuống MySQL **riêng biệt**. MySQL phân tích cấu trúc câu lệnh **trước**, sau đó mới nhét giá trị vào ô `?`. Giá trị đó luôn được hiểu là **dữ liệu**, không bao giờ là **lệnh**. Kẻ tấn công gõ gì cũng chỉ thành một chuỗi ký tự vô hại.

Lợi ích kèm theo: MySQL cache được kế hoạch thực thi, chạy lại nhiều lần nhanh hơn.

**Quy tắc:** giá trị đi vào SQL thì luôn qua `?`. Không có ngoại lệ.
(Tên bảng và tên cột *không* đặt được bằng `?` — nếu buộc phải động thì phải kiểm tra bằng whitelist.)

---

## 5. Transaction — được ăn cả, ngã về không

Lưu một hóa đơn không phải một lệnh, mà là nhiều lệnh:

```
INSERT Invoice        (1 dòng)
INSERT Invoice_Line   (N dòng)
INSERT Payment        (M dòng)
```

Nếu điện cúp giữa chừng, database còn lại một hóa đơn không có dòng hàng nào — dữ liệu rác không thể sửa tự động.

```java
Connection conn = Db.getConnection();
try {
    conn.setAutoCommit(false);     // MỞ transaction

    insertInvoice(conn, ...);      // chú ý: TRUYỀN CÙNG MỘT conn
    insertLines(conn, ...);
    insertPayments(conn, ...);

    conn.commit();                 // tất cả cùng có hiệu lực

} catch (SQLException e) {
    conn.rollback();               // tất cả cùng bị hủy, như chưa từng xảy ra
    throw e;
} finally {
    conn.setAutoCommit(true);
    conn.close();
}
```

Ba điểm dễ sai:

1. **Mặc định `autoCommit = true`** — mỗi câu lệnh tự commit ngay lập tức, không gộp nhóm được. Phải tắt đi bằng `setAutoCommit(false)`.
2. **Phải dùng chung một `Connection`.** Hai connection = hai transaction độc lập, `rollback()` ở cái này không ảnh hưởng cái kia. Đây là lỗi hay gặp khi mỗi DAO tự mở connection riêng.
3. **`rollback()` phải nằm trong `catch`**, và `setAutoCommit(true)` nằm trong `finally` trước khi đóng.

Xem code thật ở `src/billing/dao/InvoiceDao.java`, hàm `createInvoice()`.

---

## 6. Kiểu dữ liệu: SQL ↔ Java

| Kiểu MySQL | Kiểu Java | Hàm đọc | Hàm ghi |
|---|---|---|---|
| `VARCHAR`, `TEXT`, `ENUM` | `String` | `getString()` | `setString()` |
| `INT` | `int` | `getInt()` | `setInt()` |
| `BIGINT` | `long` | `getLong()` | `setLong()` |
| `DECIMAL(12,2)` | **`BigDecimal`** | `getBigDecimal()` | `setBigDecimal()` |
| `DATE` | `java.sql.Date` | `getDate()` | `setDate()` |
| `TIME` | `java.sql.Time` | `getTime()` | `setTime()` |
| `DATETIME` | `java.sql.Timestamp` | `getTimestamp()` | `setTimestamp()` |
| `BOOLEAN` / `TINYINT(1)` | `boolean` | `getBoolean()` | `setBoolean()` |

### Tiền bạc: `BigDecimal`, không bao giờ `double`

```java
System.out.println(0.1 + 0.2);   // 0.30000000000000004
```

`double` lưu số theo hệ nhị phân, không biểu diễn chính xác được `0.1`. Cộng đủ nhiều lần thì báo cáo doanh thu lệch vài đồng — và kế toán sẽ hỏi tại sao.

```java
BigDecimal a = new BigDecimal("0.1");
BigDecimal b = new BigDecimal("0.2");
a.add(b);                        // đúng 0.3
```

Ba lưu ý về `BigDecimal`:

- **Khởi tạo từ `String`**, không từ `double`: `new BigDecimal(0.1)` vẫn dính sai số.
- **Bất biến**: `a.add(b)` **không** đổi `a`, nó trả về object mới. Phải viết `a = a.add(b)`.
- **So sánh bằng `compareTo()`**, không bằng `equals()`: `equals()` coi `1.0` khác `1.00`.

### `LocalDate` ↔ `java.sql.Date`

```java
LocalDate d = LocalDate.now();
ps.setDate(1, java.sql.Date.valueOf(d));        // Java -> SQL
LocalDate back = rs.getDate("Ngay").toLocalDate();  // SQL -> Java
```

### `NULL`

```java
ps.setNull(6, java.sql.Types.VARCHAR);   // ghi NULL xuống DB

String v = rs.getString("Customer_ID");
if (rs.wasNull()) { ... }                // kiểm tra giá trị vừa đọc có phải NULL không
```

Cẩn thận với số: `rs.getInt()` trả `0` khi gặp NULL, không phải `null`. Phải hỏi `rs.wasNull()` mới phân biệt được "bằng không" với "không có giá trị".

---

## 7. Phân tầng: vì sao không viết SQL thẳng trong nút bấm

Project này chia làm ba tầng:

```
billing/ui    Swing. Chỉ lo hiển thị và bắt sự kiện. KHÔNG có chữ SELECT nào.
     |
billing/dao   Mọi câu SQL nằm ở đây. Một lớp *Dao cho một nhóm bảng.
     |
billing/db    Mở/đóng kết nối, chạy câu lệnh, ghi log.
     |
   MySQL
```

Lợi ích:

- Sửa một câu query chỉ cần mở đúng một file, không phải lục khắp các file giao diện.
- Muốn đổi Swing sang web (Spring Boot chẳng hạn) thì **giữ nguyên toàn bộ tầng dao**, chỉ viết lại tầng ui.
- Viết unit test cho DAO được, vì nó không cần cửa sổ nào cả.

Đây chính là kiến trúc mà Spring Boot dùng, chỉ khác là ở đó tầng `dao` gọi là Repository và Spring tự sinh code cho bạn.

---

## 8. Những lỗi bạn chắc chắn sẽ gặp

| Thông báo lỗi | Nguyên nhân | Cách sửa |
|---|---|---|
| `No suitable driver found` | Thiếu file jar trong classpath | Chạy `setup.ps1`, kiểm tra tham số `-cp` |
| `Access denied for user 'root'@'localhost'` | Sai mật khẩu | Sửa `db.password` trong `config.properties` |
| `Unknown database 'retail_billing'` | Chưa tạo DB | Chạy `sql/01_schema.sql` |
| `Communications link failure` | MySQL chưa chạy / sai cổng | Mở Services, bật `MySQL80` |
| `Before start of result set` | Quên gọi `rs.next()` | Bọc trong `if`/`while (rs.next())` |
| `Cannot add or update a child row: foreign key constraint fails` | Ghi con trước khi có cha | Insert `Invoice` trước `Invoice_Line` |
| `Column 'X' in field list is ambiguous` | Hai bảng JOIN cùng có cột tên X | Ghi rõ `i.Invoice_ID` thay vì `Invoice_ID` |
| `Table 'retail_billing.return' doesn't exist` | `RETURN` là từ khóa MySQL | Bọc backtick: `` `Return` `` |
| `Parameter index out of range` | Số dấu `?` không khớp số lần `set...()` | Đếm lại, nhớ đánh số từ 1 |

---

## 9. Bước tiếp theo khi bạn đã hiểu phần này

1. **Connection pool (HikariCP).** Mở connection mới mỗi lần rất chậm (~50ms). Pool giữ sẵn 10 connection và cho mượn. Đây là thứ đầu tiên nên thêm vào dự án thật.
2. **Spring JdbcTemplate.** Bỏ được đống boilerplate, nhưng vẫn là SQL thật do bạn viết. Bước chuyển tiếp tự nhiên nhất từ JDBC thuần.
3. **JPA / Hibernate.** Sinh SQL tự động từ class Java. Mạnh nhưng che giấu nhiều thứ — nắm chắc JDBC trước rồi hãy dùng, nếu không sẽ không debug nổi khi nó sinh ra query chậm.

---

## Liên quan

- [01-NORMALIZATION.md](01-NORMALIZATION.md) — vì sao database lại chia nhiều bảng như vậy
- [03-SCHEMA.md](03-SCHEMA.md) — giải thích từng bảng
- [05-SQL-JAVA-CONVENTION.md](05-SQL-JAVA-CONVENTION.md) — quy chuẩn phân chia và giao tiếp giữa Dev Java và Dev SQL
