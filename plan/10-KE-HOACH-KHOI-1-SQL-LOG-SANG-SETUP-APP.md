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
| 3 | Trong billing: tab Lập hoá đơn → chọn sản phẩm → `Them vao hoa don` | Tab 5 thêm **đúng 2 dòng**: `{? = call fn_get_product_price(?, ?)}` (tra giá theo ngày bán — việc đọc `Price_History` nằm bên trong hàm này, phía MySQL) và `{call sp_get_active_promo(?, ?)}`. Mỗi dòng có đủ `ms`, `dong` và giá trị tham số `?`. |
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

## 6. Trạng Thái Triển Khai (đã hoàn thành)

### 6.1. File đã thay đổi

| File | Trạng thái | Ghi chú |
|------|-----------|---------|
| `.gitignore` | Bổ sung | Thêm `logs/` |
| `src/billing/db/SqlLog.java` | Viết lại | Ghi file `logs/sql.log`, một bản ghi một dòng; giữ nguyên chữ ký `add()`; thêm `appStart()` |
| `src/billing/Main.java` | Chỉnh sửa | Gọi `SqlLog.appStart(Db.getUrl())` sau khi test kết nối thành công |
| `src/billing/ui/SqlLogPanel.java` | **Đã xoá** | Code chết, đã bị bỏ khỏi `billing/ui/MainWindow.java` từ trước |
| `src/envsetup/runner/LogTailer.java` | Tạo mới | Đọc phần mới của file UTF-8 theo byte thô, decode sau khi cắt theo `\n` |
| `src/envsetup/ui/SqlLogTab.java` | Tạo mới | Tab 5: `Timer` 1s, ô lọc, 3 nút, dòng trạng thái |
| `src/envsetup/ui/BaseTab.java` | Bổ sung | Thêm `setStatus()` và `addToButtonBar()` (chỉ thêm, không sửa code cũ) |
| `src/envsetup/ui/LogConsole.java` | **Sửa lỗi** | `clear()` đi chung hàng đợi với `append()` — xem mục 6.2.2 |
| `src/envsetup/ui/Icons.java` | Bổ sung | Thêm hình `FOLDER` cho nút "Mở thư mục log" |
| `src/envsetup/ui/MainWindow.java` | Chỉnh sửa | Đăng ký tab `  5. SQL Log  ` |
| `SetupApp.jar` | Build lại | Qua `scripts\ps1\build-setup.ps1`, mã thoát 0, class version 65 (Java 21) |

> Lưu ý: `.gitignore` loại trừ `src/envsetup/`, nên mã nguồn `LogTailer` / `SqlLogTab` và các sửa đổi
> trong `BaseTab` / `LogConsole` / `MainWindow` (phía envsetup) **không được commit**; các thành viên
> chỉ nhận bản `SetupApp.jar` đã build sẵn ở thư mục gốc. Phần billing (`SqlLog`, `Main`, việc xoá
> `SqlLogPanel`) thì được commit bình thường.

### 6.2. Những điểm làm khác kế hoạch (và lý do)

1. **`LogTailer` nhận diện file bị thay bằng "vân tay" 64 byte đầu, không chỉ so `length < offset`.**
   Kế hoạch (mục 3.4) chỉ nêu dấu hiệu `file.length() < offset`. Dấu hiệu đó **không đủ**: file có
   thể bị xoá trắng rồi được ghi lại *vượt* qua offset cũ giữa hai lượt poll — lúc đó kích thước vẫn
   tăng, tailer đọc tiếp từ giữa một file hoàn toàn khác và nhả ra những dòng rác bị cắt đầu. Lỗi
   này **đã tái hiện thật** trong kiểm thử T6 (xoay file khi offset đang nhỏ) trước khi sửa. Dòng đầu
   file luôn chứa mốc thời gian tới milli giây nên hai file khác nhau gần như không thể trùng vân tay.

2. **Sửa một lỗi tranh chấp (race) có sẵn trong `LogConsole.clear()`.** Bản cũ xoá bằng
   `invokeLater(() -> textPane.setText(""))`, trong khi `append()` chỉ đẩy dòng vào hàng đợi và một
   `Timer` 60ms mới ghi chúng vào document. Nếu một lượt flush đã được xếp vào hàng đợi EDT **trước**
   lúc `clear()` gọi `invokeLater`, thì flush chạy trước và ghi vào document những dòng được thêm
   *sau* lệnh xoá, rồi `setText("")` mới chạy và xoá mất chúng. Hậu quả: bấm `Đọc lại từ đầu` thì
   console trống trơn. Lỗi này **đã tái hiện thật** (kiểm thử #9 fail) trước khi sửa. Cách sửa: xoá
   trở thành một mốc đi chung hàng đợi với các dòng log, nên thứ tự xoá/ghi luôn đúng, gọi từ luồng
   nào cũng vậy. Lỗi này vốn cũng ảnh hưởng nút "Xoá log" của 4 tab cũ (bấm lúc script đang in ra),
   nên đây là sửa lỗi chung, không riêng tab 5.

3. **Ô lọc có hoãn 250ms (debounce) trước khi đọc lại từ đầu.** Kế hoạch nói "sửa nội dung ô này thì
   tự Đọc lại từ đầu". Làm đúng từng chữ thì mỗi lần gõ một ký tự là một lần đọc lại cả file 2 MB.
   Hoãn 250ms sau khi người dùng ngừng gõ cho kết quả giống hệt mà không đọc lại 5 lần cho chữ
   `promo`.

4. **Dòng trạng thái báo thêm số dòng khớp khi đang lọc.** Kế hoạch: `logs/sql.log -- 134 KB -- 812
   câu SQL`. Khi đang lọc, hiện `hiện 45 / 812 câu SQL khớp "promo"` — không có số này thì người
   dùng không biết bộ lọc đã bỏ qua bao nhiêu dòng. Số "câu SQL" **không đếm** dòng mốc
   `=== APP START ... ===` vì nó không phải câu SQL.

5. **Nút "Xoá nhật ký" ghi file rỗng chứ không xoá file** (đúng như kế hoạch mục 3.5), và cũng không
   xoá `sql.log.1`. Giữ lại file giúp tránh luôn trường hợp người dùng đang mở file đó bằng Notepad.

6. **`appStart()` cũng `synchronized`** như `add()`, để dòng mốc không bị xen vào giữa một bản ghi khác.

7. **Giá trị tham số `?` dài bị cắt ở 120 ký tự.** Kế hoạch chỉ nói cắt câu SQL ở 500. Một tham số
   dị thường (ví bản ghi dán cả đoạn văn) cũng làm vỡ bảng y như vậy.

8. **Mỗi lượt poll đọc cạn file (tối đa 64 lượt đọc 1 MB)** thay vì đọc đúng một lần. Cần thiết khi
   bấm `Đọc lại từ đầu` trên file đã lớn: một lần đọc chỉ lấy 1 MB, phần còn lại sẽ phải chờ các
   nhịp timer sau.

### 6.3. Kết quả kiểm thử đã chạy

**Biên dịch** — cả hai phía, từ trạng thái sạch:

- [x] Phía billing: 25 file, `javac -encoding UTF-8 -d out -cp "lib\*"` mã thoát 0 (chỉ còn cảnh báo
      deprecated có từ trước trong `SearchPanel.java`).
- [x] Phía Setup App: `build-setup.ps1` mã thoát 0; `LogTailer`, `SqlLogTab`, `MainWindow` trong jar
      đều là class version 65 (Java 21) — máy cài Temurin 21 chạy được.
- [x] `grep -rn "SqlLogPanel\|SqlLog.dump\|SqlLog.clear" src/` → không còn chỗ nào tham chiếu trước
      khi xoá; không còn `SqlLogPanel.class` sót trong `out/`.
- [x] `git check-ignore logs/sql.log logs/sql.log.1` → cả hai bị `.gitignore:10:logs/` chặn.

**Hợp đồng file giữa hai app** (32 phép kiểm, chạy `SqlLog` thật ghi và `LogTailer` thật đọc, tất cả PASS):

| Phép kiểm | Kết quả |
|---|---|
| Mỗi bản ghi đúng một dòng, đúng 5 trường cách nhau bằng ` \| ` | PASS |
| SQL nhiều dòng (`\n`, `\r\n`) bị co thành một dòng | PASS |
| Trường tham số rỗng nhưng dấu `\|` vẫn còn | PASS |
| SQL dài 900 ký tự bị cắt ở 500 kèm `...` | PASS |
| Tham số có dấu (`Nguyễn Thị Hoà - Đường Lê Lợi`) ghi và đọc lại đúng | PASS |
| File chưa tồn tại → danh sách rỗng, không ném lỗi | PASS |
| Đọc tăng dần: lượt sau chỉ trả về dòng mới | PASS |
| Đọc đúng lúc đang ghi dở một dòng → chưa trả về gì, dòng sau mới trọn vẹn | PASS |
| **Cắt đúng giữa một ký tự UTF-8 nhiều byte** → ghép lại đúng, không thành `?` | PASS |
| File bị xoá trắng → đọc lại được từ đầu | PASS |
| File bị xoá hẳn rồi tạo lại → đọc lại từ đầu | PASS |
| Xoay file khi `>= 2 MB`: `sql.log.1` giữ bản cũ, `sql.log` mới còn 1 dòng, tailer đọc đúng | PASS |
| Bất biến: không có `\r` hay `\n` bên trong bất kỳ bản ghi nào | PASS |

**Kịch bản mục 5** — phần tự động hoá được (điều khiển thẳng component của tab 5, 18 phép kiểm, tất
cả PASS):

- [x] **#1** Chưa có `logs/` → dòng trạng thái đúng chữ "Chưa có logs/sql.log. Hãy chạy app billing
      (tab 2)."; dựng tab không ném exception; `Timer` chạy nhiều nhịp không ném exception.
- [x] **#2** Mốc `=== APP START ... ===` hiện ra; dòng trạng thái đếm đúng số câu SQL.
- [x] **#3** Câu tra `Price_History` hiện ra kèm đủ `ms`, `dong` và `? 1=8934008, 2=2026-10-10`.
- [x] **#4** Gõ `PROMO` (hoa) khớp dòng chứa `promo` (thường); các dòng không khớp và cả mốc
      APP START bị ẩn; trạng thái báo `hiện 1 / 5 câu SQL khớp "PROMO"`.
- [x] **#5** Xoá ô lọc → hiện lại toàn bộ.
- [x] **#6** Dòng `COMMIT;` hiện ra (được `detectLevel` tô xanh vì chứa `thanh cong`).
- [x] **#7** Bỏ tick `Tự động đọc` → dòng mới không tự hiện; bấm `Đọc lại từ đầu` → hiện ra;
      tick lại → tự đọc tiếp.
- [x] **#8** (phần quyết định, chạy bằng **hai tiến trình thật**) Trong lúc một tiến trình đang ghi
      `SqlLog` liên tục 50ms/câu: ghi file rỗng từ tiến trình khác → **thành công**; xoá hẳn file →
      **thành công**; tiến trình ghi không hề báo lỗi (stderr rỗng, không có dòng
      `[SqlLog] Khong ghi duoc`), tự tạo lại file và ghi tiếp 64 dòng nữa, kết thúc bình thường.
      → Xác nhận đúng lý do của quy tắc "mở — ghi — đóng ngay cho từng bản ghi" (mục 2.4).
- [x] **#9** Sau khi tiến trình ghi đã tắt, bấm `Đọc lại từ đầu` → đọc lại được toàn bộ nhật ký của
      phiên vừa rồi, không nhân đôi nội dung.
- [x] **#8 qua hộp thoại thật** Bấm `Xoá nhật ký` → hộp thoại xác nhận hiện ra → bấm `Yes`: file về
      0 byte, console sạch và in `[OK] Đã xoá nội dung ...`; ghi tiếp một câu SQL thì dòng mới lại
      hiện ra. (Hộp thoại modal chặn EDT nên phải bấm nút bằng `invokeLater` rồi từ luồng khác tìm
      hộp thoại trong `Window.getWindows()` và bấm `Yes`.)
- [x] **#10** `Desktop.Action.OPEN` khả dụng; bấm `Mở thư mục log` mở Explorer thật, không sinh cảnh
      báo nào. Nhánh phòng lỗi (thiếu thư mục / `Desktop` không khả dụng) chỉ in cảnh báo kèm đường
      dẫn tuyệt đối, không ném exception.
- [x] **#11** File thật decode được bằng UTF-8 strict, **không có BOM**, chữ `Nguyễn Thị Hoà` đúng,
      mọi bản ghi đúng 5 trường.
- [x] **#12** Dựng `MainWindow` thật ở đúng bề rộng tối thiểu 880px: đủ 5 tab, tab 5 là `SqlLogTab`,
      **5 tab nằm gọn một hàng** — đo được tổng bề rộng 392px trong tab pane rộng 866px, còn thừa
      474px (tab Git từng tràn box ở commit `59910eb` nên lần này đo bằng `getTabBounds` chứ không
      nhìn mắt).
- [x] **Kiểm thử âm** Đặt `logs` thành file thường thay vì thư mục rồi ghi 6 bản ghi: app **không
      chết**, console vẫn in đủ 6 dòng, stderr in **đúng một** dòng cảnh báo (cờ `disabled` chặn
      spam), file `logs` không bị ghi đè.

**Dữ liệu thật trên MySQL thật** — gọi thẳng các DAO mà giao diện billing gọi
(`retail_billing` trên `localhost:3306`, 10 sản phẩm / 4 quầy / 5 thu ngân trong seed):

- [x] **#2** `AccountDao.authenticate("cashier","cashier")` thành công; nạp được quầy, thu ngân,
      sản phẩm. File có đủ dòng mốc `=== APP START ... ===`, `sp_authenticate_account`,
      `sp_lookup_counters`.
- [x] **#3** Một lần "Them vao hoa don" (= `productDao.priceOn` + `productDao.activePromoOn`) sinh ra
      **đúng 2 dòng**, mỗi dòng đủ 5 trường và có giá trị tham số thật:
      ```
      ... | 27 ms | 1 dong | {? = call fn_get_product_price(?, ?)} | ? 1=8934002, 2=2026-10-10
      ... | 15 ms | 0 dong | {call sp_get_active_promo(?, ?)}      | ? 1=8934002, 2=2026-10-10
      ```
      **Kế hoạch mục 5 ghi sai tên câu SQL**: viết là "một câu tra `Price_History`", nhưng code thật
      gọi hàm `fn_get_product_price` (việc đọc `Price_History` nằm bên trong hàm đó, phía MySQL).
      Số dòng và ý nghĩa thì đúng y như kế hoạch.
- [x] **#6** Lưu **một hoá đơn thật** qua `InvoiceDao.createInvoice` (2 dòng hàng, 2 lần thanh toán
      CASH + CARD): có `fn_next_invoice_id`, `sp_create_invoice_header`,
      `sp_add_invoice_line [batch x 2]`, **đúng 2 dòng** `sp_add_payment`, và
      `COMMIT;  -- luu thanh cong hoa don INV-0016` (chứa `thanh cong` nên `detectLevel` tô xanh).
      Hoá đơn đã thật sự vào database, **sau đó bị xoá lại** bằng `InvoiceDao.deleteInvoice` —
      database trở về đúng 17 hoá đơn / 17 thanh toán như trước khi kiểm thử.

> Không còn mục nào phải kiểm bằng tay. Việc duy nhất chưa làm là *đưa chuột bấm trên giao diện
> billing*; mọi câu SQL mà các nút đó phát ra đều đã chạy thật qua chính các DAO đó.
