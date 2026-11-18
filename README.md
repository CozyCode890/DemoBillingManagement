# Demo Retail Invoicing — Java Swing + JDBC + MySQL 8

App demo cho đề bài **19. Retail Invoicing** (IT079IU – Principles of Database Management).

Mục tiêu: cho bạn thấy **tận mắt** Java gửi query xuống MySQL như thế nào. Mỗi lần bạn bấm một nút trên giao diện, app ghi lại câu SQL vừa chạy vào tab *Nhat ky SQL*.

Không Maven, không Spring, không Hibernate — chỉ `javac`, `java` và JDBC thuần.

---

## Chạy app — một nút

| Hệ điều hành | Bấm đúp vào |
|---|---|
| Windows | **`Setup.exe`** |
| macOS | **`Setup.app`** |

Cửa sổ Setup App mở ra và tự bấm **CHẠY ỨNG DỤNG**. Không cần cài Java, MySQL hay gõ lệnh nào — chờ tới khi màn hình đăng nhập hiện ra là xong.

| Bước | Lần đầu tiên trên máy | Các lần sau |
|---|---|---|
| Java | Launcher tự tải JDK 21 bản portable (~190 MB) nếu máy chưa có | dùng lại |
| 1. Thư viện | Tải các jar khai báo trong `pom.xml` vào `lib/` (kiểm SHA-1) | bỏ qua |
| 2. MySQL | Có MySQL đang chạy ở cổng 3306 và vào được bằng tài khoản trong `config.properties` → dùng luôn. Không có → tự tải MySQL 8.0 portable (~235 MB), khởi tạo và bật | bật lại trong ~2 giây |
| 3. Database | Nạp `sql/` theo thứ tự 01 → 04 → 02 → 05 | **bỏ qua nếu SQL không đổi** |
| 4. Biên dịch | `src/` → `out/` | bỏ qua nếu mã không đổi |
| 5. Mở app | Màn hình đăng nhập (hoặc vào thẳng nếu chọn *Đăng nhập sẵn*) | |

Mọi thứ tải về nằm trong `~/.retail-billing` (Windows: `C:\Users\<tên>\.retail-billing`) — **ngoài repo**, dùng chung cho mọi nhánh và mọi bản clone. Xoá thư mục đó là về như máy mới.

### Đổi nhánh git: chỉ cần bấm CHẠY

Setup App băm nội dung các file SQL dựng database ("vân tay") và ghi vân tay đó vào MySQL mỗi lần nạp xong. Bấm CHẠY:

- SQL giống lần nạp trước → giữ nguyên database và dữ liệu, mở app ngay;
- SQL khác (đổi nhánh, pull seed mới, tự sửa routine...) → tự xoá và nạp lại (2–15 giây).

Dòng *Nhánh · SQL · Database* ngay dưới năm bước luôn cho biết database đang khớp nhánh nào, và tự cập nhật khi bạn đổi nhánh trong lúc Setup App đang mở.

> Dữ liệu bạn tự nhập trong app billing sẽ mất khi database được nạp lại — mỗi nhánh có schema và seed riêng nên không giữ chung được.

Nhánh nào thêm file SQL mới thì khai báo thứ tự nạp trong `config.properties` của nhánh đó (không khai báo thì dùng thứ tự mặc định dưới đây):

```properties
db.build.files=01_schema.sql, 04_routines.sql, 02_seed.sql, 05_invariants.sql
```

`03_queries.sql` không nằm trong danh sách này: sửa bài tập truy vấn không làm nạp lại database.

### Công cụ database

Hàng nút phụ dưới năm bước:

| Nút | Làm gì |
|---|---|
| **Nạp lại database** | Xoá và nạp lại từ `sql/` dù SQL không đổi — về đúng dữ liệu mẫu |
| **Xoá dữ liệu...** | Xoá các dòng, giữ mọi bảng và hàm/thủ tục. Hai mức: chỉ giao dịch (vẫn đăng nhập được) hoặc toàn bộ (kể cả tài khoản) — dành cho người tập viết seed |
| **Chạy 03_queries.sql** | Chạy bài tập truy vấn, in kết quả dạng bảng vào log |
| **Kiểm tra seed** | Gọi `sp_check_seed`: 14 bất biến của dữ liệu (plan/15) |
| **Tắt MySQL** | Tắt MySQL riêng của Setup App. MySQL có sẵn trên máy (service MySQL80, Homebrew...) thì không đụng tới |

Tab **Nhật ký SQL** hiện mọi câu SQL mà app billing gửi xuống MySQL.

Muốn seed tự viết chạy lại được nhiều lần, đặt `CALL sp_wipe_transactions();` ở đầu file. Chi tiết ở [docs/05 mục 8.5](docs/05-SQL-JAVA-CONVENTION.md).

<details>
<summary>Nếu launcher bị hệ điều hành chặn (chỉ xảy ra khi tải repo dạng ZIP)</summary>

`git clone` thì không gặp. Chỉ khi tải ZIP từ GitHub, hệ điều hành mới đánh dấu "tải từ Internet":

- **Windows** hiện *"Windows protected your PC"* → bấm `More info` → `Run anyway` (`Setup.exe` chưa mua chữ ký số).
- **macOS** báo *"không mở được vì không rõ nhà phát triển"* → mở Terminal chạy một lần:

  ```bash
  xattr -dr com.apple.quarantine /đường/dẫn/tới/DemoBillingManagement
  ```

- **macOS** báo không đọc được thư mục dự án: repo đang nằm trong Desktop / Documents / Downloads (vùng bảo vệ quyền riêng tư). Chuyển nó vào thư mục cá nhân rồi bấm đúp lại.
</details>

<details>
<summary>Máy đã có MySQL riêng với mật khẩu khác 'root'</summary>

Setup App không bao giờ sửa MySQL không phải của nó. Nếu cổng 3306 đang có một MySQL mà tài khoản trong `config.properties` không vào được, nó dừng lại và hướng dẫn chọn một trong hai:

1. Tắt MySQL đó (Windows: `services.msc` → MySQL80 → Stop, Startup type = Manual; macOS: `brew services stop mysql`) rồi bấm CHẠY — Setup App tự bật MySQL riêng.
2. Hoặc đổi mật khẩu `root` của MySQL đó thành `root`.

**Đừng sửa `db.password` trong `config.properties` rồi commit** — mật khẩu riêng của máy bạn sẽ đi theo sang máy cả nhóm.
</details>

### Chạy bằng IDE (IntelliJ / Eclipse)

Mở thư mục repo, IDE nhận `pom.xml`. Bấm CHẠY trong Setup App **một lần** để có MySQL và database, sau đó chạy `billing.Main` từ IDE như bình thường (thư mục làm việc = gốc repo, để app đọc được `config.properties`).

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
├── config.properties       <- kết nối DB, đăng nhập nhanh, QR, thứ tự nạp SQL (db.build.files)
├── SetupApp.jar            <- Setup App (Swing): làm mọi việc sau khi có Java
├── Setup.exe               <- BẤM ĐÚP trên Windows (launcher: tìm / tự tải JDK 21)
├── Setup.app/              <- BẤM ĐÚP trên macOS (launcher: tìm / tự tải JDK 21)
├── pom.xml                 <- danh sách thư viện: Setup App tải theo file này; IDE cũng đọc
│
├── scripts/                <- chỉ còn công cụ kiểm tra và đóng gói (chạy tay, không bắt buộc)
│   ├── ps1/  check-seed, check-contracts, build-setup, build-launcher   (Windows)
│   └── bash/ check-seed, check-contracts, build-setup, build-launcher   (macOS / Linux)
│
├── tools/launcher/         <- Mã nguồn của hai launcher (chỉ người bảo trì cần)
│   ├── WindowsLauncher.cs  <- nguồn của Setup.exe, biên dịch bằng csc.exe
│   ├── app.manifest        <- bật comctl32 v6 + DPI aware cho Setup.exe
│   ├── IconGen.java        <- vẽ icon, xuất ra cả .ico lẫn .icns
│   └── Setup.ico
│
├── tools/check/            <- Chốt bằng số cho 4 hợp đồng (docs/05 mục 7)
│   └── ContractCheck.java  <- đọc/hiện số, giảm giá, tiền mặt, mã QR
│
├── sql/
│   ├── 01_schema.sql       <- 16 bảng, có comment từng bảng
│   ├── 02_seed.sql         <- dữ liệu mẫu 90 ngày, sinh bằng vòng lặp SQL
│   ├── 03_queries.sql      <- 5 câu truy vấn đề bài, chạy được độc lập
│   ├── 04_routines.sql     <- Hàm (fn_) và Thủ tục (sp_) do Dev SQL quản lý
│   └── 05_invariants.sql   <- 14 bất biến của dữ liệu (sp_check_seed)
│
├── docs/                   <- Tài liệu giải thích chi tiết & quy chuẩn chung
│
└── src/billing/
    ├── Main.java           <- điểm khởi động ứng dụng bán hàng
    ├── db/                 <- tầng kết nối: Db, QueryResult (call), SqlLog
    ├── model/              <- các lớp dữ liệu: Product, CartLine, ...
    ├── dao/                <- Gọi Stored Functions & Procedures qua CallableStatement
    └── ui/                 <- giao diện Swing, không có SQL
```

Kiến trúc ba tầng: **ui → dao → db → MySQL**. Tầng giao diện không được viết SQL, tầng DAO không được mở cửa sổ. Đây cũng là cách Spring Boot tổ chức code, chỉ khác tên gọi.

---

## Dữ liệu mẫu có sẵn

- 3 cửa hàng, 6 quầy (**2 quầy mỗi shop**), 5 thu ngân, 30 khách hàng
- 20 sản phẩm, trong đó **5 sản phẩm tăng giá hơn 10%** trong năm nay
- **91 ngày liên tục đều có hóa đơn** — khoảng 550 hóa đơn, mỗi shop 2 hóa đơn mỗi ngày
- Cả **ba trạng thái** đều có hàng chục hóa đơn: đã thanh toán, đang lập (trả thiếu), đã hủy
- Hàng trăm hóa đơn tiền mặt có **tiền thối** thật, và hàng chục hóa đơn thanh toán **tách làm hai phương thức**
- Khoảng 50 lượt trả hàng, có cả **hai lượt trả trên cùng một dòng**, và vài hóa đơn bị trả quá nửa giá trị
- 6 chương trình khuyến mãi phủ kín 91 ngày: hai đợt **gối nhau** trên cùng một sản phẩm (phải chọn mức cao hơn), một chương trình **tạm dừng** (có dòng trong bảng mà vẫn không được áp dụng), **2 hóa đơn từ năm ngoái dùng giá cũ**

Ngày tháng trong `02_seed.sql` được tính tương đối theo `CURDATE()`, nên bạn chạy lúc nào thì cả năm báo cáo cũng có dữ liệu đáng xem.

Điểm đáng xem nhất: **ngày đổi giá nằm giữa cửa sổ 90 ngày**, nên cùng một sản phẩm xuất hiện ở **hai đơn giá khác nhau** trên hai hóa đơn mà bạn thấy cạnh nhau trong tab *Xem hóa đơn* — "hóa đơn cũ giữ giá cũ" không còn phải lùi 400 ngày mới thấy.

Dữ liệu được sinh bằng **một vòng lặp SQL đi qua đúng những thủ tục mà tab *Lập hóa đơn* đi qua**, nên mọi con số trong database là con số mà app *có thể* đã tự tạo ra. Không `Status`, `Unit_Price`, `Discount` hay `Return_ID` nào được gõ tay. Kiểm bằng nút **Kiểm tra seed** trong Setup App, hoặc từ terminal:

```powershell
.\scripts\ps1\check-seed.ps1
```

```bash
./scripts/bash/check-seed.sh
```

Chi tiết quy ước: [docs/05-SQL-JAVA-CONVENTION.md](docs/05-SQL-JAVA-CONVENTION.md) mục 8.

---

## Gặp lỗi?

| Thông báo | Cách xử lý |
|---|---|
Gần như mọi lỗi môi trường đều sửa được bằng cách **bấm CHẠY lần nữa** — Setup App kiểm tra lại từng bước và in lý do cụ thể vào log. Còn lại:

| Thông báo | Cách xử lý |
|---|---|
| *"Cổng 3306 đang có một MySQL khác chạy sẵn..."* | Máy có MySQL riêng với mật khẩu khác `root`. Xem mục *Máy đã có MySQL riêng* ở phần [Chạy app](#chạy-app--một-nút) |
| *"Không tải được ..."* | Mất mạng giữa chừng. Bấm CHẠY lại — phần đã tải được giữ lại và tải tiếp |
| Database báo lỗi ở `sql/xx.sql dòng N` | Lỗi nằm trong chính file SQL của nhánh đó (log in câu lệnh và mã lỗi MySQL). Sửa file rồi bấm CHẠY — database tự nạp lại |
| *"Biên dịch thất bại"* | Lỗi Java trong `src/`, log in `file:dòng: lỗi`. Sửa rồi bấm CHẠY |
| `Communications link failure` khi chạy `billing.Main` từ IDE | MySQL chưa bật — mở Setup App bấm CHẠY một lần |
| `UnsupportedClassVersionError ... class file version 69` | `SetupApp.jar` bị đóng gói bằng JDK mới hơn JDK đang chạy. Đóng gói lại bằng script `build-setup` (script đã khoá `--release 21`) |
| Bấm đúp `Setup.exe` báo `side-by-side configuration is incorrect` | `tools/launcher/app.manifest` hỏng cú pháp XML — chạy `scripts\ps1\build-launcher.ps1`, nó kiểm tra và chỉ ra lỗi |
| *"MySQL riêng không cho root đăng nhập"* | Tắt Setup App, xoá thư mục `~/.retail-billing/mysql-data` rồi bấm CHẠY — MySQL riêng được khởi tạo lại |
| `check-seed` báo một quy tắc `SAI` | Cột `Hop_dong` trong bảng kết quả chỉ ra mục nào của `plan/15` đang bị vi phạm. Bấm **Nạp lại database** là về 0. R12 (91 ngày liên tục) sai riêng một mình thường chỉ vì database được nạp từ nhiều ngày trước |

Danh sách lỗi JDBC đầy đủ hơn nằm ở [docs/02-JDBC.md](docs/02-JDBC.md) mục 8.

---

## Dựng lại Setup App và launcher (chỉ người bảo trì)

`SetupApp.jar`, `Setup.exe` và `Setup.app` đã được commit sẵn nên người dùng không cần dựng lại.

**`SetupApp.jar`** đóng gói từ `src/envsetup/` — thư mục này bị `.gitignore` bỏ qua (không đẩy mã nguồn công cụ lên repo), nên chỉ máy có mã nguồn mới đóng gói lại được:

```bash
powershell -ExecutionPolicy Bypass -File scripts\ps1\build-setup.ps1
```

```bash
bash scripts/bash/build-setup.sh
```

Bên trong: `envsetup.core` chứa toàn bộ logic (tải thư viện theo `pom.xml`, quản lý MySQL, vân tay SQL, tách câu lệnh theo `DELIMITER`, biên dịch bằng `javax.tools`), `envsetup.ui` chỉ là giao diện. SQL được chạy qua JDBC chứ không qua client `mysql`, nên máy chỉ có server (hoặc chưa có gì) vẫn chạy được.

**Launcher** chỉ cần dựng lại khi sửa `tools/launcher/WindowsLauncher.cs`, `app.manifest`, `IconGen.java` hoặc `Setup.app/Contents/MacOS/Setup`:

```bash
powershell -ExecutionPolicy Bypass -File scripts\ps1\build-launcher.ps1
```

```bash
bash scripts/bash/build-launcher.sh
```

Vài điều đáng biết:

- **Không cần cài thêm công cụ gì.** Trình biên dịch C# (`csc.exe`) nằm sẵn trong Windows từ bản 10, và `.icns` cho macOS được `IconGen.java` ghi tay bằng Java thuần nên máy Windows cũng tạo được, không cần `iconutil`.
- **`Setup.exe` phải dựng trên Windows**, `Setup.app` thì dựng ở đâu cũng được vì nó chỉ là thư mục chứa `Info.plist` + script bash.
- **Bit thực thi của `Setup.app/Contents/MacOS/Setup` phải nằm trong git.** Thiếu nó, máy Mac clone về sẽ báo "ứng dụng bị hỏng". Hai script build tự kiểm tra và sửa bằng `git update-index --chmod=+x`.
- **XML trong `app.manifest` cấm hai dấu gạch ngang liền nhau trong comment.** `csc.exe` nhúng manifest mà không kiểm tra nên lỗi chỉ lộ ra lúc bấm đúp; script build kiểm tra hộ trước khi biên dịch.
