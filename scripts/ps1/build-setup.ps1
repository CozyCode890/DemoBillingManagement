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

& javac -encoding UTF-8 -d $outDir "@$sources"
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
