# =====================================================================
#  build-launcher.ps1 -- dong goi Setup.exe (launcher 1-click Windows)
# ---------------------------------------------------------------------
#  Chi NGUOI BAO TRI du an can chay script nay. Nguoi dung binh thuong
#  clone repo ve la co san Setup.exe, bam dup chay luon.
#
#  Script lam 2 viec:
#    1. Sinh icon  -> tools\launcher\Setup.ico  va
#                     Setup.app\Contents\Resources\Setup.icns  (cho macOS)
#    2. Bien dich  -> Setup.exe  tu tools\launcher\WindowsLauncher.cs
#
#  Khong can cai them gi: csc.exe (trinh bien dich C#) nam san trong
#  Windows tu ban 10 tro di, duong dan C:\Windows\Microsoft.NET\...
#
#  Tham so:
#    -Force   sinh lai icon du da co san (mac dinh bo qua de tranh
#             thay doi vo nghia trong git)
# =====================================================================

[CmdletBinding()]
param(
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $projectRoot

$launcherDir = Join-Path $projectRoot 'tools\launcher'
$sourceFile  = Join-Path $launcherDir 'WindowsLauncher.cs'
$manifest    = Join-Path $launcherDir 'app.manifest'
$iconFile    = Join-Path $launcherDir 'Setup.ico'
$icnsFile    = Join-Path $projectRoot 'Setup.app\Contents\Resources\Setup.icns'
$exeFile     = Join-Path $projectRoot 'Setup.exe'

foreach ($required in @($sourceFile, $manifest)) {
    if (-not (Test-Path $required)) {
        Write-Host "[LOI] Thieu file nguon: $required" -ForegroundColor Red
        exit 1
    }
}

# --- Buoc 0: kiem tra manifest la XML hop le -------------------------
# csc.exe nhung manifest NGUYEN XI, khong he kiem tra cu phap. Manifest
# hong thi exe van bien dich thanh cong nhung bam dup se bao
# "side-by-side configuration is incorrect" -- rat kho doan nguyen nhan.
# Loi hay gap nhat: hai dau gach ngang lien nhau trong comment XML.
try {
    [xml](Get-Content -Path $manifest -Raw -Encoding UTF8) | Out-Null
} catch {
    Write-Host "[LOI] app.manifest khong phai XML hop le:" -ForegroundColor Red
    Write-Host "      $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "      (XML cam hai dau gach ngang lien nhau ben trong comment)" -ForegroundColor Yellow
    exit 1
}

# --- Buoc 1: sinh icon ------------------------------------------------
$needIcons = $Force -or -not (Test-Path $iconFile) -or -not (Test-Path $icnsFile)

if ($needIcons) {
    if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
        Write-Host "[LOI] Can JDK de sinh icon (tools\launcher\IconGen.java)." -ForegroundColor Red
        Write-Host "      Cai bang: winget install EclipseAdoptium.Temurin.21.JDK" -ForegroundColor Yellow
        exit 1
    }

    Write-Host "Dang sinh icon tu IconGen.java..." -ForegroundColor Cyan
    $tempOut = Join-Path ([IO.Path]::GetTempPath()) ("icongen-" + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory $tempOut | Out-Null
    try {
        & javac -encoding UTF-8 -d $tempOut (Join-Path $launcherDir 'IconGen.java')
        if ($LASTEXITCODE -ne 0) { throw "javac that bai khi bien dich IconGen.java" }

        & java -cp $tempOut IconGen $iconFile $icnsFile
        if ($LASTEXITCODE -ne 0) { throw "IconGen that bai" }
    } finally {
        Remove-Item -Recurse -Force $tempOut -ErrorAction SilentlyContinue
    }
} else {
    Write-Host "[BO QUA] Icon da co san (dung -Force de sinh lai)." -ForegroundColor DarkGray
}

# --- Buoc 2: tim csc.exe ---------------------------------------------
# Uu tien ban 64-bit; may 32-bit cu thi lui ve thu muc Framework thuong.
$cscCandidates = @(
    "$env:WINDIR\Microsoft.NET\Framework64\v4.0.30319\csc.exe",
    "$env:WINDIR\Microsoft.NET\Framework\v4.0.30319\csc.exe"
)
$csc = $cscCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1

if (-not $csc) {
    Write-Host "[LOI] Khong tim thay csc.exe cua .NET Framework 4." -ForegroundColor Red
    Write-Host "      Bat .NET Framework 4.8 trong 'Turn Windows features on or off' roi chay lai." -ForegroundColor Yellow
    exit 1
}

# --- Buoc 3: bien dich Setup.exe -------------------------------------
# csc.exe cua .NET Framework khong co tuy chon /deterministic (chi Roslyn
# moi co), nen moi lan bien dich lai deu sinh ra file .exe khac byte du
# ma nguon y nguyen -- vi no nhung GUID va dau thoi gian moi. Setup.exe
# lai duoc commit vao git, nen bien dich thua se tao diff vo nghia.
# Chi bien dich khi that su co file nguon moi hon.
$needCompile = $Force -or -not (Test-Path $exeFile)
if (-not $needCompile) {
    $exeTime = (Get-Item $exeFile).LastWriteTimeUtc
    foreach ($input in @($sourceFile, $manifest, $iconFile)) {
        if ((Get-Item $input).LastWriteTimeUtc -gt $exeTime) {
            $needCompile = $true
            break
        }
    }
}

if ($needCompile) {
    Write-Host "Dang bien dich Setup.exe bang $csc..." -ForegroundColor Cyan

    # /target:winexe  -> khong bao gio hien cua so console den
    # /codepage:65001 -> doc duoc tieng Viet co dau trong ma nguon .cs
    # /win32manifest  -> bat comctl32 v6 (TaskDialog) + DPI aware
    & $csc `
        /nologo `
        /target:winexe `
        /platform:anycpu `
        /optimize+ `
        /warnaserror- `
        /codepage:65001 `
        /win32icon:"$iconFile" `
        /win32manifest:"$manifest" `
        /out:"$exeFile" `
        "$sourceFile"

    if ($LASTEXITCODE -ne 0) {
        Write-Host "Bien dich that bai!" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "[BO QUA] Setup.exe da moi hon ma nguon (dung -Force de bien dich lai)." -ForegroundColor DarkGray
}

# --- Buoc 4: danh dau script macOS la file thuc thi trong git ---------
# Git tren Windows khong giu bit +x cua he thong file NTFS, phai ghi
# thang vao index. Thieu buoc nay thi may Mac clone ve se bam dup
# Setup.app ra loi "khong the mo ung dung".
$macScript = 'Setup.app/Contents/MacOS/Setup'
if ((Test-Path (Join-Path $projectRoot $macScript)) -and (Get-Command git -ErrorAction SilentlyContinue)) {
    # Gom ca stderr vao bien: file chua duoc git theo doi la chuyen binh
    # thuong, khong nen in thong bao loi do git sinh ra ra man hinh.
    $tracked = & git ls-files --error-unmatch $macScript 2>&1
    if ($LASTEXITCODE -eq 0 -and $tracked) {
        $mode = (& git ls-files -s $macScript).Split(' ')[0]
        if ($mode -ne '100755') {
            & git update-index --chmod=+x $macScript
            Write-Host "[OK] Da dat quyen thuc thi (+x) cho $macScript trong git." -ForegroundColor Green
        }
    } else {
        Write-Host "[!!] $macScript chua duoc git theo doi." -ForegroundColor Yellow
        Write-Host "     Sau khi 'git add', chay: git update-index --chmod=+x $macScript" -ForegroundColor Yellow
    }
}

$kb = [math]::Round((Get-Item $exeFile).Length / 1KB, 1)
Write-Host ""
if ($needCompile) {
    Write-Host "[OK] Da tao Setup.exe ($kb KB) tai thu muc goc." -ForegroundColor Green
} else {
    Write-Host "[OK] Setup.exe ($kb KB) da san sang, khong can bien dich lai." -ForegroundColor Green
}
Write-Host "     Bam dup Setup.exe de mo GUI quan ly moi truong." -ForegroundColor DarkGray

exit 0
