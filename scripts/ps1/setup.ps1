# =====================================================================
#  setup.ps1 -- tải thư viện Java cần thiết (chỉ cần chạy 1 lần)
# ---------------------------------------------------------------------
#  Tải về:  mysql-connector-j-8.4.0.jar   (~2.4 MB)
#           zxing-core-3.5.0.jar          (~0.6 MB)
#           zxing-javase-3.5.0.jar        (~0.04 MB)
#  Nguồn :  Maven Central (repo1.maven.org) -- kho thư viện Java chính thức
#  Lưu vào: .\lib\
#
#  Driver JDBC là phần phiên dịch giữa Java và MySQL. Không có nó thì
#  Java báo lỗi "No suitable driver found".
#  ZXing dùng để sinh mã QR VietQR (billing.util.VietQRHelper). Không có
#  nó thì javac báo lỗi "package com.google.zxing does not exist".
# =====================================================================

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $projectRoot

$version = '8.4.0'
$jarName = "mysql-connector-j-$version.jar"
$url     = "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/$version/$jarName"
$dest    = Join-Path $projectRoot "lib\$jarName"

if (-not (Test-Path '.\lib')) { New-Item -ItemType Directory '.\lib' | Out-Null }

if (Test-Path $dest) {
    Write-Host "[OK] Da co san: $dest" -ForegroundColor Green
} else {
    Write-Host "Dang tai $jarName tu Maven Central..." -ForegroundColor Cyan
    Invoke-WebRequest -Uri $url -OutFile $dest -UseBasicParsing
    $mb = [math]::Round((Get-Item $dest).Length / 1MB, 2)
    Write-Host "[OK] Da tai xong ($mb MB): $dest" -ForegroundColor Green
}

# --- Tai ZXing Core & Javase (sinh ma QR VietQR) ----------------------
$zxingVersion = '3.5.0'
foreach ($artifact in @('core', 'javase')) {
    $zxJar  = "zxing-$artifact-$zxingVersion.jar"
    $zxUrl  = "https://repo1.maven.org/maven2/com/google/zxing/$artifact/$zxingVersion/$artifact-$zxingVersion.jar"
    $zxDest = Join-Path $projectRoot "lib\$zxJar"

    # Chap nhan ca ten da tai truoc do khong co tien to "zxing-".
    $existing = Get-ChildItem (Join-Path $projectRoot "lib\*$artifact-$zxingVersion.jar") -ErrorAction SilentlyContinue |
                Select-Object -First 1
    if ($existing) {
        Write-Host "[OK] Da co san: $($existing.FullName)" -ForegroundColor Green
    } else {
        Write-Host "Dang tai $zxJar tu Maven Central..." -ForegroundColor Cyan
        Invoke-WebRequest -Uri $zxUrl -OutFile $zxDest -UseBasicParsing
        $mb = [math]::Round((Get-Item $zxDest).Length / 1MB, 2)
        Write-Host "[OK] Da tai xong ($mb MB): $zxDest" -ForegroundColor Green
    }
}

# --- Kiem tra JDK -----------------------------------------------------
$javac = Get-Command javac -ErrorAction SilentlyContinue
if ($javac) {
    Write-Host "[OK] Tim thay JDK: $((javac -version 2>&1))" -ForegroundColor Green
} else {
    Write-Host "[!!] Chua co JDK. Cai bang lenh:" -ForegroundColor Yellow
    Write-Host "     winget install EclipseAdoptium.Temurin.21.JDK" -ForegroundColor Yellow
    Write-Host "     (cai xong nho MO LAI cua so terminal)" -ForegroundColor Yellow
}

# --- Kiem tra MySQL ---------------------------------------------------
$portable = Join-Path $env:USERPROFILE 'mysql8\mysql-8.0.45-winx64\bin\mysql.exe'
$mysql = (Get-Command mysql -ErrorAction SilentlyContinue).Source
if (-not $mysql -and (Test-Path $portable)) { $mysql = $portable }

if ($mysql) {
    Write-Host "[OK] Tim thay MySQL client: $mysql" -ForegroundColor Green
    if (Get-Process mysqld -ErrorAction SilentlyContinue) {
        Write-Host "[OK] Server MySQL dang chay." -ForegroundColor Green
    } else {
        Write-Host "[!!] Server chua chay. Chay .\scripts\ps1\mysql-start.ps1 hoac dung Setup.bat" -ForegroundColor Yellow
    }
} else {
    Write-Host "[!!] Chua thay MySQL. Xem muc 'Cai lai tu dau' trong README.md" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Buoc tiep theo:  .\scripts\ps1\mysql-start.ps1  ->  .\scripts\ps1\sql-create-db.ps1  ->  .\scripts\ps1\run.ps1" -ForegroundColor Cyan

exit 0
