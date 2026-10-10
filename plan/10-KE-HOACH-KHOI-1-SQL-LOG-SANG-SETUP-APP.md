# Kế Hoạch Chi Tiết — Khối 1: Chuyển Nhật Ký SQL Sang App Quản Lý Môi Trường

> **Dành cho AI / lập trình viên tiếp theo:** đây là khối đầu tiên trong đợt cải tiến gồm 6 khối
> (1 SQL log → Setup App, 2 đăng nhập nhanh, 3 sửa trả hàng, 4 trạng thái PAID/OPEN/VOIDED,
> 5 UI tab lập hoá đơn, 6 seed động 90 ngày). Khối này độc lập hoàn toàn với 5 khối còn lại:
> làm xong là build và kiểm thử được ngay, không cần chạm vào database.

---

## 1. Bối Cảnh & Vấn Đề

### 1.1. Hiện trạng

- `src/billing/db/SqlLog.java` gom mọi câu SQL vào một `static List<String>` **trong RAM** của tiến
  trình billing, đồng thời `System.out.print` ra console.
- `src/billing/ui/SqlLogPanel.java` là màn hình đọc list đó. Nhưng tab này **đã bị bỏ khỏi**
  `billing/ui/MainWindow.java` từ trước (`MainWindow` hiện chỉ còn 4 tab: Lập hoá đơn, Tra cứu dữ
  liệu, Xem hoá đơn, Báo cáo) → `SqlLogPanel` là **code chết**, biên dịch ra nhưng không ai mở được.
- Nhật ký mất sạch khi app billing tắt.

### 1.2. Mục tiêu

1. App billing **không còn màn hình nhật ký SQL** — nó chỉ còn nhiệm vụ *ghi lại*.
2. Setup App (app quản lý môi trường) có **tab 5 "SQL Log"** để xem, lọc, xoá nhật ký đó.
3. Xem được nhật ký **kể cả khi app billing đã tắt**, và kể cả khi billing được bật bằng tay
   ngoài Setup App (ví dụ chạy từ IntelliJ).

### 1.3. Ràng buộc kỹ thuật phải giải quyết

`billing.Main` và `envsetup.Main` là **hai tiến trình JVM riêng biệt**, nạp bằng hai classpath khác
nhau (`run.ps1` loại trừ `src/envsetup`, `build-setup.ps1` chỉ biên dịch `src/envsetup`). Chúng
**không dùng chung được một đối tượng Java nào**. Vì vậy phải có một đường truyền bên ngoài JVM.

Ba phương án đã cân nhắc, phương án đã chốt là **(A) file trung gian**:

| | Phương án | Vì sao chọn / loại |
|---|---|---|
| **A** | **Billing ghi file, Setup App tail file** | **ĐÃ CHỌN.** Xem được sau khi app tắt; không phụ thuộc cách khởi chạy billing; không cần socket/port; debug được bằng Notepad. |
| B | Lọc stdout của tiến trình billing | Loại: chỉ hoạt động khi billing được bật *từ* Setup App, và log bay mất khi tiến trình kết thúc. |
| C | Đọc `general_log` của MySQL | Loại: cần quyền SUPER, làm chậm MySQL, và mất đúng hai thứ giá trị nhất mà app đang ghi — giá trị tham số `?` và thời gian chạy phía client. |

---

## 2. Hợp Đồng Giữa Hai App (quan trọng nhất)

Hai app không chia sẻ code, nên **định dạng file là hợp đồng duy nhất** giữa chúng. Sửa một bên
phải sửa bên kia.

### 2.1. Đường dẫn

```
<thư mục gốc dự án>/logs/sql.log        <- file đang ghi
<thư mục gốc dự án>/logs/sql.log.1      <- bản lưu trước đó sau khi xoay file
```

- **Phía billing:** đường dẫn tương đối `logs/sql.log`, tức là tính theo thư mục làm việc hiện tại.
  App billing vốn đã giả định thư mục làm việc là gốc dự án (`billing/db/Db.java` đọc
  `config.properties` bằng đường dẫn tương đối), và `run.ps1` / `run.sh` đều `Set-Location`
  về gốc dự án trước khi gọi `java`. Khối này giữ nguyên giả định đó, không phát minh thêm cách
  dò thư mục gốc.
- **Phía Setup App:** dùng `PlatformUtil.getProjectRoot()` (đã có sẵn, biết leo ngược từ vị trí
  `SetupApp.jar`) rồi ghép `logs/sql.log`.
- Không đưa đường dẫn này vào `config.properties`: thêm một khoá cấu hình nữa chỉ tạo thêm một
  chỗ để lệch nhau giữa hai app, trong khi app demo không có nhu cầu đổi đường dẫn.

### 2.2. Định dạng: **một câu SQL = đúng một dòng**

```
2026-10-10 14:03:21.123 | 12 ms | 3 dong | {call sp_get_invoice_lines(?)} | ? 1=INV-0001
2026-10-10 14:03:24.880 | 4 ms | 1 dong | SELECT Price FROM Price_History WHERE Barcode = ? AND ... | ? 1=8934008, 2=2026-10-10
2026-10-10 14:03:30.004 | 61 ms | 0 dong | COMMIT;  -- luu thanh cong hoa don INV-0042 |
```

Dòng đánh dấu mỗi lần app billing khởi động:

```
=== APP START 2026-10-10 14:03:19 | pid 24180 | jdbc:mysql://localhost:3306/retail_billing ===
```

**Vì sao một dòng mà không giữ khối nhiều dòng như `SqlLog` hiện tại?** Vì phía Setup App có ô
"lọc dòng chứa...". Với khối nhiều dòng (dòng thời gian, dòng SQL, dòng tham số, dòng gạch ngang),
lọc theo `SELECT` sẽ ném mất dòng thời gian của chính câu đó — lọc trở nên vô dụng. Một dòng một
câu thì lọc, `Select-String`, `grep`, đếm số câu đều đúng. Đổi lại dòng dài: SQL trong app chủ yếu
là `{call sp_...}` ngắn, còn SQL nhiều dòng (ví dụ `nextPaymentSeq`) sẽ được **co mọi khoảng trắng
liên tiếp thành một dấu cách**, và cắt ở 500 ký tự kèm `...` để một câu dị thường không làm vỡ bảng.

Quy tắc chi tiết:

- Dấu phân cách là ` | ` (space-pipe-space). Trường cuối (tham số) **có thể rỗng** nhưng dấu `|`
  vẫn còn, để số trường luôn cố định là 5.
- Mốc thời gian `yyyy-MM-dd HH:mm:ss.SSS` — có **ngày** chứ không chỉ `HH:mm:ss` như hiện tại, vì
  file giờ sống qua nhiều phiên làm việc, nhiều ngày.
- Mọi ký tự `\r` và `\n` bên trong câu SQL hoặc giá trị tham số bị thay bằng dấu cách. Đây là bất
  biến bắt buộc: một dòng trong file *phải* là một bản ghi.

### 2.3. Xoay file

Trước mỗi lần ghi, kiểm tra kích thước file; nếu `>= 2 MB` thì đổi tên `sql.log` → `sql.log.1`
(ghi đè bản cũ nếu có) rồi ghi tiếp vào `sql.log` mới. Giữ đúng một bản lưu — nhật ký SQL của app
demo không cần lưu trữ lâu dài, chỉ cần không phình vô hạn.

### 2.4. Hai nguyên tắc an toàn

1. **Mở — ghi — đóng ngay cho từng bản ghi**, không giữ `Writer` mở suốt phiên.
   Lý do: trên Windows, một file đang bị tiến trình khác giữ handle thì xoá/đổi tên hay bị từ chối.
   Nếu billing giữ handle suốt phiên thì nút "Xoá log" bên Setup App sẽ thất bại đúng lúc người
   dùng cần nó nhất. Tần suất ghi ở đây do người dùng bấm chuột quyết định (vài câu mỗi lần bấm),
   nên chi phí mở/đóng file là không đáng kể.
2. **Lỗi ghi log tuyệt đối không được làm app billing chết.** Mọi thao tác file bọc trong
   `try/catch (Exception)`; lỗi đầu tiên in một dòng cảnh báo ra `System.err` rồi **tự tắt hẳn việc
   ghi file** cho phần còn lại của phiên (cờ `disabled`) để không spam cảnh báo ở mỗi câu SQL.
   Nghiệp vụ bán hàng không bao giờ được đổ vì cái ổ đĩa đầy.

---

## 3. Kiến Trúc & Chi Tiết Từng File

### 3.1. Sửa `src/billing/db/SqlLog.java`

Giữ **nguyên chữ ký** `public static void add(String sql, Object[] params, long millis, int rows)`
— đây là điểm cốt yếu: có khoảng 20 chỗ gọi rải khắp `billing/dao/*.java`, không phải sửa chỗ nào.

Bỏ: `private static final List<String> lines`, `dump()`, `clear()` (chỉ `SqlLogPanel` dùng, mà
panel đó sắp bị xoá).

Thêm:

```java
public class SqlLog {

    /** Hợp đồng với Setup App -- xem plan/10-KE-HOACH-KHOI-1-SQL-LOG-SANG-SETUP-APP.md */
    private static final Path LOG_FILE = Paths.get("logs", "sql.log");
    private static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final int  MAX_SQL_LEN = 500;

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /** Bật lên khi ghi file thất bại: từ đó chỉ còn in console, không thử ghi nữa. */
    private static boolean disabled = false;

    /** Ghi dòng mốc "=== APP START ... ===". Gọi một lần từ billing.Main. */
    public static void appStart(String dbUrl) { ... }

    public static synchronized void add(String sql, Object[] params, long millis, int rows) {
        String line = stamp() + " | " + millis + " ms | " + rows + " dong | "
                    + oneLine(sql, MAX_SQL_LEN) + " | " + formatParams(params);
        System.out.println(line);   // console vẫn thấy, tab Java của Setup App cũng thấy
        write(line);
    }

    /** Co mọi khoảng trắng (kể cả \r\n) thành một dấu cách, cắt nếu quá dài. */
    private static String oneLine(String s, int max) { ... }

    /** "? 1=INV-0001, 2=2026-10-10" hoặc chuỗi rỗng nếu không có tham số. */
    private static String formatParams(Object[] params) { ... }

    private static void write(String line) {
        if (disabled) return;
        try {
            Files.createDirectories(LOG_FILE.getParent());
            rotateIfTooBig();
            Files.writeString(LOG_FILE, line + System.lineSeparator(),
                    StandardCharsets.UTF_8, CREATE, APPEND);
        } catch (Exception e) {
            disabled = true;
            System.err.println("[SqlLog] Khong ghi duoc " + LOG_FILE.toAbsolutePath()
                    + " -- tat ghi file, chi con in console. Ly do: " + e.getMessage());
        }
    }

    private static void rotateIfTooBig() throws IOException { ... }
}
```

`synchronized` vì `add()` tuy phần lớn được gọi từ EDT nhưng không có gì bảo đảm điều đó về sau
(ví dụ sau này DAO chạy trong `SwingWorker`), và hai luồng ghi xen nhau sẽ phá bất biến "một dòng
một bản ghi".

### 3.2. Sửa `src/billing/Main.java`

Thêm một dòng gọi `SqlLog.appStart(Db.getUrl())` ngay sau khi test kết nối thành công, trước khi
mở `LoginDialog`. Nhờ dòng mốc này, người đọc log phân biệt được "phiên chạy nào" trong một file
chứa nhật ký của nhiều ngày.

### 3.3. Xoá `src/billing/ui/SqlLogPanel.java`

Xoá hẳn file. Kiểm tra trước bằng `grep -rn SqlLogPanel src/` — nếu còn chỗ nào tham chiếu thì sửa
chỗ đó trước (dự kiến: không còn chỗ nào, panel đã bị bỏ khỏi `MainWindow` từ trước).

### 3.4. Mới: `src/envsetup/runner/LogTailer.java`

Đọc phần **mới thêm** của một file văn bản UTF-8, không đọc lại từ đầu mỗi lần.

```java
public final class LogTailer {

    private final File file;
    private long offset;        // số byte đã đọc xong
    private byte[] carry;       // phần byte của dòng chưa trọn vẹn, chờ lần đọc sau

    public LogTailer(File file) { ... }

    /** Trả về các dòng MỚI kể từ lần gọi trước. Không bao giờ ném ngoại lệ. */
    public List<String> pollNewLines() { ... }

    /** Quên vị trí đã đọc, lần poll sau sẽ đọc lại từ đầu file. */
    public void rewind() { offset = 0; carry = null; }

    public boolean exists()  { return file.isFile(); }
    public long   length()   { return file.isFile() ? file.length() : 0L; }
    public File   getFile()  { return file; }
}
```

Ba tình huống phải xử lý đúng — đây là chỗ dễ sai nhất của khối này:

| Tình huống | Dấu hiệu | Xử lý |
|---|---|---|
| File chưa tồn tại (chưa chạy billing lần nào) | `!file.isFile()` | Trả về danh sách rỗng, không ném lỗi. Tab hiện gợi ý "hãy chạy app billing". |
| File bị **co lại** (người dùng bấm Xoá log, hoặc file vừa bị xoay) | `file.length() < offset` | `rewind()` rồi đọc từ đầu; tab cũng xoá console để không còn log cũ dính lại. |
| Đọc đúng lúc billing đang ghi dở một dòng | byte cuối không phải `\n` | **Chỉ decode tới dấu `\n` cuối cùng**, giữ phần đuôi vào `carry` cho lần sau. Nếu decode cả đoạn dở thì ký tự UTF-8 nhiều byte (chữ có dấu) bị chẻ làm đôi thành `?`. |

Đọc bằng `RandomAccessFile` + `seek(offset)` + đọc byte thô, **decode sau** khi đã cắt theo `\n` —
không dùng `BufferedReader.readLine()` trên stream đang được ghi, vì `readLine()` trả về dòng dở
dang mà không cho biết nó dở.

### 3.5. Mới: `src/envsetup/ui/SqlLogTab.java`

Kế thừa `BaseTab` để dùng lại khung card + `LogConsole` (`LogConsole` đã có sẵn gom dòng theo lô
60ms, giới hạn 5000 dòng, tự tô màu, nút Sao chép / Xoá log, ô "Tự động cuộn" — đúng những gì cần,
không phải viết lại). Tab này **không chạy script nào**, chỉ dùng `console` và `Timer`.

```java
public class SqlLogTab extends BaseTab {

    private static final int POLL_MS = 1000;

    private final LogTailer tailer =
            new LogTailer(new File(PlatformUtil.getProjectRoot(), "logs/sql.log"));
    private final JTextField tfFilter = new JTextField(18);
    private final JCheckBox  chkAuto  = new JCheckBox("Tự động đọc (1s)", true);
    private final Timer      timer;
    private int shown;      // số dòng đã hiện, cho dòng trạng thái

    public SqlLogTab() {
        super("Nhật ký SQL của ứng dụng billing",
              "Đọc logs/sql.log -- mỗi dòng là một câu SQL kèm thời gian chạy, số dòng trả về "
            + "và giá trị các tham số ?. File do app billing ghi ra khi bạn bấm trên giao diện.");
        ...
    }
}
```

Thành phần trên hàng nút:

| Thành phần | Hành vi |
|---|---|
| `Đọc lại từ đầu` (xanh dương) | `console.clear()` + `tailer.rewind()` + poll ngay. Dùng khi mới mở tab hoặc sau khi lọc. |
| `Xoá nhật ký` (đỏ) | Hộp thoại xác nhận → ghi file rỗng (`Files.write(path, new byte[0])`), `rewind()`, `console.clear()`. Nói rõ trong hộp thoại: chỉ xoá file log, **không ảnh hưởng database**. |
| `Mở thư mục log` | `Desktop.getDesktop().open(logs/)`. Nếu `Desktop` không khả dụng thì in đường dẫn tuyệt đối ra console để người dùng tự copy. |
| `Tự động đọc (1s)` (checkbox) | Bật/tắt `Timer`. |
| `Lọc dòng chứa:` (text field) | Khớp chuỗi con, **không phân biệt hoa thường**. Rỗng = hiện tất cả. Sửa nội dung ô này thì tự `Đọc lại từ đầu` để kết quả lọc nhất quán (nếu chỉ lọc dòng mới thì phần trên console vẫn là dữ liệu chưa lọc, gây hiểu sai). |

Vòng lặp `Timer` mỗi 1s:

```
nếu file không tồn tại            -> trạng thái: "Chưa có logs/sql.log. Hãy chạy app billing (tab 2)."
nếu length() == lastLength        -> không làm gì  (ghi chú: so sánh kích thước trước khi đọc, để
                                     tab đứng im hoàn toàn khi billing không chạy)
ngược lại                         -> với mỗi dòng mới: nếu khớp bộ lọc thì console.append(dòng)
                                     cập nhật trạng thái: "logs/sql.log -- 134 KB -- 812 câu SQL"
```

`console.append()` tự nhận màu theo nội dung (`detectLevel`): dòng chứa `ROLLBACK` / `EXCEPTION`
sẽ đỏ, chứa `COMMIT` ... `thanh cong` sẽ xanh. Đây là lợi ích miễn phí của việc dùng lại
`LogConsole` thay vì `JTextArea` như `SqlLogPanel` cũ.

### 3.6. Sửa `src/envsetup/ui/BaseTab.java` (chỉ thêm, không sửa code cũ)

`BaseTab` đang dựng sẵn khung card nhưng `lblStatus` và `buttonBar` đều `private`. Thêm hai method
`protected` để `SqlLogTab` dùng, không chạm vào phần điều phối script hiện có:

```java
/** Đặt dòng trạng thái của tab (tab không chạy script dùng để báo tình trạng file, ...). */
protected void setStatus(String text, Color color) { ... }

/** Thêm một component bất kỳ vào hàng nút (ô lọc, checkbox, ...). */
protected void addToButtonBar(JComponent c) { buttonBar.add(c); }
```

Bốn tab hiện có không gọi hai method này nên hành vi của chúng không đổi.

### 3.7. Sửa `src/envsetup/ui/MainWindow.java`

```java
tabs.addTab("  5. SQL Log  ", new SqlLogTab());
```

Cửa sổ đang `setSize(1050, 720)`; 5 tab với nhãn ngắn vẫn vừa một hàng ở bề rộng tối thiểu 880px —
cần xác nhận bằng mắt khi kiểm thử (tab Git trước đây từng bị tràn box, xem commit `59910eb`).

### 3.8. Sửa `.gitignore`

Thêm `logs/`. Nhật ký là sản phẩm lúc chạy, không phải mã nguồn.

---

## 4. Danh Sách Công Việc (theo thứ tự thực hiện)

1. `.gitignore`: thêm `logs/`.
2. Viết lại `billing/db/SqlLog.java` theo mục 3.1 (giữ chữ ký `add`).
3. `billing/Main.java`: gọi `SqlLog.appStart(Db.getUrl())`.
4. `grep -rn "SqlLogPanel\|SqlLog\.dump\|SqlLog\.clear" src/` → không còn chỗ nào thì xoá
   `billing/ui/SqlLogPanel.java`.
5. Biên dịch phía billing: `javac -encoding UTF-8 -d out -cp "lib\*"` trên danh sách file
   `src/**/*.java` trừ `src/envsetup` (giống `run.ps1` mục 4).
6. Viết `envsetup/runner/LogTailer.java` (mục 3.4).
7. Thêm `setStatus` / `addToButtonBar` vào `envsetup/ui/BaseTab.java` (mục 3.6).
8. Viết `envsetup/ui/SqlLogTab.java` (mục 3.5).
9. `envsetup/ui/MainWindow.java`: thêm tab 5.
10. `scripts/ps1/build-setup.ps1` để biên dịch lại `SetupApp.jar`.
11. Chạy kịch bản kiểm thử mục 5, ghi kết quả vào mục 6.

---

## 5. Kịch Bản Kiểm Thử

| # | Bước | Kết quả mong đợi |
|---|---|---|
| 1 | Mở `SetupApp.jar` khi `logs/` chưa tồn tại, vào tab 5 | Trạng thái: "Chưa có logs/sql.log. Hãy chạy app billing (tab 2)." Không có exception nào trong console. |
| 2 | Tab 2 → `Chạy source billing` → đăng nhập | Tab 5 xuất hiện dòng `=== APP START ... ===` và các câu `sp_authenticate_account`, `sp_lookup_counters`, ... trong vòng ~1 giây. |
| 3 | Trong billing: tab Lập hoá đơn → chọn sản phẩm → `Them vao hoa don` | Tab 5 thêm **đúng 2 dòng**: một câu tra `Price_History`, một câu tra khuyến mãi. Mỗi dòng có đủ `ms`, `dong` và giá trị tham số `?`. |
| 4 | Gõ `promo` vào ô `Lọc dòng chứa:` | Console đọc lại từ đầu, chỉ còn các dòng chứa `promo` (không phân biệt hoa thường). |
| 5 | Xoá ô lọc | Hiện lại toàn bộ. |
| 6 | Lưu một hoá đơn | Có dòng `sp_create_invoice_header`, nhiều dòng `sp_add_payment`, và dòng `COMMIT;` được tô xanh. |
| 7 | Bỏ tick `Tự động đọc`, thao tác thêm trong billing, bấm `Đọc lại từ đầu` | Khi bỏ tick thì console đứng im; bấm đọc lại thì các câu vừa phát sinh hiện ra. |
| 8 | **Trong lúc billing vẫn đang mở**, bấm `Xoá nhật ký` ở tab 5 | Xoá thành công (không bị "file đang được dùng bởi tiến trình khác"), console trống. Thao tác tiếp trong billing → dòng mới lại hiện ra bình thường. |
| 9 | Tắt app billing, quay lại tab 5, bấm `Đọc lại từ đầu` | Vẫn đọc được toàn bộ nhật ký của phiên vừa rồi — đây chính là điểm phương án B không làm được. |
| 10 | `Mở thư mục log` | Explorer/Finder mở đúng thư mục `logs/`. |
| 11 | Mở `logs/sql.log` bằng Notepad | Mỗi câu SQL đúng một dòng; chữ có dấu trong tham số (ví dụ tên khách hàng) hiện đúng, không thành `?` hay ký tự rác → xác nhận UTF-8 và việc cắt theo `\n` đã đúng. |
| 12 | Mở lại Setup App, xem 5 tab | 5 tab nằm gọn một hàng, không tràn, không bị cắt chữ ở bề rộng cửa sổ tối thiểu. |

Kiểm thử âm (không bắt buộc, nhưng nên thử nếu còn thời gian): đặt `logs` thành file thường thay vì
thư mục rồi chạy billing → app vẫn chạy bình thường, chỉ in một dòng cảnh báo ra console, không có
hộp thoại lỗi nào bật lên.

---

## 6. Trạng Thái Triển Khai

*(điền sau khi code xong — theo mẫu của plan/09: file đã thay đổi, những điểm làm khác kế hoạch và
lý do, kết quả từng bước kiểm thử)*
