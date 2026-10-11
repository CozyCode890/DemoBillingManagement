@echo off
rem =====================================================================
rem  Setup.bat -- BAN DU PHONG cua Setup.exe
rem ---------------------------------------------------------------------
rem  Cach chay thong thuong: bam dup Setup.exe (khong co cua so den, tu
rem  do Java o 4 noi, thieu Java thi moi cai).
rem
rem  File .bat nay giu lai cho hai truong hop hiem:
rem    - Setup.exe bi phan mem diet virus cach ly hoac chinh sach cong ty
rem      chan chay file .exe khong co chu ky so.
rem    - Nguoi dung tai repo ve dang ZIP va Windows danh dau exe la
rem      "tai tu Internet".
rem =====================================================================
setlocal
cd /d "%~dp0"

rem Uu tien Setup.exe neu con nguyen.
if exist "%~dp0Setup.exe" (
    start "" "%~dp0Setup.exe"
    exit /b 0
)

if not exist "%~dp0SetupApp.jar" goto :no_jar

rem Uu tien javaw trong PATH (khong hien cua so cmd den).
where javaw >nul 2>&1
if %errorlevel% equ 0 (
    start "" javaw -Dfile.encoding=UTF-8 -jar "%~dp0SetupApp.jar"
    exit /b 0
)

rem Du phong: lay javaw tu JAVA_HOME.
if not "%JAVA_HOME%"=="" if exist "%JAVA_HOME%\bin\javaw.exe" (
    start "" "%JAVA_HOME%\bin\javaw.exe" -Dfile.encoding=UTF-8 -jar "%~dp0SetupApp.jar"
    exit /b 0
)

echo.
echo   [LOI] Khong tim thay Java (javaw) tren may nay.
echo.
echo   Cai JDK 21 bang lenh sau trong PowerShell:
echo       winget install EclipseAdoptium.Temurin.21.JDK
echo.
echo   Cai xong nho MO LAI cua so roi chay lai Setup.bat.
echo   (Setup.exe khong can mo lai cua so -- no tu tim JDK vua cai.)
echo.
pause
exit /b 1

:no_jar
echo.
echo   [LOI] Khong tim thay SetupApp.jar ben canh file Setup.bat.
echo.
echo   Dong goi lai bang lenh:
echo       powershell -ExecutionPolicy Bypass -File scripts\ps1\build-setup.ps1
echo.
pause
exit /b 1
