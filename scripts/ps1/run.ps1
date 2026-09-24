# =====================================================================
#  run.ps1 -- Biên dịch rồi chạy ứng dụng Java
# ---------------------------------------------------------------------
#  Hỗ trợ linh hoạt:
#  - Quét tất cả file .java trong src\ (tự động loại trừ src\envsetup)
#  - Tự động nhận diện hoặc đọc app.main.class từ config.properties
#  - Tự động tắt tiến trình cũ để tránh bị khóa file thư mục out\
# =====================================================================

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $projectRoot

# --- 0. Kiểm tra điều kiện tiên quyết --------------------------------
if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
    Write-Host "Chưa có JDK. Chạy:  winget install EclipseAdoptium.Temurin.21.JDK" -ForegroundColor Red
    Write-Host "Cài xong nhớ MỞ LẠI terminal." -ForegroundColor Red
    exit 1
}
$jar = Get-ChildItem (Join-Path $projectRoot 'lib\mysql-connector-j-*.jar') -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $jar) {
    Write-Host "Thiếu driver MySQL trong lib\. Chạy:  .\scripts\ps1\setup.ps1" -ForegroundColor Red
    exit 1
}

# --- 1. Đọc cấu hình Main Class --------------------------------------
$mainClass = $null
$configPath = Join-Path $projectRoot 'config.properties'
if (Test-Path $configPath) {
    Get-Content $configPath | ForEach-Object {
        if ($_ -match '^\s*app\.main\.class\s*=\s*(.+)$') {
            $mainClass = $matches[1].Trim()
        }
    }
}

# --- 2. Dọn dẹp tiến trình cũ để tránh lock file out\ ----------------
$billingStop = Join-Path $PSScriptRoot 'billing-stop.ps1'
if (Test-Path $billingStop) {
    & $billingStop
}

# --- 3. Quét danh sách file .java trong src\ -------------------------
Write-Host "Đang quét mã nguồn trong src\..." -ForegroundColor Cyan
$srcDir = Join-Path $projectRoot 'src'
$javaFiles = Get-ChildItem -Path $srcDir -Recurse -Filter '*.java' -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -notmatch '[\\/]envsetup[\\/]' }

if (-not $javaFiles -or $javaFiles.Count -eq 0) {
    Write-Host "[LỖI] Không tìm thấy file mã nguồn .java nào trong src\ (đã loại trừ src\envsetup)." -ForegroundColor Red
    Write-Host "Hãy tạo các file .java của dự án trong src\ trước khi chạy." -ForegroundColor Yellow
    exit 1
}

# Tự động đoán main class nếu chưa được cấu hình
if ([string]::IsNullOrWhiteSpace($mainClass)) {
    $mainFile = $javaFiles | Where-Object { $_.Name -like '*Main.java' } | Select-Object -First 1
    if ($mainFile) {
        $pkgLine = Get-Content $mainFile.FullName | Where-Object { $_ -match '^\s*package\s+([^;]+);' } | Select-Object -First 1
        $baseName = [System.IO.Path]::GetFileNameWithoutExtension($mainFile.Name)
        if ($pkgLine -and ($pkgLine -match '^\s*package\s+([^;]+);')) {
            $mainClass = "$($matches[1].Trim()).$baseName"
        } else {
            $mainClass = $baseName
        }
    } else {
        $mainClass = 'billing.Main'
    }
}
Write-Host "Lớp khởi chạy chính (Main Class): $mainClass" -ForegroundColor DarkGray

# --- 4. Biên dịch ----------------------------------------------------
Write-Host "Đang biên dịch $($javaFiles.Count) file mã nguồn..." -ForegroundColor Cyan
$outDir = Join-Path $projectRoot 'out'
if (Test-Path $outDir) {
    Get-ChildItem $outDir -Recurse | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
} else {
    New-Item -ItemType Directory $outDir | Out-Null
}

# Dùng đường dẫn tương đối để tránh lỗi ký tự đặc biệt / dấu tiếng Việt
$sources = Join-Path $env:TEMP 'billing-sources.txt'
$sourceLines = $javaFiles | ForEach-Object {
    $rel = $_.FullName.Substring($projectRoot.Length).TrimStart('\', '/') -replace '\\', '/'
    "`"$rel`""
}

# Ghi UTF-8 KHONG BOM: Set-Content -Encoding UTF8 cua Windows PowerShell 5.1
# them BOM vao dau file, javac doc BOM nhu mot phan cua ten file dau tien
# roi bao loi: file not found: ?src/billing/Main.java
[System.IO.File]::WriteAllLines($sources, [string[]]$sourceLines, (New-Object System.Text.UTF8Encoding $false))

& javac -encoding UTF-8 -d out -cp "lib\*" "@$sources"
if ($LASTEXITCODE -ne 0) {
    Write-Host "[LỖI] Biên dịch thất bại." -ForegroundColor Red
    exit 1
}
Write-Host "[OK] Biên dịch xong vào out\" -ForegroundColor Green

# --- 5. Khởi chạy -----------------------------------------------------
Write-Host "Đang khởi động app ($mainClass)..." -ForegroundColor Cyan
& java "-Dfile.encoding=UTF-8" -cp "out;lib\*" $mainClass
exit $LASTEXITCODE
