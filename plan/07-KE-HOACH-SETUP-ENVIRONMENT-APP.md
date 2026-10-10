# Kế Hoạch Chi Tiết: Xây Dựng Ứng Dụng GUI Quản Lý Môi Trường (Setup Environment Portable App)

> **Dành cho AI / Lập trình viên tiếp theo:** Tài liệu này chứa đặc tả kỹ thuật toàn diện, kiến trúc thư mục, quy tắc Git, thiết kế giao diện Swing và danh sách các script PowerShell cần xây dựng. Hãy đọc kỹ và tuân thủ đúng các nguyên tắc bên dưới để triển khai hoàn thiện dự án.

---

## 1. Mục Tiêu & Các Nguyên Tắc Bắt Buộc

### 1.1. Mục tiêu
1. **Portable ("Bấm phát chạy luôn"):**
   - Người dùng bấm đúp chuột vào file `Setup.bat` (hoặc `SetupApp.jar`) tại thư mục gốc là giao diện ứng dụng mở lên ngay lập tức trong vòng 1-2 giây.
   - Không xuất hiện cửa sổ cmd đen treo ngoài màn hình (sử dụng `javaw`).
   - Không cần cài thêm runtime/build-tool nào khác ngoài JDK 21 đã có sẵn.

2. **Vị trí mã nguồn & Quy tắc Git (RẤT QUAN TRỌNG):**
   - **Mã nguồn (Source Code):** Nằm trong `src/envsetup/` (bên cạnh thư mục `src/billing/`).
   - **Thêm vào `.gitignore`:** Thêm dòng `src/envsetup/` vào file `.gitignore` ở thư mục gốc để **tuyệt đối KHÔNG đẩy mã nguồn này lên GitHub**.
   - **File thực thi sau khi biên dịch (Artifacts):**
     - Đặt tại thư mục gốc: `SetupApp.jar` và `Setup.bat`.
     - **KHÔNG thêm** 2 file này vào `.gitignore`. Hai file này **ĐƯỢC ĐẨY LÊN GITHUB** để bất kỳ ai tải project về cũng có thể bấm dùng được ngay.
   - **Script đóng gói:** `build-setup.ps1` đặt tại thư mục gốc để tiện biên dịch và đóng gói `SetupApp.jar` bất kỳ lúc nào.

3. **Công nghệ đồ hoạ & Phong cách:**
   - Sử dụng **Java Swing thuần** (`javax.swing.*`, `java.awt.*`) đồng nhất với codebase của `billing`.
   - Font chữ chuẩn: `Segoe UI Bold` cho nút bấm / tiêu đề, `Consolas` cho màn hình log.
   - Bảng log màu đen đặt ở **dưới cùng mỗi tab** (mã màu nền: `#181A1E` / `Color(24, 26, 30)`, mã màu chữ: `#C8DCF0` / `Color(200, 220, 240)`).

4. **Bản chất thực thi:**
   - GUI là lớp điều khiển bất đồng bộ (non-blocking async), thực thi các file PowerShell `.ps1` ở thư mục gốc.
   - Toàn bộ kết quả `stdout` và `stderr` được stream thời gian thực (real-time) vào bảng đen log của tab tương ứng với mã hóa UTF-8.

---

## 2. Thiết Kế Giao Diện (3 Tab & Bảng Log Đen)

Cửa sổ chính (`MainWindow`) có kích thước tiêu chuẩn `1050 x 720`, căn giữa màn hình, chứa `JTabbedPane` gồm 3 tab:

### 2.1. Tab 1: Chung (General)
- **Khu vực nút bấm (Phía trên):**
  1. `[⚙️ Cài đặt môi trường]`: Chạy `.\setup.ps1` (kiểm tra JDK, tải JDBC driver `mysql-connector-j-8.4.0.jar` vào `lib\`, kiểm tra MySQL portable).
  2. `[▶️ Chạy SQL Server]`: Chạy `.\mysql-start.ps1` (khởi động `mysqld.exe` chạy nền, kiểm tra cổng 3306 đã sẵn sàng).
  3. `[⏹️ Dừng SQL Server]`: Chạy `.\mysql-stop.ps1` (gọi `mysqladmin shutdown` để dừng an toàn).
- **Bảng log đen (Phía dưới):**
  - Hiển thị toàn bộ nhật ký cài đặt và trạng thái bật/tắt của MySQL.

### 2.2. Tab 2: Java
- **Khu vực nút bấm (Phía trên):**
  1. `[▶️ Chạy source billing]`: Chạy `.\run.ps1` (biên dịch `src/billing` bằng `javac` và khởi chạy `billing.Main`).
  2. `[⏹️ Dừng chạy source]`: Chạy `.\billing-stop.ps1` (tìm tiến trình Java chạy `billing.Main` và tắt an toàn).
- **Bảng log đen (Phía dưới):**
  - Hiển thị log biên dịch và toàn bộ console output của ứng dụng billing.

### 2.3. Tab 3: SQL
- **Khu vực nút bấm (Phía trên):**
  1. `[🏗️ Chạy tạo database]`: Chạy `.\sql-create-db.ps1` (thực thi `sql/01_schema.sql` và `sql/04_routines.sql` tự động từ `config.properties`).
  2. `[🌱 Tạo dữ liệu mẫu]`: Chạy `.\sql-seed-data.ps1` (thực thi `sql/02_seed.sql`).
  3. `[📊 Chạy bài tập 03_queries.sql]`: Chạy `.\sql-run-queries.ps1` (thực thi `sql/03_queries.sql` và hiển thị kết quả dạng bảng).
  4. `[🗑️ Xoá database]`: Bật hộp thoại `JOptionPane.showConfirmDialog` cảnh báo. Nếu chọn Yes, thực thi `.\sql-drop-db.ps1` (`DROP DATABASE IF EXISTS retail_billing;`).
- **Bảng log đen (Phía dưới):**
  - Hiển thị kết quả truy vấn SQL, báo cáo số dòng đã chèn hoặc xóa.

---

## 3. Cấu Trúc File & Thư Mục Toàn Dự Án

```
d:\DemoBillingManagement\
│
├── .gitignore                       <-- [CẦN SỬA] Thêm: src/envsetup/
├── config.properties
├── pom.xml
│
├── SetupApp.jar                     <-- [FILE BIÊN DỊCH] Được commit lên Git (Portable Jar)
├── Setup.bat                        <-- [FILE LAUNCHER] Được commit lên Git (Click đúp chạy javaw)
├── build-setup.ps1                  <-- [SCRIPT ĐÓNG GÓI] Biên dịch src/envsetup ra SetupApp.jar
│
├── setup.ps1                        <-- [SẴN CÓ / HOÀN THIỆN] Cài đặt driver & check môi trường
├── mysql-start.ps1                  <-- [CẦN SỬA] Thêm cơ chế chạy nền (Start-Process) cho GUI
├── mysql-stop.ps1                   <-- [SẴN CÓ] Tắt MySQL an toàn
├── run.ps1                          <-- [SẴN CÓ] Biên dịch và chạy billing
├── billing-stop.ps1                 <-- [CẦN TẠO] Dừng ứng dụng billing.Main
├── sql-create-db.ps1                <-- [CẦN TẠO] Tạo DB & Routines tự động
├── sql-seed-data.ps1                <-- [CẦN TẠO] Nạp dữ liệu mẫu tự động
├── sql-run-queries.ps1              <-- [CẦN TẠO] Chạy và in kết quả 03_queries.sql
├── sql-drop-db.ps1                  <-- [CẦN TẠO] Xóa database retail_billing
│
├── sql\                             <-- 01_schema, 02_seed, 03_queries, 04_routines
├── lib\                             <-- Chứa mysql-connector-j-8.4.0.jar
│
└── src\
    ├── billing\                     <-- Mã nguồn billing chính (ĐƯỢC COMMIT LÊN GIT)
    │
    └── envsetup\                    <-- Mã nguồn Setup App (BỊ GITIGNORE - KHÔNG ĐẨY LÊN GIT)
        ├── Main.java                <-- Entrypoint khởi động Swing GUI
        ├── ui\
        │   ├── MainWindow.java      <-- Khung JFrame, chứa JTabbedPane 3 tab
        │   ├── GeneralTab.java      <-- Giao diện Tab Chung (3 nút + LogConsole)
        │   ├── JavaTab.java         <-- Giao diện Tab Java (2 nút + LogConsole)
        │   ├── SqlTab.java          <-- Giao diện Tab SQL (4 nút + LogConsole)
        │   ├── LogConsole.java      <-- Component bảng đen (JTextArea + ScrollPane + Toolbar)
        │   └── UiHelper.java        <-- Hằng số màu sắc, font, button style
        └── runner\
            └── ScriptRunner.java    <-- Engine gọi powershell.exe bất đồng bộ & stream UTF-8
```

---

## 4. Chi Tiết Các File Cần Triển Khai

### 4.1. Cập nhật `.gitignore`
Mở `.gitignore` và thêm vào cuối:
```gitignore
# Source code cua setup tool (khong commit len git)
src/envsetup/
```

### 4.2. File `Setup.bat` (Thư mục gốc)
```cmd
@echo off
start javaw -Dfile.encoding=UTF-8 -jar "%~dp0SetupApp.jar"
```

### 4.3. Script `build-setup.ps1` (Thư mục gốc)
```powershell
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

Write-Host "Dang bien dich Setup App..." -ForegroundColor Cyan
$outDir = Join-Path $PSScriptRoot 'out\setup'
if (Test-Path $outDir) { Remove-Item -Recurse -Force $outDir }
New-Item -ItemType Directory $outDir | Out-Null

$sources = Join-Path $env:TEMP 'envsetup-sources.txt'
Get-ChildItem -Path '.\src\envsetup' -Recurse -Filter '*.java' |
    ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' } |
    Set-Content -Path $sources -Encoding ASCII

& javac -encoding UTF-8 -d $outDir "@$sources"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Bien dich that bai!" -ForegroundColor Red
    exit 1
}

Write-Host "Dang dong goi SetupApp.jar..." -ForegroundColor Cyan
& jar cfe SetupApp.jar envsetup.Main -C $outDir .
if ($LASTEXITCODE -ne 0) {
    Write-Host "Dong goi JAR that bai!" -ForegroundColor Red
    exit 1
}

Remove-Item -Recurse -Force $outDir
Write-Host "[OK] Da tao thanh cong SetupApp.jar tai thu muc goc." -ForegroundColor Green
```

### 4.4. Các script PowerShell bổ sung trong thư mục gốc

#### `mysql-start.ps1` (Nâng cấp)
Hỗ trợ chạy `mysqld` ở chế độ background nếu chưa chạy, rồi loop kiểm tra kết nối cổng 3306 trong tối đa 10 giây:
```powershell
$ErrorActionPreference = 'Stop'
$base = Join-Path $env:USERPROFILE 'mysql8\mysql-8.0.45-winx64'
$data = Join-Path $base 'data'

if (-not (Test-Path "$base\bin\mysqld.exe")) {
    Write-Host "[LOI] Khong tim thay MySQL tai: $base" -ForegroundColor Red
    exit 1
}

$running = Get-Process mysqld -ErrorAction SilentlyContinue
if ($running) {
    Write-Host "[OK] MySQL Server dang chay san (PID $($running.Id))." -ForegroundColor Green
    exit 0
}

Write-Host "Dang khoi dong MySQL 8.0.45 tren cong 3306..." -ForegroundColor Cyan
Start-Process "$base\bin\mysqld.exe" -ArgumentList "--basedir=`"$base`"", "--datadir=`"$data`"", "--port=3306" -WindowStyle Hidden

# Doi cong 3306 san sang
$ready = $false
for ($i = 0; $i -lt 10; $i++) {
    Start-Sleep -Seconds 1
    if (Get-Process mysqld -ErrorAction SilentlyContinue) {
        $ready = $true
        break
    }
}

if ($ready) {
    Write-Host "[OK] MySQL Server da khoi dong thanh cong tren cong 3306." -ForegroundColor Green
} else {
    Write-Host "[!!] MySQL khoi dong chua thanh cong. Kiem tra lai thu muc data." -ForegroundColor Yellow
}
```

#### `billing-stop.ps1`
```powershell
$ErrorActionPreference = 'SilentlyContinue'
Write-Host "Dang kiem tra tien trinh billing..." -ForegroundColor Cyan

$procs = Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*billing.Main*' }
if ($procs) {
    foreach ($p in $procs) {
        Stop-Process -Id $p.ProcessId -Force
        Write-Host "[OK] Da dung tien trinh billing (PID $($p.ProcessId))." -ForegroundColor Green
    }
} else {
    Write-Host "Khong co tien trinh billing nao dang chay." -ForegroundColor Yellow
}
```

#### `sql-create-db.ps1`
```powershell
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$mysql = (Join-Path $env:USERPROFILE 'mysql8\mysql-8.0.45-winx64\bin\mysql.exe')
if (-not (Test-Path $mysql)) { $mysql = 'mysql' }

Write-Host "--- Tao Database retail_billing va cac bang ---" -ForegroundColor Cyan
$env:MYSQL_PWD = 'root'

Get-Content '.\sql\01_schema.sql' -Raw -Encoding UTF8 |
    & $mysql -u root -h 127.0.0.1 -P 3306 --default-character-set=utf8mb4

Get-Content '.\sql\04_routines.sql' -Raw -Encoding UTF8 |
    & $mysql -u root -h 127.0.0.1 -P 3306 --default-character-set=utf8mb4

$env:MYSQL_PWD = $null
Write-Host "[OK] Da tao thanh cong Database retail_billing va cac Store Procedures / Views." -ForegroundColor Green
```

#### `sql-seed-data.ps1`
```powershell
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$mysql = (Join-Path $env:USERPROFILE 'mysql8\mysql-8.0.45-winx64\bin\mysql.exe')
if (-not (Test-Path $mysql)) { $mysql = 'mysql' }

Write-Host "--- Nap du lieu mau vao retail_billing ---" -ForegroundColor Cyan
$env:MYSQL_PWD = 'root'

Get-Content '.\sql\02_seed.sql' -Raw -Encoding UTF8 |
    & $mysql -u root -h 127.0.0.1 -P 3306 --default-character-set=utf8mb4

$env:MYSQL_PWD = $null
Write-Host "[OK] Da nap thanh cong du lieu mau vao Database." -ForegroundColor Green
```

#### `sql-run-queries.ps1`
```powershell
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$mysql = (Join-Path $env:USERPROFILE 'mysql8\mysql-8.0.45-winx64\bin\mysql.exe')
if (-not (Test-Path $mysql)) { $mysql = 'mysql' }

Write-Host "================ CHAY BAI TAP 03_QUERIES.SQL ================" -ForegroundColor Cyan
$env:MYSQL_PWD = 'root'

& $mysql -u root -h 127.0.0.1 -P 3306 -t --default-character-set=utf8mb4 -e "source ./sql/03_queries.sql"

$env:MYSQL_PWD = $null
Write-Host "================ HOAN THANH CHAY CAC QUERY ================" -ForegroundColor Green
```

#### `sql-drop-db.ps1`
```powershell
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$mysql = (Join-Path $env:USERPROFILE 'mysql8\mysql-8.0.45-winx64\bin\mysql.exe')
if (-not (Test-Path $mysql)) { $mysql = 'mysql' }

Write-Host "Dang xoa database retail_billing..." -ForegroundColor Yellow
$env:MYSQL_PWD = 'root'

& $mysql -u root -h 127.0.0.1 -P 3306 -e "DROP DATABASE IF EXISTS retail_billing;"

$env:MYSQL_PWD = $null
Write-Host "[OK] Da xoa sach database retail_billing." -ForegroundColor Green
```

---

### 4.5. Kiến trúc Code Java trong `src/envsetup/`

1. **`UiHelper.java`:**
   - Cung cấp:
     - `FONT_TITLE = new Font("Segoe UI", Font.BOLD, 15);`
     - `FONT_BUTTON = new Font("Segoe UI", Font.BOLD, 13);`
     - `FONT_CONSOLE = new Font("Consolas", Font.PLAIN, 13);`
     - `COLOR_BG = new Color(24, 26, 30);`
     - `COLOR_FG = new Color(200, 220, 240);`
     - Phương thức tạo nút bấm chuẩn: `JButton createBtn(String title, Color bgColor)` với margin thoải mái.

2. **`LogConsole.java`:**
   - Kế thừa `JPanel` với `BorderLayout`.
   - Nửa trên: Thanh công cụ gồm Label tiêu đề nhật ký, nút `Xóa log`, nút `Sao chép`, checkbox `Tự động cuộn`.
   - Giữa: `JScrollPane` bọc `JTextArea` cấu hình:
     - `setEditable(false)`
     - `setBackground(UiHelper.COLOR_BG)`
     - `setForeground(UiHelper.COLOR_FG)`
     - `setFont(UiHelper.FONT_CONSOLE)`
   - Cung cấp hàm `append(String line)` gọi qua `SwingUtilities.invokeLater`.

3. **`ScriptRunner.java`:**
   - Chạy lệnh PowerShell bất đồng bộ bằng `ProcessBuilder`:
     ```java
     ProcessBuilder pb = new ProcessBuilder(
         "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", scriptName
     );
     pb.directory(new File("."));
     pb.redirectErrorStream(true);
     ```
   - Khởi chạy một thread nền để đọc từng dòng stream `InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)`.
   - Tự động disable các nút bấm trong tab khi script bắt đầu chạy và enable lại khi process kết thúc.

4. **`GeneralTab.java`, `JavaTab.java`, `SqlTab.java`:**
   - Mỗi tab chứa nhóm nút bấm phía trên (sử dụng `FlowLayout` hoặc `GridLayout` gọn gàng) và một đối tượng `LogConsole` ở phía dưới.
   - Khi bấm nút, gọi `ScriptRunner.run(scriptName, logConsole, buttons)`.

5. **`MainWindow.java`:**
   - Tạo `JTabbedPane`, add lần lượt 3 tab.
   - Phía dưới cùng có thanh status bar nhỏ hiển thị thư mục làm việc hiện tại và thông báo sẵn sàng.

6. **`Main.java`:**
   - Hàm `public static void main(String[] args)`:
     ```java
     UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
     SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
     ```

---

## 5. Quy Trình Kiểm Thử Xác Minh Sau Khi Hoàn Thành

1. **Biên dịch & đóng gói:**
   - Chạy `.\build-setup.ps1` -> Kiểm tra tạo ra `SetupApp.jar` ở root.
2. **Kiểm tra Git:**
   - Chạy `git status` -> Đảm bảo `src/envsetup/` **không** hiện diện.
   - Đảm bảo `SetupApp.jar` và `Setup.bat` hiện diện ở mục Untracked để sẵn sàng commit.
3. **Chạy thử Portable:**
   - Bấm đúp vào `Setup.bat` -> Mở ứng dụng ngay tức thì, giao diện chuẩn đẹp.
4. **Kiểm tra từng nút:**
   - Tab 1: Cài đặt môi trường, Chạy MySQL, Dừng MySQL.
   - Tab 2: Chạy source billing, Dừng source billing.
   - Tab 3: Tạo DB, Seed mẫu, Chạy 03_queries, Xoá DB.
