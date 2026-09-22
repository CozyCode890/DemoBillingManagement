# Demo Retail Invoicing — Java Swing + JDBC + MySQL 8

App demo cho đề bài **19. Retail Invoicing** (IT079IU – Principles of Database Management).

Mục tiêu: cho bạn thấy **tận mắt** Java gửi query xuống MySQL như thế nào. Mỗi lần bạn bấm một nút trên giao diện, app ghi lại câu SQL vừa chạy vào tab *Nhat ky SQL*.

Không Maven, không Spring, không Hibernate — chỉ `javac`, `java` và JDBC thuần.

---

## Chạy app

**Mọi thứ đã được cài và kiểm thử sẵn trên máy bạn.** Mỗi lần muốn dùng app, mở **hai** cửa sổ PowerShell:

Cửa sổ 1 — bật MySQL (để nguyên, đừng đóng):

```bash
powershell -ExecutionPolicy Bypass -File .\mysql-start.ps1
```

Cửa sổ 2 — chạy app:

```bash
powershell -ExecutionPolicy Bypass -File .\run.ps1
```

Tắt MySQL khi xong:

```bash
powershell -ExecutionPolicy Bypass -File .\mysql-stop.ps1
```

### Môi trường đã cài sẵn

| Thành phần | Phiên bản | Vị trí |
|---|---|---|
| JDK | Temurin 21.0.12 | `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot` |
| MySQL Server | 8.0.45 (bản ZIP portable) | `%USERPROFILE%\mysql8\mysql-8.0.45-winx64` |
| Driver JDBC | mysql-connector-j 8.4.0 | `lib\` |
| Database | `retail_billing`, đã nạp dữ liệu mẫu | cổng 3306 |
| Tài khoản | `root` / mật khẩu `root` | đã điền sẵn trong `config.properties` |

> **Vì sao MySQL phải bật thủ công?** Bản ZIP không đăng ký Windows Service nên không tự chạy khi bật máy. Đăng ký service cần quyền Administrator — xem mục cuối README nếu bạn muốn làm.
>
> **Mật khẩu `root`/`root`** chỉ hợp lý vì đây là database học tập chạy trên localhost. Đừng dùng mật khẩu này cho bất cứ thứ gì thật.

### Nạp lại dữ liệu mẫu

Nếu bạn nghịch lung tung và muốn về trạng thái ban đầu:

```bash
powershell -ExecutionPolicy Bypass -File .\db-setup.ps1
```

> ⚠️ Script này chạy `DROP DATABASE IF EXISTS retail_billing` trước khi tạo mới. Database khác trên máy không bị ảnh hưởng.

---

## Cài lại từ đầu (trên máy khác)

<details>
<summary>Bấm để xem 6 bước</summary>

**1. Cài JDK**

```bash
winget install EclipseAdoptium.Temurin.21.JDK
```

Cài xong **đóng terminal và mở lại**, kiểm tra bằng `java -version`.

**2. Cài MySQL 8.0**

Lưu ý: `winget install Oracle.MySQL` chỉ cài *MySQL Installer* (công cụ GUI) chứ chưa có server, và nó cần quyền Administrator để cài tiếp. Cách nhanh hơn là dùng bản ZIP:

Tải `mysql-8.0.45-winx64.zip` (~233 MB) từ <https://dev.mysql.com/downloads/mysql/>, giải nén vào `%USERPROFILE%\mysql8`, rồi khởi tạo:

```bash
.\mysql8\mysql-8.0.45-winx64\bin\mysqld.exe --initialize-insecure --console
```

**3. Bật MySQL**

```bash
powershell -ExecutionPolicy Bypass -File .\mysql-start.ps1
```

**4. Đặt mật khẩu root**

```bash
.\mysql8\mysql-8.0.45-winx64\bin\mysql.exe -u root -e "ALTER USER 'root'@'localhost' IDENTIFIED BY 'root';"
```

**5. Tải driver JDBC và tạo database**

```bash
powershell -ExecutionPolicy Bypass -File .\setup.ps1
```

```bash
powershell -ExecutionPolicy Bypass -File .\db-setup.ps1
```

**6. Chạy app**

```bash
powershell -ExecutionPolicy Bypass -File .\run.ps1
```

</details>

---

## Bốn màn hình của app

### 1. Lap hoa don
Chọn quầy, thu ngân, khách hàng, ngày bán. Thêm sản phẩm → app tự tra giá trong `Price_History` và tự tìm khuyến mãi đang chạy. Thêm các dòng thanh toán (một hóa đơn có thể trả bằng nhiều phương thức). Bấm **LUU HOA DON** để ghi xuống database trong một transaction.

> **Thử ngay điều này:** đổi ô *Ngay ban* về khoảng 400 ngày trước rồi thêm sản phẩm *Thit ba chi*. Đơn giá hiện **130.000** thay vì **158.000** — app không hardcode gì cả, nó hỏi database "giá của ngày hôm đó là bao nhiêu". Đây chính là yêu cầu khó nhất của đề bài.

### 2. Xem hoa don
Danh sách hóa đơn bên trái, bấm vào để xem chi tiết bên phải: thông tin chung, các dòng hàng, các lần thanh toán, các lần trả hàng. Mỗi cú click chạy 4 câu SELECT.

### 3. Bao cao
Bốn câu truy vấn đề bài yêu cầu. Mỗi nút hiện **câu SQL** ở khung đen và **kết quả** ở bảng bên dưới, kèm một đoạn giải thích câu lệnh đó đang làm gì.

### 4. Nhat ky SQL
Mọi câu lệnh app đã gửi xuống MySQL, kèm thời gian chạy và số dòng trả về.

> **Thử ngay điều này:** vào tab này bấm *Xoa nhat ky*, sang tab 1 thêm một sản phẩm, quay lại bấm *Lam moi*. Bạn sẽ thấy đúng 2 câu SELECT: một câu tra giá, một câu tra khuyến mãi.

---

## Đọc tài liệu theo thứ tự này

| Thứ tự | File | Nội dung |
|---|---|---|
| 1 | [docs/01-NORMALIZATION.md](docs/01-NORMALIZATION.md) | **Chuẩn hóa là cái gì** — giải thích từ đầu bằng chính schema này |
| 2 | [docs/02-JDBC.md](docs/02-JDBC.md) | **Java nói chuyện với MySQL ra sao** — 6 bước JDBC, PreparedStatement, transaction, BigDecimal |
| 3 | [docs/03-SCHEMA.md](docs/03-SCHEMA.md) | Giải thích từng bảng và 3 điểm khó của đề bài |
| 4 | [docs/04-CODE-LEARNING-GUIDE.md](docs/04-CODE-LEARNING-GUIDE.md) | **Hướng dẫn học code toàn diện (Frontend & Backend)** — Flow đọc code, cạm bẫy, đường vòng và đối chiếu Spring/React |

---

## Đọc code theo thứ tự này

Nếu bạn muốn hiểu cơ chế, đọc đúng thứ tự dưới đây, mỗi file đều có comment giải thích:

| Thứ tự | File | Vì sao đọc |
|---|---|---|
| 1 | [Db.java](src/billing/db/Db.java) | Mở kết nối — điểm bắt đầu của mọi thứ |
| 2 | [QueryResult.java](src/billing/db/QueryResult.java) | **6 bước JDBC gói gọn trong một hàm** |
| 3 | [ProductDao.java](src/billing/dao/ProductDao.java) | Hàm `priceOn()` — trái tim của đề bài |
| 4 | [InvoiceDao.java](src/billing/dao/InvoiceDao.java) | Hàm `createInvoice()` — **transaction commit/rollback** |
| 5 | [ReportDao.java](src/billing/dao/ReportDao.java) | 4 câu báo cáo, mỗi câu có comment giải thích kỹ thuật |
| 6 | [NewInvoicePanel.java](src/billing/ui/NewInvoicePanel.java) | Giao diện gọi xuống DAO như thế nào |

---

## Cấu trúc thư mục

```
DemoBillingManagement/
├── README.md               <- bạn đang đọc file này
├── config.properties       <- sửa mật khẩu MySQL ở đây
├── mysql-start.ps1         <- bật MySQL (chạy trước khi mở app)
├── mysql-stop.ps1          <- tắt MySQL
├── setup.ps1               <- tải driver JDBC
├── db-setup.ps1            <- tạo lại database + nạp dữ liệu mẫu
├── run.ps1                 <- biên dịch và chạy app
├── pom.xml                 <- chỉ cần nếu bạn mở bằng IntelliJ/Eclipse
│
├── sql/
│   ├── 01_schema.sql       <- 15 bảng, có comment từng bảng
│   ├── 02_seed.sql         <- dữ liệu mẫu (ngày tháng tự tính theo hôm nay)
│   ├── 03_queries.sql      <- 5 câu truy vấn đề bài, chạy được độc lập
│   └── 04_routines.sql     <- Hàm (fn_) và Thủ tục (sp_) do Dev SQL quản lý
│
├── docs/                   <- 5 tài liệu giải thích (có quy chuẩn chung Java & SQL)
│
└── src/billing/
    ├── Main.java           <- điểm khởi động
    ├── db/                 <- tầng kết nối: Db, QueryResult (call), SqlLog
    ├── model/              <- các lớp dữ liệu: Product, CartLine, ...
    ├── dao/                <- Gọi Stored Functions & Procedures qua CallableStatement
    └── ui/                 <- giao diện Swing, không có SQL
```

Kiến trúc ba tầng: **ui → dao → db → MySQL**. Tầng giao diện không được viết SQL, tầng DAO không được mở cửa sổ. Đây cũng là cách Spring Boot tổ chức code, chỉ khác tên gọi.

---

## Dữ liệu mẫu có sẵn

- 3 cửa hàng, 4 quầy, 5 thu ngân, 4 khách hàng
- 10 sản phẩm, trong đó **5 sản phẩm tăng giá hơn 10%** trong năm nay
- 17 hóa đơn: 14 hóa đơn trong 7 ngày gần nhất, 1 hóa đơn bị hủy, **2 hóa đơn từ năm ngoái dùng giá cũ**
- 1 hóa đơn thanh toán **tách làm hai phương thức** (tiền mặt + thẻ)
- 3 lần trả hàng, trong đó **2 hóa đơn bị trả quá nửa giá trị**

Ngày tháng trong `02_seed.sql` được tính tương đối theo `CURDATE()`, nên bạn chạy lúc nào thì báo cáo "7 ngày gần nhất" cũng có dữ liệu.

---

## Gặp lỗi?

| Thông báo | Cách xử lý |
|---|---|
| `Communications link failure` | **Lỗi hay gặp nhất.** MySQL chưa bật — chạy `.\mysql-start.ps1` ở cửa sổ khác |
| `javac` / `java` không phải lệnh nhận dạng được | Chưa mở lại terminal sau khi cài JDK |
| `No suitable driver found` | Thiếu jar trong `lib\` — chạy `.\setup.ps1` |
| `Access denied for user 'root'@'localhost'` | Sai mật khẩu trong `config.properties` (phải là `root`) |
| `Unknown database 'retail_billing'` | Chưa chạy `.\db-setup.ps1` |
| App mở ra nhưng bảng trống | Chưa nạp `02_seed.sql` |

Danh sách lỗi JDBC đầy đủ hơn nằm ở [docs/02-JDBC.md](docs/02-JDBC.md) mục 8.

---

## Muốn MySQL tự chạy khi bật máy?

Hiện tại MySQL phải bật thủ công. Muốn nó thành dịch vụ chạy nền tự động, mở PowerShell **với quyền Administrator** (chuột phải → *Run as administrator*) rồi chạy:

```bash
& "$env:USERPROFILE\mysql8\mysql-8.0.45-winx64\bin\mysqld.exe" --install MySQL80 --defaults-file="$env:USERPROFILE\mysql8\my.ini"
```

File `my.ini` cần có tối thiểu:

```ini
[mysqld]
basedir=C:/Users/<ten-cua-ban>/mysql8/mysql-8.0.45-winx64
datadir=C:/Users/<ten-cua-ban>/mysql8/mysql-8.0.45-winx64/data
port=3306
```

Sau đó `Start-Service MySQL80`. Từ đó không cần `mysql-start.ps1` nữa.

Gỡ dịch vụ: `mysqld.exe --remove MySQL80` (cũng cần quyền Administrator).
