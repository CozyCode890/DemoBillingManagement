$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $projectRoot

Write-Host "Dang bien dich Setup App..." -ForegroundColor Cyan
$outDir = Join-Path $projectRoot 'out\setup'
if (Test-Path $outDir) { Remove-Item -Recurse -Force $outDir }
New-Item -ItemType Directory $outDir | Out-Null

$sources = Join-Path $env:TEMP 'envsetup-sources.txt'
Get-ChildItem -Path (Join-Path $projectRoot 'src\envsetup') -Recurse -Filter '*.java' |
    ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' } |
    Set-Content -Path $sources -Encoding ASCII

# --release 21 la BAT BUOC, khong phai tuy chon:
# javac mac dinh sinh bytecode theo dung phien ban JDK dang chay. May bao
# tri dung JDK 25 se tao jar class version 69, va may hoc vien cai Temurin
# 21 (dung nhu README huong dan) se bam dup Setup.exe roi nhan loi
# UnsupportedClassVersionError. Khoa o 21 thi ai cung chay duoc.
& javac --release 21 -Xlint:-options -encoding UTF-8 -d $outDir "@$sources"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Bien dich that bai!" -ForegroundColor Red
    exit 1
}

$jarPath = Join-Path $projectRoot 'SetupApp.jar'
Write-Host "Dang dong goi SetupApp.jar tai $jarPath..." -ForegroundColor Cyan
& jar cfe $jarPath envsetup.Main -C $outDir .
if ($LASTEXITCODE -ne 0) {
    Write-Host "Dong goi JAR that bai!" -ForegroundColor Red
    exit 1
}

Remove-Item -Recurse -Force $outDir
Write-Host "[OK] Da tao thanh cong SetupApp.jar tai thu muc goc." -ForegroundColor Green

exit 0
