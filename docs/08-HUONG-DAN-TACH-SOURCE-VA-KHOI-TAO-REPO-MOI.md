# Hướng Dẫn Tách Source Sang `D:\BillingManagement`, Khởi Tạo Git Repo Mới & Giữ Nguyên Môi Trường Setup Portable

> **Mục tiêu:** Hướng dẫn chi tiết cách tách mã nguồn từ dự án `DemoBillingManagement` sang thư mục mới `D:\BillingManagement`, khởi tạo một Git repository độc lập hoàn toàn, nhưng **kế thừa và giữ nguyên 100% môi trường setup tiện lợi** (MySQL Portable, JVM check, JDBC Driver, bộ script PowerShell, công cụ GUI Setup App, cấu trúc SQL & cấu hình).

---

## 1. Bối Cảnh & Mục Tiêu Kiến Trúc

Trong dự án `DemoBillingManagement`, bạn đã xây dựng một hệ sinh thái môi trường phát triển (Dev Environment) rất hoàn thiện và tiện lợi:
- **Hệ thống Script PowerShell:** Tự động hóa kiểm tra JDK, tải JDBC driver, bật/tắt MySQL Server, khởi tạo database 15 bảng, nạp seed data và biên dịch chạy app chỉ bằng 1 dòng lệnh.
- **Ứng dụng GUI Setup App (`Setup.bat` / `SetupApp.jar`):** Giao diện đồ họa Swing portable cho phép người dùng click đúp là mở, quản lý toàn bộ hệ thống bằng các nút bấm trực quan kèm bảng log thời gian thực.
- **Tập lệnh SQL chuẩn hóa:** Toàn bộ schema, routines, seed data đặt trong `sql/`.
- **Driver JDBC sẵn có:** Đặt trong thư mục `lib/`.

Khi nhóm quyết định **viết lại mã nguồn (rewrite source) cho phần Java SQL** tại thư mục mới `D:\BillingManagement`:
- Bạn **KHÔNG CẦN** và **KHÔNG NÊN** thiết lập lại các công cụ môi trường từ đầu.
- Bạn cần một Git repository mới tinh để theo dõi lịch sử phát triển của dự án chính thức (không bị lẫn commit của bài demo cũ).
- Bạn cần đảm bảo các script chạy (`run.ps1`, `billing-stop.ps1`, `SetupApp.jar`) có thể **thích ứng linh hoạt** với cấu trúc mã nguồn mới mà không bị lỗi hardcoded.

---

## 2. Phân Tích Chuyên Sâu: TẠI SAO NÊN LÀM NHƯ VẬY? (The "Why")

Trước khi bắt tay vào thao tác, việc hiểu rõ bản chất của từng thành phần sẽ giúp bạn tránh được những lỗi xung đột nghiêm trọng:

### 2.1. Tại sao TUYỆT ĐỐI KHÔNG copy thư mục `.git` sang `D:\BillingManagement`?
- **Nguyên lý của Git:** Thư mục ẩn `.git` chứa toàn bộ cơ sở dữ liệu của repository: lịch sử tất cả các commit, các nhánh (branches), thông tin tác giả, và quan trọng nhất là **Git Remote URL** (`origin` đang trỏ tới `git@github.com:CozyCode890/DemoBillingManagement.git`).
- **Hậu quả nếu copy `.git`:**
  1. Repo mới sẽ bị "ô nhiễm" bởi toàn bộ commit cũ của bản demo, làm mất tính chuyên nghiệp của dự án mới.
  2. Nguy cơ vô tình gõ lệnh `git push` khiến code mới bị đẩy đè lên repository demo trên GitHub, gây mất mát hoặc xung đột dữ liệu.
- **Quy tắc chuẩn:** Khởi tạo Git mới bằng `git init` tại `D:\BillingManagement`, tạo một commit ban đầu (Initial commit) sạch sẽ và kết nối tới URL GitHub repository mới của dự án chính thức.

### 2.2. Tại sao phải giữ lại nguyên vẹn `lib/`, `SetupApp.jar`, `Setup.bat` và các file `.ps1`?
- **Tính chất "Portable" (Bấm là chạy):** Nhóm của bạn hoặc thầy cô chấm bài không cần phải cài đặt phức tạp, không cần cấu hình Maven/Gradle hay biến môi trường phức tạp trên Windows.
- Thư mục `lib/` chứa sẵn `mysql-connector-j-8.4.0.jar`. Nếu thiếu file này, Java sẽ lập tức báo lỗi kinh điển `java.lang.ClassNotFoundException: com.mysql.cj.jdbc.Driver`.
- Các file script `.ps1` đóng vai trò là một "Lightweight Build Tool": biên dịch javac siêu tốc với cờ mã hóa UTF-8, gom file tự động, và quản lý tiến trình.
- `SetupApp.jar` được biên dịch sẵn bằng Java Swing thuần. Khi giữ lại file này và `Setup.bat`, các thành viên trong nhóm chỉ cần click đúp là có ngay giao diện quản lý tiện lợi.

### 2.3. Tại sao giữ nguyên cấu trúc `sql/` và `config.properties`?
- Database `retail_billing` gồm 15 bảng chuẩn hóa 3NF, các Stored Procedures và Views trong `sql/` là tài sản dữ liệu dùng chung cho logic Java SQL mới.
- `config.properties` tách biệt hoàn toàn thông tin nhạy cảm (User, Password, Port, DB Name) ra khỏi code Java. Khi ai đó trong nhóm đổi mật khẩu MySQL từ `root` sang mật khẩu khác, họ chỉ cần sửa 1 dòng trong file này mà **không cần biên dịch lại Java**.

### 2.4. Mã nguồn GUI `src/envsetup` xử lý như thế nào?
- Trong thư mục `src/` hiện tại có 2 phần:
  1. `src/billing/`: Mã nguồn nghiệp vụ cũ (phần bạn định viết lại).
  2. `src/envsetup/`: Mã nguồn viết ứng dụng GUI `SetupApp.jar`.
- File `.gitignore` hiện tại đã chặn `src/envsetup/` để không đẩy code này lên Git public (chỉ đẩy `SetupApp.jar` và `Setup.bat`).
- **Khuyến nghị:** Khi chuyển sang `D:\BillingManagement`, bạn **vẫn nên copy `src/envsetup/`** sang nếu sau này bạn muốn bổ sung thêm tính năng cho giao diện GUI (bằng script `build-setup.ps1`). Đồng thời, giữ nguyên dòng `src/envsetup/` trong `.gitignore` của repo mới.

---

## 3. Bảng Phân Loại Thành Phần Khi Di Chuyển

| Thành phần | Đường dẫn | Có Copy sang `D:\BillingManagement`? | Lý do |
| :--- | :--- | :---: | :--- |
| **Thư mục Git** | `.git/` | ❌ **KHÔNG** | Phải tạo Git mới tinh bằng `git init` để có lịch sử commit sạch, không bị dính remote cũ. |
| **Build output** | `out/`, `target/` | ❌ **KHÔNG** | File nhị phân `.class` biên dịch tạm, sẽ được tự động sinh ra khi chạy script. |
| **Cấu hình IDE** | `.idea/`, `.vscode/` | ❌ **KHÔNG** | Tùy thuộc cấu hình máy cá nhân, không nên đem sang. |
| **Thư viện JDBC** | `lib/` | ✅ **CÓ** | Chứa driver kết nối MySQL `mysql-connector-j-8.4.0.jar`. |
| **Tập lệnh SQL** | `sql/` | ✅ **CÓ** | Chứa schema 15 bảng, seed data, queries và stored procedures. |
| **Tài liệu** | `docs/` | ✅ **CÓ** | Tài liệu kiến trúc 3NF, tài liệu JDBC, hướng dẫn convention. |
| **File cấu hình** | `config.properties` | ✅ **CÓ** | Chứa cấu hình kết nối DB (URL, user, password, app.main.class). |
| **File mô tả Maven**| `pom.xml` | ✅ **CÓ** | Dành cho thành viên nào thích mở bằng IntelliJ/Eclipse. |
| **Bộ script PS1** | `*.ps1` | ✅ **CÓ** | Toàn bộ engine tự động hóa (setup, start/stop MySQL, chạy DB, run app). |
| **Ứng dụng GUI** | `Setup.bat`, `SetupApp.jar` | ✅ **CÓ** | Công cụ GUI tiện ích click đúp chạy ngay. |
| **Mã nguồn GUI** | `src/envsetup/` | ✅ **CÓ (Khuyên dùng)** | Giữ lại để có thể dùng `build-setup.ps1` sửa giao diện GUI khi cần. |
| **Mã nguồn cũ** | `src/billing/` | ⚠️ **TÙY CHỌN** | Nếu viết lại từ đầu: tạo `src/<package_mới>` rỗng. Nếu muốn tham khảo: copy sang rồi refactor dần. |
| **File Git ignore** | `.gitignore` | ✅ **CÓ** | Giúp repo mới tự động loại bỏ các file rác, file `.class` và file tạm. |

---

## 4. Kiểm Tra & Nâng Cấp `run.ps1` Để Thích Ứng Source Mới

### 4.1. Hiện trạng và hạn chế của `run.ps1` cũ
Nếu mở file `run.ps1` cũ, bạn sẽ thấy 2 điểm bị **hardcode cứng**:
```powershell
# Điểm 1: Chỉ tìm file trong đúng thư mục 'src\billing'
Get-ChildItem -Path '.\src\billing' -Recurse -Filter '*.java' | ...

# Điểm 2: Chỉ chạy đúng lớp 'billing.Main'
& java "-Dfile.encoding=UTF-8" -cp "out;lib\*" billing.Main
```

**Hậu quả nếu viết source mới:**
1. Nếu bạn đổi tên package (ví dụ `com.billing`, `app.billing`, `billingmanagement`) hoặc chia thành nhiều package ngang hàng trong `src/`, `run.ps1` cũ sẽ **không tìm thấy bất kỳ file `.java` nào**, dẫn đến lỗi biên dịch rỗng `javac: no source files`.
2. Nếu bạn đổi tên file chứa hàm `main` (ví dụ `com.billing.App`, `Main`, `Application`), lệnh chạy sẽ văng lỗi `Error: Could not find or load main class billing.Main`.
3. Nếu ứng dụng cũ đang mở mà bạn bấm chạy lại, lệnh xóa thư mục `out` sẽ bị Windows chặn (`Access Denied` do tiến trình Java đang khóa file `.class`).

### 4.2. Giải pháp nâng cấp toàn diện cho `run.ps1`
File `run.ps1` cần được cải tiến với 4 tính năng thông minh:
1. **Quét linh hoạt mọi package trong `src/`:** Quét toàn bộ `.\src\*.java` nhưng tự động **loại trừ** thư mục `src\envsetup` (để tránh biên dịch nhầm mã nguồn của GUI Setup App vào ứng dụng chính).
2. **Cấu hình Main class qua `config.properties`:** Đọc thuộc tính `app.main.class`. Ví dụ:
   ```properties
   app.main.class=com.billing.Main
   ```
   Nếu người dùng không khai báo trong `config.properties`, script sẽ tự động tìm kiếm file có tên `*Main.java` trong `src/`, hoặc fallback an toàn về `billing.Main`.
3. **Sử dụng đường dẫn tương đối (Relative Path) chuẩn hóa:** Tránh tuyệt đối lỗi khi thư mục dự án chứa dấu cách hoặc dấu tiếng Việt.
4. **Tự động dọn dẹp tiến trình cũ trước khi biên dịch:** Gọi cơ chế tương tự `billing-stop.ps1` để tắt tiến trình Java cũ nếu đang chạy, tránh lỗi xung đột khóa file trong thư mục `out\`.

*(Chi tiết mã nguồn file `run.ps1` và `billing-stop.ps1` đã cải tiến được trình bày chi tiết ở Mục 6 bên dưới).*

---

## 5. Hướng Dẫn Từng Bước Thực Hành (Step-by-Step)

Dưới đây là 2 cách thực hiện: **Cách 1 (Tự động bằng PowerShell)** nhanh gọn và chính xác nhất; **Cách 2 (Thủ công)** nếu bạn muốn tự tay kiểm soát từng bước.

### Cách 1: Tự động sao chép bằng PowerShell (Khuyên dùng)

Mở cửa sổ **PowerShell** tại thư mục `d:\DemoBillingManagement` và chạy khối lệnh sau:

```powershell
# 1. Định nghĩa thư mục nguồn và thư mục đích
$src = "D:\DemoBillingManagement"
$dst = "D:\BillingManagement"

# 2. Tạo thư mục đích nếu chưa có
if (-not (Test-Path $dst)) {
    New-Item -ItemType Directory -Path $dst -Force | Out-Null
    Write-Host "[OK] Đã tạo thư mục mới: $dst" -ForegroundColor Green
}

# 3. Sao chép các thư mục hạ tầng cốt lõi (Bỏ qua .git, out, target)
$folders = @('sql', 'lib', 'docs')
foreach ($f in $folders) {
    if (Test-Path "$src\$f") {
        Copy-Item -Path "$src\$f" -Destination "$dst\$f" -Recurse -Force
        Write-Host "[OK] Đã copy thư mục: $f" -ForegroundColor Green
    }
}

# 4. Sao chép các file cấu hình và script PowerShell ở thư mục gốc
Get-ChildItem -Path $src -File | Where-Object {
    $_.Name -match '\.(ps1|bat|jar|properties|xml|md)$' -or $_.Name -eq '.gitignore'
} | ForEach-Object {
    Copy-Item -Path $_.FullName -Destination $dst -Force
    Write-Host "[OK] Đã copy file: $($_.Name)" -ForegroundColor Green
}

# 5. Tạo thư mục src mới và copy mã nguồn envsetup (GUI)
New-Item -ItemType Directory -Path "$dst\src" -Force | Out-Null
if (Test-Path "$src\src\envsetup") {
    Copy-Item -Path "$src\src\envsetup" -Destination "$dst\src\envsetup" -Recurse -Force
    Write-Host "[OK] Đã copy mã nguồn GUI src/envsetup" -ForegroundColor Green
}

Write-Host "`n>>> HOÀN TẤT SAO CHÉP MÔI TRƯỜNG SANG $dst <<<" -ForegroundColor Cyan
```

Sau khi chạy xong, chuyển sang mục **5.2 Khởi tạo Git Repo Mới**.

---

### Cách 2: Thực hiện thủ công từng bước

#### Bước 5.1: Chuẩn bị thư mục và sao chép file
1. Mở File Explorer trên Windows, vào ổ `D:\` và tạo thư mục mới đặt tên là: `BillingManagement`.
2. Mở thư mục cũ `D:\DemoBillingManagement`, chọn và copy các mục sau sang `D:\BillingManagement`:
   - Thư mục `sql\`
   - Thư mục `lib\`
   - Thư mục `docs\`
   - Các file script: `setup.ps1`, `mysql-start.ps1`, `mysql-stop.ps1`, `sql-create-db.ps1`, `sql-seed-data.ps1`, `sql-run-queries.ps1`, `sql-drop-db.ps1`, `run.ps1`, `billing-stop.ps1`, `build-setup.ps1`
   - File chạy GUI: `Setup.bat`, `SetupApp.jar`
   - File cấu hình: `config.properties`, `pom.xml`, `README.md`, `.gitignore`
3. Tại `D:\BillingManagement`, tạo thư mục `src`.
4. Copy thư mục `src\envsetup\` từ demo cũ vào `D:\BillingManagement\src\envsetup\` (để giữ công cụ build GUI).

---

#### Bước 5.2: Khởi tạo Git Repository mới cho `D:\BillingManagement`

Mở terminal PowerShell tại `D:\BillingManagement` và thực hiện tuần tự:

```powershell
Set-Location "D:\BillingManagement"

# 1. Khởi tạo Git repository mới
git init

# 2. Đổi tên nhánh mặc định thành 'main' (chuẩn GitHub)
git branch -M main

# 3. Kiểm tra trạng thái các file
git status
```

> [!NOTE]
> Bạn sẽ thấy các file `.ps1`, `sql/`, `lib/`, `SetupApp.jar`, `Setup.bat` xuất hiện trong danh sách Untracked files. Riêng thư mục `src/envsetup/` sẽ **không xuất hiện** vì đã được định nghĩa trong `.gitignore`. Đây chính xác là thiết kế mong muốn!

Tiếp theo, tạo commit đầu tiên cho bộ khung dự án:

```powershell
# 4. Thêm toàn bộ khung môi trường vào staging
git add .

# 5. Tạo commit khởi tạo (Initial Commit)
git commit -m "feat: initial commit with portable setup environment, scripts, and SQL schema"

# 6. (Tùy chọn) Kết nối tới GitHub Repository mới của nhóm
# Thay URL bên dưới bằng URL repo thật của bạn trên GitHub:
# git remote add origin git@github.com:<your-org-or-user>/BillingManagement.git
# git push -u origin main
```

---

#### Bước 5.3: Đặt mã nguồn Java SQL mới vào `src/`

Bây giờ bạn bắt đầu viết mã nguồn mới:
- Ví dụ nếu bạn viết theo cấu trúc package chuẩn `com.billing`:
  ```
  D:\BillingManagement\src\
  ├── envsetup\               <-- (Có sẵn, phục vụ GUI)
  └── com\
      └── billing\
          ├── Main.java       <-- Lớp chứa hàm main()
          ├── model\
          ├── dao\
          └── service\
  ```
- Hoặc nếu bạn tiếp tục dùng package `billing` tương đương source cũ:
  ```
  D:\BillingManagement\src\
  ├── envsetup\
  └── billing\
      ├── Main.java
      └── ...
  ```

---

#### Bước 5.4: Khai báo Main Class trong `config.properties`

Mở file `D:\BillingManagement\config.properties`, bạn chỉ cần kiểm tra hoặc thêm dòng sau:

```properties
# Thông tin kết nối MySQL
db.url=jdbc:mysql://localhost:3306/retail_billing?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=UTF-8
db.user=root
db.password=root

# [MỚI] Tên lớp chứa hàm main() của ứng dụng Java mới
# Ví dụ: com.billing.Main hoặc billing.Main
app.main.class=billing.Main
```

---

## 6. Mã Nguồn Nâng Cấp Chuẩn Cho `run.ps1` & `billing-stop.ps1`

Để đảm bảo các file script thích ứng 100% với cả source cũ lẫn source mới (kể cả khi chạy trực tiếp từ PowerShell hoặc chạy thông qua giao diện `SetupApp.jar`), hãy cập nhật nội dung 2 file này:

### 6.1. File `run.ps1` (Nâng cấp)

```powershell
# =====================================================================
#  run.ps1 -- Biên dịch và khởi chạy ứng dụng Java
#  Hỗ trợ linh hoạt mọi cấu trúc package trong src/ (loại trừ envsetup)
# =====================================================================

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

# --- 0. Kiểm tra điều kiện tiên quyết --------------------------------
if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
    Write-Host "Chưa có JDK. Hãy cài: winget install EclipseAdoptium.Temurin.21.JDK" -ForegroundColor Red
    exit 1
}
$jar = Get-ChildItem '.\lib\mysql-connector-j-*.jar' -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $jar) {
    Write-Host "Thiếu driver MySQL trong lib\. Hãy chạy: .\setup.ps1" -ForegroundColor Red
    exit 1
}

# --- 1. Xác định Main Class ------------------------------------------
# Đọc từ config.properties nếu có khai báo app.main.class
$mainClass = $null
if (Test-Path '.\config.properties') {
    Get-Content '.\config.properties' | ForEach-Object {
        if ($_ -match '^\s*app\.main\.class\s*=\s*(.+)$') {
            $mainClass = $matches[1].Trim()
        }
    }
}

# --- 2. Dọn dẹp tiến trình cũ để tránh lock file out\ ----------------
if (Test-Path '.\billing-stop.ps1') {
    & '.\billing-stop.ps1'
}

# --- 3. Thu thập danh sách file mã nguồn .java -----------------------
Write-Host "Đang quét mã nguồn trong src\..." -ForegroundColor Cyan

# Quét tất cả file .java trong src, LOẠI TRỪ thư mục src\envsetup (mã nguồn GUI)
$javaFiles = Get-ChildItem -Path '.\src' -Recurse -Filter '*.java' -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -notmatch '[\\/]envsetup[\\/]' }

if (-not $javaFiles -or $javaFiles.Count -eq 0) {
    Write-Host "[LỖI] Không tìm thấy file mã nguồn .java nào trong src\ (đã loại trừ src\envsetup)." -ForegroundColor Red
    Write-Host "Hãy tạo các file .java của dự án trong src\ trước khi chạy." -ForegroundColor Yellow
    exit 1
}

# Tự động đoán main class nếu chưa được chỉ định trong config.properties
if ([string]::IsNullOrWhiteSpace($mainClass)) {
    $mainFile = $javaFiles | Where-Object { $_.Name -like '*Main.java' } | Select-Object -First 1
    if ($mainFile) {
        # Lấy package name từ nội dung file
        $pkgLine = Get-Content $mainFile.FullName | Where-Object { $_ -match '^\s*package\s+([^;]+);' } | Select-Object -First 1
        $baseName = [System.IO.Path]::GetFileNameWithoutExtension($mainFile.Name)
        if ($pkgLine -and ($pkgLine -match '^\s*package\s+([^;]+);')) {
            $mainClass = "$($matches[1].Trim()).$baseName"
        } else {
            $mainClass = $baseName
        }
    } else {
        $mainClass = 'billing.Main' # Fallback mặc định
    }
}
Write-Host "Lớp khởi chạy chính (Main Class): $mainClass" -ForegroundColor DarkGray

# --- 4. Biên dịch mã nguồn -------------------------------------------
Write-Host "Đang biên dịch $($javaFiles.Count) file Java..." -ForegroundColor Cyan
$outDir = Join-Path $PSScriptRoot 'out'
if (Test-Path $outDir) {
    Get-ChildItem $outDir -Recurse | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
} else {
    New-Item -ItemType Directory $outDir | Out-Null
}

# Sử dụng đường dẫn tương đối để tránh lỗi ký tự đặc biệt / dấu tiếng Việt
$sourcesFile = Join-Path $env:TEMP 'billing-sources.txt'
$javaFiles | ForEach-Object {
    $rel = $_.FullName.Substring($PSScriptRoot.Length).TrimStart('\', '/') -replace '\\', '/'
    "`"$rel`""
} | Set-Content -Path $sourcesFile -Encoding UTF8

& javac -encoding UTF-8 -d out -cp "lib\*" "@$sourcesFile"
if ($LASTEXITCODE -ne 0) {
    Write-Host "[LỖI] Biên dịch thất bại." -ForegroundColor Red
    exit 1
}
Write-Host "[OK] Biên dịch thành công vào thư mục out\" -ForegroundColor Green

# --- 5. Khởi chạy ứng dụng -------------------------------------------
Write-Host "Đang khởi động $mainClass..." -ForegroundColor Cyan
& java "-Dfile.encoding=UTF-8" -cp "out;lib\*" $mainClass
```

---

### 6.2. File `billing-stop.ps1` (Nâng cấp)

```powershell
# =====================================================================
#  billing-stop.ps1 -- Dừng an toàn tiến trình Java của ứng dụng
# =====================================================================

$ErrorActionPreference = 'SilentlyContinue'
$OutputEncoding = [Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# Lấy tên main class từ config.properties nếu có
$targetClass = 'billing.Main'
if (Test-Path '.\config.properties') {
    Get-Content '.\config.properties' | ForEach-Object {
        if ($_ -match '^\s*app\.main\.class\s*=\s*(.+)$') {
            $targetClass = $matches[1].Trim()
        }
    }
}

Write-Host "Đang kiểm tra tiến trình ứng dụng Java ($targetClass)..." -ForegroundColor Cyan

# Tìm các tiến trình Java đang chạy từ thư mục out hoặc chứa tên main class
$procs = Get-CimInstance Win32_Process | Where-Object {
    $_.Name -eq 'java.exe' -and (
        $_.CommandLine -like "*$targetClass*" -or
        $_.CommandLine -like "*out;lib\**"
    )
}

if ($procs) {
    foreach ($p in $procs) {
        Stop-Process -Id $p.ProcessId -Force
        Write-Host "[OK] Đã dừng tiến trình Java (PID $($p.ProcessId))." -ForegroundColor Green
    }
} else {
    Write-Host "Không có tiến trình ứng dụng nào đang chạy." -ForegroundColor DarkGray
}
```

---

## 7. Quy Trình Kiểm Thử Hoạt Động (Post-Migration Verification)

Sau khi hoàn thành sao chép sang `D:\BillingManagement`, bạn thực hiện kiểm tra theo checklist sau:

1. **Kiểm tra công cụ GUI Portable:**
   - Bấm đúp chuột vào `Setup.bat`.
   - Cửa sổ **Setup App (Swing GUI)** mở lên với 3 tab: *Chung*, *Java*, *SQL*.
2. **Kiểm tra MySQL Server (Tab Chung):**
   - Bấm nút `[▶️ Chạy SQL Server]` -> Kiểm tra log báo MySQL khởi động thành công trên cổng 3306.
3. **Kiểm tra Tạo Database (Tab SQL):**
   - Bấm `[🏗️ Chạy tạo database]` -> Bảng log chạy file `01_schema.sql` và `04_routines.sql`, tạo xong 15 bảng.
   - Bấm `[🌱 Tạo dữ liệu mẫu]` -> Bảng log nạp dữ liệu từ `02_seed.sql`.
4. **Kiểm tra Biên Dịch & Chạy Source Mới (Tab Java hoặc Terminal):**
   - Đặt source code mới vào `src/`.
   - Nếu đổi tên class chính, cập nhật `app.main.class` trong `config.properties`.
   - Bấm nút `[▶️ Chạy source billing]` trên GUI hoặc gõ `.\run.ps1` trong PowerShell.
   - Ứng dụng biên dịch thành công và giao diện phần mềm bán hàng xuất hiện!
5. **Kiểm tra Git Repository:**
   - Gõ `git status`: chỉ thấy các file hợp lệ, không có rác `out/` hay mã nguồn GUI `src/envsetup/`.
   - Gõ `git log`: chỉ thấy commit của bạn tại dự án mới, hoàn toàn tách biệt khỏi repo demo.
