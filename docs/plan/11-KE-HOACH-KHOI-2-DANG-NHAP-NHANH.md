# Kế Hoạch Chi Tiết — Khối 2: Đăng Nhập Nhanh

> **Dành cho AI / lập trình viên tiếp theo:** đây là khối thứ hai trong đợt cải tiến gồm 6 khối
> (1 SQL log → Setup App ✅ xong, **2 đăng nhập nhanh ✅ xong — xem mục 9**, 3 sửa trả hàng,
> 4 trạng thái PAID/OPEN/VOIDED, 5 UI tab lập hoá đơn, 6 seed động 90 ngày).
>
> Khối này **không chạm vào database**: không thêm bảng, không thêm/sửa stored procedure, không sửa
> seed. Toàn bộ thay đổi nằm ở `src/billing/`, `config.properties` và hai script `run`. Vì vậy làm
> xong là biên dịch và kiểm thử được ngay, độc lập với 4 khối còn lại.

---

## 1. Bối Cảnh & Vấn Đề

### 1.1. Hiện trạng (đã đọc code, không phỏng đoán)

| Thành phần | Trạng thái hiện tại |
|---|---|
| `src/billing/Main.java:53-61` | Luôn mở `LoginDialog` modal trước, chỉ mở `MainWindow` khi `isSucceeded()` |
| `src/billing/ui/LoginDialog.java` | 2 ô nhập + nút `Dang nhap` / `Thoat`. Khung gợi ý **in sẵn cả mật khẩu** ra màn hình (dòng 59-64) |
| `src/billing/ui/LoginDialog.java:94-121` | `doLogin()` chạy **trực tiếp trên EDT** — mở kết nối JDBC, gọi `sp_authenticate_account` |
| `src/billing/dao/AccountDao.java` | `authenticate(user, pass)` → `Account` hoặc `null`. Ghi `SqlLog` với mật khẩu đã che `******` |
| `src/billing/ui/MainWindow.java:82-100` | `doLogout()`: `dispose()` → `UserSession.clear()` → mở lại `LoginDialog` → `new MainWindow()` |
| `src/billing/ui/InvoiceViewPanel.java:401-424` | `applyRolePermissions()` — **chỗ duy nhất** trong toàn bộ codebase khoá/mở UI theo vai trò |
| `sql/02_seed.sql:254-256` | Đúng 2 tài khoản: `cashier/cashier`, `manager/manager` |

### 1.2. Vấn đề cụ thể

1. **Gõ lại mật khẩu đang hiện trên màn hình.** `LoginDialog` in ra `cashier / cashier` và
   `manager / manager` rồi bắt người dùng đánh máy lại đúng những chữ đó. Đây là nghịch lý: màn hình
   đăng nhập không bảo vệ gì cả (mật khẩu nằm ngay trên nó, và cả trong `sql/02_seed.sql` đã commit),
   nhưng vẫn thu đủ phí đánh máy mỗi lần chạy app.

2. **Chi phí lặp lại rất cao trong lúc học.** Mỗi lần sửa một dòng Java là một vòng
   `run.ps1` → biên dịch → đăng nhập. Một buổi làm việc dễ 30-50 lần.

3. **Demo phân quyền tốn 4 bước.** Muốn cho lớp thấy nút Sửa/Xoá bị khoá với `cashier` rồi mở với
   `manager`: Đăng xuất → gõ `cashier` ×2 → xem → Đăng xuất → gõ `manager` ×2. Và mỗi lần đăng xuất
   là `MainWindow` bị `dispose()` — **giỏ hàng đang nhập ở tab 1 mất sạch, không một lời cảnh báo**.

4. **Không chạy được app ở sẵn một vai trò.** Nút `Chạy source billing` ở tab 2 của Setup App, hay
   Run configuration trong IntelliJ, đều không có cách nào nói "vào thẳng với vai trò manager".

5. **Mật khẩu lộ ra khi chiếu máy chiếu.** Khung gợi ý in mật khẩu ra màn hình là chi tiết không
   nên dạy cho người mới, dù ở đây vô hại.

---

## 2. Mục Tiêu & Phạm Vi

### 2.1. Mục tiêu

1. **Một cú bấm (hoặc một tổ hợp phím) là vào được app**, cho từng vai trò.
2. **Đổi vai trò giữa phiên không mất dữ liệu đang nhập** — đây là thứ `doLogout()` hiện không làm
   được, và là đường demo phân quyền ngắn nhất.
3. **Bỏ qua hẳn màn hình đăng nhập** khi đang phát triển, bật/tắt bằng cấu hình hoặc tham số dòng
   lệnh. **Mặc định TẮT**, và khi BẬT thì phải nhìn thấy được trên giao diện.
4. **Nhớ tên đăng nhập gần nhất** để điền sẵn. **Không bao giờ nhớ mật khẩu.**
5. Màn hình đăng nhập **không còn in mật khẩu** ra màn hình.

### 2.2. Nguyên tắc bất di bất dịch

> **Đăng nhập nhanh là ĐIỀN NHANH, không phải BỎ QUA XÁC THỰC.**

Mọi đường vào app — nút nhanh, auto-login, đổi vai trò — đều phải gọi thật
`AccountDao.authenticate()` → `sp_authenticate_account` trên MySQL. Ba lý do:

- Giữ nguyên bài học JDBC + Stored Procedure, vốn là mục đích của cả dự án.
- Nhật ký SQL (khối 1) vẫn ghi được dòng xác thực — nếu bỏ qua DB thì tab 5 của Setup App mất
  dòng đầu tiên của mỗi phiên.
- Nếu một bạn trong nhóm đổi mật khẩu trong bảng `Account`, nút nhanh **phải thất bại đúng như gõ
  tay**. Một nút nhanh "luôn thành công" là một cái bẫy: người học sẽ tin app vẫn chạy trong khi DB
  đã lệch.

### 2.3. Ngoài phạm vi khối này (và vì sao)

| Việc | Vì sao không làm ở khối này |
|---|---|
| Băm mật khẩu (hash/salt) | `sp_authenticate_account` đang so sánh `Password = p_password` trực tiếp; đổi sang hash phải sửa thủ tục + seed + viết hàm băm phía Java → đó là một khối riêng, và nó **ngược hướng** với khối này (khối này cần biết mật khẩu để điền sẵn). |
| Quản lý tài khoản trong app (thêm / xoá / đổi mật khẩu) | Cần thêm 3 stored procedure và một màn hình mới. Khối riêng. |
| `sp_list_accounts` để hàng nút nhanh tự đọc tài khoản từ DB | Nghe hay nhưng **không giải quyết đúng vấn đề**: thủ tục đó không được trả mật khẩu, nên nó chỉ điền được username — người dùng vẫn phải gõ mật khẩu. Để dành cho khối quản lý tài khoản. |
| Gắn `Account` với `Cashier_ID` để tab Lập hoá đơn tự điền thu ngân | Cần sửa schema (thêm cột/khoá ngoại) → vi phạm tính chất "khối này không chạm DB". Khối riêng. |
| Nhớ mật khẩu ("Remember me") | **Cố tình không làm.** Ghi mật khẩu ra một file trên đĩa là đúng thứ không nên dạy cho người mới, và hàng nút nhanh đã giải quyết 100% nhu cầu đó cho các tài khoản mẫu. |

---

## 3. Hợp Đồng Cấu Hình (quan trọng nhất)

Khối 1 có "hợp đồng định dạng file" giữa hai app. Khối này có **hợp đồng cấu hình**: 4 khoá mới
trong `config.properties` và thứ tự ưu tiên của chúng. Sửa một chỗ phải sửa tài liệu này.

### 3.1. Bốn khoá mới, thêm vào cuối `config.properties`

```properties
# ---------------------------------------------------------------
# ĐĂNG NHẬP NHANH -- chỉ dành cho bản demo / lúc học
# ---------------------------------------------------------------

# Bật hàng nút "Đăng nhập nhanh" trên màn hình đăng nhập.
# Đặt false: màn hình đăng nhập trở về đúng như trước khối 2 (chỉ gõ tay),
#            auto-login và tham số --login cũng bị vô hiệu luôn.
quick.login.enabled=true

# Danh sách tài khoản của hàng nút nhanh, các mục cách nhau bằng dấu PHẨY.
# Cú pháp một mục:   <username>:<password>[:<nhãn hiển thị>]
#
# CẢNH BÁO: đây là mật khẩu để NGUYÊN VĂN trong file cấu hình đã được commit.
#   Chỉ điền 2 tài khoản mẫu mà sql/02_seed.sql tạo ra.
#   TUYỆT ĐỐI không điền tài khoản thật. Dự án thật thì đặt quick.login.enabled=false
#   và gitignore cả config.properties (xem hướng dẫn ở .gitignore dòng 26-30).
quick.login.accounts=cashier:cashier:Thu ngan,manager:manager:Quan ly

# Tự động đăng nhập bằng username này, BỎ QUA HẲN màn hình đăng nhập.
# Để TRỐNG (mặc định) = luôn hiện màn hình đăng nhập.
# Username điền ở đây phải có trong quick.login.accounts (vì cần mật khẩu).
quick.login.auto=

# Nhớ tên đăng nhập lần trước để điền sẵn vào ô username.
# KHÔNG BAO GIỜ nhớ mật khẩu -- xem mục 2.3.
quick.login.remember=true
```

**Giới hạn cú pháp phải ghi rõ trong tài liệu và trong comment:** dấu `,` tách mục, dấu `:` tách
trường (cắt tối đa 3 phần, phần thứ 3 là nhãn). Nên mật khẩu chứa `,` hoặc `:` **không biểu diễn
được** bằng khoá này — người đó phải gõ tay. Chấp nhận được cho app demo; phát minh thêm cơ chế
escape chỉ để phục vụ một trường hợp không tồn tại là không đáng.

**Mục sai cú pháp thì bỏ qua mục đó**, in một dòng cảnh báo ra console, các mục còn lại vẫn dùng
được. Không được để một dấu phẩy thừa làm màn hình đăng nhập chết.

### 3.2. Thứ tự ưu tiên khi quyết định có auto-login hay không

```
1. Tham số dòng lệnh   --login=<username>            (hoặc --login=<username>:<password>)
2. Khoá cấu hình       quick.login.auto=<username>
3. Không có gì         -> hiện màn hình đăng nhập như bình thường
```

- Mọi mức trên đều bị **vô hiệu** nếu `quick.login.enabled=false`. Một công tắc tắt được tất cả.
- `--login=manager` (không kèm mật khẩu) → tra mật khẩu trong `quick.login.accounts`. Không tìm thấy
  → báo lý do rồi hiện màn hình đăng nhập.
- `--login=someone:secret` → dùng thẳng cặp đó, không cần có trong danh sách. Đây là đường dành cho
  Run configuration của IDE khi nhóm đã tự thêm tài khoản vào DB.
- **Auto-login thất bại không bao giờ được làm app thoát.** Luôn rơi về màn hình đăng nhập, kèm lý
  do hiện ở dòng trạng thái của dialog (`Tài khoản 'manager' sai mật khẩu — hãy đăng nhập bằng tay`).

Biến môi trường `BILLING_LOGIN` **không** được `billing.Main` đọc. Nó chỉ là đường ống giữa Setup
App và `run.ps1` / `run.sh` (xem mục 6, phần B): script đọc biến đó rồi truyền xuống java thành
`--login=`. Giữ `Main` chỉ biết 2 nguồn cấu hình để còn giải thích được cho người học.

### 3.3. Dấu hiệu nhìn thấy được khi auto-login đang bật

Bỏ qua màn hình đăng nhập mà không có dấu hiệu gì là cách nhanh nhất để một bạn trong nhóm kết luận
"app này không có đăng nhập". Bắt buộc có **cả hai**:

1. Một dòng ra console lúc khởi động:
   `[AUTO-LOGIN] Da dang nhap san bang 'manager' (nguon: config quick.login.auto). Dat quick.login.auto= trong config.properties de tat.`
2. Trên thanh trạng thái của `MainWindow`: chữ `(DANG NHAP TU DONG)` màu cam
   (`new Color(190, 110, 0)`) ngay sau thông tin người dùng.

---

## 4. Thiết Kế Giao Diện

### 4.1. `LoginDialog` sau khi sửa

```
┌─ Dang nhap he thong -- Retail Invoicing ────────────────────────┐
│                                                                 │
│   Ten dang nhap:  [ manager                        ]            │
│   Mat khau:       [ ••••••••                       ]            │
│                                                                 │
│  ─ Dang nhap nhanh (tai khoan mau cua ban demo) ──────────────  │
│   [ 1. Thu ngan (cashier) ]  [ 2. Quan ly (manager) ]           │
│   Bam mot nut de dien san va dang nhap ngay.                    │
│   Phim tat: Alt+1 / Alt+2.  Enter = Dang nhap.                  │
│                                                                 │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │ Dang kiem tra tai khoan...        [ Dang nhap ]  [ Thoat ]  │ │
│ └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

Thay đổi so với hiện tại:

1. **Khung gợi ý in mật khẩu (dòng 59-64) bị bỏ**, thay bằng hàng nút nhanh + 2 dòng hướng dẫn
   ngắn. Nhãn nút chỉ có vai trò và username, **không có mật khẩu**.
2. **Hàng nút nhanh biến mất hoàn toàn** khi `quick.login.enabled=false` hoặc danh sách rỗng — kể cả
   đường gạch phân cách và 2 dòng hướng dẫn. Một hàng nút bị `setEnabled(false)` không giải thích
   được gì cho người xem; không có thì rõ hơn.
3. **Phím tắt bằng mnemonic**, không dùng F1/F2 (F1 theo quy ước là Trợ giúp): nhãn nút bắt đầu bằng
   `1. ` / `2. ` rồi `setMnemonic('1')` / `setMnemonic('2')` → Alt+1, Alt+2. Mục thứ 10 trở đi trong
   danh sách thì không gán mnemonic (hết số); không phải lỗi, chỉ là không có phím tắt.
4. **Dòng trạng thái** thay cho `JOptionPane` ở các lỗi thường gặp (thiếu thông tin, sai mật khẩu).
   Một hộp thoại modal phải bấm OK mới làm tiếp được là thêm một cú bấm nữa vào chính cái vòng lặp
   mà khối này đang muốn rút ngắn. Giữ `Ui.error()` cho ngoại lệ thật (mất kết nối DB).
   Dòng trạng thái đặt ở `BorderLayout.CENTER` của panel nút phía nam — ở `WEST` thì nó chỉ được
   cấp đúng bề rộng lúc `pack()` (khi đó đang là chuỗi rỗng) và mọi thông báo sau đó bị cắt mất.
   Dialog đang `setResizable(false)` nên không có lần `pack()` thứ hai để sửa.

### 4.2. Xác thực chạy ngoài EDT

`doLogin()` hiện gọi `Db.getConnection()` ngay trên EDT. Nếu MySQL vừa bị tắt, `DriverManager` chờ
tới khi hết thời gian chờ TCP — dialog đứng hình, không vẽ lại, người dùng bấm thêm 3-4 lần nữa và
mỗi lần là một kết nối JDBC mới đang chờ. Nút nhanh làm chuyện này **dễ xảy ra hơn** vì bấm nút
nhanh hơn gõ mật khẩu.

Sửa: bọc trong `SwingWorker<Account, Void>`:

```java
private void submit(String username, String password) {
    setBusy(true, "Dang kiem tra tai khoan...");
    new SwingWorker<Account, Void>() {
        @Override protected Account doInBackground() {
            return accountDao.authenticate(username, password);   // luồng nền
        }
        @Override protected void done() {
            setBusy(false, "");
            try {
                Account acc = get();
                if (acc == null) {
                    setStatus("Sai ten dang nhap hoac mat khau.", COLOR_ERROR);
                    pfPassword.setText("");
                    pfPassword.requestFocusInWindow();
                } else {
                    LoginPrefs.saveLastUsername(acc.getUsername());
                    UserSession.setCurrentUser(acc);
                    succeeded = true;
                    dispose();
                }
            } catch (Exception ex) {
                Ui.error(LoginDialog.this, ex);    // ngoại lệ thật: mất kết nối, thiếu driver
            }
        }
    }.execute();
}
```

`setBusy(true, ...)` khoá **tất cả** nút (Đăng nhập, Thoát, mọi nút nhanh) → không thể xếp hàng hai
lần xác thực. `setBusy(false, ...)` mở lại.

### 4.3. Thanh trạng thái `MainWindow` sau khi sửa

```
  Nguoi dung: Quan Ly He Thong  [Vai tro: MANAGER]  (DANG NHAP TU DONG)
                                              [ Doi vai tro ▾ ]  [ Dang xuat ]
```

- **`Doi vai tro ▾`** mở một `JPopupMenu` liệt kê các tài khoản nhanh **khác** tài khoản hiện tại.
  Dùng popup chứ không dùng một nút "lật qua lật lại" vì `quick.login.accounts` có thể có 3 mục trở
  lên, và một nút "lật" sẽ vô nghĩa khi đó.
- Nút này **ẩn hẳn** khi `quick.login.enabled=false` hoặc không còn tài khoản nào khác để đổi.
- **`Dang xuat`** giữ nguyên hành vi hiện tại (dispose + clear + mở lại login), chỉ thêm cảnh báo
  giỏ hàng ở mục 5.4.

### 4.4. Đổi vai trò **tại chỗ**, không dựng lại cửa sổ

Đây là điểm thiết kế quan trọng nhất của khối này.

Cách làm hiển nhiên là copy `doLogout()`: dispose → authenticate → `new MainWindow()`. **Không làm
thế.** Vì `MainWindow` dựng lại nghĩa là `NewInvoicePanel` mới → giỏ hàng, danh sách thanh toán,
khách hàng đang nhập mất sạch; và tab đang xem nhảy về tab 1. Đổi vai trò là thao tác sẽ được bấm
rất nhiều lần khi demo, nên mất dữ liệu kiểu đó là không chấp nhận được.

Đã kiểm tra bằng `grep -rn "UserSession\|isManager\|isCashier\|applyRolePermissions" src/`: toàn bộ
phần giao diện phụ thuộc vai trò chỉ gồm **đúng hai chỗ**:

| Chỗ | Việc phải làm khi đổi vai trò |
|---|---|
| `InvoiceViewPanel.applyRolePermissions()` (dòng 401) | Gọi lại — đọc `UserSession` nên tự đúng |
| Nhãn người dùng ở thanh trạng thái `MainWindow` (dòng 57-62) | Vẽ lại chữ |

Chỗ thứ ba — `InvoiceViewPanel` dòng 1024-1029 lấy `supervisorId` cho nghiệp vụ trả hàng — đọc
`UserSession.getCurrentUser()` **tại thời điểm bấm nút**, không chụp sẵn lúc dựng panel, nên nó tự
dùng tài khoản mới. Không phải sửa.

Vậy `switchRole()` chỉ cần:

```java
private void switchRole(QuickAccount target) {
    setStatusBarBusy(true);
    new SwingWorker<Account, Void>() {
        @Override protected Account doInBackground() {
            return accountDao.authenticate(target.getUsername(), target.getPassword());
        }
        @Override protected void done() {
            setStatusBarBusy(false);
            Account acc;
            try { acc = get(); } catch (Exception ex) { Ui.error(MainWindow.this, ex); return; }
            if (acc == null) {
                JOptionPane.showMessageDialog(MainWindow.this,
                    "Tai khoan '" + target.getUsername() + "' trong config.properties khong dang nhap duoc.\n"
                  + "Co the mat khau trong bang Account da bi doi.",
                  "Doi vai tro that bai", JOptionPane.WARNING_MESSAGE);
                return;
            }
            UserSession.setCurrentUser(acc);     // 1. đổi phiên
            refreshUserInfo();                   // 2. vẽ lại nhãn thanh trạng thái
            viewPanel.applyRolePermissions();    // 3. khoá/mở lại các nút
            rebuildSwitchRoleMenu();             // 4. tài khoản hiện tại không còn trong menu
        }
    }.execute();
}
```

Giỏ hàng ở tab 1 còn nguyên, tab đang xem không nhảy. **Không cần hộp thoại xác nhận** cho thao tác
này: nó không mất gì và đảo lại được bằng một cú bấm nữa.

---

## 5. Kiến Trúc & Chi Tiết Từng File

### 5.1. Bảng tổng hợp

| File | Trạng thái | Việc |
|---|---|---|
| `config.properties` | Bổ sung | 4 khoá `quick.login.*` theo mục 3.1 |
| `src/billing/util/AppConfig.java` | **Tạo mới** | Đọc `config.properties` một lần, cung cấp `get` / `getBoolean` |
| `src/billing/model/QuickAccount.java` | **Tạo mới** | Bản ghi `(username, password, label)` + `parse(String)` |
| `src/billing/util/LoginPrefs.java` | **Tạo mới** | Đọc/ghi `logs/last-login.properties` (chỉ username) |
| `src/billing/ui/AppFlow.java` | **Tạo mới** | Gom luồng "mở login → mở MainWindow" về một chỗ |
| `src/billing/ui/LoginDialog.java` | Sửa | Hàng nút nhanh, dòng trạng thái, `SwingWorker`, điền sẵn username |
| `src/billing/ui/MainWindow.java` | Sửa | Nút `Doi vai tro`, cờ auto-login, `refreshUserInfo()`, cảnh báo giỏ hàng |
| `src/billing/ui/NewInvoicePanel.java` | Bổ sung | `public boolean hasPendingWork()` (chỉ thêm 1 hàm) |
| `src/billing/Main.java` | Sửa | Phân tích `--login=`, auto-login, gọi `AppFlow` |
| `scripts/ps1/run.ps1` | Sửa | Tham số `-Login <user>` + đọc `$env:BILLING_LOGIN`, truyền xuống java |
| `scripts/bash/run.sh` | Sửa | Tương tự, `$1` + `$BILLING_LOGIN` |
| `.gitignore` | Sửa comment | Nói rõ `logs/` còn chứa file trạng thái cục bộ, không chỉ nhật ký |

Không file nào bị xoá. Không file SQL nào bị sửa.

### 5.2. Mới: `src/billing/util/AppConfig.java`

Hiện `Db.java` đã đọc `config.properties` trong static block nhưng chỉ giữ lại 3 khoá `db.*` rồi
bỏ `Properties`. Thêm các khoá đăng nhập vào `Db` sẽ bắt lớp "cửa ngõ duy nhất tới MySQL" gánh thêm
việc không liên quan — và `Db` đang là một trong những file được dùng làm tài liệu dạy JDBC
(`docs/02-JDBC.md`), không nên làm nó rối.

```java
package billing.util;

/**
 * Đọc config.properties một lần duy nhất rồi cho tra cứu theo khoá.
 * Dùng cho các cấu hình KHÔNG thuộc kết nối database (billing.db.Db tự đọc phần db.*).
 */
public final class AppConfig {

    private static final Properties P = new Properties();

    static {
        try (InputStream in = new FileInputStream("config.properties")) {
            P.load(in);
        } catch (Exception e) {
            System.out.println("[AppConfig] Khong doc duoc config.properties, dung gia tri mac dinh.");
        }
    }

    public static String get(String key, String fallback) {
        String v = P.getProperty(key);
        return (v == null || v.isBlank()) ? fallback : v.trim();
    }

    /** true chỉ khi giá trị là "true"/"1"/"yes" (không phân biệt hoa thường). */
    public static boolean getBoolean(String key, boolean fallback) { ... }

    private AppConfig() { }
}
```

Đường dẫn tương đối `config.properties` — **giữ đúng giả định đã có** của `Db.java` (thư mục làm
việc là gốc dự án, `run.ps1`/`run.sh` đều `Set-Location` về đó). Không phát minh thêm cách dò thư
mục gốc cho riêng khối này.

### 5.3. Mới: `src/billing/model/QuickAccount.java`

```java
package billing.model;

/** Một tài khoản trên hàng nút "Đăng nhập nhanh", đọc từ quick.login.accounts. */
public class QuickAccount {

    private final String username;
    private final String password;
    private final String label;      // nhãn hiển thị, ví dụ "Thu ngan"

    /**
     * Phân tích chuỗi cấu hình thành danh sách tài khoản nhanh.
     * Cú pháp: "user1:pass1:Nhan 1,user2:pass2:Nhan 2"
     * Mục sai cú pháp bị bỏ qua kèm một dòng cảnh báo ra console --
     * một dấu phẩy thừa không được làm màn hình đăng nhập chết.
     */
    public static List<QuickAccount> parse(String raw) { ... }

    /** Nhãn đầy đủ cho nút: "Thu ngan (cashier)". Không bao giờ chứa mật khẩu. */
    public String getButtonLabel() { return label + " (" + username + ")"; }

    public String getUsername() { return username; }
    public String getPassword() { return password; }
}
```

Quy tắc phân tích, viết rõ trong Javadoc để người sau không đoán:

- Tách mục bằng `,`; mục rỗng (do `,,` hay dấu phẩy cuối) **bỏ qua im lặng**, không cảnh báo — đó là
  lỗi gõ vô hại.
- Mỗi mục `split(":", 3)`: thiếu phần thứ 2 (mật khẩu) → **cảnh báo + bỏ qua mục**. Thiếu phần thứ 3
  → nhãn mặc định là chính username.
- `trim()` từng trường, trừ mật khẩu: mật khẩu `" cashier "` và `"cashier"` là hai mật khẩu khác
  nhau, tự ý `trim()` sẽ sinh ra một lỗi "sai mật khẩu" không thể hiểu nổi. **Không trim mật khẩu.**
  (Ghi chú: `LoginDialog.doLogin()` hiện đang `trim()` mật khẩu người dùng gõ — giữ nguyên, không
  phải việc của khối này, nhưng không lặp lại ở đây.)

Nơi lấy danh sách: `QuickAccount.parse(AppConfig.get("quick.login.accounts", ""))`, và chỉ gọi khi
`AppConfig.getBoolean("quick.login.enabled", false)` là true. **Mặc định của `enabled` là `false`**
trong code, không phải `true`: nếu ai xoá mất khoá đó khỏi `config.properties` thì hành vi an toàn
hơn là trở về màn hình đăng nhập thường, chứ không phải tự bật tính năng tiện lợi.

### 5.4. Mới: `src/billing/util/LoginPrefs.java`

```java
/** Nhớ tên đăng nhập gần nhất. KHÔNG BAO GIỜ ghi mật khẩu -- xem plan/11 mục 2.3. */
public final class LoginPrefs {

    private static final Path FILE = Paths.get("logs", "last-login.properties");

    /** Trả về username lần trước, hoặc "" nếu chưa có / không bật / đọc lỗi. */
    public static String loadLastUsername() { ... }

    /** Ghi lại username. Lỗi ghi file chỉ in cảnh báo, không bao giờ ném ra. */
    public static void saveLastUsername(String username) { ... }
}
```

- Chỉ đọc/ghi khi `quick.login.remember=true`.
- Đặt trong `logs/` để **dùng lại một thư mục đã bị `.gitignore` chặn** (dòng 10), không phát minh
  chỗ lưu trạng thái cục bộ thứ hai cho một app demo. Đổi lại phải sửa comment dòng 9 của
  `.gitignore` cho khỏi sai: thư mục này không chỉ chứa nhật ký.
- Áp dụng **đúng nguyên tắc an toàn của khối 1** (`plan/10` mục 2.4): bọc mọi thao tác file trong
  `try/catch (Exception)`, lỗi đầu tiên in một dòng ra `System.err` rồi tắt hẳn việc ghi cho phần
  còn lại của phiên. Việc nhớ một cái tên không bao giờ được làm app đổ.
- Tab 5 của Setup App tail `logs/sql.log` theo **đường dẫn cố định**, nên thêm file này vào `logs/`
  không ảnh hưởng gì tới nó (`LogTailer` không quét thư mục).

### 5.5. Mới: `src/billing/ui/AppFlow.java`

Đoạn "mở LoginDialog, nếu thành công thì mở MainWindow" hiện bị copy ở **2 chỗ**
(`Main.java:53-61` và `MainWindow.java:92-98`), và khối này thêm chỗ thứ ba (auto-login thất bại).
Ba bản copy của cùng 5 dòng là chỗ để lệch nhau.

```java
package billing.ui;

/** Điều phối luồng vào app: màn hình đăng nhập -> cửa sổ chính. Mọi đường đều đi qua đây. */
public final class AppFlow {

    /**
     * Mở màn hình đăng nhập (trên EDT); đăng nhập thành công thì mở MainWindow.
     * @param notice thông báo hiện ở dòng trạng thái của dialog, null nếu không có
     */
    public static void showLogin(String notice) {
        SwingUtilities.invokeLater(() -> {
            LoginDialog login = new LoginDialog(null, notice);
            login.setVisible(true);
            if (login.isSucceeded()) {
                openMainWindow(false);
            }
        });
    }

    /** Mở cửa sổ chính với phiên đã có trong UserSession. */
    public static void openMainWindow(boolean autoLogin) {
        SwingUtilities.invokeLater(() -> new MainWindow(autoLogin).setVisible(true));
    }
}
```

### 5.6. Sửa `src/billing/Main.java`

Chèn giữa `SqlLog.appStart(...)` (dòng 50) và chỗ mở dialog (dòng 53):

```java
// --- Đăng nhập nhanh: thử auto-login trước khi mở màn hình đăng nhập -------
// Thứ tự ưu tiên: --login=... > quick.login.auto > hiện màn hình đăng nhập.
// Xem plan/11-KE-HOACH-KHOI-2-DANG-NHAP-NHANH.md muc 3.2.
AutoLogin.Result r = AutoLogin.tryFrom(args);     // hoặc một static method trong AppFlow
switch (r.kind()) {
    case SUCCESS -> {
        System.out.println("[AUTO-LOGIN] Da dang nhap san bang '" + r.username()
            + "' (nguon: " + r.source() + "). Dat quick.login.auto= trong config.properties de tat.");
        UserSession.setCurrentUser(r.account());
        AppFlow.openMainWindow(true);
    }
    case FAILED  -> AppFlow.showLogin(r.message());   // sai mật khẩu / không tìm thấy tài khoản
    case NONE    -> AppFlow.showLogin(null);          // không cấu hình gì -> đường mặc định
}
```

Chỗ đặt logic này: một lớp nhỏ `billing.ui.AppFlow` (static method `tryAutoLogin(String[] args)`)
hay một file riêng đều được — miễn **không** nhét vào `Main.main()` làm nó phình lên, vì `Main` là
file đầu tiên người học đọc.

Phân tích tham số: chỉ nhận đúng dạng `--login=<giá trị>`. Tham số lạ → in một dòng cảnh báo
`[LOI THAM SO] Khong hieu tham so '<x>'. Chi ho tro --login=<username>[:<password>].` rồi **vẫn chạy
tiếp** như không có tham số. Thoát app vì một tham số gõ sai là phản ứng quá đáng với một app demo.

Auto-login xác thực **trên luồng main**, trước khi có EDT nào — đúng như `Db.testConnection()` đang
làm ở dòng 30, nên không thêm kiểu rủi ro mới.

### 5.7. Sửa `src/billing/ui/MainWindow.java`

1. **Giữ `NewInvoicePanel` làm field** thay vì biến cục bộ (dòng 31), để `doLogout()` hỏi được giỏ
   hàng. Đây là lý do duy nhất phải sửa `NewInvoicePanel`.
2. **Giữ `lbInfo` làm field** + thêm `private void refreshUserInfo()` vẽ lại chữ. Hiện nhãn đó được
   dựng trong `statusBar()` và không ai giữ tham chiếu.
3. **Constructor nhận cờ `autoLogin`**: giữ luôn `public MainWindow()` cũ gọi `this(false)` để không
   phá chỗ gọi nào khác (nguyên tắc "additive first" của `plan/06` mục 1.2).
4. **Nút `Doi vai tro ▾`** + `rebuildSwitchRoleMenu()` + `switchRole()` theo mục 4.4.
5. **Cảnh báo giỏ hàng trước khi đăng xuất.** `doLogout()` hiện hỏi "Ban co chac chan muon dang
   xuat?" rồi `dispose()` — xoá sạch giỏ hàng đang nhập mà không hề nói. Sửa nội dung hộp thoại khi
   `newInvoice.hasPendingWork()`:

   ```
   Tab "Lap hoa don" dang co 3 dong hang chua luu.
   Dang xuat se mat toan bo. Van dang xuat?

   Goi y: muon xem giao dien cua vai tro khac ma khong mat gio hang
   thi dung nut "Doi vai tro" ben canh.
   ```

   Dòng gợi ý này quan trọng hơn vẻ ngoài của nó: nó dạy người dùng rằng đã có đường không mất dữ
   liệu, đúng lúc họ đang định đi đường mất dữ liệu.

### 5.8. Bổ sung `src/billing/ui/NewInvoicePanel.java`

`cart` và `payments` là `private final List<...>` (dòng 66-67) không có hàm truy cập nào. Thêm đúng
một hàm, không sửa dòng nào có sẵn:

```java
/**
 * Có dữ liệu chưa lưu trong tab này hay không.
 * Dùng để cảnh báo trước khi đăng xuất (MainWindow.doLogout) --
 * dispose() cửa sổ sẽ xoá sạch giỏ hàng mà không hỏi gì.
 */
public boolean hasPendingWork() {
    return !cart.isEmpty() || !payments.isEmpty();
}
```

### 5.9. Sửa `scripts/ps1/run.ps1` và `scripts/bash/run.sh`

`run.ps1` hiện khởi chạy ở dòng 102:
```powershell
& java "-Dfile.encoding=UTF-8" -cp "out;lib\*" $mainClass
```

Thêm `param([string]$Login)` ở đầu file (**phải là câu lệnh đầu tiên**, trước
`$ErrorActionPreference` — nếu không PowerShell báo lỗi cú pháp), rồi:

```powershell
# Cho phép chọn sẵn vai trò đăng nhập: .\scripts\ps1\run.ps1 -Login manager
# Setup App truyền qua biến môi trường BILLING_LOGIN (xem plan/11 muc 3.2).
if ([string]::IsNullOrWhiteSpace($Login)) { $Login = $env:BILLING_LOGIN }

$appArgs = @()
if (-not [string]::IsNullOrWhiteSpace($Login)) {
    $appArgs += "--login=$Login"
    Write-Host "Dang nhap san bang: $Login" -ForegroundColor DarkGray
}

& java "-Dfile.encoding=UTF-8" -cp "out;lib\*" $mainClass @appArgs
```

`run.sh`: `LOGIN="${1:-$BILLING_LOGIN}"`, rồi thêm `--login="$LOGIN"` vào dòng `java` nếu khác rỗng.
Đọc `scripts/bash/run.sh` trước khi sửa để khớp văn phong của nó.

---

## 6. Danh Sách Công Việc (theo thứ tự thực hiện)

### Phần A — bắt buộc, nằm hết trong `src/billing/` (được commit lên git)

1. `config.properties`: thêm 4 khoá `quick.login.*` kèm comment cảnh báo (mục 3.1).
2. `.gitignore`: sửa comment dòng 9 cho đúng (`logs/` còn chứa trạng thái cục bộ).
3. `src/billing/util/AppConfig.java` (mục 5.2).
4. `src/billing/model/QuickAccount.java` (mục 5.3).
5. `src/billing/util/LoginPrefs.java` (mục 5.4).
6. `src/billing/ui/AppFlow.java` (mục 5.5) — chuyển luôn đoạn mở login của `Main` vào đây.
7. `src/billing/ui/LoginDialog.java`: hàng nút nhanh, dòng trạng thái, `SwingWorker`, điền sẵn
   username, thêm hàm dựng `LoginDialog(Frame, String notice)` (giữ hàm dựng cũ 1 tham số).
8. `src/billing/ui/NewInvoicePanel.java`: thêm `hasPendingWork()` (mục 5.8).
9. `src/billing/ui/MainWindow.java`: 5 việc ở mục 5.7.
10. `src/billing/Main.java`: auto-login + phân tích `--login=` (mục 5.6).
11. `scripts/ps1/run.ps1` và `scripts/bash/run.sh`: tham số `-Login` (mục 5.9).
12. Biên dịch: `javac -encoding UTF-8 -d out -cp "lib\*"` trên danh sách `src/**/*.java` trừ
    `src/envsetup` (giống `run.ps1` mục 4). Phải sạch, không thêm cảnh báo mới nào.
13. Chạy kịch bản kiểm thử mục 7, ghi kết quả vào mục 9.

### Phần B — tuỳ chọn, nằm trong `src/envsetup/` (bị `.gitignore`, chỉ phát hành qua `SetupApp.jar`)

Làm sau khi phần A đã xong và kiểm thử sạch. Giá trị: bấm `Chạy source billing` ở tab 2 của Setup
App là vào thẳng app với vai trò đã chọn, không gõ gì.

14. `src/envsetup/runner/PlatformUtil.java`: thêm nạp chồng
    `createProcessBuilder(String action, Map<String,String> extraEnv)` — đặt thêm biến môi trường
    cho tiến trình con. **Dùng biến môi trường, không ghép tham số vào dòng lệnh**: trên Windows
    lệnh được dựng thành một chuỗi `-Command "& 'đường\dẫn'; exit $LASTEXITCODE"` (dòng 87-96), ghép
    thêm tham số vào đó là tự tạo một bài toán escape nháy đơn/nháy kép không cần thiết. Biến môi
    trường đi qua `ProcessBuilder.environment()` nguyên văn, giống hệt nhau trên cả hai hệ điều hành.
15. `src/envsetup/runner/ScriptRunner.java`: nạp chồng `run(action, console, onFinished, extraEnv)`;
    hàm 3 tham số hiện có gọi vào hàm mới với `Map.of()` (không sửa hành vi của 4 tab đang dùng).
16. `src/envsetup/ui/BaseTab.java`: nạp chồng `runScript(action, label, onExit, extraEnv)`.
17. `src/envsetup/ui/JavaTab.java`: thêm `JComboBox` "Đăng nhập sẵn: (hiện màn hình đăng nhập) /
    cashier / manager" cạnh nút Chạy, dùng `UiHelper.createComboBox()` (hàm này đã được thêm ở lần
    sửa lỗi tràn box của tab Git — `plan/09` mục 7.1 lỗi #2; đừng dùng `new JComboBox<>()` trần,
    nó cao 19px và vẽ lệch trên look-and-feel Windows).
18. Đóng gói lại: `scripts/ps1/build-setup.ps1` (hoặc `build-setup.sh`), kiểm tra mã thoát 0.

---

## 7. Kịch Bản Kiểm Thử

### 7.1. Đường đăng nhập (phần A)

| # | Bước | Kết quả mong đợi |
|---|---|---|
| 1 | Chạy app với `config.properties` mặc định của khối này | Màn hình đăng nhập hiện ra, có hàng 2 nút nhanh, **không chỗ nào in mật khẩu** |
| 2 | Bấm `1. Thu ngan (cashier)` | 2 ô được điền, dòng trạng thái hiện "Đang kiểm tra...", vào app với vai trò CASHIER. Tổng cộng **1 cú bấm** |
| 3 | Nhấn `Alt+2` ở màn hình đăng nhập | Vào app với vai trò MANAGER |
| 4 | Gõ tay `manager`/`manager` rồi Enter | Vẫn vào được — đường cũ không bị phá |
| 5 | Gõ sai mật khẩu | Dòng trạng thái đỏ "Sai ten dang nhap hoac mat khau.", ô mật khẩu được xoá và nhận con trỏ, **không có hộp thoại phải bấm OK** |
| 6 | Mở lại app sau khi đã đăng nhập bằng `manager` | Ô username điền sẵn `manager`, con trỏ ở ô mật khẩu |
| 7 | Đặt `quick.login.remember=false`, đăng nhập, mở lại app | Ô username trống; file `logs/last-login.properties` **không** được tạo/cập nhật |
| 8 | Đặt `quick.login.enabled=false`, mở app | Màn hình đăng nhập **đúng như trước khối 2**: không hàng nút nhanh, không đường phân cách, không dòng hướng dẫn phím tắt |

### 7.2. Auto-login

| # | Bước | Kết quả mong đợi |
|---|---|---|
| 9 | `quick.login.auto=manager`, chạy app | Vào thẳng `MainWindow`, **không thấy** màn hình đăng nhập. Console in dòng `[AUTO-LOGIN] ...`. Thanh trạng thái có chữ `(DANG NHAP TU DONG)` màu cam |
| 10 | `quick.login.auto=khongcothat` | Màn hình đăng nhập hiện ra, dòng trạng thái nói rõ không tìm thấy tài khoản đó trong `quick.login.accounts`. App **không thoát** |
| 11 | `quick.login.auto=manager` nhưng đổi mật khẩu `manager` trong bảng `Account` | Màn hình đăng nhập hiện ra kèm lý do "sai mật khẩu". **Đây là phép kiểm chứng minh nguyên tắc mục 2.2** — nút nhanh không bỏ qua xác thực |
| 12 | `quick.login.auto=manager` **và** `quick.login.enabled=false` | Hiện màn hình đăng nhập thường — một công tắc tắt được tất cả |
| 13 | `.\scripts\ps1\run.ps1 -Login cashier` (với `quick.login.auto=` để trống) | Vào thẳng app với vai trò CASHIER — tham số dòng lệnh thắng cấu hình |
| 14 | `java -cp "out;lib\*" billing.Main --login=manager:manager` | Vào thẳng app, dùng cặp user:pass truyền trực tiếp (không cần có trong danh sách) |
| 15 | `java ... billing.Main --loginn=manager` (gõ sai) | In cảnh báo tham số rồi **hiện màn hình đăng nhập**, không thoát |

### 7.3. Đổi vai trò tại chỗ

| # | Bước | Kết quả mong đợi |
|---|---|---|
| 16 | Đăng nhập `cashier`, sang tab 3, xem các nút Sửa/Xoá | Mờ (disabled), tooltip giải thích |
| 17 | Bấm `Doi vai tro` → chọn `Quan ly (manager)` | Các nút **sáng lên ngay**, nhãn thanh trạng thái đổi thành MANAGER, cửa sổ **không** bị dựng lại (tab đang xem không nhảy về tab 1) |
| 18 | Trước đó thêm 3 dòng vào giỏ hàng ở tab 1, rồi làm lại #17 | **Giỏ hàng còn nguyên 3 dòng.** Đây là lý do tồn tại của thao tác này |
| 19 | Mở menu `Doi vai tro` khi đang là `manager` | Menu chỉ còn `Thu ngan (cashier)`, không liệt kê chính tài khoản đang dùng |
| 20 | `quick.login.enabled=false`, mở app, xem thanh trạng thái | Nút `Doi vai tro` **ẩn hẳn**, không phải mờ đi |
| 21 | Đổi mật khẩu `cashier` trong DB rồi bấm `Doi vai tro` → `cashier` | Hộp thoại cảnh báo "không đăng nhập được, có thể mật khẩu trong bảng Account đã bị đổi"; **phiên hiện tại giữ nguyên** là manager, không bị rơi về trạng thái chưa đăng nhập |
| 22 | Có 3 dòng trong giỏ hàng → bấm `Dang xuat` | Hộp thoại nói rõ số dòng sẽ mất **và** gợi ý dùng `Doi vai tro`; chọn No thì không mất gì |
| 23 | Giỏ hàng trống → bấm `Dang xuat` | Hộp thoại xác nhận ngắn như cũ, không nhắc giỏ hàng |

### 7.4. Nhật ký SQL (giao điểm với khối 1)

| # | Bước | Kết quả mong đợi |
|---|---|---|
| 24 | Đăng nhập bằng nút nhanh, mở tab 5 của Setup App | Có dòng `{call sp_authenticate_account(?, ?)}` với tham số `? 1=cashier, 2=******` — **mật khẩu đã che**, đúng như `AccountDao:44` đang làm |
| 25 | Bấm `Doi vai tro` 3 lần qua lại | Tab 5 thêm đúng 3 dòng `sp_authenticate_account`, không dòng nào lộ mật khẩu |
| 26 | `grep -i "password\|matkhau" logs/sql.log logs/last-login.properties` | Không có mật khẩu nguyên văn trong bất kỳ file nào dưới `logs/` |

### 7.5. Kiểm thử âm (bắt buộc — tất cả phải "app vẫn chạy")

| # | Bước | Kết quả mong đợi |
|---|---|---|
| 27 | `quick.login.accounts=cashier,manager:manager` (mục đầu thiếu mật khẩu) | Một dòng cảnh báo ra console, hàng nút nhanh còn **1 nút** (`manager`), app chạy bình thường |
| 28 | `quick.login.accounts=` (rỗng) | Hàng nút nhanh ẩn hẳn, màn hình đăng nhập thường, không exception |
| 29 | `quick.login.accounts=,,,` | Không nút nào, không cảnh báo (mục rỗng là lỗi gõ vô hại), không exception |
| 30 | Xoá hẳn 4 khoá `quick.login.*` khỏi `config.properties` | Hành vi **đúng như trước khối 2** (vì `enabled` mặc định `false`) |
| 31 | Đặt `logs` thành một **file thường** thay vì thư mục, rồi đăng nhập | App không chết, in **đúng một** dòng cảnh báo của `LoginPrefs` ra `System.err`, không hộp thoại lỗi. (Cùng bài kiểm thử âm mà khối 1 đã chạy cho `SqlLog`) |
| 32 | Tắt MySQL rồi bấm nút nhanh | Nút bị khoá trong lúc chờ, dialog **vẫn vẽ lại được** (không đứng hình), hết chờ thì `Ui.error` hiện lỗi kết nối và các nút mở lại |
| 33 | Bấm nút nhanh 5 lần thật nhanh | Chỉ **một** lượt xác thực được chạy (các nút bị khoá ngay từ cú bấm đầu) |

### 7.6. Phần B (chỉ khi làm phần B)

| # | Bước | Kết quả mong đợi |
|---|---|---|
| 34 | Setup App tab 2, combo để `(hiện màn hình đăng nhập)`, bấm Chạy | Như hiện nay: màn hình đăng nhập hiện ra |
| 35 | Combo chọn `manager`, bấm Chạy | Vào thẳng app với vai trò MANAGER; log của tab 2 in dòng `Dang nhap san bang: manager` |
| 36 | 4 tab cũ (Chung, Java, SQL, Git, SQL Log) | Không tab nào bị ảnh hưởng bởi các hàm nạp chồng mới ở `ScriptRunner` / `BaseTab` / `PlatformUtil` |
| 37 | Chiều cao combo ở tab 2, chụp ở 100% và 125% DPI | Vẽ đúng, không bóp méo, không tràn box (lỗi đã gặp ở `plan/09` mục 7.1) |

---

## 8. Rủi Ro & Cách Giảm

| # | Rủi ro | Mức | Cách giảm |
|---|---|---|---|
| 1 | Mật khẩu nguyên văn trong `config.properties` (file **đã được commit**) | Trung bình | Không phải lộ thông tin mới: 2 mật khẩu này đã nằm trong `sql/02_seed.sql` đã commit và đang được in ra màn hình đăng nhập. Khối này còn **bớt** một chỗ lộ (bỏ khung gợi ý in mật khẩu). Giảm thêm: comment cảnh báo trong `config.properties`, `quick.login.enabled=false` tắt tất cả, nhắc lại hướng dẫn gitignore `config.properties` cho dự án thật |
| 2 | Người học quên `quick.login.auto` đang bật, kết luận "app không có đăng nhập" | Cao nếu không xử lý | Hai dấu hiệu bắt buộc ở mục 3.3 (dòng console + chữ cam trên thanh trạng thái). Mặc định để trống |
| 3 | `--login` làm bài kiểm thử phân quyền bị bỏ qua | Trung bình | Kịch bản mục 7.1 và 7.3 **vẫn phải chạy qua đường gõ tay**; #4 và #8 là hai phép kiểm chứng đường cũ còn nguyên |
| 4 | `MainWindow` thêm nút mới → thanh trạng thái tràn ngang ở cửa sổ nhỏ | Thấp | Đã có tiền lệ thật ở `plan/09` mục 7.1 (6/8 lỗi là lỗi kích thước). Đo `getBounds()` so với `getPreferredSize()` ở bề rộng nhỏ nhất thay vì nhìn bằng mắt — kết luận "nhìn thấy ổn" đã sai một lần rồi (`plan/09` mục 6.3) |
| 5 | Đổi vai trò tại chỗ bỏ sót một chỗ UI phụ thuộc vai trò | Thấp | Đã `grep` toàn bộ `src/` (mục 4.4): chỉ 2 chỗ phải cập nhật, chỗ thứ 3 đọc `UserSession` tại thời điểm bấm nên tự đúng. **Nếu khối sau thêm chỗ phụ thuộc vai trò thì phải thêm vào `switchRole()`** — ghi chú này cần nằm ngay trong Javadoc của `switchRole()`, không chỉ trong tài liệu |
| 6 | `param()` trong `run.ps1` đặt sai chỗ → lỗi cú pháp PowerShell | Thấp | `param()` **phải là câu lệnh đầu tiên** của file, trước cả `$ErrorActionPreference` (mục 5.9). Kiểm bằng cách chạy `run.ps1` không tham số trước, rồi mới thử có tham số |

---

## 9. Trạng Thái Triển Khai (đã hoàn thành)

### 9.1. File đã thay đổi

**Phần A — billing, được commit lên git:**

| File | Trạng thái | Ghi chú |
|---|---|---|
| `config.properties` | Bổ sung | 4 khoá `quick.login.*` kèm comment cảnh báo mật khẩu nguyên văn |
| `.gitignore` | Sửa comment | Nói rõ `logs/` chứa cả `sql.log` và `last-login.properties` |
| `src/billing/util/AppConfig.java` | Tạo mới | Đọc `config.properties` một lần, `get` / `getBoolean` |
| `src/billing/util/QuickLogin.java` | **Tạo mới (ngoài kế hoạch)** | Cửa ngõ duy nhất tới 4 khoá — xem 9.2 mục 1 |
| `src/billing/model/QuickAccount.java` | Tạo mới | `(username, password, label)` + `parse()` |
| `src/billing/util/LoginPrefs.java` | Tạo mới | `logs/last-login.properties`, chỉ username |
| `src/billing/ui/AppFlow.java` | Tạo mới | `showLogin` / `openMainWindow` / `tryAutoLogin` + `record AutoLoginResult` |
| `src/billing/ui/LoginDialog.java` | Sửa | Hàng nút nhanh, dòng trạng thái, `SwingWorker`, điền sẵn username; bỏ khung in mật khẩu |
| `src/billing/ui/MainWindow.java` | Sửa | Nút `Doi vai tro`, cờ auto-login, `refreshUserInfo()`, cảnh báo giỏ hàng, **sửa lỗi tràn thanh trạng thái** (9.2 mục 5) |
| `src/billing/ui/NewInvoicePanel.java` | Bổ sung 2 hàm | `hasPendingWork()` + `pendingWorkSummary()` — xem 9.2 mục 3 |
| `src/billing/Main.java` | Sửa | Gọi `AppFlow.tryAutoLogin(args)` rồi `switch` 3 nhánh |
| `scripts/ps1/run.ps1` | Sửa | `param([string]$Login)` ở dòng đầu + đọc `$env:BILLING_LOGIN` |
| `scripts/bash/run.sh` | Sửa | `LOGIN="${1:-$BILLING_LOGIN}"` + mảng `APP_ARGS` |

**Phần B — envsetup, bị `.gitignore`, chỉ phát hành qua `SetupApp.jar`:**

| File | Trạng thái | Ghi chú |
|---|---|---|
| `src/envsetup/runner/PlatformUtil.java` | Bổ sung | Nạp chồng `createProcessBuilder(action, extraEnv)`; hàm 1 tham số gọi vào nó với `Map.of()` |
| `src/envsetup/runner/ScriptRunner.java` | Bổ sung | Nạp chồng `run(action, console, onFinished, extraEnv)` |
| `src/envsetup/ui/BaseTab.java` | Bổ sung | Nạp chồng `runScript(action, label, onExit, extraEnv)` |
| `src/envsetup/ui/JavaTab.java` | Sửa | Ô chọn "Đăng nhập sẵn" + `readQuickLoginUsers()` đọc từ `config.properties` |
| `SetupApp.jar` | Build lại | `.\scripts\ps1\build-setup.ps1` mã thoát 0, 78 KB; `JavaTab.class` và `PlatformUtil.class` trong jar đều là class version 65 (Java 21) — máy cài Temurin 21 chạy được. Lần build đầu thất bại vì jar đang bị tiến trình Setup App mở giữ; phải đóng app trước |

Không file nào bị xoá. Không file SQL nào bị sửa. Không bảng / stored procedure / seed nào bị chạm.

### 9.2. Những điểm làm khác kế hoạch (và lý do)

1. **Thêm `billing.util.QuickLogin` — file mới thứ 5, kế hoạch mục 5.1 chỉ liệt kê 4.** Ba chỗ cần
   danh sách tài khoản nhanh (`LoginDialog` vẽ hàng nút, `MainWindow` dựng menu đổi vai trò,
   `AppFlow` thử auto-login). Nếu mỗi chỗ tự gọi `AppConfig.getBoolean("quick.login.enabled", false)`
   thì tên khoá bị viết lại 3 lần: gõ sai một lần là một lỗi im lặng — tính năng tắt ở đúng một chỗ
   mà không ai hiểu vì sao. Giờ 4 khoá chỉ xuất hiện trong một file.

2. **Auto-login nằm trong `AppFlow` chứ không phải một lớp `AutoLogin` riêng.** Kế hoạch mục 5.6 cho
   cả hai lựa chọn; chọn gộp vì auto-login là một trong ba đường vào app, để cùng chỗ thì đọc được
   thứ tự ưu tiên. Kết quả trả về là `record AutoLoginResult(kind, account, username, source, message)`
   đúng kiểu truy cập `r.kind()` / `r.account()` mà kế hoạch đã viết.

3. **`NewInvoicePanel` thêm 2 hàm, không phải 1** (kế hoạch mục 5.8 nói "thêm đúng một hàm"). Kịch
   bản kiểm thử #22 đòi hộp thoại **"nói rõ số dòng sẽ mất"**, mà một hàm trả `boolean` thì không
   nói được con số. Thêm `pendingWorkSummary()` trả `"3 dong hang, 1 lan thanh toan"` (chuỗi rỗng khi
   không có gì). `hasPendingWork()` vẫn giữ vì nó đọc rõ hơn ở chỗ gọi. Vẫn không sửa dòng nào có sẵn.

4. **Dòng trạng thái của `LoginDialog` đặt ở hàng riêng phía trên hàng nút**, không phải cùng hàng
   như hình vẽ mục 4.1. Kế hoạch đúng ở chẩn đoán (đặt ở `WEST` thì bị cắt vì chỉ được cấp bề rộng
   lúc `pack()`) nhưng cách sửa của nó chưa đủ: ở `CENTER` **cùng hàng với 2 nút**, dòng trạng thái
   chỉ còn ~210px sau khi trừ nút, mà thông báo lý do auto-login thất bại dài hơn 500px. Đặt ở
   `NORTH` của panel phía nam thì nó được cả bề ngang dialog mà **không phải nới rộng dialog**.
   Thông báo dài hơn 60 ký tự được ngắt dòng bằng HTML — chỉ xảy ra với thông báo truyền vào hàm
   dựng, tức là trước `pack()`, nên dialog vẫn đủ cao.

5. **Lỗi thật, đo được: thanh trạng thái `MainWindow` tràn ngang — đúng rủi ro #4 của kế hoạch.**
   Bề rộng mong muốn của cả thanh (nhãn người dùng + chữ `(DANG NHAP TU DONG)` + 2 nút) là
   **673px**. `BorderLayout` cấp cho `WEST` và `EAST` đúng bề rộng mong muốn của chúng, nên ở cửa sổ
   hẹp hơn 673px thì **hàng nút đè lên nhãn** (không phải cắt chữ — đè hẳn). Phát hiện bằng phép đo
   `getBounds()`, không nhìn bằng mắt, đúng như rủi ro #4 yêu cầu. Cách sửa:
   - Nhãn chuyển từ `WEST` sang **`CENTER`** → `BorderLayout` cấp cho nó phần còn lại sau khi trừ
     hàng nút, nên chữ chỉ bị cắt chứ không bị đè. Thêm tooltip cho `lbInfo` để đọc đủ khi bị cắt.
   - Panel nhãn dùng **`BoxLayout.X_AXIS`** thay `FlowLayout`: `FlowLayout` sẽ *xuống dòng* khi hẹp,
     làm thanh trạng thái cao gấp đôi.

   Đã kiểm lại ở 1280 / 900 / 700 / 640 / 520 / 400px: không chỗ nào đè nhau, không chỗ nào thanh
   cao thêm.

6. **`--login=user:pass` trim username nhưng không trim mật khẩu**, cùng nguyên tắc với
   `QuickAccount.parse` (mục 5.3): tự ý trim mật khẩu sinh ra lỗi "sai mật khẩu" không thể hiểu nổi.

7. **`--login=` để trống được coi như không truyền tham số**, giống khoá cấu hình để trống. Nếu
   không thì `run.ps1` với `-Login ""` sẽ rơi vào nhánh FAILED một cách vô nghĩa.

8. **`quick.login.enabled=false` mà vẫn có `--login=` thì in một dòng nói rõ bị bỏ qua vì khoá nào.**
   Kế hoạch mục 3.2 chỉ nói "bị vô hiệu". Im lặng thì người dùng kết luận "tham số `--login` không
   chạy" và đi tìm lỗi ở chỗ khác.

9. **Auto-login thành công cũng ghi `LoginPrefs.saveLastUsername()`** (kế hoạch chỉ nói `LoginDialog`
   ghi). Đó cũng là một lần đăng nhập thành công; không ghi thì mở app bằng tay lần sau lại điền
   tên cũ hơn.

10. **Hàng nút nhanh xếp tối đa 3 nút một hàng** (`GridLayout`), không phải một hàng ngang như hình
    vẽ mục 4.1. Với 2 tài khoản mẫu thì giống nhau; với 6-9 tài khoản thì một hàng ngang sẽ làm
    dialog rộng ra vô lý (dialog đang `setResizable(false)` nên không sửa lại được).

11. **Phần B: ô chọn "Đăng nhập sẵn" đọc danh sách từ `config.properties`**, không hardcode
    `cashier / manager` như hình trong mục 6 việc 17. Setup App không được hứa những tài khoản không
    tồn tại. Hai app là hai tiến trình JVM riêng nên không dùng chung được `QuickAccount` —
    **cú pháp của khoá `quick.login.accounts` chính là hợp đồng giữa chúng**, đúng kiểu "định dạng
    file là hợp đồng" của khối 1. Khi tính năng tắt thì ô bị khoá kèm tooltip chỉ cách bật.

12. **Phát hiện đo được, có chủ ý KHÔNG sửa ở khối này:** hàng nút của `BaseTab` dùng `FlowLayout`,
    mà `FlowLayout.preferredLayoutSize()` **luôn tính theo một hàng**. Nên khi hàng nút xuống dòng
    thì hàng thứ hai bị cắt mất (vì `buttonBar` chỉ được cấp chiều cao một hàng). Sau khi thêm nhãn
    + ô chọn vào tab 2, ngưỡng đo được là **bề rộng cửa sổ 700px**; `envsetup.ui.MainWindow` khoá
    `setMinimumSize(880, 620)` nên người dùng **không thể** kéo tới đó — còn 180px dự phòng. Sửa
    `FlowLayout` của hàng nút là sửa dùng chung cho cả 5 tab, vượt phạm vi "chỉ thêm hàm nạp chồng"
    của phần B. **Khối sau thêm nút hoặc ô vào tab 2 thì phải đo lại con số 700px này.**

13. **Kịch bản #11 và #21 ("đổi mật khẩu `manager` / `cashier` trong bảng `Account`") được kiểm bằng
    cách đặt mật khẩu sai trong `config.properties`, không sửa bảng `Account`.** Cùng một đường mã
    (`authenticate()` trả `null` → nhánh FAILED / hộp thoại cảnh báo) và giữ đúng tính chất "khối
    này không chạm database" ở cả phần kiểm thử. Xem 9.3 để biết mục nào vì vậy mà là `[~]`.

### 9.3. Kết quả kiểm thử đã chạy

**Biên dịch** (từ trạng thái sạch):

- [x] Phía billing: **30 file**, `javac -encoding UTF-8 -d out -cp "lib\*"` mã thoát 0. Cảnh báo
      duy nhất là note deprecated có từ trước trong `SearchPanel.java` — đúng như khối 1 đã ghi.
- [x] Chạy thêm `-Xlint:all`: 40 cảnh báo, **tất cả** thuộc 3 nhóm đã có sẵn khắp codebase
      (`serial`, `this-escape`, `cast`). Không sinh ra nhóm cảnh báo mới nào.
- [x] Phía Setup App: `javac --release 21 -Xlint:-options` mã thoát 0, 29 class.
- [x] `SetupApp.jar`: `build-setup.ps1` mã thoát 0; `JavaTab`/`PlatformUtil` trong jar là class
      version 65 (Java 21). **Lưu ý thao tác:** lần build đầu thất bại với *"file đang bị tiến trình
      khác dùng"* — phải đóng Setup App trước khi build, nó giữ handle lên chính file jar đó.

**Công cụ kiểm thử.** Toàn bộ số liệu dưới đây do chương trình kiểm, không nhìn bằng mắt:
`K2Test.java` điều khiển trực tiếp component thật (`doClick()`, `dispatchEvent`, đọc `getBounds()`,
đọc `JOptionPane.getMessage()`) và nói chuyện với **MySQL thật**; 16 kịch bản, mỗi kịch bản một JVM
riêng với `config.properties` riêng (bắt buộc, vì `AppConfig`/`Db` đọc file đó một lần trong static
block). Cộng thêm `e2e.ps1` chạy `run.ps1` và `java billing.Main` như tiến trình thật rồi đọc
console, và `K2SetupTest.java` cho phần B. Tổng **273 phép kiểm, tất cả PASS**.

**7.1. Đường đăng nhập**

- [x] **#1** 2 nút nhanh đúng nhãn `1. Thu ngan (cashier)` / `2. Quan ly (manager)`; khung gợi ý in
      mật khẩu đã biến mất. Phép kiểm quyết định chạy với một tài khoản có mật khẩu **khác**
      username (`boss:s3cret:Giam doc`): chuỗi `s3cret` không xuất hiện trong bất kỳ text hay
      tooltip nào của cây component.
- [x] **#2** Một lần `doClick()` nút 1 → vào app với vai trò `cashier`, 2 ô được điền sẵn.
- [x] **#3** Alt+2 → vào app với `manager`. Kiểm hai tầng: (a) `InputMap` WHEN_IN_FOCUSED_WINDOW của
      nút 2 có đúng `alt pressed 2` trỏ tới hành động bấm nút; (b) dispatch `KeyEvent` Alt+2 thật
      (cả PRESSED và RELEASED) vào cửa sổ **đang hiện**. Chạy lại 4 lần, 4/4 vào `manager`.
- [x] **#4** Gõ tay `manager`/`manager` → vẫn vào được: đường cũ không bị phá.
- [x] **#5** Dòng trạng thái đỏ đúng chữ `Sai ten dang nhap hoac mat khau.`, ô mật khẩu bị xoá và
      nhận con trỏ, **0 hộp thoại modal** (đếm bằng chương trình, không phải "thấy không có").
- [x] **#6** Mở lại dialog → ô username điền sẵn `manager`; file `logs/last-login.properties` chỉ
      chứa `last.username=manager`.
- [x] **#7** `quick.login.remember=false` → file **không** được tạo, `loadLastUsername()` trả rỗng,
      ô username trống.
- [x] **#8** `quick.login.enabled=false` → 0 nút nhanh, **0 `TitledBorder`** trong cả dialog (tức là
      không còn cả đường phân cách), không dòng `Phim tat`, không dòng `Bam mot nut`; vẫn còn ô nhập
      và nút Đăng nhập.

**7.2. Auto-login**

- [x] **#9** `quick.login.auto=manager` → `SUCCESS`, nguồn `config quick.login.auto`; dòng
      `[AUTO-LOGIN]` ra console; thanh trạng thái có nhãn `(DANG NHAP TU DONG)` đúng màu
      `(190,110,0)` kèm tooltip chỉ cách tắt. Đăng nhập thường thì **không** có nhãn đó.
- [x] **#10** `quick.login.auto=khongcothat` → `FAILED`, lý do nói rõ không tìm thấy tài khoản trong
      `quick.login.accounts` và hướng dẫn đăng nhập bằng tay; hàm trả về bình thường, app không thoát.
- [~] **#11** Mật khẩu lệch → `FAILED` với lý do `sai mat khau`, không vào app, **nhật ký SQL vẫn ghi
      được dòng `sp_authenticate_account`** (chứng minh nguyên tắc mục 2.2: không bỏ qua xác thực).
      `[~]` vì mật khẩu lệch được đặt trong `config.properties` chứ không sửa bảng `Account` — cùng
      đường mã, nhưng không phải đúng thao tác mà mục 7.2 viết. Xem 9.2 mục 13.
- [x] **#12** `auto=manager` + `enabled=false` → `NONE`; `--login=manager` cũng bị vô hiệu kèm một
      dòng giải thích.
- [x] **#13** Chạy thật `.\scripts\ps1\run.ps1 -Login cashier`: script in `Dang nhap san bang: cashier`,
      biên dịch mã thoát 0, app in `[AUTO-LOGIN] Da dang nhap san bang 'cashier' (nguon: tham so --login)`,
      tiến trình còn sống. Kiểm thêm đường `BILLING_LOGIN=manager` (đường ống cho Setup App): cùng kết quả.
- [x] **#14** Chạy thật `java -cp "out;lib\*" billing.Main --login=manager:manager` → vào thẳng app,
      nguồn `tham so --login=<user>:<pass>`, không cần có trong danh sách.
- [x] **#15** `--loginn=manager` → in `[LOI THAM SO] Khong hieu tham so '--loginn=manager'.` kèm
      hướng dẫn cú pháp, **không** auto-login, app **không thoát**.
- [x] **Rủi ro #6** `run.ps1` không tham số: không lỗi cú pháp PowerShell (`param()` đặt đúng chỗ),
      app khởi động bình thường, không auto-login.

**7.3. Đổi vai trò tại chỗ**

- [x] **#16** `cashier`: 4 nút Sửa/Xoá đều `disabled`, tooltip đúng câu giải thích.
- [x] **#17** Bấm mục menu `Quan ly (manager)` → 3 nút sáng lên ngay, nhãn đổi thành `MANAGER`,
      **cùng một đối tượng `NewInvoicePanel` và `InvoiceViewPanel`** (so sánh identity → cửa sổ
      không bị dựng lại), tab đang xem vẫn là tab 3, cửa sổ vẫn `visible`, 0 hộp thoại.
- [x] **#18** 3 dòng trong giỏ hàng trước khi đổi → **vẫn đúng 3 dòng** sau đó, và vẫn còn nguyên
      sau 4 lần đổi qua lại.
- [x] **#19** Menu chỉ liệt kê tài khoản khác tài khoản đang dùng, kiểm ở cả hai chiều.
- [x] **#20** `enabled=false` → `btSwitchRole.isVisible() == false` (ẩn hẳn, không phải mờ); nút
      Đăng xuất vẫn hiện.
- [~] **#21** Hộp thoại đúng tiêu đề `Doi vai tro that bai`, nội dung nói "không đăng nhập được",
      "mật khẩu trong bảng Account đã bị đổi" và "phiên hiện tại vẫn được giữ nguyên"; phiên vẫn là
      `manager`, vẫn đăng nhập, 2 nút được mở lại. `[~]` vì hai lý do: mật khẩu lệch đặt trong config
      (như #11), và hộp thoại được đóng bằng `JOptionPane.setValue(CLOSED_OPTION)` (tương đương chọn
      No / bấm X) chứ không bấm chuột thật.
- [x] **#22** Giỏ hàng 3 dòng → hộp thoại nói đúng `dang co 3 dong hang chua luu`, `Dang xuat se mat
      toan bo`, và có dòng gợi ý dùng `"Doi vai tro"`; chọn No thì giỏ hàng còn nguyên, cửa sổ còn
      nguyên, vẫn đăng nhập. Nội dung hộp thoại đọc bằng `JOptionPane.getMessage()`, không nhìn mắt.
- [x] **#23** Giỏ trống → đúng câu ngắn cũ `Ban co chac chan muon dang xuat khoi he thong?`, không
      nhắc giỏ hàng.

**7.4. Nhật ký SQL (giao điểm với khối 1)**

- [~] **#24** File `logs/sql.log` có dòng `{call sp_authenticate_account(?, ?)}` với
      `? 1=cashier, 2=******`. `[~]` vì kiểm trực tiếp trên file chứ **không mở tab 5 của Setup App**
      — tab 5 chỉ tail đúng file đó (`LogTailer` dùng đường dẫn cố định, đã kiểm ở khối 1) nên nội
      dung hiện ra là nội dung này.
- [~] **#25** Đổi vai trò 3 lần → đúng **3** dòng `sp_authenticate_account` thêm vào (đếm bằng
      chương trình), không dòng nào lộ mật khẩu. `[~]` cùng lý do #24.
- [x] **#26** Quét **17 file** nhật ký của mọi kịch bản: **24/24** dòng xác thực đều có `2=******`,
      không dòng nào thiếu; không có chuỗi mật khẩu nguyên văn (`s3cret`, `mat-khau-sai`) ở bất kỳ
      file nào dưới `logs/`. `last-login.properties` chỉ có `last.username=`.

**7.5. Kiểm thử âm — tất cả đều "app vẫn chạy"**

- [x] **#27** `cashier,manager:manager` → một dòng cảnh báo ra console, còn đúng 1 nút (`manager`),
      nhãn mặc định là chính username.
- [x] **#28** `quick.login.accounts=` rỗng (và `null`, và toàn khoảng trắng) → 0 nút, không exception.
- [x] **#29** `,,,` → 0 nút, **không** cảnh báo (mục rỗng là lỗi gõ vô hại), không exception.
- [x] **#30** Xoá hẳn 4 khoá → `isEnabled()` là `false` (mặc định an toàn), 0 nút nhanh, auto-login
      `NONE`, `--login` bị vô hiệu, không nhớ username, nút Đổi vai trò ẩn, **vẫn đăng nhập được
      bằng tay** → đúng như trước khối 2.
- [x] **#31** `logs` là một **file thường**: `LoginPrefs` in **đúng một** dòng ra `System.err` (bắt
      `System.err` ra buffer rồi đếm, gọi `saveLastUsername` 3 lần vẫn chỉ 1 dòng), không hộp thoại,
      màn hình đăng nhập vẫn dựng được.
- [x] **#32** MySQL không với tới được (`db.url` trỏ `10.255.255.1`, `connectTimeout=6000`): trong
      lúc chờ, mọi nút bị khoá và dòng trạng thái là `Dang kiem tra tai khoan...`; **EDT vẫn xử lý
      được sự kiện** (`invokeAndWait` trả về ngay giữa lúc chờ → dialog vẽ lại được, không đứng
      hình); hết chờ thì hiện `Ui.error` với lỗi `Communications link failure` và các nút mở lại.
- [x] **#33** Bấm nút nhanh **5 lần** liên tiếp → đúng **1** dòng `sp_authenticate_account` trong
      nhật ký (đếm hiệu số trước/sau), 0 hộp thoại.

**7.6. Phần B (Setup App)** — 32 phép kiểm, chạy hai lần: trên class vừa biên dịch **và** trên chính
`SetupApp.jar` vừa đóng gói (`-cp SetupApp.jar`), cả hai lần đều 32/32 PASS:

- [x] **#34** Ô chọn có mục đầu `(hiện màn hình đăng nhập)` và nó là mục được chọn mặc định → bấm
      Chạy không truyền biến môi trường nào.
- [x] **#35** Chọn `manager` → `BILLING_LOGIN=manager` có trong `ProcessBuilder.environment()`, và
      **dòng lệnh không bị ghép thêm tham số** (so sánh `command()` với bản không có env → giống hệt),
      đúng nguyên tắc mục 6 việc 14. Giá trị rỗng thì khoá bị bỏ qua. Danh sách đọc đúng
      `[cashier, manager]` từ `config.properties` thật.
- [x] **#36** Hàm 1 tham số cũ `createProcessBuilder(action)` cho ra **đúng cùng** `command()`, cùng
      thư mục làm việc, và **không** có `BILLING_LOGIN` → 4 tab cũ không bị ảnh hưởng. Hai hàm nạp
      chồng còn lại (`ScriptRunner.run`, `BaseTab.runScript`) đều chỉ gọi lại bản mới với `Map.of()`,
      nên đường cũ không có nhánh mã riêng nào để lệch.
- [~] **#37** Chiều cao ô chọn 28px (= chiều cao chữ + 12, nhờ `UiHelper.createComboBox`),
      `getMaximumSize() == getPreferredSize()`, lệch tâm dọc **0px** so với nút Chạy (tức là cùng
      hàng, không xuống dòng), nằm trọn trong hàng nút ở **1600 / 1050 / 920 / 880px** — 880px là bề
      rộng nhỏ nhất mà `envsetup.ui.MainWindow` cho phép. Ngưỡng bắt đầu bị cắt đo được là **700px**
      (xem 9.2 mục 12). `[~]` vì **không chụp ở 125% DPI**: phép đo trên chỉ chạy ở DPI của máy kiểm
      (100%). Phần phụ thuộc DPI còn lại nằm trong `UiHelper.createComboBox`, vốn đã được dùng và
      kiểm ở tab Git từ `plan/09`.
