# =====================================================================
#  run.ps1 -- biên dịch rồi chạy app
# ---------------------------------------------------------------------
#  Hai lệnh cốt lõi bên dưới chính là toàn bộ "build system" của project
#  này. Không Maven, không Gradle, chỉ javac và java:
#
#     javac -encoding UTF-8 -d out  -cp "lib\*"       (danh sach file .java)
#     java  -cp "out;lib\*"  billing.Main
#
#  Dấu ; trong classpath là của Windows (Linux/Mac dùng dấu :).
#  "lib\*" nghĩa là "tất cả file .jar trong thư mục lib".
# =====================================================================

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

# --- 0. Kiem tra dieu kien -------------------------------------------
if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
    Write-Host "Chua co JDK. Chay:  winget install EclipseAdoptium.Temurin.21.JDK" -ForegroundColor Red
    Write-Host "Cai xong nho MO LAI terminal." -ForegroundColor Red
    exit 1
}
$jar = Get-ChildItem '.\lib\mysql-connector-j-*.jar' -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $jar) {
    Write-Host "Thieu driver MySQL trong lib\. Chay:  .\setup.ps1" -ForegroundColor Red
    exit 1
}

# --- 1. Bien dich -----------------------------------------------------
Write-Host "Dang bien dich..." -ForegroundColor Cyan
$outDir = Join-Path $PSScriptRoot 'out'
if (Test-Path $outDir) { Get-ChildItem $outDir -Recurse | Remove-Item -Recurse -Force }
else { New-Item -ItemType Directory $outDir | Out-Null }

# Gom duong dan tat ca file .java vao mot file tam, roi dua cho javac
# bang cu phap @file (tranh loi "dong lenh qua dai").
#
# LUU Y: trong argfile cua javac, dau \ la ky tu ESCAPE, khong phai dau
# phan cach thu muc. Neu ghi thang "C:\src\Main.java" thi javac doc ra
# "C:srcMain.java" va bao khong tim thay file. Vi vay phai doi \ thanh /.
$sources = Join-Path $env:TEMP 'billing-sources.txt'
Get-ChildItem -Path '.\src' -Recurse -Filter '*.java' |
    ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' } |
    Set-Content -Path $sources -Encoding ASCII

& javac -encoding UTF-8 -d out -cp "lib\*" "@$sources"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Bien dich that bai." -ForegroundColor Red
    exit 1
}
Write-Host "[OK] Bien dich xong." -ForegroundColor Green

# --- 2. Chay ----------------------------------------------------------
Write-Host "Dang khoi dong app..." -ForegroundColor Cyan
& java "-Dfile.encoding=UTF-8" -cp "out;lib\*" billing.Main
