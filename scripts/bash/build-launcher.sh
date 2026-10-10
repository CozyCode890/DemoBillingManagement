#!/bin/bash
# =====================================================================
#  build-launcher.sh -- dựng launcher 1-click trên macOS / Linux
# ---------------------------------------------------------------------
#  Chỉ NGƯỜI BẢO TRÌ dự án cần chạy. Người dùng clone repo về là đã có
#  sẵn Setup.app và Setup.exe, bấm đúp chạy luôn.
#
#  Script làm 3 việc:
#    1. Sinh icon  -> tools/launcher/Setup.ico (cho Windows) và
#                     Setup.app/Contents/Resources/Setup.icns
#    2. Cấp quyền thực thi cho Setup.app/Contents/MacOS/Setup và ghi
#       quyền đó vào git (bước hay bị quên nhất).
#    3. Kiểm tra bundle có đủ file chưa.
#
#  Lưu ý: Setup.exe KHÔNG dựng được ở đây vì cần csc.exe của Windows.
#  Nó đã được commit sẵn trong repo; chỉ khi sửa WindowsLauncher.cs mới
#  phải chạy scripts/ps1/build-launcher.ps1 trên máy Windows.
#
#  Tham số:
#    --force   sinh lại icon dù đã có sẵn
# =====================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

GREEN='\033[0;32m'
CYAN='\033[0;36m'
RED='\033[0;31m'
GRAY='\033[0;90m'
YELLOW='\033[0;33m'
NC='\033[0m'

FORCE=0
if [ "${1:-}" = "--force" ]; then
    FORCE=1
fi

LAUNCHER_DIR="$PROJECT_ROOT/tools/launcher"
ICO_FILE="$LAUNCHER_DIR/Setup.ico"
ICNS_FILE="$PROJECT_ROOT/Setup.app/Contents/Resources/Setup.icns"
MAC_LAUNCHER="Setup.app/Contents/MacOS/Setup"

if [ "$(uname -s)" = "Darwin" ]; then
    if [ -x "/opt/homebrew/bin/brew" ]; then
        eval "$(/opt/homebrew/bin/brew shellenv)"
    elif [ -x "/usr/local/bin/brew" ]; then
        eval "$(/usr/local/bin/brew shellenv)"
    fi
    if [ -z "${JAVA_HOME:-}" ] && [ -x "/usr/libexec/java_home" ]; then
        JH=$(/usr/libexec/java_home 2>/dev/null || true)
        if [ -n "$JH" ]; then
            export JAVA_HOME="$JH"
            export PATH="$JAVA_HOME/bin:$PATH"
        fi
    fi
fi

# --- Bước 1: sinh icon -----------------------------------------------
if [ "$FORCE" = "1" ] || [ ! -f "$ICO_FILE" ] || [ ! -f "$ICNS_FILE" ]; then
    if ! command -v javac >/dev/null 2>&1; then
        echo -e "${RED}[LỖI] Cần JDK để sinh icon (tools/launcher/IconGen.java).${NC}"
        echo -e "${YELLOW}      Cài bằng: brew install --cask temurin@21${NC}"
        exit 1
    fi

    echo -e "${CYAN}Đang sinh icon từ IconGen.java...${NC}"
    TEMP_OUT="$(mktemp -d)"
    trap 'rm -rf "$TEMP_OUT"' EXIT
    javac -encoding UTF-8 -d "$TEMP_OUT" "$LAUNCHER_DIR/IconGen.java"
    java -cp "$TEMP_OUT" IconGen "$ICO_FILE" "$ICNS_FILE"
else
    echo -e "${GRAY}[BỎ QUA] Icon đã có sẵn (dùng --force để sinh lại).${NC}"
fi

# --- Bước 2: quyền thực thi cho launcher macOS -----------------------
# Thiếu bit +x thì macOS báo "ứng dụng bị hỏng" khi bấm đúp Setup.app.
# Phải ghi cả vào index của git, nếu không máy Windows clone về rồi
# commit tiếp sẽ xoá mất quyền này.
if [ -f "$MAC_LAUNCHER" ]; then
    chmod +x "$MAC_LAUNCHER"
    if command -v git >/dev/null 2>&1 && git ls-files --error-unmatch "$MAC_LAUNCHER" >/dev/null 2>&1; then
        MODE="$(git ls-files -s "$MAC_LAUNCHER" | cut -d' ' -f1)"
        if [ "$MODE" != "100755" ]; then
            git update-index --chmod=+x "$MAC_LAUNCHER"
            echo -e "${GREEN}[OK] Đã ghi quyền thực thi (+x) cho $MAC_LAUNCHER vào git.${NC}"
        fi
    fi
else
    echo -e "${RED}[LỖI] Thiếu $MAC_LAUNCHER${NC}"
    exit 1
fi

# --- Bước 3: kiểm tra bundle đủ file ---------------------------------
MISSING=0
for f in "Setup.app/Contents/Info.plist" \
         "Setup.app/Contents/PkgInfo" \
         "Setup.app/Contents/MacOS/Setup" \
         "Setup.app/Contents/Resources/Setup.icns"; do
    if [ ! -f "$PROJECT_ROOT/$f" ]; then
        echo -e "${RED}[THIẾU] $f${NC}"
        MISSING=1
    fi
done
if [ "$MISSING" = "1" ]; then
    exit 1
fi

# Trên macOS, Finder chỉ nhận ra bundle sau khi Launch Services đọc lại
# Info.plist. Chạm vào thư mục app để ép nó cập nhật icon ngay.
if [ "$(uname -s)" = "Darwin" ]; then
    touch "$PROJECT_ROOT/Setup.app"
    if [ -x /System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister ]; then
        /System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister \
            -f "$PROJECT_ROOT/Setup.app" >/dev/null 2>&1 || true
    fi
fi

echo ""
echo -e "${GREEN}[OK] Setup.app đã sẵn sàng.${NC}"
echo -e "${GRAY}     Bấm đúp Setup.app trong Finder để mở GUI quản lý môi trường.${NC}"
if [ ! -f "$PROJECT_ROOT/Setup.exe" ]; then
    echo -e "${YELLOW}     (Chưa thấy Setup.exe -- dựng trên Windows bằng scripts\\ps1\\build-launcher.ps1)${NC}"
fi
